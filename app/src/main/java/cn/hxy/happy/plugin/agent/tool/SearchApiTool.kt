package cn.hxy.happy.plugin.agent.tool

import cn.hxy.happy.plugin.agent.AgentPrompts
import org.json.JSONArray
import org.json.JSONObject

/**
 * 检索 API 文档。
 *
 * 这是把「流式提示词模板」推向 agent 的关键一件：文档不再整份塞进 system prompt，
 * 由模型自己决定要查什么。文档因此可以继续长大，而每次请求的固定成本不变。
 */
object SearchApiTool : AgentTool {

    override val name = "search_api"

    override val description =
        "检索 Happy 插件 API 文档，返回匹配的段落（含完整签名与用法）。" +
                "可传方法名、功能词或中文描述。写代码前用它确认方法确实存在 —— " +
                "方法名臆造是本环境最常见的失败，且在本地完全看不出来。"

    override val parameters: JSONObject = JSONObject().apply {
        put("type", "object")
        put(
            "properties",
            JSONObject().apply {
                put(
                    "keyword",
                    JSONObject().apply {
                        put("type", "string")
                        put("description", "要查的关键字，例如 sendMsg、群管理、菜单、持久化")
                    }
                )
            }
        )
        put("required", JSONArray().put("keyword"))
    }

    override fun execute(arguments: JSONObject, context: AgentToolContext): String =
        AgentPrompts.searchApi(arguments.optString("keyword"))
}
