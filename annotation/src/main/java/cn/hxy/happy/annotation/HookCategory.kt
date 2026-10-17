package cn.hxy.happy.annotation

object HookCategory {
    const val CHAT = "聊天"
    const val MSG = "消息"
    const val GROUP = "群聊"
    const val PURIFY = "净化"
    const val DEVICE = "设备"
    const val BEAUTY = "美化"
    const val NOTIFICATION = "通知"
    const val FILE = "文件"
    const val SOCIAL = "社交"
    const val RED_PACKET = "红包"
    const val OTHER = "其他"

    val ORDER = listOf(
        CHAT, MSG, GROUP, SOCIAL, RED_PACKET, PURIFY, DEVICE, BEAUTY, NOTIFICATION, FILE, OTHER
    )
}
