package cn.hxy.happy.plugin.bean

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/** Agent 会话中的单条消息。 */
@Serializable
data class AgentChatMessage(
    val role: String,
    val content: String,
    /**
     * 稳定标识，供列表做 key。
     *
     * 以前用 `hashCode()` 当 key，流式每来一个 token content 就变、key 就变，
     * LazyColumn 于是每帧销毁重建气泡，思考区跟着反复重排 —— 就是那个上下抖动。
     * 旧版本存下来的消息这里会是空串，消费方需要按序号兜底。
     */
    val id: String = "",
    /** 模型的思考过程（DeepSeek R1 等），无则为 null。 */
    val reasoningContent: String? = null,
    val isStreaming: Boolean = false,
    /**
     * 本条回复解析出的三件套，仅供界面即时展示。
     *
     * 刻意不落盘：会话级已经存了一份完整产物，每条消息再存一份，
     * 改几轮就把历史文件撑到几 MB，冷启动读它会明显卡顿。
     * 切回历史会话时这个字段是空的，判断产物要用会话级状态。
     */
    @Transient
    val files: AgentScriptFiles? = null,
    /** 工具卡片：这条回复走过的生成流水线，随流式实时点亮。 */
    val steps: List<AgentToolStep> = emptyList(),
    /** 生成式 UI：本条产物被静态解析出的能力摘要。 */
    val insight: AgentScriptInsight? = null,
    /** 整轮生成耗时（毫秒），0 表示未记录。 */
    val elapsedMs: Long = 0L,
    /** 本轮 token 用量，服务端未返回时为空。 */
    val usage: AgentUsage? = null,
    /**
     * 这一轮的输出不完整：撞上了 max_tokens，或者被用户中途取消。
     *
     * 界面据此在对话末尾给出「继续输出」入口 —— 之前只在提示里写
     * 「直接说继续」，等于把补救动作丢给用户手打；手动暂停后更是
     * 完全没有接着生成的办法。
     */
    val truncated: Boolean = false,
) {
    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
        const val ROLE_SYSTEM = "system"
    }
}

/**
 * 一次完整的生成会话。
 *
 * 对齐 Web 版的侧边历史：一个会话同时持有对话流与最后一次生成的产物，
 * 便于随时切回查看或重新落盘。
 */
@Serializable
data class AgentSession(
    val id: String,
    val title: String,
    val timestamp: Long,
    val prompt: String = "",
    val files: AgentScriptFiles = AgentScriptFiles(),
    val messages: List<AgentChatMessage> = emptyList(),
    /** 正在修改的既有脚本 ID；为空表示新建脚本。 */
    val editingScriptId: String? = null,
    /** 这条会话属于哪个模式，切回历史时一并恢复。 */
    val mode: String = AgentMode.TASK.name,
)
