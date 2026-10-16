package cn.hxy.happy.plugin.agent

import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Turns transport and HTTP failures from an OpenAI-compatible endpoint into one actionable
 * Chinese sentence, and decides whether a retry is worth it.
 *
 * Callers are expected to surface the returned text verbatim: the user configuring a base URL
 * or key needs to know *which* part is wrong, and "网络错误" alone sends them looking in the
 * wrong place.
 */
object AgentHttpErrors {

    const val CONNECT_TIMEOUT_MS = 15_000

    /** Generation can legitimately run long, so this is generous compared with ordinary fetches. */
    const val READ_TIMEOUT_MS = 180_000

    private const val MAX_RETRIES = 3

    /** Classifies a non-2xx response. [body] is the raw error body, if one could be read. */
    fun classifyStatus(code: Int, body: String?): String {
        val detail = extractApiMessage(body)
        val suffix = detail?.let { "：$it" }.orEmpty()
        return when (code) {
            400 -> "请求被服务端拒绝（400）$suffix，可能是模型名不受支持或参数不合法"
            401 -> "鉴权失败（401）$suffix，请检查 API Key"
            403 -> "访问被拒绝（403）$suffix，可能是 Key 无权使用该模型"
            404 -> "接口不存在（404）$suffix，请检查 Base URL 是否需要带 /v1"
            408 -> "服务端超时（408），可稍后重试"
            413 -> "请求体过大（413），请缩短会话历史或减少参考资料"
            422 -> "参数无法处理（422）$suffix"
            429 -> "请求过于频繁或额度不足（429）$suffix"
            in 500..599 -> "服务端错误（$code）$suffix，通常稍后重试即可"
            else -> "请求失败（$code）$suffix"
        }
    }

    /** Classifies a transport-level exception. Cancellation is never routed here by callers. */
    fun classifyThrowable(e: Throwable): String = when (e) {
        is SocketTimeoutException -> "连接或读取超时，请检查网络与 Base URL，或稍后重试"
        is UnknownHostException -> "无法解析域名：${e.message ?: "未知主机"}，请检查 Base URL"
        is SSLException -> "TLS 握手失败：${e.message ?: "证书或协议问题"}"
        is IOException -> "网络异常：${e.message ?: e.javaClass.simpleName}"
        else -> e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
    }

    fun isRetryable(code: Int?): Boolean = code == null || code == 408 || code == 429 || code in 500..599

    /** Exponential backoff with a short cap — the user is watching a spinner, not a batch job. */
    fun backoffMs(attempt: Int): Long {
        val base = 500L * (1L shl (attempt.coerceIn(0, MAX_RETRIES - 1)))
        return base.coerceAtMost(4_000L)
    }

    fun maxRetries(): Int = MAX_RETRIES

    /** Pulls a human message out of an OpenAI-style `{"error":{"message":"..."}}` body. */
    fun extractApiMessage(body: String?): String? {
        val text = body?.trim().orEmpty()
        if (text.isEmpty()) return null
        runCatching {
            val json = JSONObject(text)
            json.optJSONObject("error")?.let { error ->
                val message = error.optString("message").trim()
                if (message.isNotEmpty()) return message
                val code = error.optString("code").trim()
                if (code.isNotEmpty()) return code
            }
            listOf("message", "msg", "error", "detail").forEach { key ->
                val value = json.optString(key).trim()
                if (value.isNotEmpty() && !value.startsWith("{")) return value
            }
        }
        return text.take(200).replace(Regex("\\s+"), " ").takeIf { it.isNotBlank() }
    }
}
