package cn.hxy.happy.plugin.bean

import kotlinx.serialization.Serializable

/**
 * 生成流水线的阶段。
 *
 * 工具卡片按这个顺序逐点亮：模型只吐文本，本地要经过连接、思考、
 * 落码、解析四段才算真的拿到可落盘的脚本。把过程显性化，
 * 用户才知道卡住的是网络还是解析。
 */
enum class AgentStage(
    val label: String,
    /** 问答模式下的措辞：不产代码，自然也没有「解析产物」这一步。 */
    val qaLabel: String = label,
) {
    CONNECTING("连接模型"),
    THINKING("思考中"),
    WRITING("生成代码", "生成回答"),
    PARSING("解析产物", "整理回答"),
    DONE("完成"),
    FAILED("中断"),

    /**
     * 模型主动调用工具。
     *
     * 刻意不在 [PIPELINE] 里：它按需出现、次数不定，插在「生成代码」与「解析产物」之间。
     */
    TOOL("调用工具");

    /** 该阶段在当前模式下的展示名。 */
    fun labelIn(mode: AgentMode): String = if (mode == AgentMode.QA) qaLabel else label

    companion object {
        /** 正常情况下会依次经过的阶段，失败时在中断处停下。 */
        val PIPELINE: List<AgentStage> = listOf(CONNECTING, THINKING, WRITING, PARSING, DONE)

        fun of(name: String): AgentStage = entries.find { it.name == name } ?: CONNECTING
    }
}

/** 工具卡片里的一行执行记录。 */
@Serializable
data class AgentToolStep(
    val stage: String,
    /**
     * 展示标题。
     *
     * 生成时就按当时的模式定好并随消息存下来，这样回看历史会话时
     * 措辞不会跟着当前模式变。留空则回落到阶段默认名（兼容旧存档）。
     */
    val title: String = "",
    val detail: String = "",
    val done: Boolean = false,
    /** 该阶段耗时（毫秒），未结束时为 0。 */
    val costMs: Long = 0L,
) {
    val stageEnum: AgentStage get() = AgentStage.of(stage)
    val displayTitle: String get() = title.ifBlank { stageEnum.label }
}
