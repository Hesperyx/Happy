package cn.hxy.happy.hook.msg

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.core.view.isVisible
import com.tencent.qqnt.kernel.nativeinterface.IQQNTWrapperSession
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord
import com.tencent.qqnt.kernelpublic.nativeinterface.Contact
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import cn.hxy.happy.annotation.HookCategory
import cn.hxy.happy.annotation.HookItemAnnotation
import cn.hxy.happy.common.ModuleScope
import cn.hxy.happy.conf.RevokeConfig
import cn.hxy.happy.hook.api.AIOViewUpdateListener
import cn.hxy.happy.hook.base.BaseClickableHookItem
import cn.hxy.happy.loader.hookapi.HookParam
import cn.hxy.happy.plugin.bean.MsgData
import cn.hxy.happy.ui.pages.configs.RevokeConfigPage
import cn.hxy.happy.utils.hook.hookBefore
import cn.hxy.happy.utils.io.ObjectStore
import cn.hxy.happy.utils.log.LogUtils
import cn.hxy.happy.utils.qq.FriendTool
import cn.hxy.happy.utils.qq.MsgTool
import cn.hxy.happy.utils.qq.NtGrayTipJsonBuilder
import cn.hxy.happy.utils.qq.QQCurrentEnv
import cn.hxy.happy.utils.qq.TroopTool
import cn.hxy.happy.utils.reflect.findMethod
import top.artmoe.inao.entries.InfoSyncPushOuterClass
import top.artmoe.inao.entries.MsgPushOuterClass
import top.artmoe.inao.entries.QQMessageOuterClass
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@HookItemAnnotation(
    "防撤回",
    "拦截撤回指令并显示提示，点击可设置提示样式(需保活)",
    HookCategory.MSG
)
object AntiRevoke : BaseClickableHookItem<RevokeConfig>(RevokeConfig.serializer()),
    AIOViewUpdateListener {

    override val defaultConfig: RevokeConfig = RevokeConfig()

    /**
     * 撤回记录表。
     *
     * 值必须是线程安全的集合：记录写在 MsfPush 线程（[recordRevoke]），读却在主线程
     * （[typeOf] / [isRevoked] 遍历）。之前值是普通 ArrayList，被并发修改时主线程会抛
     * ConcurrentModificationException —— 那是主线程未捕获异常，直接让 QQ 崩溃重启，
     * 于是每崩一次就生成一个崩溃报告，手机里攒了 2800 多个、2.6GB。改用 CopyOnWriteArrayList。
     */
    private var retractMessageMap = ConcurrentHashMap<String, CopyOnWriteArrayList<String>>()
    private const val STORE_KEY = "RetractMessageMap"

    private const val VIEW_TAG = "RevokeTagView"

    /**
     * 文案字号（模了个块 6.0 逆向文档 §6.1：setTextSize(16.0f)）
     */
    private const val TIPS_TEXT_SIZE_SP = 16f

    /** 配置缺省时的文字顶部距离（dp） */
    private const val DEFAULT_TOP_MARGIN = 10

    /** 行容器没就绪时最多等几帧再画，避免刚进聊天页时标记先闪在别的位置 */
    private const val RETRY_FRAMES = 2

    /**
     * 撤回类型（对应文档 §4.1 落盘的「id|type」里的 type，UI 端按同一枚举选文案）
     * 文档 §6.1 只有三类：1=管理员撤回、2=群主撤回、其它=自己撤回
     */
    private const val TYPE_SELF = 0      // 自己撤回（消息发送者自己撤回）
    private const val TYPE_ADMIN = 1     // 管理员撤回
    private const val TYPE_OWNER = 2     // 群主撤回
    private const val TYPE_PEER = 3      // 私聊：对端撤回（0 区分不出「我撤」和「对方撤」，色带文案要分档）

    private val mapSerializer =
        MapSerializer(String.serializer(), ListSerializer(String.serializer()))

    private fun loadRetractMap() {
        ObjectStore.load("data", STORE_KEY, mapSerializer)?.let { loaded ->
            loaded.forEach { (k, v) ->
                retractMessageMap[k] = CopyOnWriteArrayList(v)
            }
        }
    }

    private fun saveRetractMap() {
        ObjectStore.save(
            "data",
            STORE_KEY,
            retractMessageMap.toMap().mapValues { it.value.toList() },
            mapSerializer
        )
    }

    override fun initData() {
        super.initData()
        loadRetractMap()
    }

    override fun onHook() {
        IQQNTWrapperSession.CppProxy::class.java
            .findMethod {
                name = "onMsfPush"
            }.hookBefore(this) { param ->
                val cmd = param.args[0] as String
                val protoBuf = param.args[1] as ByteArray

                try {
                    when (cmd) {
                        "trpc.msg.register_proxy.RegisterProxy.InfoSyncPush" -> {
                            handleInfoSyncPush(protoBuf, param)
                        }

                        "trpc.msg.olpush.OlPushService.MsgPush" -> {
                            handleMsgPush(protoBuf, param)
                        }
                    }
                } catch (t: Throwable) {
                    LogUtils.e(this, t)
                }
            }
    }

    override fun onUpdate(frameLayout: FrameLayout, msgRecord: MsgRecord) {
        val msgData = MsgData(msgRecord)
        // 把「这一格现在显示的是哪条消息」记在 View 上：
        // 延迟帧重试时必须重新读，否则行被复用后会把标记画到别人身上（用户反馈的闪动）
        rowMessage[frameLayout] = msgData.peerUid to msgRecord.msgSeq.toString()
        renderTip(frameLayout, RETRY_FRAMES)
    }

    /**
     * 画标记。**布局没落定的时候不下结论**：宁可等下一帧再画，也不要先按兜底方案画一次，
     * 否则刚进聊天页时会看到标记先闪在别的位置（用户反馈：从消息上方闪到设置位置）
     */
    private fun renderTip(frameLayout: FrameLayout, retry: Int) {
        val (peerUid, msgSeq) = rowMessage[frameLayout] ?: return
        val rowRoot = findRowRoot(frameLayout)

        if (!isRevoked(peerUid, msgSeq)) {
            rowRoot?.let { clearBand(it) }
            frameLayout.findViewWithTag<TextView>(VIEW_TAG)?.isVisible = false
            return
        }

        // 优于文档的标记方式：整行铺满色带（范围与 QQ 截图选中一致），文案仍按文档三分类
        if (rowRoot != null) {
            frameLayout.findViewWithTag<TextView>(VIEW_TAG)?.isVisible = false
            applyBand(rowRoot, tipText(peerUid, msgSeq))
            return
        }

        if (retry > 0) {
            frameLayout.post { renderTip(frameLayout, retry - 1) }
            return
        }

        // 兜底：实在拿不到整行容器时才退回文档 §6.1 的行内提示
        drawInlineTip(frameLayout, peerUid, msgSeq)
    }

    /**
     * 撤回事件里主动补一次色带，而不是等 QQ 重新绑定那一行。
     *
     * 色带是在 onUpdate（QQ 绑定气泡视图）里画的。私聊撤回后 QQ 会刷新这条会话的底部，
     * 被撤那行很快重新绑定，所以立刻能看到；群撤回的推送被我们清空后，QQ 对那条消息
     * 什么都没做，那一行不会被重新绑定，色带要等下次绑定才出现（表现就是「群聊要退出重进才有」）。
     * 这里在记录完撤回后，直接扫一遍当前已联系起来的行，命中 (peerId, seq) 的当场铺色带，
     * 让两条链路表现一致。
     */
    private fun bandBoundRows(peerId: String, seq: Int) {
        if (peerId.isEmpty() || seq <= 0) return
        val seqStr = seq.toString()
        Handler(Looper.getMainLooper()).post {
            val text = tipText(peerId, seqStr)
            for ((frame, key) in rowMessage.entries.toList()) {
                if (key.first != peerId || key.second != seqStr) continue
                if (!frame.isAttachedToWindow) continue
                val rowRoot = findRowRoot(frame) ?: continue
                applyBand(rowRoot, text)
            }
        }
    }

    /** 文档 §6.1 的行内提示：气泡列里塞一个 TextView（仅在找不到整行容器时使用） */
    private fun drawInlineTip(frameLayout: FrameLayout, peerUid: String, msgSeq: String) {
        var tipsView = frameLayout.findViewWithTag<TextView>(VIEW_TAG)

        if (tipsView != null && tipsView.parent !== frameLayout) {
            (tipsView.parent as? ViewGroup)?.removeView(tipsView)
            tipsView = null
        }

        if (tipsView == null) {
            tipsView = TextView(frameLayout.context).apply {
                tag = VIEW_TAG
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                }
            }
            frameLayout.addView(tipsView)
        }

        val cfg = config
        tipsView.text = tipText(peerUid, msgSeq)
        tipsView.textSize = TIPS_TEXT_SIZE_SP
        tipsView.setTextColor(cfg.textColor)
        tipsView.setBackgroundColor(cfg.bgColor)
        tipsView.gravity = Gravity.CENTER
        (tipsView.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
            val margin = (cfg.topMargin.toIntOrNull() ?: DEFAULT_TOP_MARGIN)
                .coerceIn(0, 200)
            val marginPx =
                (margin * frameLayout.resources.displayMetrics.density).toInt()
            if (lp.topMargin != marginPx) {
                lp.topMargin = marginPx
                tipsView.layoutParams = lp
            }
        }
        tipsView.isVisible = true
    }

    @Composable
    override fun ConfigContent(onDismiss: () -> Unit) {
        RevokeConfigPage(
            currentConfig = config,
            onSave = ::updateConfig,
            onDismiss = onDismiss
        )
    }

    /**
     * 文档 §6.1 的三分类文案：管理员撤回 / 群主撤回 / 自己撤回
     */
    private fun tipText(peerUid: String, msgSeq: String): String {
        return when (typeOf(peerUid, msgSeq)) {
            TYPE_ADMIN -> "该消息被管理员撤回"
            TYPE_OWNER -> "该消息被群主撤回"
            TYPE_PEER -> "该消息被对方撤回"
            else -> "该消息被自己撤回"
        }
    }

    // ============ 行级色带：比文档的「气泡内嵌 TextView」更贴近 QQ 的撤回观感 ============

    /** 行根 → 原始背景：列表复用后要把 QQ 自己的背景还回去 */
    private val rowOriginalBackground = WeakHashMap<View, Drawable?>()

    /** 消息行（气泡列）→ 当前绑定的 (peerUid, msgSeq)，延迟帧重试时重新读 */
    private val rowMessage = WeakHashMap<View, Pair<String, String>>()

    /**
     * 向上找整条消息行的容器（QQ 的 TemplateLayout：整屏宽、高度含昵称行）。
     * 气泡列是 WRAP_CONTENT，只有行根是 MATCH_PARENT 且几乎满屏宽，据此识别。
     *
     * 额外加两道保险，避免把「还没测量完的行」或「整屏容器」当成行根——
     * 那会让标记先画在错误位置再跳回来（用户反馈的闪动）：
     *   · 宽/高都为 0 视为布局没落定，返回 null 让调用方下一帧重试
     *   · 高度超过屏幕 6 成的一律不认（单条消息行不可能那么高）
     */
    private fun findRowRoot(start: View): View? {
        val metrics = start.resources.displayMetrics
        val screenWidth = metrics.widthPixels
        val maxRowHeight = metrics.heightPixels * 6 / 10
        var node: View = start
        var fallback: View? = null
        repeat(4) {
            val parent = node.parent as? View ?: return fallback
            val lp = parent.layoutParams
            val fullWidth = lp != null && lp.width == ViewGroup.LayoutParams.MATCH_PARENT
            if (fullWidth && parent.width >= screenWidth * 0.9f &&
                parent.height in 1..maxRowHeight
            ) {
                // QQ 的消息行根就是 TemplateLayout；认出来就直接用它，
                // 认不出才退而取最外层的候选（气泡列内的中间 wrapper 不会白占位置）
                if (parent.javaClass.name.contains("TemplateLayout")) return parent
                fallback = parent
            }
            node = parent
        }
        return fallback
    }

    private fun applyBand(row: View, text: String) {
        val cfg = config
        val metrics = row.resources.displayMetrics
        val marginPx = ((cfg.topMargin.toIntOrNull() ?: DEFAULT_TOP_MARGIN)
            .coerceIn(0, 200) * metrics.density).toInt()
        val sizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            TIPS_TEXT_SIZE_SP,
            metrics
        )

        val exists = row.background as? RevokeBandDrawable
        val band = exists?.apply {
            this.text = text
            this.textColor = cfg.textColor
            this.bgColor = cfg.bgColor
            this.topMarginPx = marginPx
            this.textSizePx = sizePx
        } ?: RevokeBandDrawable(text, cfg.textColor, cfg.bgColor, marginPx, sizePx)

        if (band !== row.background) {
            if (!rowOriginalBackground.containsKey(row)) {
                rowOriginalBackground[row] = row.background
            }
            row.background = band
        }
        row.invalidate()
    }

    private fun clearBand(row: View) {
        if (row.background !is RevokeBandDrawable) return
        row.background = rowOriginalBackground.remove(row)
        row.invalidate()
    }

    /** 整行铺满的色带：底色铺满整行，文案居中画在昵称行位置（顶部距离可配） */
    private class RevokeBandDrawable(
        var text: String,
        var textColor: Int,
        var bgColor: Int,
        var topMarginPx: Int,
        var textSizePx: Float
    ) : Drawable() {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT
        }

        override fun draw(canvas: Canvas) {
            paint.color = bgColor
            canvas.drawRect(
                bounds.left.toFloat(),
                bounds.top.toFloat(),
                bounds.right.toFloat(),
                bounds.bottom.toFloat(),
                paint
            )

            paint.color = textColor
            paint.textSize = textSizePx
            val fm = paint.fontMetrics
            val baseline = bounds.top + topMarginPx + (fm.descent - fm.ascent) / 2f - fm.descent
            canvas.drawText(text, bounds.left + bounds.width() / 2f, baseline, paint)
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
        }

        @Suppress("OVERRIDE_DEPRECATION")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private fun handleInfoSyncPush(protoBuf: ByteArray, param: HookParam) {
        // 同一 cmd 下也可能混入别的业务包，本地 proto 解析失败是正常情况：直接跳过。
        // 这里一旦走 LogUtils.e，每个失败包都会往 error_log.txt 堆一段栈（实测刷到 14MB）
        val infoSyncPush = runCatching { InfoSyncPushOuterClass.InfoSyncPush.parseFrom(protoBuf) }
            .getOrElse { t ->
                LogUtils.d("AntiRevoke: infoSyncPush 解析失败 ${t.javaClass.simpleName}")
                return
            }
        if (!infoSyncPush.hasSyncRecallContent()) return

        val syncRecallContent = infoSyncPush.syncRecallContent
        val syncInfoBodyList = syncRecallContent.syncInfoBodyList

        var modified = false
        val newSyncRecallBuilder = syncRecallContent.toBuilder()
        val selfUid = selfUid()

        for (i in syncInfoBodyList.indices) {
            val syncInfoBody = syncInfoBodyList[i]
            val msgList = syncInfoBody.msgList

            val recallMsgList = msgList.filter { msg ->
                val content = msg.messageContentInfo
                isGroupRecall(content.msgType, content.msgSubType, content.subSeq) ||
                        isC2CRecall(content.msgType, content.msgSubType, content.subSeq)
            }

            if (recallMsgList.isNotEmpty()) {

                // 离线/历史撤回同步体：QQ 自己不会把消息删掉，但我们要把「谁撤的」记下来，
                // 让这条消息重新滚到屏幕里时也能标上文案（原来这里只静默 clearMsg，等于没防）
                for (msg in recallMsgList) {
                    val head = msg.messageHead
                    val isGroup = msg.messageContentInfo.msgType == 732
                    val senderIsMe = head.senderUid.isNotEmpty() && head.senderUid == selfUid
                    val peerKey = when {
                        isGroup -> head.senderPeerId.toString()
                        senderIsMe -> head.receiverUid
                        else -> head.senderUid
                    }
                    // 离线同步体里拿不到操作者身份，按文档 §6.1 的兜底分类记为「自己撤回」，
                    // 在线包进来时（onGroupRecallByMsgPush）会用群角色补正成管理员/群主。
                    // 三个字段都空（例如同步体里 senderUid 缺失）时不能记：
                    // 空串会被当成一个合法会话 key 落盘，撤回表里会攒出永远清不掉的脏数据
                    if (peerKey.isEmpty() || selfUid().isEmpty()) continue
                    runCatching {
                        recordRevoke(peerKey, msg.messageContentInfo.msgSeq, TYPE_SELF)
                    }
                }

                val newBody = syncInfoBody.toBuilder().clearMsg().build()
                newSyncRecallBuilder.setSyncInfoBody(i, newBody)
                modified = true
            }
        }

        if (modified) {
            val newInfoSyncPush = infoSyncPush.toBuilder()
                .setSyncRecallContent(newSyncRecallBuilder)
                .build()
            param.args[1] = newInfoSyncPush.toByteArray()
        }
    }

    private fun handleMsgPush(protoBuf: ByteArray, param: HookParam) {
        // 见 handleInfoSyncPush：解析失败是常态，静默跳过，不进 error 日志
        val msgPush = runCatching { MsgPushOuterClass.MsgPush.parseFrom(protoBuf) }
            .getOrElse { t ->
                LogUtils.d("AntiRevoke: msgPush 解析失败 ${t.javaClass.simpleName}")
                return
            }
        val qqMessage = msgPush.qqMessage
        val content = qqMessage.messageContentInfo

        val msgType = content.msgType
        val msgSubType = content.msgSubType
        val subSeq = content.subSeq

        val opBytes = qqMessage.messageBody.operationInfo.toByteArray()

        if (isGroupRecall(msgType, msgSubType, subSeq)) {
            onGroupRecallByMsgPush(opBytes, param)
        } else if (isC2CRecall(msgType, msgSubType, subSeq)) {
            onC2CRecallByMsgPush(opBytes, param)
        }
    }

    /**
     * 撤回判定（文档 §3.2 判定表：群 732/17、私聊 528/138）
     *
     * 真机上这两个值在两条路上落点不同：
     *  - 在线推送 MsgPush：msgType=732 / msgSubType=1 / subSeq=17（subType 只是「消息大类」）
     *  - 离线同步 InfoSyncPush：msgType=732 / msgSubType=17（没有 subSeq）
     * 所以 subType 与 subSeq 都要认，否则会像改之前那样一条都命中不了。
     */
    private fun isGroupRecall(msgType: Int, msgSubType: Int, subSeq: Int): Boolean {
        return msgType == 732 && (msgSubType == 17 || subSeq == 17)
    }

    private fun isC2CRecall(msgType: Int, msgSubType: Int, subSeq: Int): Boolean {
        return msgType == 528 && (msgSubType == 138 || subSeq == 138)
    }

    private fun onGroupRecallByMsgPush(opBytes: ByteArray, param: HookParam) {

        val realBytes = if (opBytes.size > 7) opBytes.copyOfRange(7, opBytes.size) else opBytes
        val info =
            QQMessageOuterClass.QQMessage.MessageBody.GroupRecallOperationInfo.parseFrom(realBytes)

        val msgInfo = info.info.msgInfo
        val msgTime = msgInfo.msgTime
        val msgSeq = msgInfo.msgSeq
        val groupPeerId = info.peerId.toString()
        val operatorUid = info.info.operatorUid
        val senderUid = msgInfo.senderUid

        if (isTimeExpired(msgTime)) {
            return
        }
        if (isRevoked(groupPeerId, msgSeq.toString())) {
            return
        }

        // 自己撤回自己的消息也要标（文档 §6.1：非管理员/群主的都归到「该消息被自己撤回」）。
        // 群主/管理员撤回的类型随后在 IO 线程里按群角色补正。
        recordRevoke(groupPeerId, msgSeq, TYPE_SELF)
        bandBoundRows(groupPeerId, msgSeq)

        // 清空载荷：QQ 拿不到撤回操作，就不会把这条消息删掉（防撤回的本体动作）
        param.args[1] = ByteArray(0)

        ModuleScope.launchIO(name) {
            val operatorUin = FriendTool.getUinFromUid(operatorUid)
            val member = runCatching { TroopTool.getMemberInfo(groupPeerId, operatorUin) }
                .getOrNull()

            val type = when (member?.role) {
                "OWNER" -> TYPE_OWNER
                "ADMIN" -> TYPE_ADMIN
                else -> TYPE_SELF
            }
            if (type != TYPE_SELF) {
                recordRevoke(groupPeerId, msgSeq, type)
                // 角色补正后文案要从「自己撤回」换成「管理员/群主撤回」，把已铺好的色带文字刷新一次
                bandBoundRows(groupPeerId, msgSeq)
            }

            val operatorName = member?.uinName ?: operatorUin
            val senderName = if (operatorUid != senderUid) {
                TroopTool.getMemberInfo(
                    groupPeerId,
                    FriendTool.getUinFromUid(senderUid)
                ).uinName
            } else null

            val builder = NtGrayTipJsonBuilder()

            builder.append(
                NtGrayTipJsonBuilder.UserItem(
                    FriendTool.getUinFromUid(operatorUid),
                    operatorUid,
                    operatorName
                )
            )

            builder.appendText(" 尝试撤回")

            if (senderName != null) {
                builder.append(
                    NtGrayTipJsonBuilder.UserItem(
                        FriendTool.getUinFromUid(senderUid),
                        senderUid,
                        senderName
                    )
                )
                builder.appendText(" 的")
            }

            builder.append(NtGrayTipJsonBuilder.MsgRefItem("一条消息", msgSeq))

            val contact = Contact(2, groupPeerId, "")
            MsgTool.addLocalGrayTipMsg(
                contact,
                builder.build().toString(),
                NtGrayTipJsonBuilder.AIO_AV_GROUP_NOTICE
            )
        }
    }

    private fun onC2CRecallByMsgPush(opBytes: ByteArray, param: HookParam) {

        // 私聊撤回 operationInfo 真实布局（QQ 9.3.70 真机实测，与本地 proto QQMessage.proto 一致）：
        //   op.1.1 = 会话对端 uid | op.1.2 = 本机 uid | op.1.5 = msgTime
        //   op.1.6 = msgRandom | op.20 = 被撤 msgSeq
        // 踩过的坑：op.3 不是序号（是随机量），按它记账会让撤回文案永远匹配不上消息行；
        // 且解析不完整时绝不能拿空 uid 去调 native，否则 QQ 直接 SIGSEGV。
        val reader = CodedInputReader(opBytes)
        var peerUid = ""
        var msgSeq = 0
        var msgTime = 0L
        var operatorUid = ""

        val protoInfo = runCatching {
            QQMessageOuterClass.QQMessage.MessageBody.C2CRecallOperationInfo.parseFrom(opBytes).info
        }.getOrNull()

        if (protoInfo != null) {
            val me = selfUid()
            operatorUid = protoInfo.operatorUid
            msgSeq = protoInfo.msgSeq
            msgTime = protoInfo.msgTime
            peerUid = when {
                operatorUid.isNotEmpty() && operatorUid != me -> operatorUid
                protoInfo.receiverUid.isNotEmpty() && protoInfo.receiverUid != me -> protoInfo.receiverUid
                else -> operatorUid.ifEmpty { protoInfo.receiverUid }
            }
        }

        if (peerUid.isEmpty() || msgSeq <= 0) {
            // 兜底：本地 proto 与真机报文对不上时按 wire 手工解（对端 uid 在 1.1/1.2，msgSeq 在 1.20）
            val me = selfUid()
            val v11 = reader.readString(1, 1)
            val v12 = reader.readString(1, 2)
            val seq = reader.readVarint(1, 20) ?: reader.readVarint(1, 3)
            val time = reader.readVarint(1, 5)
            if (v11.isNotEmpty() && v11 != me) {
                peerUid = v11
            } else if (v12.isNotEmpty() && v12 != me) {
                peerUid = v12
            }
            if (seq != null) msgSeq = seq.toInt()
            if (time != null) msgTime = time
        }

        // 解析不完整时绝不继续：空 uid 调 native 会让 QQ 直接崩
        if (peerUid.isEmpty() || msgSeq <= 0) {
            return
        }

        if (isTimeExpired(msgTime)) {
            return
        }
        if (isRevoked(peerUid, msgSeq.toString())) {
            return
        }

        val selfRecall = operatorUid.isNotEmpty() && operatorUid == selfUid()
        // 私聊分两档文案：拿得到操作者且不是自己 → 「对方撤回」，拿不到操作者时沿用旧的三分类兜底
        val recallType = if (operatorUid.isNotEmpty() && !selfRecall) TYPE_PEER else TYPE_SELF
        recordRevoke(peerUid, msgSeq, recallType)
        bandBoundRows(peerUid, msgSeq)

        // 清空载荷：QQ 拿不到撤回操作，就不会把这条消息删掉（防撤回的本体动作）
        param.args[1] = ByteArray(0)

        ModuleScope.launchIO(name) {
            val builder = NtGrayTipJsonBuilder()
            builder.appendText(if (selfRecall) "我" else "对方")
            builder.appendText(" 尝试撤回")
            builder.append(NtGrayTipJsonBuilder.MsgRefItem("一条消息", msgSeq))

            val contact = Contact(1, peerUid, "")
            MsgTool.addLocalGrayTipMsg(
                contact,
                builder.build().toString(),
                NtGrayTipJsonBuilder.AIO_AV_C2C_NOTICE
            )
        }
    }

    private fun selfUid(): String = runCatching {
        FriendTool.getUidFromUin(QQCurrentEnv.currentUin)
    }.getOrDefault("")

    private fun isTimeExpired(msgTime: Long): Boolean {
        return (System.currentTimeMillis() / 1000 - msgTime) > 3600
    }

    private fun isRevoked(peerId: String, seq: String): Boolean {
        return retractMessageMap[peerId]?.any { it == seq || it.startsWith("$seq|") } == true
    }

    /** 记录格式与文档 §4.1 一致：同一 seq 落两条，`id` 与 `id|type` */
    private fun recordRevoke(peerId: String, seq: Int, type: Int) {
        val seqStr = seq.toString()
        val list = retractMessageMap.computeIfAbsent(peerId) { CopyOnWriteArrayList() }
        // add/removeAll 在 CopyOnWriteArrayList 上各自线程安全，但这一组操作要连续，加锁避免交错
        synchronized(list) {
            if (!list.contains(seqStr)) list.add(seqStr)
            val typed = "$seqStr|$type"
            list.removeAll { it.startsWith("$seqStr|") && it != typed }
            if (!list.contains(typed)) list.add(typed)
        }
        saveRetractMap()
    }

    private fun typeOf(peerId: String, seq: String): Int {
        return retractMessageMap[peerId]
            ?.firstOrNull { it.startsWith("$seq|") }
            ?.substringAfter('|')
            ?.toIntOrNull()
            ?: TYPE_SELF
    }

    /**
     * 零依赖 protobuf wire 解析：只按 path 取需要的字段，用于真机报文与本地 proto 定义对不上的情况
     */
    private class CodedInputReader(private val buf: ByteArray) {

        fun readVarint(vararg path: Int): Long? {
            return find(buf, path, 0) as? Long
        }

        fun readString(vararg path: Int): String {
            return when (val raw = find(buf, path, 0)) {
                is String -> raw
                is ByteArray -> String(raw, Charsets.UTF_8)
                else -> ""
            }
        }

        /** 沿 path 逐层下钻：数字层取 varint，最后一层按 wire type 取值 */
        private fun find(bytes: ByteArray, path: IntArray, index: Int): Any? {
            if (index > path.lastIndex) return null
            val field = path[index]
            var offset = 0
            while (offset < bytes.size) {
                val tag = readRawVarint(bytes, offset) ?: return null
                var pos = tag.second
                val fieldNo = (tag.first shr 3).toInt()
                when ((tag.first and 0x7).toInt()) {
                    0 -> {
                        val value = readRawVarint(bytes, pos) ?: return null
                        pos = value.second
                        if (fieldNo == field) {
                            if (index == path.lastIndex) return value.first
                            return null
                        }
                    }

                    1 -> pos += 8

                    2 -> {
                        val len = readRawVarint(bytes, pos) ?: return null
                        pos = len.second
                        val size = len.first.toInt()
                        if (size < 0 || pos + size > bytes.size) return null
                        if (fieldNo == field) {
                            if (index == path.lastIndex) return bytes.copyOfRange(pos, pos + size)
                            return find(bytes.copyOfRange(pos, pos + size), path, index + 1)
                        }
                        pos += size
                    }

                    5 -> pos += 4

                    else -> return null
                }
                offset = pos
            }
            return null
        }

        private fun readRawVarint(bytes: ByteArray, from: Int): Pair<Long, Int>? {
            var result = 0L
            var shift = 0
            var offset = from
            while (offset < bytes.size) {
                val b = bytes[offset].toInt()
                result = result or ((b and 0x7F).toLong() shl shift)
                offset++
                if (b and 0x80 == 0) return result to offset
                shift += 7
                if (shift > 63) return null
            }
            return null
        }
    }
}
