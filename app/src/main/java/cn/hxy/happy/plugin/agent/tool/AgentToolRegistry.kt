package cn.hxy.happy.plugin.agent.tool

import org.json.JSONArray
import org.json.JSONObject

/**
 * 工具注册表：拼请求体的 `tools` 字段，并按名分发调用。
 *
 * 集中在一处是为了让「Agent 能用什么」一眼可见 —— 加工具只改 [default] 这一个列表。
 */
class AgentToolRegistry(private val tools: List<AgentTool>) {

    private val byName: Map<String, AgentTool> = tools.associateBy { it.name }

    val names: List<String> get() = tools.map { it.name }

    fun find(name: String): AgentTool? = byName[name]

    /** 拼进请求体的 `tools` 数组。没注册任何工具时返回 null，请求就不带这个字段。 */
    fun toRequestJson(): JSONArray? {
        if (tools.isEmpty()) return null
        return JSONArray().apply {
            tools.forEach { tool ->
                put(
                    JSONObject().apply {
                        put("type", "function")
                        put(
                            "function",
                            JSONObject().apply {
                                put("name", tool.name)
                                put("description", tool.description)
                                put("parameters", tool.parameters)
                            }
                        )
                    }
                )
            }
        }
    }

    companion object {
        fun default(): AgentToolRegistry = AgentToolRegistry(
            listOf(SearchApiTool, ListFilesTool, ReadFileTool)
        )
    }
}
