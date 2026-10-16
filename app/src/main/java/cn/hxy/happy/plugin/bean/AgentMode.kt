package cn.hxy.happy.plugin.bean

/**
 * Agent 的两种工作方式。
 *
 * 以前只有一套提示词，靠模型自己判断「这条输入是需求还是闲聊」，
 * 结果一句「你好」也可能被做成脚本。现在拆成显式的两个模式：
 * 问答只答疑，任务才产脚本，判断权交回用户。
 */
enum class AgentMode(val label: String, val hint: String) {
    QA("问答", "询问 API 用法、参数含义，获取使用示例"),
    TASK("任务", "描述需求，生成 Happy 插件脚本三件套");

    companion object {
        /** 宽容解析：存档或配置里读到未知值时回落到 [TASK]。 */
        fun of(name: String?): AgentMode = entries.firstOrNull { it.name == name } ?: TASK
    }
}
