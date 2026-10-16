package cn.hxy.happy.plugin.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import cn.hxy.happy.plugin.agent.AgentHttpErrors
import cn.hxy.happy.plugin.agent.AgentPrompts
import cn.hxy.happy.plugin.bean.AgentScriptFiles
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLException

data class AgentMessage(
    val role: String,
    val content: String,
    /** assistant 要求调用的工具；只有 role=assistant 时才可能非空。 */
    val toolCalls: List<AgentToolCall>? = null,
    /** role=tool 时，这条结果对应哪一次调用。 */
    val toolCallId: String? = null,
)

/**
 * 模型要求调用的一次工具。
 *
 * [arguments] 保留原始 JSON 字符串 —— 协议要求回灌时原样带回，
 * 中途反序列化再序列化容易把转义弄坏。
 */
data class AgentToolCall(
    val id: String,
    val name: String,
    val arguments: String,
)

data class AgentConfig(
    val apiUrl: String,
    val apiKey: String,
    val model: String,
    /** 代码生成任务：偏低温度，减少语法走样。 */
    val temperature: Double = DEFAULT_TEMPERATURE,
    val maxTokens: Int = DEFAULT_MAX_TOKENS
) {
    companion object {
        const val DEFAULT_TEMPERATURE = 0.3
        const val DEFAULT_MAX_TOKENS = 16384
        const val MIN_TEMPERATURE = 0.0
        const val MAX_TEMPERATURE = 2.0
        const val MIN_MAX_TOKENS = 256
        const val MAX_MAX_TOKENS = 65536
    }
}

/** 流式响应的分片：思考过程 / 正式内容 / 用量统计 */
sealed class AgentStreamChunk {
    data class Reasoning(val text: String) : AgentStreamChunk()
    data class Content(val text: String) : AgentStreamChunk()

    /** 服务端在流末尾补发的 token 用量（需 `stream_options.include_usage`）。 */
    data class Usage(
        val promptTokens: Int,
        val completionTokens: Int,
        val totalTokens: Int,
    ) : AgentStreamChunk()

    /**
     * 结束原因。
     *
     * `length` 表示撞上 max_tokens 被截断 —— 拿到的是半截产物，必须提示用户。
     * `tool_calls` 表示模型要求调用工具，调用方执行后把结果回灌即可继续。
     */
    data class Finish(val reason: String) : AgentStreamChunk()

    /**
     * 工具调用分片。
     *
     * OpenAI 协议里 `function.arguments` 是**逐步拼接**的 JSON 字符串片段，
     * 不是一次到齐，所以这里只做转发，累积和解析交给调用方。
     */
    data class ToolCallDelta(
        val index: Int,
        val id: String?,
        val name: String?,
        val argumentsChunk: String,
    ) : AgentStreamChunk()
}

object AgentService {




    /**
     * 发一次流式请求，原样吐分片。
     *
     * 这是给 [cn.hxy.happy.plugin.agent.AgentPipeline] 用的底层入口 ——
     * 工具调用需要多轮往返，消息序列由上层维护，这里只负责「把这一轮发出去」。
     */
    fun streamRaw(
        messages: List<AgentMessage>,
        systemPrompt: String,
        config: AgentConfig,
        tools: JSONArray? = null,
    ): Flow<AgentStreamChunk> = streamChat(messages, config, systemPrompt, tools)

    private fun streamChat(
        messages: List<AgentMessage>,
        config: AgentConfig,
        systemPrompt: String,
        tools: JSONArray? = null,
    ): Flow<AgentStreamChunk> = flow {
        var emittedAny = false
        var includeUsage = true
        var attempt = 0

        while (true) {
            try {
                streamOnce(messages, config, systemPrompt, tools, includeUsage).collect {
                    emittedAny = true
                    emit(it)
                }
                return@flow
            } catch (e: StreamOptionsUnsupportedException) {
                // 服务端不认 stream_options。统计用量是附加功能，不能因为它让整轮生成失败，
                // 所以去掉该字段重来一次。只有还没吐出任何分片时才重试，避免内容重复。
                if (emittedAny) throw e
                includeUsage = false
            } catch (e: Exception) {
                // 已经有分片落地就不能重试了：重来一遍会把半截回答再吐一次，
                // 面板上出现重复正文比直接报错更难排查。
                if (emittedAny || !isRetryable(e)) throw e
                attempt++
                if (attempt > AgentHttpErrors.maxRetries()) throw e
                delay(AgentHttpErrors.backoffMs(attempt))
            }
        }
    }

