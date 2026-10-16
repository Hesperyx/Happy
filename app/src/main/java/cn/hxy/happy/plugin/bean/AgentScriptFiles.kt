package cn.hxy.happy.plugin.bean

import kotlinx.serialization.Serializable

/**
 * Agent 生成的插件脚本产物。
 *
 * 核心是三件套 `main.java` / `info.prop` / `desc.txt`，与 Happy 插件目录一一对应。
 * 复杂脚本还会把逻辑拆成多个文件（用 `loadJava(pluginPath + "/core/xxx.java")` 逐个拉起），
 * 并带 `config` 目录下的 json 之类的数据文件 —— 这些装不进三件套，走 [extraFiles]。
 *
 * 注意：注释里不要写出「目录 + 斜杠 + 星号」的通配写法，Kotlin 支持嵌套块注释，
 * 那个星号会把 KDoc 提前打开一层，整个文件都会报未闭合注释。
 */
@Serializable
data class AgentScriptFiles(
    val pluginId: String = "",
    val pluginName: String = "",
    val mainJava: String = "",
    /** info.prop 的原始文本。落盘时会保留其中的注释与自定义键，只覆盖 id / pluginName。 */
    val infoProp: String = "",
    val descTxt: String = "",
    /** 附加文件：脚本目录内的相对路径 → 内容，例如 `core/base.java`、`config/rules.json`。 */
    val extraFiles: Map<String, String> = emptyMap(),
) {

    val isEmpty: Boolean
        get() = mainJava.isBlank() && infoProp.isBlank() && descTxt.isBlank() && extraFiles.isEmpty()

    /** 三件套在前、附加文件在后。文件标签行与落盘遍历共用这一个顺序。 */
    val fileNames: List<String>
        get() = CORE_FILES + extraFiles.keys.sorted()

    fun contentOf(path: String): String = when (path) {
        FILE_MAIN -> mainJava
        FILE_INFO -> infoProp
        FILE_DESC -> descTxt
        else -> extraFiles[path].orEmpty()
    }

    /**
     * 从 info.prop 原文里读出的版本号。
     *
     * 不做序列化字段：版本号以 [infoProp] 原文为准，另存一份会在落盘时打架。
     * 读不到就回落到 [DEFAULT_VERSION]。
     */
    val version: String
        get() = infoValue(KEY_VERSION) ?: DEFAULT_VERSION

    /** 从 info.prop 原文里读出的作者；读不到就回落到 [DEFAULT_AUTHOR]。 */
    val author: String
        get() = infoValue(KEY_AUTHOR) ?: DEFAULT_AUTHOR

    /** 取 info.prop 某一行的值；大小写不敏感，取到空值视为没有。 */
    private fun infoValue(key: String): String? =
        infoProp.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("$key=", ignoreCase = true) }
            ?.substringAfter('=')
            ?.trim()
            ?.ifBlank { null }

    companion object {
        const val FILE_MAIN = "main.java"
        const val FILE_INFO = "info.prop"
        const val FILE_DESC = "desc.txt"

        /** info.prop 里会被 Agent 覆盖的四个键。 */
        const val KEY_ID = "id"
        const val KEY_NAME = "pluginName"
        const val KEY_VERSION = "versionCode"
        const val KEY_AUTHOR = "author"

        const val DEFAULT_VERSION = "1.0"
        const val DEFAULT_AUTHOR = "Happy Agent"

        /** 生产协议里顺序固定的三个核心文件。 */
        val CORE_FILES = listOf(FILE_MAIN, FILE_INFO, FILE_DESC)
    }
}
