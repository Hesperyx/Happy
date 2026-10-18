package cn.hxy.happy.hook.chat

import android.annotation.SuppressLint
import android.app.Activity
import android.net.Uri
import android.os.SystemClock
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import com.tencent.input.base.mvicompat.DelegateMediator
import com.tencent.mobileqq.aio.input.reply.InputReplyUIState
import com.tencent.qqnt.kernel.nativeinterface.MsgElement
import cn.hxy.happy.annotation.HookCategory
import cn.hxy.happy.annotation.HookItemAnnotation
import cn.hxy.happy.hook.api.SendMsgListener
import cn.hxy.happy.hook.base.BaseSwitchHookItem
import cn.hxy.happy.utils.dexkit.DexKitTask
import cn.hxy.happy.utils.hook.hookAfter
import cn.hxy.happy.utils.log.LogUtils
import cn.hxy.happy.utils.qq.HostInfo
import cn.hxy.happy.utils.qq.QQCurrentEnv
import cn.hxy.happy.utils.reflect.findMethod
import cn.hxy.happy.utils.reflect.instance
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.base.BaseMatcher
import java.io.File
import java.lang.reflect.Method

@HookItemAnnotation(
    "发送文字表情复合消息",
    "当输入框有任意内容时，点击表情会暂存在输入框中与其他消息混合发送",
    HookCategory.CHAT
)
object MixedEmoAndText : BaseSwitchHookItem(), SendMsgListener, DexKitTask {

    private const val ELEMENT_TYPE_PIC = 2

    /** 输入框 EditText 的资源 id，按优先级尝试，宿主版本不同可能换名 */
    private val inputIds = arrayOf("input", "input_et", "aio_input", "inputEdit", "et_input")

    /** 回复引用最多替用户保留这么久，超时就当作草稿已放弃 */
    private const val PENDING_REPLY_TTL_MS = 3 * 60 * 1000L

    /** 暂存图片的识别窗口，超过就不再把同路径的图片当成「刚插入的那张」 */
    private const val INSERTED_PIC_TTL_MS = 5 * 60 * 1000L

    private lateinit var createImageSpan: Method

    private lateinit var handleUIState: Method

    private var isReplying = false

    /**
     * 被暂存吞掉的那次发送里的回复引用。
     * 宿主在点发送时就把回复状态清掉了，所以表情被暂存后输入框的引用条会消失；
     * 这里把宿主当时构造好的回复元素留住，等用户真正点发送时再补回发送列表。
     */
    private var pendingReply: MsgElement? = null

    private var pendingReplyAt = 0L

    /** 已经插进输入框的图片路径 → 插入时刻，用来认出「这张图本来就是暂存进去的」 */
    private val insertedPics = HashMap<String, Long>()

    override fun onSend(elements: ArrayList<MsgElement>) {
        // 只有「本次发送仅由一张图片构成」时才需要暂存进输入框；
        // 回复引用等附属元素可以和图片共存，不能因为它们就放弃拦截。
        val pic = resolveSolePicture(elements)
        if (pic == null) {
            // 正常发送（纯文字 / 图文混排）：如果上一次暂存吞掉了回复，这里补回去
            restorePendingReply(elements)
            return
        }
        if (isInsertedPic(pic)) {
            // 这张图就是之前暂存进输入框的那张，用户现在点发送了，交还宿主正常发出
            restorePendingReply(elements)
            return
        }
        if (!isReplying && getInputEdit()?.text.isNullOrEmpty()) {
            logSkip("no-content-in-box", elements)
            return
        }
        val path = pic.picElement?.sourcePath
        if (path.isNullOrEmpty()) {
            logSkip("empty-source-path", elements)
            return
        }
        if (!insertImage(path)) {
            // 拿不到 Span 就让宿主照常发送，避免把用户的消息吞掉
            logSkip("insert-span-failed", elements)
            return
        }
        rememberInsertedPic(path)
        captureReply(elements)
        elements.clear()
    }

    /** 记住这次发送带上的回复引用，供后续真正发送时补回 */
    private fun captureReply(elements: ArrayList<MsgElement>) {
        val reply = elements.firstOrNull { it.replyElement != null } ?: return
        pendingReply = reply
        pendingReplyAt = SystemClock.elapsedRealtime()
        LogUtils.d("MixedEmoAndText: capture-reply msgId=${reply.replyElement?.replayMsgId}")
    }

    /**
     * 把被暂存吞掉的回复引用补回本次发送。
     * 宿主自己带了回复（引用条还在）时不重复添加；超过时限也丢弃，避免挂到无关消息上。
     */
    private fun restorePendingReply(elements: ArrayList<MsgElement>) {
        insertedPics.clear()
        val reply = pendingReply ?: return
        pendingReply = null
        if (elements.isEmpty()) return
        if (elements.any { it.replyElement != null }) return
        if (elements.none { it.textElement != null || it.picElement != null }) return
        if (SystemClock.elapsedRealtime() - pendingReplyAt > PENDING_REPLY_TTL_MS) {
            LogUtils.d("MixedEmoAndText: drop-stale-reply")
            return
        }
        elements.add(0, reply)
        LogUtils.d("MixedEmoAndText: restore-reply msgId=${reply.replyElement?.replayMsgId}")
    }