    /**
     * 判断这次失败值不值得再来一次。
     *
     * 超时、连接中断、限流和服务端 5xx 都会自愈；域名解析不了、TLS 握手失败、
     * 密钥或参数错误则不会 —— 盲目重试只会让用户多等三个退避周期才看到同一句话。
     */
    private fun isRetryable(e: Throwable): Boolean = when (e) {
        is HttpStatusException -> AgentHttpErrors.isRetryable(e.code)
        is UnknownHostException, is SSLException -> false
        is IOException -> true
        else -> false
    }

    /** 服务端拒绝 `stream_options` 时的内部信号，供 [streamChat] 降级重试。 */
    private class StreamOptionsUnsupportedException(message: String) : Exception(message)

    /** 非 2xx 响应；带上状态码，让 [isRetryable] 能判断值不值得重试。 */
    private class HttpStatusException(val code: Int, message: String) : Exception(message)

    /** 读到错误体才算数：errorStream 可能为 null，且必须按 UTF-8 读，否则中文提示是乱码。 */
    private fun readErrorBody(connection: HttpURLConnection): String = runCatching {
        connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
    }.getOrDefault("")

    /** 统一鉴权头：Azure 用 api-key，其余 OpenAI 兼容端点用 Bearer。 */
    private fun applyAuth(connection: HttpURLConnection, endpoint: String, config: AgentConfig) {
        if (isAzureEndpoint(endpoint)) {
            connection.setRequestProperty("api-key", config.apiKey)
        } else {
            connection.setRequestProperty("Authorization", "Bearer ${config.apiKey}")
        }
    }

    private fun streamOnce(
        messages: List<AgentMessage>,
        config: AgentConfig,
        systemPrompt: String,
        tools: JSONArray?,
        includeUsage: Boolean
    ): Flow<AgentStreamChunk> = flow {
        val endpoint = buildEndpoint(config.apiUrl)
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            applyAuth(connection, endpoint, config)
            connection.doOutput = true
            // 连不上要快点失败（用户在看转圈），但生成本身可能很久，读超时给足
            connection.connectTimeout = AgentHttpErrors.CONNECT_TIMEOUT_MS
            connection.readTimeout = AgentHttpErrors.READ_TIMEOUT_MS

            val messagesArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                messages.forEach { msg ->
                    put(JSONObject().apply {
                        put("role", msg.role)
                        when {
                            // 工具结果：必须带上对应的 tool_call_id，协议才认
                            msg.role == "tool" -> {
                                put("tool_call_id", msg.toolCallId.orEmpty())
                                put("content", msg.content)
                            }

                            // assistant 的工具请求：content 允许为空但字段要在
                            msg.toolCalls != null -> {
                                put("content", msg.content.ifBlank { JSONObject.NULL })
                                put(
                                    "tool_calls",
                                    JSONArray().apply {
                                        msg.toolCalls.forEach { call ->
                                            put(JSONObject().apply {
                                                put("id", call.id)
                                                put("type", "function")
                                                put(
                                                    "function",
                                                    JSONObject().apply {
                                                        put("name", call.name)
                                                        put("arguments", call.arguments)
                                                    }
                                                )
                                            })
                                        }
                                    }
                                )
                            }

                            else -> put("content", msg.content)
                        }
                    })
                }
            }

