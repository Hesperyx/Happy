package cn.hxy.happy.plugin.agent

import cn.hxy.happy.plugin.bean.AgentScriptFiles

/**
 * 新旧产物的差异摘要。
 *
 * 覆盖既有脚本之前给用户看的东西。原来只说「这几个文件将被替换」等于什么都没说 ——
 * 用户确认的是「要不要覆盖」，而不是「改对了没有」。真正的安全网得让人看见改了什么。
 *
 * 不追求精确的 diff 视图：行级统计 + 方法增删足够了，重点是把「加了什么、删了什么」
 * 摆出来。真要逐行对比，用户会去看代码抽屉。
 */
data class ScriptDiff(
    val files: List<FileChange>,
    val addedMethods: List<String>,
    val removedMethods: List<String>,
) {
    val hasChanges: Boolean
        get() = files.isNotEmpty() || addedMethods.isNotEmpty() || removedMethods.isNotEmpty()

    /** 一句话概括，没变化时返回 null。 */
    val summary: String?
        get() {
            if (!hasChanges) return null
            return buildList {
                if (files.isNotEmpty()) add("${files.size} 个文件有改动")
                if (addedMethods.isNotEmpty()) add("新增 ${addedMethods.size} 个方法")
                if (removedMethods.isNotEmpty()) add("删除 ${removedMethods.size} 个方法")
            }.joinToString(" · ")
        }

    /** 单文件的行级变化。 */
    data class FileChange(
        val path: String,
        val addedLines: Int,
        val removedLines: Int,
        val isNew: Boolean,
    )

    companion object {
        /** 脚本里的方法声明，用来算方法增删。 */
        private val METHOD_DECL = Regex(
            """(?:void|String|int|long|boolean|float|double|Object)\s+(\w+)\s*\("""
        )

        fun between(old: AgentScriptFiles, new: AgentScriptFiles): ScriptDiff {
            val changes = (old.fileNames + new.fileNames)
                .distinct()
                .mapNotNull { path ->
                    val before = old.contentOf(path)
                    val after = new.contentOf(path)
                    if (before == after) return@mapNotNull null

                    val beforeLines = before.lines().filter { it.isNotBlank() }
                    val afterLines = after.lines().filter { it.isNotBlank() }

                    FileChange(
                        path = path,
                        // 用「不在对面出现的行数」近似增删：够表达改动规模，
                        // 也不至于为此引入一套完整的 diff 算法
                        addedLines = afterLines.count { it !in beforeLines },
                        removedLines = beforeLines.count { it !in afterLines },
                        isNew = before.isBlank(),
                    )
                }

            val oldMethods = METHOD_DECL.findAll(old.mainJava).map { it.groupValues[1] }.toSet()
            val newMethods = METHOD_DECL.findAll(new.mainJava).map { it.groupValues[1] }.toSet()

            return ScriptDiff(
                files = changes,
                addedMethods = (newMethods - oldMethods).sorted(),
                removedMethods = (oldMethods - newMethods).sorted(),
            )
        }
    }
}
