package cn.hxy.happy.plugin.agent

import cn.hxy.happy.plugin.bean.AgentStage

/**
 * 流水线吐出的事件。
 *
 * 有了这层，ViewModel 只做「把事件折叠成界面状态」，时序逻辑（轮次推进、工具往返、
 * 打字机节奏、截断判断）全在流水线里，可以脱离 UI 单独验证。
 */
sealed interface AgentEvent {

    /** 阶段推进。工具卡片据此点亮某一行。 */
    data class Stage(val stage: AgentStage, val detail: String = "") : AgentEvent

    /** 思考过程分片。 */
    data class Reasoning(val text: String) : AgentEvent

    /** 正文分片。 */
    data class Content(val text: String) : AgentEvent

    /** 模型要求调用工具。界面据此显示「正在查文档 / 正在读文件」。 */
    data class ToolCall(val name: String, val arguments: String) : AgentEvent

    /** 工具返回。 */
    data class ToolResult(val name: String, val preview: String) : AgentEvent

    /** token 用量。 */
    data class Usage(val promptTokens: Int, val completionTokens: Int, val totalTokens: Int) : AgentEvent

    /**
     * 所有轮次结束。
     *
     * [raw] 是累积的正文，交给产物解析；[finishReason] 为 `length` 时说明被截断。
     */
    data class Completed(val raw: String, val finishReason: String?) : AgentEvent

    /** 出错。已经吐出的内容仍保留在 Completed 之外，由调用方决定怎么展示。 */
    data class Failed(val message: String) : AgentEvent
}
