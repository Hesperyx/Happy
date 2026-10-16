package cn.hxy.happy.plugin.agent.tool

import org.json.JSONObject
import java.io.File

/**
 * Agent 能主动调用的一个工具。
 *
 * 工具的存在是为了让模型**按需取信息**，而不是被一次性塞满：脚本目录可能有几百 KB、
 * API 文档有几百行，全量塞进上下文既贵又稀释注意力 —— 631 行里找那 20 行相关的内容，
 * 模型反而容易抓错重点。
 *
 * 产出物仍然走流式文本协议（`=== 文件名 ===`），工具只负责「探查」。
 * 这样打字机效果和已经验证过的解析协议都不用动，而 agent 的实质已经具备。
 */
interface AgentTool {

    /** 工具名，必须与 [parameters] 所在声明的 name 一致。 */
    val name: String

    /** 给模型看的说明：什么时候该用它。写得越具体，误用越少。 */
    val description: String

    /** OpenAI function calling 的 parameters schema。 */
    val parameters: JSONObject

    /** 同步执行。允许失败 —— 把失败原因当正常结果回传，模型自己能看懂。 */
    fun execute(arguments: JSONObject, context: AgentToolContext): String
}

/**
 * 工具执行需要的外部环境。
 *
 * [scriptDir] 是正在编辑的脚本目录；新建脚本时为 null。此时浏览类工具会明确
 * 告诉模型「还没有目录」，而不是返回空字符串让人摸不着头脑。
 */
data class AgentToolContext(val scriptDir: File?)
