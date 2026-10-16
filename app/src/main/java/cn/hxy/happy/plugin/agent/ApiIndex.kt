package cn.hxy.happy.plugin.agent

import cn.hxy.happy.generated.ScriptApiIndex

/**
 * Script-facing API reference used by the plugin agent.
 *
 * Signatures come from [ScriptApiIndex], which KSP generates by reading
 * `cn.hxy.happy.plugin.api.PluginMethod`, so the agent can only ever see methods that
 * actually exist in this build. Grouping and prose are maintained here and looked up by
 * method name; an entry with no prose still shows up (marked as undocumented) instead of
 * silently disappearing, which is what makes API drift visible.
 */
object ApiIndex {

    data class Entry(
        val name: String,
        val signature: String,
        val returnType: String,
        val params: String,
        val group: String,
        val description: String,
    )

    private const val UNDOCUMENTED = "（暂无说明，请参考签名与模块内 API 文档）"

    private val namesByGroup: List<Pair<String, List<String>>> = listOf(
        "日志与提示" to listOf("log", "toast", "qqToast", "getNowActivity"),
        "菜单与窗口" to listOf("addItem", "addMenuItem", "registerActivity"),
        "动态加载" to listOf("loadJava", "loadJar", "loadDex"),
        "好友" to listOf("getAllFriend", "isFriend", "getUidFromUin", "getUinFromUid", "sendZan"),
        "群管理" to listOf(
            "clockIn", "getGroupList", "getGroupInfo", "getGroupMemberList", "getProhibitList",
            "getMemberInfo", "isShutUp", "shutUpAll", "shutUp", "setGroupAdmin",
            "setGroupMemberTitle", "changeMemberName", "kickGroup",
        ),
        "消息" to listOf(
            "sendMsg", "sendReplyMsg", "recallMsg", "sendPai", "sendPic", "sendPtt",
            "sendVideo", "sendFile", "sendCard",
        ),
        "配置存储" to listOf(
            "putString", "putInt", "putBoolean", "putLong",
            "getString", "getInt", "getBoolean", "getLong",
        ),
        "Cookie 与 Token" to listOf(
            "getRealSkey", "getSkey", "getStweb", "getPt4Token", "getGTK", "getBkn",
            "getGroupRKey", "getFriendRKey", "getPskey",
        ),
    )

    private val groups = HashMap<String, String>().apply {
        namesByGroup.forEach { (group, names) ->
            names.forEach { put(it, group) }
        }
    }

    private val descriptions: Map<String, String> = mapOf(
        "log" to "写入脚本日志文件（默认 log.txt），第二个参数为日志内容。",
        "toast" to "弹出宿主 Toast。",
        "qqToast" to "弹带图标的提示，icon：0 警告 / 1 错误 / 2 成功。",
        "getNowActivity" to "取当前前台 Activity，可用于弹窗、跳转或取 Context。",
        "addItem" to "在脚本悬浮窗注册入口按钮，callback 为脚本内方法名。",
        "addMenuItem" to "在消息长按菜单注册入口；msgTypes 为空表示全部消息类型。",
        "registerActivity" to "把自定义 Activity 类注册进宿主，可用 getNowActivity 之上启动。",
        "loadJava" to "运行时加载一个 Java 源码文件。",
        "loadJar" to "运行时加载 jar 包内的类。",
        "loadDex" to "运行时加载 dex 文件中的类。",
        "getAllFriend" to "取全部好友列表（FriendInfo）。",
        "isFriend" to "判断 uin 是否为好友。",
        "getUidFromUin" to "由 uin 换 uid（NT 架构内部标识）。",
        "getUinFromUid" to "由 uid 换 uin。",
        "sendZan" to "给指定好友点赞 num 次。",
        "clockIn" to "群打卡。",
        "getGroupList" to "取全部群列表（GroupInfo）。",
        "getGroupInfo" to "取单个群信息。",
        "getGroupMemberList" to "取群成员列表（MemberInfo）。",
        "getProhibitList" to "取群禁言列表（ForbidInfo）。",
        "getMemberInfo" to "取群内指定成员信息。",
        "isShutUp" to "查询群是否处于全员禁言。",
        "shutUpAll" to "开启/关闭全员禁言。",
        "shutUp" to "禁言指定成员，time 为秒数。",
        "setGroupAdmin" to "设置/取消群管理员。",
        "setGroupMemberTitle" to "设置群成员头衔。",
        "changeMemberName" to "修改群成员名片。",
        "kickGroup" to "移出群成员；block 为是否同时拉黑。",
        "sendMsg" to "发送文本消息，chatType：1 好友 / 2 群。",
        "sendReplyMsg" to "发送引用回复消息，replyMsgId 为被引用消息 ID。",
        "recallMsg" to "撤回消息。",
        "sendPai" to "发送拍一拍。",
        "sendPic" to "发送图片，path 为本地文件路径。",
        "sendPtt" to "发送语音，可选 durationMs 指定时长。",
        "sendVideo" to "发送视频。",
        "sendFile" to "发送文件。",
        "sendCard" to "发送卡片消息，data 为卡片 JSON。",
        "putString" to "写入脚本配置（字符串），存放在脚本目录 config 下。",
        "putInt" to "写入脚本配置（整数）。",
        "putBoolean" to "写入脚本配置（布尔）。",
        "putLong" to "写入脚本配置（长整数）。",
        "getString" to "读取字符串配置，缺失时返回 defaultValue。",
        "getInt" to "读取整数配置，缺失时返回 defaultValue。",
        "getBoolean" to "读取布尔配置，缺失时返回 defaultValue。",
        "getLong" to "读取长整数配置，缺失时返回 defaultValue。",
        "getRealSkey" to "取真实 skey（敏感，勿外传）。",
        "getSkey" to "取 skey。",
        "getStweb" to "取 stweb。",
        "getPt4Token" to "按 url 取 pt4_token。",
        "getGTK" to "按 url 取 g_tk。",
        "getBkn" to "由 skey 计算 bkn。",
        "getGroupRKey" to "取群 rkey（群文件接口用）。",
        "getFriendRKey" to "取好友 rkey（私聊文件接口用）。",
        "getPskey" to "按 url 取 p_skey。",
    )

