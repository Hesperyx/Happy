package cn.hxy.happy.plugin.bean

import kotlinx.serialization.Serializable

/**
 * 从生成的 main.java 静态提炼出的能力摘要 —— 生成式 UI 的数据源。
 *
 * 模型交出来的只有纯文本代码，用户在落盘前其实看不懂它会做什么。
 * 这里把代码读成「几个菜单、挂了哪些事件、存了哪些字段、动了哪些 API」，
 * 让确认卡能用人的语言把脚本说清楚。
 */
@Serializable
data class AgentScriptInsight(
    /** 悬浮窗 addItem 注册的菜单项标题。 */
    val menuItems: List<String> = emptyList(),
    /** addMenuItem 注册的长按/菜单项。 */
    val messageMenuItems: List<String> = emptyList(),
    /** 实际实现的事件回调方法名。 */
    val callbacks: List<String> = emptyList(),
    /** 通过 put/get 系列写入的持久化字段名。 */
    val configNames: List<String> = emptyList(),
    /** 触达的能力模块（消息发送、群管理……）。 */
    val apiGroups: List<String> = emptyList(),
) {
    val isEmpty: Boolean
        get() = menuItems.isEmpty() && messageMenuItems.isEmpty() &&
                callbacks.isEmpty() && configNames.isEmpty() && apiGroups.isEmpty()

    /** 一句话概括，用于确认卡与气泡角标。 */
    val summary: String
        get() {
            if (isEmpty) return "未识别到可展示的能力"
            return buildList {
                if (menuItems.isNotEmpty()) add("${menuItems.size} 个悬浮菜单")
                if (messageMenuItems.isNotEmpty()) add("${messageMenuItems.size} 个菜单项")
                if (callbacks.isNotEmpty()) add("${callbacks.size} 个事件回调")
                if (configNames.isNotEmpty()) add("${configNames.size} 个配置项")
                if (apiGroups.isNotEmpty()) add("${apiGroups.size} 类 API")
            }.joinToString(" · ")
        }

    companion object {
        private val ADD_ITEM = Regex("""addItem\s*\(\s*"([^"]{1,40})"\s*,\s*"([^"]{1,60})"""")
        private val ADD_MENU_ITEM = Regex("""addMenuItem\s*\(\s*"([^"]{1,40})"""")

        /** 只认「有方法体声明」的回调，避免把注释或字符串里出现的名字算进来。 */
        private val CALLBACK_NAMES = listOf(
            "onMsg", "onGroupMsg", "onFriendMsg", "onLoadPlugin", "unLoadPlugin",
            "onJoinGroup", "onQuitGroup", "onShutUp", "onPoke", "onChatInterface",
            "onPaiYiPai", "onCreate", "onDestroy", "onTroopManager",
        )

        private val CONFIG_CALL =
            Regex("""(?:put|get)(?:String|Int|Long|Boolean|Float|Double)\s*\(\s*"([^"]{1,40})"""")

        private val API_GROUPS: Map<String, List<String>> = linkedMapOf(
            "消息发送" to listOf(
                "sendMsg", "sendPic", "sendPtt", "sendCard", "sendFile", "sendVideo",
                "sendReplyMsg", "sendArk",
            ),
            "消息操作" to listOf("recallMsg", "sendPai"),
            "好友" to listOf(
                "getAllFriend", "isFriend", "sendZan", "getUidFromUin", "getUinFromUid",
            ),
            "群管理" to listOf(
                "kickGroup", "shutUp", "shutUpAll", "setGroupAdmin",
                "setGroupMemberTitle", "changeMemberName",
            ),
            "群查询" to listOf(
                "getGroupList", "getGroupMemberList", "getProhibitList", "getGroupInfo",
                "getMemberInfo", "isShutUp", "clockIn",
            ),
            "凭证" to listOf(
                "getSkey", "getRealSkey", "getPskey", "getPt4Token", "getStweb",
                "getGTK", "getGroupRKey", "getFriendRKey", "getBkn", "getCookie",
            ),
            "持久化" to listOf(
                "putString", "putInt", "putLong", "putBoolean",
                "getString", "getInt", "getLong", "getBoolean",
            ),
        )

        fun analyze(mainJava: String): AgentScriptInsight {
            if (mainJava.isBlank()) return AgentScriptInsight()

            return AgentScriptInsight(
                menuItems = ADD_ITEM.findAll(mainJava)
                    .map { it.groupValues[1] }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .take(8)
                    .toList(),
                messageMenuItems = ADD_MENU_ITEM.findAll(mainJava)
                    .map { it.groupValues[1] }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .take(8)
                    .toList(),
                callbacks = CALLBACK_NAMES
                    .filter { Regex("""\b\w+\s+$it\s*\(""").containsMatchIn(mainJava) }
                    .take(8),
                configNames = CONFIG_CALL.findAll(mainJava)
                    .map { it.groupValues[1] }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .take(8)
                    .toList(),
                apiGroups = API_GROUPS
                    .filterValues { apis -> apis.any { Regex("""\b$it\s*\(""").containsMatchIn(mainJava) } }
                    .keys
                    .toList(),
            )
        }
    }
}
