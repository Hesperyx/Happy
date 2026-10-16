package cn.hxy.happy.plugin.agent.tool

import cn.hxy.happy.plugin.agent.SandboxResult
import cn.hxy.happy.plugin.agent.ToolSandbox
import org.json.JSONObject

/**
 * 列出脚本目录里的文件树。编辑现有脚本前，模型先用它看清结构。
 *
 * 输出走 [ToolSandbox.listTree]：条目数、深度都有上限。脚本目录本身不大，
 * 但 `extraFiles` 允许任意子目录，没有上限的话一次工具调用就能把上下文填满。
 */
object ListFilesTool : AgentTool {

    override val name = "list_files"

    override val description =
        "列出当前脚本目录里的全部文件及其相对路径与大小。" +
                "编辑现有脚本前先用它看清结构，别凭猜测改。"

    override val parameters: JSONObject = JSONObject().apply {
        put("type", "object")
        put("properties", JSONObject())
    }

    override fun execute(arguments: JSONObject, context: AgentToolContext): String {
        val dir = context.scriptDir
            ?: return "当前是新建脚本，还没有脚本目录。直接产出文件即可，不需要列目录。"

        if (!dir.exists()) return "脚本目录不存在。"

        return when (val result = ToolSandbox.listTree(dir)) {
            is SandboxResult.Ok -> result.text
            is SandboxResult.Denied -> result.message
        }
    }
}