    val entries: List<Entry> = ScriptApiIndex.entries.map { entry ->
        Entry(
            name = entry.name,
            signature = entry.signature,
            returnType = entry.returnType,
            params = entry.params,
            group = groups[entry.name] ?: "其他",
            description = descriptions[entry.name] ?: UNDOCUMENTED,
        )
    }

    /** Method names that exist in the build but have no curated description yet. */
    val undocumented: List<String> = entries.filter { it.description == UNDOCUMENTED }.map { it.name }

    /** Grouped method names with required parameters only — cheap enough for every request. */
    fun promptSection(): String = buildString {
        appendLine("可用 API 概览（只需传必填参数；参数含义可调用 search_api 查询）：")
        namesByGroup.forEach { (group, names) ->
            val sigs = names.mapNotNull { name ->
                entries.firstOrNull { it.name == name }?.signature
            }
            if (sigs.isNotEmpty()) {
                appendLine("- $group：${sigs.joinToString("、")}")
            }
        }
        val rest = entries.filter { it.group == "其他" }
        if (rest.isNotEmpty()) {
            appendLine("- 其他：${rest.joinToString("、") { it.signature }}")
        }
    }

    /**
     * Keyword search over names, signatures and prose. Returns a formatted block; when nothing
     * matches it answers with the closest names instead of an empty string, so the model can
     * correct itself in one round.
     */
    fun search(query: String, limit: Int = 8): String {
        val keywords = query.lowercase()
            .split(' ', ',', '，', '、', '/', '|', '\n', '\t')
            .map { it.trim() }
            .filter { it.length >= 1 }

        if (keywords.isEmpty()) return promptSection()

        val scored = entries.mapNotNull { entry ->
            val haystack = "${entry.name} ${entry.params} ${entry.description} ${entry.group}"
                .lowercase()
            var score = 0
            keywords.forEach { keyword ->
                if (entry.name.equals(keyword, ignoreCase = true)) score += 12
                if (entry.name.lowercase().contains(keyword)) score += 6
                if (entry.group.lowercase().contains(keyword)) score += 4
                if (haystack.contains(keyword)) score += 2
            }
            if (score == 0) null else entry to score
        }.sortedByDescending { it.second }

        if (scored.isEmpty()) {
            val names = entries.map { it.name }
            val hint = names.filter { name ->
                keywords.any { keyword -> name.lowercase().contains(keyword.take(3)) }
            }.take(6)
            val fallback = if (hint.isEmpty()) names.take(12) else hint
            return buildString {
                appendLine("没有找到与「$query」直接匹配的 API。相近名称：${fallback.joinToString("、")}")
                appendLine("可用的分组：${namesByGroup.joinToString("、") { it.first }}")
            }
        }

        return buildString {
            scored.take(limit).forEach { (entry, _) ->
                appendLine("${entry.signature}  →  ${entry.returnType}")
                appendLine("  分组：${entry.group}")
                appendLine("  参数：${entry.params.ifBlank { "无" }}")
                appendLine("  说明：${entry.description}")
            }
            if (scored.size > limit) {
                appendLine("（还有 ${scored.size - limit} 个匹配结果，可缩小关键词）")
            }
        }.trimEnd()
    }
}
