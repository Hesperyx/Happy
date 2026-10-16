package cn.hxy.happy.plugin.bean

import kotlinx.serialization.Serializable

/**
 * 一轮生成消耗的 token。
 *
 * 取自服务端在流末尾补发的 usage 分片。不是所有 OpenAI 兼容端点都会返回，
 * 拿不到时三个字段都是 0，[isEmpty] 为 true，界面上直接不显示。
 */
@Serializable
data class AgentUsage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0,
) {
    val isEmpty: Boolean
        get() = promptTokens == 0 && completionTokens == 0 && totalTokens == 0

    /** 紧凑读数，例如 `↑1.2k ↓3.4k`。 */
    val shortLabel: String get() = "↑${compact(promptTokens)} ↓${compact(completionTokens)}"

    /** 明细读数，例如 `输入 1200 · 输出 3400 · 共 4600`。 */
    val detailLabel: String
        get() = "输入 $promptTokens · 输出 $completionTokens · 共 $totalTokens"

    companion object {
        fun compact(n: Int): String = when {
            n <= 0 -> "0"
            n < 1000 -> n.toString()
            else -> "%.1fk".format(n / 1000.0)
        }
    }
}
