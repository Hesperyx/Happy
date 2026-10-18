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
import cn.hxy.happy.utils.hook.hookReplace
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

    /** 吞掉发送后，宿主多久内清空回复算「我们造成的」，超时就算用户自己取消 */
    private const val REPLY_CLEAR_GRACE_MS = 1500L

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

    /** 已插入输入框的 Span 对象，用来判断输入框里是否还留着我们插的图 */
    private val insertedSpans = ArrayList<Any>()

    /** 最近一次吞掉发送的时刻，用来判断宿主紧跟其后的「清空回复」是不是我们造成的 */
    private var lastHijackAt = 0L

    private val logTime = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US)

    private fun stamp(): String = logTime.format(java.util.Date())

    /** 打日志用：取调用栈前几帧，确认这次发送是用户点的还是宿主自己补发的 */
    private fun callerStack(): String =
        Throwable().stackTrace
            .filter { it.className != Thread::class.java.name }
            .drop(2)
            .take(6)
            .joinToString(" <- ") { "${it.className.substringAfterLast('.')}.${it.methodName}" }

    /** 统计输入框里仍然存在的自定义图片 Span 数量 */
    private fun spansInBox(): Int {
        val edit = getInputEdit() ?: return 0
        val text = edit.text
        if (text !is android.text.Spanned) return 0
        return insertedSpans.count { text.getSpanStart(it) >= 0 }
    }

    override fun onSend(elements: ArrayList<MsgElement>) {
        logSkip("onSend", elements)
        // 只有「本次发送仅由一张图片构成」时才需要暂存进输入框；
        // 回复引用等附属元素可以和图片共存，不能因为它们就放弃拦截。
        val pic = resolveSolePicture(elements)
        if (pic == null) {
            // 正常发送（纯文字 / 图文混排）：如果上一次暂存吞掉了回复，这里补回去
            if (pendingReply != null) {
                LogUtils.d("MixedEmoAndText: [${stamp()}] mixed-send-caller ${callerStack()}")
            }
            restorePendingReply(elements)
            return
        }
        val edit = getInputEdit()
        val boxLen = edit?.text?.length ?: 0
        val spans = spansInBox()
        // 宿主发送输入框内容时会先把输入框清空，并且带上正文文本元素；
        // 而用户再次选图时输入框里的内容还在。只有前者才该放行。
        val sendingBoxContent = boxLen == 0 || spans == 0 || elements.any { it.textElement != null }
        if (isInsertedPic(pic) && sendingBoxContent) {
            // 这张图是之前插进输入框的那张，宿主现在把输入框内容整条发出，交还宿主正常发出
            LogUtils.d(
                "MixedEmoAndText: [${stamp()}] host-send-inserted-pic path=" +
                        "${pic.picElement?.sourcePath} boxLen=$boxLen spansInBox=$spans"
            )
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
        lastHijackAt = SystemClock.elapsedRealtime()
        logSkip("intercepted", elements)
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
        insertedSpans.clear()
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
        LogUtils.d("MixedEmoAndText: [${stamp()}] remember-pic path=$path")
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
        // 这里必须用 replace：宿主在我们吞掉发送后会紧跟着派发一次 ClearReplyUIState，
        // 必须在它到达 UI 之前拦下来，否则引用条会先消失，事后重派也来不及（用户已看到闪烁）。
        handleUIState.hookReplace(this) { chain ->
            val state = chain.args.firstOrNull()
            when (state) {
                is InputReplyUIState.SetReplyUIState -> {
                    isReplying = true
                    chain.proceed()
                }

                is InputReplyUIState.ClearReplyUIState -> {
                    val hijackedRecently =
                        SystemClock.elapsedRealtime() - lastHijackAt < REPLY_CLEAR_GRACE_MS
                    LogUtils.d(
                        "MixedEmoAndText: [${stamp()}] ui-clear-reply " +
                                "hijackedRecently=$hijackedRecently pending=${pendingReply != null}"
                    )
                    if (hijackedRecently && pendingReply != null) {
                        // 这条清空是宿主吞掉发送后自己补的，用户并没有取消回复：
                        // 直接不派发，引用条原地保留。
                        LogUtils.d("MixedEmoAndText: [${stamp()}] ui-clear-reply-skipped")
                        null
                    } else {
                        // 用户主动取消回复，或真的发送完成：暂存的引用作废
                        isReplying = false
                        pendingReply = null
                        chain.proceed()
                    }
                }

                else -> chain.proceed()
            }
        }
    }

    private fun insertImage(localPath: String): Boolean {
        val created = createImageSpan(localPath) ?: return false
        val (text, spanObj) = created
        if (!insertAtCursor(text)) return false
        insertedSpans.add(spanObj)
        LogUtils.d(
            "MixedEmoAndText: [${stamp()}] insert path=$localPath " +
                    "spanClass=${spanObj.javaClass.simpleName} " +
                    "spansInBox=${spansInBox()} editLen=${getInputEdit()?.text?.length ?: -1}"
        )
        return true
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

    private fun createImageSpan(localPath: String): Pair<CharSequence, Any>? {

        val file = File(localPath)
        if (!file.exists()) return null

        val customSpan = createImageSpan.invoke(
            requireClass("ImageUtils").instance,
            localPath,
            Uri.fromFile(file),
            false
        ) ?: return null

        val text = SpannableStringBuilder("图片").apply {
            setSpan(customSpan, 0, 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return text to customSpan
    }

    /** 记录每一次发送的元素构成与判定原因，排查「表情没插进输入框」这类问题用 */
    private fun logSkip(reason: String, elements: List<MsgElement>) {
        val edit = getInputEdit()
        val text = elements.firstOrNull { it.textElement != null }?.textElement?.content
        val paths = elements.mapNotNull { it.picElement?.sourcePath }
        LogUtils.d(
            "MixedEmoAndText: [${stamp()}] $reason types=${elements.map { it.elementType }} " +
                    "pics=${elements.count { it.picElement != null || it.elementType == ELEMENT_TYPE_PIC }} " +
                    "texts=${elements.count { it.textElement != null }} " +
                    "reply=${elements.firstOrNull { it.replyElement != null }?.replyElement?.replayMsgId} " +
                    "isReplying=$isReplying edit=${edit != null} editLen=${edit?.text?.length ?: -1} " +
                    "spansInBox=${spansInBox()} editText=${edit?.text?.toString()?.take(32)} " +
                    "textSample=${text?.take(32)} paths=$paths " +
                    "insertedPics=${insertedPics.keys.map { File(it).name }} " +
                    "pendingReply=${pendingReply?.replyElement?.replayMsgId}"
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
