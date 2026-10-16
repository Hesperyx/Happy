package cn.hxy.happy.plugin.agent

import bsh.ParseException
import bsh.Parser
import java.io.StringReader

/**
 * Validates agent-generated script content before it is written to disk.
 *
 * The main defence is a BeanShell parse: the same parser the engine uses at load time walks the
 * generated `main.java` in structural-only mode, so syntax errors (including output truncated by
 * a token limit) are reported with a line number *without* executing a single statement.
 * Structural checks on `info.prop` then catch the other failure mode, a file set that parses but
 * cannot be registered as a plugin.
 */
object ScriptValidator {

    enum class Severity { ERROR, WARNING }

    data class Issue(
        val severity: Severity,
        val message: String,
        val line: Int? = null,
    ) {
        fun render(): String = when (line) {
            null -> "[${if (severity == Severity.ERROR) "错误" else "警告"}] $message"
            else -> "[${if (severity == Severity.ERROR) "错误" else "警告"}] 第 $line 行：$message"
        }
    }

    data class Result(
        val fileName: String,
        val issues: List<Issue>,
    ) {
        val hasErrors: Boolean get() = issues.any { it.severity == Severity.ERROR }
        val hasIssues: Boolean get() = issues.isNotEmpty()
        val errorCount: Int get() = issues.count { it.severity == Severity.ERROR }

        fun render(): String = if (issues.isEmpty()) {
            "$fileName 校验通过"
        } else {
            buildString {
                appendLine("$fileName 校验发现 ${issues.size} 个问题：")
                issues.forEach { appendLine(it.render()) }
            }.trimEnd()
        }
    }

    private val PLUGIN_ID_PATTERN = Regex("^[A-Za-z][A-Za-z0-9_]{0,31}$")

    /** Parses `main.java` in structural-only mode. Returns the first parse failure, if any. */
    fun parseJava(code: String): Issue? {
        val parser = Parser(StringReader(code))
        parser.setRetainComments(false)
        return try {
            while (!parser.Line()) {
                parser.popNode()
            }
            null
        } catch (e: ParseException) {
            Issue(
                severity = Severity.ERROR,
                message = (e.message ?: "").lineSequence().firstOrNull()?.trim().orEmpty()
                    .ifBlank { "语法错误" },
                line = e.errorLineNumber.takeIf { it > 0 },
            )
        } catch (e: Exception) {
            Issue(
                severity = Severity.ERROR,
                message = "解析失败：${e.message ?: e.javaClass.simpleName}",
            )
        }
    }

    fun validateJava(code: String?, fileName: String = "main.java"): Result {
        if (code.isNullOrBlank()) {
            return Result(fileName, listOf(Issue(Severity.ERROR, "内容为空")))
        }
        val issues = ArrayList<Issue>()
        parseJava(code)?.let { issues.add(it) }
        if (code.contains("```")) {
            issues.add(Issue(Severity.WARNING, "内容里仍包含 Markdown 代码围栏，已按原文写入时会导致语法错误"))
        }
        if (!code.contains("PluginMethod") && !code.contains("onMsg") && !code.contains("onReceive")) {
            issues.add(Issue(Severity.WARNING, "未发现任何回调方法或 PluginMethod 调用，脚本可能不会产生作用"))
        }
        return Result(fileName, issues)
    }

    fun validateInfoProp(content: String?, fileName: String = "info.prop"): Result {
        if (content.isNullOrBlank()) {
            return Result(fileName, listOf(Issue(Severity.ERROR, "缺少 info.prop，脚本无法被识别")))
        }
        val issues = ArrayList<Issue>()
        val values = HashMap<String, String>()
        content.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//")) return@forEach
            val separator = trimmed.indexOfFirst { it == '=' || it == ':' }
            if (separator <= 0) return@forEach
            values[trimmed.take(separator).trim().lowercase()] = trimmed.drop(separator + 1).trim()
        }

        val id = values["id"]
        when {
            id.isNullOrBlank() -> issues.add(Issue(Severity.ERROR, "info.prop 缺少 id"))
            !PLUGIN_ID_PATTERN.matches(id) ->
                issues.add(Issue(Severity.ERROR, "id「$id」不合法，需以字母开头，仅含字母/数字/下划线，且不超过 32 位"))
        }
        if (values["pluginname"].isNullOrBlank()) {
            issues.add(Issue(Severity.ERROR, "info.prop 缺少 pluginName"))
        }
        listOf("author", "versioncode").forEach { key ->
            if (values[key].isNullOrBlank()) {
                issues.add(Issue(Severity.WARNING, "info.prop 建议补充 $key"))
            }
        }
        return Result(fileName, issues)
    }

    /**
     * Aggregate result of a full script-set validation.
     *
     * [text] is written to be fed back to the model verbatim; [hasErrors] tells the caller whether
     * spending another round on a repair is worth it. Warnings alone do not block: a script that
     * parses but lacks `author` is still loadable, and re-asking for it just burns tokens.
     */
    data class Verdict(val text: String, val hasErrors: Boolean)

    /** Validates a whole generated file set and renders one block that can be fed back to the model. */
    fun validate(
        mainJava: String?,
        infoProp: String?,
        descTxt: String? = null,
    ): Verdict {
        val results = listOf(
            validateJava(mainJava),
            validateInfoProp(infoProp),
        ).filter { it.hasIssues }

        val text = if (results.isEmpty()) {
            "校验通过：main.java 语法正确，info.prop 字段完整。"
        } else {
            buildString {
                appendLine("脚本校验未通过，请修正后重新输出完整文件：")
                results.forEach { appendLine(it.render()) }
                if (descTxt.isNullOrBlank()) {
                    appendLine("[警告] 缺少 desc.txt，脚本列表里将没有功能说明")
                }
            }.trimEnd()
        }

        return Verdict(text, results.any { it.hasErrors })
    }
}
