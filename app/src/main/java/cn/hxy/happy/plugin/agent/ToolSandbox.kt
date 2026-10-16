package cn.hxy.happy.plugin.agent

import java.io.File

/** Outcome of a sandboxed filesystem access. */
sealed interface SandboxResult {
    data class Ok(val text: String) : SandboxResult
    data class Denied(val message: String) : SandboxResult
}

/**
 * Confines every filesystem operation the agent's tools perform to one directory.
 *
 * The model writes the arguments, so `../../` and absolute paths are not hypothetical: they are
 * exactly what a confused or adversarial generation produces. Everything here resolves through
 * canonical paths and rejects anything that leaves [root] before a single byte is touched.
 */
object ToolSandbox {

    /** 硬性体积上限：超过它的文件根本不读，避免一次把几百 KB 拖进内存。 */
    const val MAX_READ_BYTES = 256 * 1024

    /** 上下文上限：真正送给模型的部分，截断后说明还剩多少没给。 */
    const val MAX_READ_CHARS = 20_000

    const val MAX_LIST_ENTRIES = 200
    const val MAX_LIST_DEPTH = 3

    /**
     * Resolves [relative] inside [root]. Returns null when the result escapes the sandbox, is
     * rooted outside it, or cannot be resolved at all.
     */
    fun resolve(root: File, relative: String?): File? {
        val rootPath = runCatching { root.canonicalFile }.getOrElse { return null }
        val raw = relative?.trim().orEmpty()
        val candidate = when {
            raw.isEmpty() -> rootPath
            File(raw).isAbsolute -> File(raw)
            else -> File(rootPath, normalize(raw))
        }
        val resolved = runCatching { candidate.canonicalFile }.getOrElse { return null }
        val rootPrefix = rootPath.path + File.separator
        return if (resolved.path == rootPath.path || resolved.path.startsWith(rootPrefix)) resolved else null
    }

    fun normalize(relative: String): String {
        val cleaned = relative.replace('\\', '/').trim().trimStart('/')
        val parts = ArrayList<String>()
        cleaned.split('/').forEach { part ->
            when (part) {
                "", "." -> Unit
                ".." -> if (parts.isEmpty()) parts.add("..") else parts.removeAt(parts.size - 1)
                else -> parts.add(part)
            }
        }
        return parts.joinToString(File.separator)
    }

    fun escapeMessage(relative: String): String =
        "路径「$relative」超出脚本目录范围，读取被拒绝。请只使用当前脚本目录内的相对路径。"

    /**
     * Reads one file for the model.
     *
     * Two caps, with different jobs: [maxBytes] protects memory and is a refusal, [maxChars] protects
     * the context window and is a truncation — a long file is still worth showing, just not all of it.
     */
    fun readText(
        root: File,
        relative: String,
        maxBytes: Int = MAX_READ_BYTES,
        maxChars: Int = MAX_READ_CHARS,
    ): SandboxResult {
        val file = resolve(root, relative)
            ?: return SandboxResult.Denied(escapeMessage(relative))
        if (!file.exists()) {
            return SandboxResult.Denied("文件不存在：${display(root, file)}。可用文件见 list_files 的结果。")
        }
        if (file.isDirectory) {
            return listTree(root)
        }
        if (file.length() > maxBytes) {
            return SandboxResult.Denied(
                "文件过大（${file.length() / 1024} KB），超过 ${maxBytes / 1024} KB 上限，请指定更小的文件。"
            )
        }
        return runCatching {
            val text = file.readText()
            val header = "=== ${display(root, file)}（${text.lines().size} 行）==="
            if (text.length > maxChars) {
                SandboxResult.Ok(
                    "$header\n${text.take(maxChars)}\n…（已截断，共 ${text.length} 字符，需要看后面某段请说明）"
                )
            } else {
                SandboxResult.Ok("$header\n$text")
            }
        }.getOrElse { error ->
            SandboxResult.Denied("读取失败：${error.message ?: error.javaClass.simpleName}")
        }
    }

    fun listTree(root: File, maxEntries: Int = MAX_LIST_ENTRIES): SandboxResult {
        val base = resolve(root, null)
            ?: return SandboxResult.Denied("脚本目录不可用")
        if (!base.exists()) return SandboxResult.Ok("脚本目录为空")
        val lines = ArrayList<String>()
        var truncated = false

        fun walk(dir: File, depth: Int) {
            if (lines.size >= maxEntries) {
                truncated = true
                return
            }
            val children = dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name })) ?: return
            children.forEach { child ->
                if (lines.size >= maxEntries) {
                    truncated = true
                    return
                }
                val suffix = if (child.isDirectory) "/" else "  (${child.length() / 1024} KB)"
                lines.add("${"  ".repeat(depth)}${display(base, child)}$suffix")
                if (child.isDirectory && depth + 1 < MAX_LIST_DEPTH) walk(child, depth + 1)
            }
        }

        walk(base, 0)
        if (lines.isEmpty()) return SandboxResult.Ok("脚本目录为空")
        return SandboxResult.Ok(
            buildString {
                appendLine(lines.joinToString("\n"))
                if (truncated) appendLine("（条目过多，仅显示前 $maxEntries 项）")
            }.trimEnd()
        )
    }

    /** Relative display path with forward slashes, safe to show the model. */
    fun display(root: File, file: File): String {
        val rootPath = runCatching { root.canonicalFile.path }.getOrElse { root.path }
        val path = runCatching { file.canonicalFile.path }.getOrElse { file.path }
        val relative = path.removePrefix(rootPath).trimStart(File.separatorChar)
        return relative.ifEmpty { "." }.replace('\\', '/')
    }
}