            val body = JSONObject().apply {
                put("model", config.model)
                put("messages", messagesArray)
                put("temperature", config.temperature)
                put("max_tokens", config.maxTokens)
                put("stream", true)
                // 让流式响应在结尾补一个带 usage 的分片，否则拿不到真实 token 用量
                if (includeUsage) {
                    put("stream_options", JSONObject().put("include_usage", true))
                }
                // 声明可用工具。模型据此决定要不要先查文档、读文件
                if (tools != null && tools.length() > 0) {
                    put("tools", tools)
                }
            }

            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(body.toString())
                writer.flush()
            }

            if (connection.responseCode != 200) {
                val code = connection.responseCode
                val error = readErrorBody(connection)
                if (includeUsage && code == 400) {
                    throw StreamOptionsUnsupportedException("API 拒绝 stream_options: $error")
                }
                throw HttpStatusException(code, AgentHttpErrors.classifyStatus(code, error))
            }

            val reader = BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line ?: continue
                if (!l.startsWith("data: ")) continue
                val data = l.removePrefix("data: ").trim()
                if (data == "[DONE]") break
                try {
                    val json = JSONObject(data)

                    // usage 挂在流末尾那个 choices 为空的分片上，必须在取 choice 之前读，
                    // 否则会被下面的 continue 直接跳过。
                    json.optJSONObject("usage")?.let { u ->
                        emit(
                            AgentStreamChunk.Usage(
                                promptTokens = u.optInt("prompt_tokens", 0),
                                completionTokens = u.optInt("completion_tokens", 0),
                                totalTokens = u.optInt("total_tokens", 0),
                            )
                        )
                    }

                    val choice = json.optJSONArray("choices")?.optJSONObject(0) ?: continue

                    // 结束原因：`length` 说明撞上了 max_tokens，产物是残的。
                    // 同样要 isNull 守卫，否则 JSON null 会变成字面量 "null"。
                    if (!choice.isNull("finish_reason")) {
                        val reason = choice.optString("finish_reason", "")
                        if (reason.isNotBlank()) emit(AgentStreamChunk.Finish(reason))
                    }

                    // 优先读 delta，兼容部分实现返回 message
                    val delta = choice.optJSONObject("delta")
                        ?: choice.optJSONObject("message")
                        ?: continue

                    // 思考过程（DeepSeek R1 等模型的 reasoning_content）
                    if (!delta.isNull("reasoning_content")) {
                        val reasoning = delta.optString("reasoning_content", "")
                        if (reasoning.isNotEmpty()) {
                            emit(AgentStreamChunk.Reasoning(reasoning))
                        }
                    }

                    // 正式内容
                    if (!delta.isNull("content")) {
                        val content = delta.optString("content", "")
                        if (content.isNotEmpty()) {
                            emit(AgentStreamChunk.Content(content))
                        }
                    }

                    // 工具调用：arguments 是逐片到达的 JSON 字符串，这里只转发，
                    // 累积与解析交给调用方（见 AgentPipeline）
                    delta.optJSONArray("tool_calls")?.let { calls ->
                        for (i in 0 until calls.length()) {
                            val call = calls.optJSONObject(i) ?: continue
                            val function = call.optJSONObject("function")
                            emit(
                                AgentStreamChunk.ToolCallDelta(
                                    index = call.optInt("index", i),
                                    id = if (call.isNull("id")) {
                                        null
                                    } else {
                                        call.optString("id").ifBlank { null }
                                    },
                                    name = if (function == null || function.isNull("name")) {
                                        null
                                    } else {
                                        function.optString("name").ifBlank { null }
                                    },
                                    argumentsChunk = if (function == null || function.isNull("arguments")) {
                                        ""
                                    } else {
                                        function.optString("arguments")
                                    },
                                )
                            )
                        }
                    }
                } catch (_: Exception) {}
            }
        } finally {
            connection.disconnect()
        }
        // RENDEZVOUS 去掉生产者侧的缓冲：默认缓冲会让 IO 线程把一串分片抢跑读完，
        // 下游于是一帧之内连续消费完 → Compose 合并成一次重组 → 没有打字机效果。
        // 这里让每次 emit 必须等到下游取走，分片就按网络节奏逐个到达。
    }.buffer(Channel.RENDEZVOUS).flowOn(Dispatchers.IO)

    /**
     * 任务模式：按需求产出脚本三件套。
     *
     * [history] 是之前几轮的对话，[existingScript] 是编辑模式下正在改的脚本。
     * 两者都要带上，否则用户说「再加一个开关」时模型既不知道「再」指什么，
     * 也不知道加在哪份代码上。
     *
     * [tools] 是可用的工具声明。给了之后模型能自己查文档、列目录、补读文件 ——
     * 预先生成的上下文总有上限，工具让它按需取。
     */
    /**
     * 把模型输出解析成插件三件套。
     *
     * 首选 `=== 文件名 ===` 分隔格式：代码原样书写，不经过 JSON 转义，
     * 模型不会把换行写成 `\n` 字面量，解析也不会因为一个转义失误而全盘失效。
     * 老格式（单个 JSON 对象）作为兼容分支保留，最后才降级为整体当 main.java。
     */
    fun parseScriptFiles(raw: String, fallbackPrompt: String = ""): AgentScriptFiles {
        parseBySections(raw)?.let { return it }
        parseByJson(raw)?.let { return it }

        // 没出现三件套标记 = 这一轮不是脚本任务（闲聊、追问、解释、报错反馈）。
        // 这里以前会把整段原文直接当 main.java 兜底落盘，一句「你好」也会被
        // 做成一个假脚本。兜底恢复只在正文里确实有代码时才允许。
        val code = extractJavaCodeBlock(raw)
        if (code == null || !looksLikeScript(code)) return AgentScriptFiles()

        val id = normalizePluginId("")
        return AgentScriptFiles(
            pluginId = id,
            pluginName = DEFAULT_PLUGIN_NAME,
            mainJava = code,
            infoProp = buildInfoProp(id, DEFAULT_PLUGIN_NAME, AgentScriptFiles.DEFAULT_VERSION, AgentScriptFiles.DEFAULT_AUTHOR),
            descTxt = fallbackPrompt.take(100).ifBlank { "由 Agent 生成的脚本" },
        )
    }

    /**
     * 兜底恢复的最低门槛。
     *
     * 只认类声明、方法声明这类结构特征；纯文字闲聊、解释说明一律判为非脚本，
     * 否则又会绕回「把回答当代码」的老路。
     */
    private fun looksLikeScript(code: String): Boolean = SCRIPT_SIGNATURE.containsMatchIn(code)

    /**
     * 文件标记：必须独占一行，且只认这三个文件名。
     *
     * 这里刻意不用 `indexOf("=== ")` 去找下一个标记 —— 代码里的分节注释
     * `// ===== 消息处理 =====` 含「三个等号 + 空格」，会让 main.java 从中间被切断，
     * 而且截断后三件套依然「齐全」，界面上看不出任何异常，脚本却是残的。
     * 限定「行首 + 文件名 + 行尾」之后，注释行（行首是 `/`）再也匹配不上。
     */
    private val SECTION_MARKER = Regex(
        """(?m)^[ \t]*={2,}[ \t]*(main\.java|info\.prop|desc\.txt)[ \t]*={2,}[ \t]*$"""
    )

    /**
     * 附加文件标记：`=== FILE: core/base.java ===`。
     *
     * 刻意与三件套用不同形状。若做成通用的 `=== 任意名 ===`，模型在解释性文字里
     * 随手写的 `=== 说明 ===` 也会被当成文件段；带 `FILE:` 前缀就不可能误伤。
     */
    private val EXTRA_MARKER = Regex(
        """(?m)^[ \t]*={2,}[ \t]*FILE:[ \t]*(.+?)[ \t]*={2,}[ \t]*$"""
    )

    /** 允许作为附加文件落盘的扩展名：只放文本类资源，挡住二进制与可执行内容。 */
    private val EXTRA_FILE_SUFFIXES =
        listOf(".java", ".json", ".txt", ".prop", ".html", ".js", ".css", ".md")

    /** 一次扫描定出所有文件段：三件套标记与 `FILE:` 标记合并后按出现位置排序。 */
    private fun splitSections(raw: String): Map<String, String> {
        class Mark(val name: String, val markStart: Int, val contentStart: Int)

        val marks = buildList {
            SECTION_MARKER.findAll(raw).forEach { m ->
                add(Mark(m.groupValues[1], m.range.first, m.range.last + 1))
            }
            EXTRA_MARKER.findAll(raw).forEach { m ->
                val path = normalizeRelativePath(m.groupValues[1]) ?: return@forEach
                add(Mark(path, m.range.first, m.range.last + 1))
            }
        }.sortedBy { it.markStart }

        if (marks.isEmpty()) return emptyMap()

        return marks.mapIndexed { index, mark ->
            val start = mark.contentStart.coerceAtMost(raw.length)
            val end = (marks.getOrNull(index + 1)?.markStart ?: raw.length).coerceAtLeast(start)
            mark.name to raw.substring(start, end).trim()
        }.toMap()
    }

    /**
     * 净化附加文件的相对路径。
     *
     * 模型有可能写出 `../` 或绝对路径，落盘时会插到脚本目录之外。
     * 这里直接拒掉 —— 宁可不生成那个文件，也不能写出界。
     */
    private fun normalizeRelativePath(rawPath: String): String? {
        val path = rawPath.trim().replace('\\', '/').removePrefix("./")
        if (path.isBlank() || path.length > 120) return null
        if (path.startsWith("/")) return null
        if (path.split('/').any { it.isBlank() || it == "." || it == ".." }) return null
        if (EXTRA_FILE_SUFFIXES.none { path.endsWith(it, ignoreCase = true) }) return null
        return path
    }

    /** 解析 `=== main.java === / === info.prop === / === desc.txt ===` 三段式输出。 */
    private fun parseBySections(raw: String): AgentScriptFiles? {
        val sections = splitSections(raw)
        val main = sections[FILE_TAG_MAIN] ?: return null

        val info = sections[FILE_TAG_INFO].orEmpty()
        val id = parseInfoValue(info, "id")?.let { normalizePluginId(it) } ?: normalizePluginId("")
        val name = parseInfoValue(info, "pluginName")?.ifBlank { null } ?: DEFAULT_PLUGIN_NAME

        return AgentScriptFiles(
            pluginId = id,
            pluginName = name,
            mainJava = main,
            // 原样保留模型写的 info.prop：里面可能有 # 注释行和自定义键。
            // 落盘时由 buildInfoPropFor 只覆盖 id / pluginName，不整份重建。
            infoProp = info,
            descTxt = sections[FILE_TAG_DESC].orEmpty().ifBlank { "由 Agent 生成的脚本" },
            extraFiles = sections.filterKeys { it !in AgentScriptFiles.CORE_FILES },
        )
    }

    /** 从 info.prop 文本里取一个键；大小写不敏感。 */
    private fun parseInfoValue(infoProp: String, key: String): String? =
        infoProp.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("$key=", ignoreCase = true) }
            ?.substringAfter('=')
            ?.trim()

    /** 兼容旧格式：单个 JSON 对象。 */
    private fun parseByJson(raw: String): AgentScriptFiles? =
        extractJsonObject(raw)?.let { json ->
            runCatching {
                val obj = JSONObject(json)
                val id = normalizePluginId(obj.optString("pluginId"))
                val name = obj.optString("pluginName").ifBlank { DEFAULT_PLUGIN_NAME }
                AgentScriptFiles(
                    pluginId = id,
                    pluginName = name,
                    mainJava = obj.optString("mainJava"),
                    infoProp = obj.optString("infoProp").ifBlank {
                        buildInfoProp(id, name, AgentScriptFiles.DEFAULT_VERSION, AgentScriptFiles.DEFAULT_AUTHOR)
                    },
                    descTxt = obj.optString("descTxt"),
                )
            }.getOrNull()?.takeIf { !it.isEmpty }
        }

    /** 按花括号配对提取首个完整 JSON 对象，跳过字符串内的括号。 */
    private fun extractJsonObject(raw: String): String? {
        val start = raw.indexOf('{')
        if (start < 0) return null

        var depth = 0
        var inString = false
        var escaped = false

        for (i in start until raw.length) {
            val c = raw[i]
            when {
                escaped -> escaped = false
                c == '\\' && inString -> escaped = true
                c == '"' -> inString = !inString
                !inString && c == '{' -> depth++
                !inString && c == '}' -> {
                    depth--
                    if (depth == 0) return raw.substring(start, i + 1)
                }
            }
        }
        return null
    }

    private fun extractJavaCodeBlock(raw: String): String? {
        val match = Regex("```(?:java|beanshell)?\\s*\\n(.*?)\\n?```", RegexOption.DOT_MATCHES_ALL)
            .find(raw)
        return match?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun normalizePluginId(raw: String): String {
        val cleaned = raw.trim().lowercase().replace(Regex("[^a-z0-9_]"), "_")
        return cleaned.ifBlank { "agent_${System.currentTimeMillis()}" }
    }

    private fun buildInfoProp(id: String, name: String, version: String, author: String): String =
        """
        |id=$id
        |pluginName=$name
        |author=$author
        |versionCode=$version
        """.trimMargin()

    /**
     * 落盘用的 info.prop。
     *
     * 优先在原文上局部修改：只替换 id / pluginName / versionCode / author 四行，
     * 注释行和其它自定义键原样保留。整份重建会把 `# 说明` 注释、以及别的框架留下的
     * `tags` / `type` / `versionName` 之类字段一起抹掉 —— 有脚本就靠这些跑在多个模块上。
     *
     * 原文为空时退回模板；原文里缺哪一行就补到末尾（Properties 不关心顺序）。
     */
    fun buildInfoPropFor(
        id: String,
        name: String,
        version: String,
        author: String,
        original: String = "",
    ): String {
        if (original.isBlank()) return buildInfoProp(id, name, version, author)

        val replacements = linkedMapOf(
            AgentScriptFiles.KEY_ID to id,
            AgentScriptFiles.KEY_NAME to name,
            AgentScriptFiles.KEY_VERSION to version,
            AgentScriptFiles.KEY_AUTHOR to author,
        )
        val seen = mutableSetOf<String>()

        val lines = original.lines().map { line ->
            val key = replacements.keys.firstOrNull {
                line.trim().startsWith("$it=", ignoreCase = true)
            }
            if (key == null) {
                line
            } else {
                seen.add(key)
                "$key=${replacements.getValue(key)}"
            }
        }.toMutableList()

        replacements.forEach { (key, value) ->
            if (key !in seen) lines.add("$key=$value")
        }

        return lines.joinToString("\n").trim()
    }

    private const val FILE_TAG_MAIN = "main.java"
    private const val FILE_TAG_INFO = "info.prop"
    private const val FILE_TAG_DESC = "desc.txt"

    private const val DEFAULT_PLUGIN_NAME = "Agent脚本"

    /** Azure OpenAI 的 API 版本。用户若在地址里自带 `api-version` 则以自带的为准。 */
    private const val AZURE_API_VERSION = "2024-10-21"

    /** 代码结构特征：类声明 / 方法声明 / 带方法体的成员。闲聊文本不会命中。 */
    private val SCRIPT_SIGNATURE = Regex(
        """class\s+\w+|\b(?:void|int|long|boolean|String|Object)\s+\w+\s*\(|\)\s*\{"""
    )

    /** 从用户填写的地址推导出 OpenAI 兼容的 base url（不含端点） */
    private fun resolveBaseUrl(apiUrl: String): String {
        var url = apiUrl.trim().trimEnd('/')
        url = url.removeSuffix("/chat/completions").trimEnd('/')
        return when {
            url.endsWith("/v1") -> url
            url.contains("/v1/") -> url.trimEnd('/')
            else -> "$url/v1"
        }
    }

    /** Azure OpenAI 的路径形状与鉴权头都和 OpenAI 不同，需要单独识别。 */
    private fun isAzureEndpoint(url: String): Boolean =
        url.contains(".openai.azure.com") || url.contains("/openai/deployments/")

    /**
     * 拼出真正的请求地址。
     *
     * `resolveBaseUrl` 的 `/v1` 启发式只适用于标准 OpenAI 形状；
     * Azure 走的是 `{endpoint}/openai/deployments/{deployment}/chat/completions`，
     * 硬拼 `/v1` 会得到一个必然 404 的地址，所以这里先分流。
     */
    private fun buildEndpoint(apiUrl: String): String {
        val url = apiUrl.trim().trimEnd('/')
        // 用户已经填了完整端点，原样使用
        if (url.endsWith("/chat/completions")) return url

        if (isAzureEndpoint(url)) {
            val withPath = "$url/chat/completions"
            return if (withPath.contains("api-version=")) {
                withPath
            } else {
                "$withPath?api-version=$AZURE_API_VERSION"
            }
        }

        return "${resolveBaseUrl(url)}/chat/completions"
    }

    /** 获取模型列表 */
    suspend fun fetchModels(config: AgentConfig): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = "${resolveBaseUrl(config.apiUrl)}/models"
            val connection = URL(endpoint).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                applyAuth(connection, endpoint, config)
                connection.connectTimeout = AgentHttpErrors.CONNECT_TIMEOUT_MS
                connection.readTimeout = 30_000

                if (connection.responseCode != 200) {
                    throw HttpStatusException(
                        connection.responseCode,
                        AgentHttpErrors.classifyStatus(connection.responseCode, readErrorBody(connection)),
                    )
                }

                val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val arr = JSONObject(body).getJSONArray("data")
                val models = ArrayList<String>()
                for (i in 0 until arr.length()) {
                    val id = arr.getJSONObject(i).optString("id")
                    if (id.isNotBlank()) models.add(id)
                }
                models
            } finally {
                connection.disconnect()
            }
        }
    }

    /** 连通性检测，返回可读的结果描述 */
    suspend fun testConnection(config: AgentConfig): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = "${resolveBaseUrl(config.apiUrl)}/models"
            val connection = URL(endpoint).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                applyAuth(connection, endpoint, config)
                connection.connectTimeout = AgentHttpErrors.CONNECT_TIMEOUT_MS
                connection.readTimeout = 30_000

                val code = connection.responseCode
                if (code == 200) {
                    "连接成功 (HTTP 200)"
                } else {
                    throw HttpStatusException(code, AgentHttpErrors.classifyStatus(code, readErrorBody(connection)))
                }
            } finally {
                connection.disconnect()
            }
        }
    }
}
