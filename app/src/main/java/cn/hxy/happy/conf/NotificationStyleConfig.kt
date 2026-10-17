package cn.hxy.happy.conf

import kotlinx.serialization.Serializable

/**
 * MessagingStyle 通知的可选项（对应 TCQT 的 multiIntOption 位集）。
 */
@Serializable
data class NotificationStyleConfig(
    /** 位集合：0=禁用子渠道发送通知，1=禁用通知气泡，2=自动清除多余通知子渠道 */
    val options: Set<Int> = emptySet()
) {
    val disableConversationSubChannel: Boolean
        get() = OPTION_DISABLE_SUB_CHANNEL in options || autoClearConversationSubChannel

    val disableBubble: Boolean
        get() = OPTION_DISABLE_BUBBLE in options

    val autoClearConversationSubChannel: Boolean
        get() = OPTION_AUTO_CLEAR_SUB_CHANNEL in options

    companion object {
        const val OPTION_DISABLE_SUB_CHANNEL = 0
        const val OPTION_DISABLE_BUBBLE = 1
        const val OPTION_AUTO_CLEAR_SUB_CHANNEL = 2

        /** 勾选「自动清除」时强制勾上「禁用子渠道」。 */
        val FORCED_SELECTIONS = mapOf(OPTION_AUTO_CLEAR_SUB_CHANNEL to listOf(OPTION_DISABLE_SUB_CHANNEL))
    }
}
