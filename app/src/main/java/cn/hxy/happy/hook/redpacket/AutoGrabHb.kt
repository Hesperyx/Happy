package cn.hxy.happy.hook.redpacket

import androidx.compose.runtime.Composable
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord
import cn.hxy.happy.annotation.HookCategory
import cn.hxy.happy.annotation.HookItemAnnotation
import cn.hxy.happy.conf.RedPacketConfig
import cn.hxy.happy.hook.api.ReceiveMsgListener
import cn.hxy.happy.hook.base.BaseClickableHookItem
import cn.hxy.happy.plugin.bean.MsgData
import cn.hxy.happy.plugin.bean.RedPacketContext
import cn.hxy.happy.ui.pages.configs.AutoGrabHbPage
import cn.hxy.happy.utils.log.LogUtils
import cn.hxy.happy.utils.qq.QQCurrentEnv

/**
 * 自动抢红包：监听接收消息，命中红包消息后自动抢。
 *
 * 逻辑说明：
 *  - 仅处理群聊（chatType == 2）的 wallet 消息（msgType == 10）
 *  - 白名单（whiteList）中的群跳过
 *  - targetTypes 过滤红包 channel 类型（1 普通/拼手气、32 口令、1024 专属、65536 语音）
 *  - 专属红包只领取自己的（grapUin 首元素 == 自己 uin）
 *  - keywords 命中的红包静默跳过（不抢、不发提示）
 */
@HookItemAnnotation(
    "自动领取红包",
    "点击可设置参数，回复语支持脚本图文消息",
    HookCategory.RED_PACKET,
    process = "All"
)
object AutoGrabHb : BaseClickableHookItem<RedPacketConfig>(RedPacketConfig.serializer()),
    ReceiveMsgListener {

    override val defaultConfig: RedPacketConfig = RedPacketConfig()

    override fun onReceive(msgRecord: MsgRecord) {
        if (msgRecord.msgType != 10 || msgRecord.chatType != 2) return
        LogUtils.d("[RedPacket] onReceive 收到群消息: msgType=${msgRecord.msgType}, chatType=${msgRecord.chatType}, peerUid=${msgRecord.peerUid}")
        if (msgRecord.peerUid in config.whiteList) return

        val walletElement = msgRecord.elements?.firstOrNull()?.walletElement ?: return

        val title = walletElement.receiver.title
        val listId = walletElement.billNo
        val authKey = walletElement.authkey
        val channel = walletElement.redChannel

        LogUtils.d("[RedPacket] onReceive 红包: billNo=$listId, channel=$channel, title=$title, authkey=$authKey")
        if (channel !in config.targetTypes) return

        // 专属红包（channel 1024）：只领取自己的，grapUin 首元素为指定收礼人 uin
        if (channel == 1024) {
            val specifyUin = walletElement.grapUin?.firstOrNull()?.toString()
            if (specifyUin != QQCurrentEnv.currentUin) {
                LogUtils.d("[RedPacket] 专属红包非指定收礼人（指定=$specifyUin），跳过")
                return
            }
        }

        // 关键词过滤：命中则静默跳过，不抢也不发提示
        val matched = config.keywords.any { it.isNotEmpty() && title.contains(it) }
        if (matched) {
            LogUtils.d("[RedPacket] 红包标题命中过滤关键词，静默跳过: title=$title")
            return
        }

        val ctx = RedPacketContext(
            msgData = MsgData(msgRecord),
            listId = listId,
            authKey = authKey,
            channel = channel,
            title = title,
            isAuto = true,
            config = config,
        )
        RedPacketHelper.startGrab(ctx)
    }

    @Composable
    override fun ConfigContent(onDismiss: () -> Unit) {
        AutoGrabHbPage(
            config = config,
            onSave = ::updateConfig,
            onDismiss = onDismiss,
        )
    }
}
