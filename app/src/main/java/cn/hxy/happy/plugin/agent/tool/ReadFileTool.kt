package cn.hxy.happy.plugin.agent.tool

import cn.hxy.happy.plugin.agent.SandboxResult
import cn.hxy.happy.plugin.agent.ToolSandbox
import org.json.JSONArray
import org.json.JSONObject

/**
 * 读取脚本目录里的单个文件。
 *
 * 有了它就不必在每轮请求里预先把整个目录塞进去 —— 模型先 `list_files` 看清有什么，
 * 再决定读哪几个。之前那种「全量塞 + 超上限静默省略」的做法，模型甚至不知道自己缺东西。
 *
 * 越界判定、体积上限、上下文截断都在 [ToolSandbox] 里，和落盘侧共用同一套规则 ——
 * 分开写两份的话，迟早出现「读得到但写不进去」或者反过来。
 */
object ReadFileTool : AgentTool {

    override val name = "read_file"

    override val description =
        "读取脚本目录里的某个文件。要改现有脚本时先读它再动手，不要凭猜测改。" +
                "一次只读一个文件，路径用相对路径。"

    override val parameters: JSONObject = JSONObject().apply {
        put("type", "object")
        put(
            "properties",
            JSONObject().apply {
                put(
                    "path",
                    JSONObject().apply {
                        put("type", "string")
                        put("description", "相对脚本目录的路径，例如 main.java、core/economy.java")
                    }
                )
            }
        )
        put("required", JSONArray().put("path"))
    }

    override fun execute(arguments: JSONObject, context: AgentToolContext): String {
        val dir = context.scriptDir
            ?: return "当前是新建脚本，还没有文件可读。"

        val raw = arguments.optString("path").trim()
        if (raw.isEmpty()) return "请给出要读取的文件路径。"

        return when (val result = ToolSandbox.readText(dir, raw)) {
            is SandboxResult.Ok -> result.text
            is SandboxResult.Denied -> result.message
        }
    }
}