    private fun rememberInsertedPic(path: String) {
        val now = SystemClock.elapsedRealtime()
        insertedPics.entries.removeAll { now - it.value > INSERTED_PIC_TTL_MS }
        insertedPics[path] = now
    }

    private fun isInsertedPic(pic: MsgElement): Boolean {
        val path = pic.picElement?.sourcePath ?: return false
        val at = insertedPics[path] ?: return false
        return SystemClock.elapsedRealtime() - at <= INSERTED_PIC_TTL_MS
    }

    /**
     * 判断本次发送是否恰好是「一张图片」。
     * 除这张图片外，其余元素只允许是回复引用这类不会独立成消息的附属元素；
     * 只要出现正文文本或第二张图片就交还宿主正常发送。
     */
    private fun resolveSolePicture(elements: ArrayList<MsgElement>): MsgElement? {
        if (elements.isEmpty()) return null
        if (elements.any { it.textElement != null }) return null
        val pictures = elements.filter { it.picElement != null || it.elementType == ELEMENT_TYPE_PIC }
        if (pictures.size != 1) return null
        val pic = pictures[0]
        if (elements.any { it !== pic && it.replyElement == null }) return null
        return pic
    }

    override fun onInit(): Boolean {
        createImageSpan = requireClass("ImageUtils").findMethod {
            paramTypes(string, Uri::class.java, boolean)
        }
        handleUIState = DelegateMediator::class.java.findMethod {
            returnType = void
            paramTypes(obj)
        }
        return super.onInit()
    }

    override fun onHook() {
        handleUIState.hookAfter(this) { param ->
            val state = param.args.firstOrNull()
            isReplying = when (state) {
                is InputReplyUIState.SetReplyUIState -> true
                is InputReplyUIState.ClearReplyUIState -> false
                else -> isReplying
            }
        }
    }

    private fun insertImage(localPath: String): Boolean {
        val span = createImageSpan(localPath) ?: return false
        return insertAtCursor(span)
    }

    private fun insertAtCursor(text: CharSequence): Boolean {
        val edit = getInputEdit() ?: return false
        edit.post {
            val start = edit.selectionStart.coerceIn(0, edit.text.length)
            val end = edit.selectionEnd.coerceIn(start, edit.text.length)
            edit.text.replace(start, end, text)
        }
        return true
    }

    @SuppressLint("DiscouragedApi")
    private fun getInputEdit(): EditText? = runCatching {
        val activity = QQCurrentEnv.activity ?: return@runCatching null
        activity.findInputById() ?: activity.findEditTextInWindow()
    }.getOrNull()

    @SuppressLint("DiscouragedApi")
    private fun Activity.findInputById(): EditText? {
        for (name in inputIds) {
            val id = runCatching {
                resources.getIdentifier(name, "id", HostInfo.packageName)
            }.getOrDefault(0)
            if (id == 0) continue
            findViewById<EditText>(id)?.let { return it }
        }
        return null
    }

    /** id 全都不匹配时（宿主换资源名）退化为在窗口里找聚焦的输入框 */
    private fun Activity.findEditTextInWindow(): EditText? {
        val root = runCatching { window?.decorView }.getOrNull() ?: return null
        return root.findEditText(0)
    }

    private fun View.findEditText(depth: Int): EditText? {
        if (depth > 32) return null
        if (this is EditText) {
            if (isFocused) return this
            val name = runCatching { resources.getResourceEntryName(id) }.getOrNull()
            if (name != null && name.contains("input", ignoreCase = true)) return this
        }
        if (this is ViewGroup) {
            for (i in 0 until childCount) {
                getChildAt(i).findEditText(depth + 1)?.let { return it }
            }
        }
        return null
    }

    private fun createImageSpan(localPath: String): SpannableStringBuilder? {

        val file = File(localPath)
        if (!file.exists()) return null

        val customSpan = createImageSpan.invoke(
            requireClass("ImageUtils").instance,
            localPath,
            Uri.fromFile(file),
            false
        ) ?: return null

        return SpannableStringBuilder("图片").apply {
            setSpan(customSpan, 0, 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    /** 只记录「本该合并却没合并」的发送，普通发送不产生日志 */
    private fun logSkip(reason: String, elements: ArrayList<MsgElement>) {
        if (elements.isEmpty()) return
        val picCount = elements.count { it.picElement != null || it.elementType == ELEMENT_TYPE_PIC }
        val textCount = elements.count { it.textElement != null }
        if (picCount == 0 && textCount == 0) return
        val edit = getInputEdit()
        LogUtils.d(
            "MixedEmoAndText: skip=$reason types=${elements.map { it.elementType }} " +
                    "pics=$picCount texts=$textCount " +
                    "reply=${elements.count { it.replyElement != null }} " +
                    "isReplying=$isReplying edit=${edit != null} textLen=${edit?.text?.length ?: -1}"
        )
    }

    override fun getQueryMap(): Map<String, BaseMatcher> = mapOf(
        "ImageUtils" to FindClass().apply {
            searchPackages("com.tencent.mobileqq.aio.input.fullscreen")
            matcher {
                usingStrings("sampleSize")
            }
        }
    )

}
