package cn.hxy.happy.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.serializer
import cn.hxy.happy.plugin.agent.AgentContextBudget
import cn.hxy.happy.plugin.agent.AgentEvent
import cn.hxy.happy.plugin.agent.AgentPipeline
import cn.hxy.happy.plugin.agent.AgentRequest
import cn.hxy.happy.plugin.agent.ScriptDiff
import cn.hxy.happy.plugin.agent.tool.AgentToolContext
import cn.hxy.happy.plugin.bean.AgentChatMessage
import cn.hxy.happy.plugin.bean.AgentMode
import cn.hxy.happy.plugin.bean.AgentScriptFiles
import cn.hxy.happy.plugin.bean.AgentScriptInsight
import cn.hxy.happy.plugin.bean.AgentSession
import cn.hxy.happy.plugin.bean.AgentStage
import cn.hxy.happy.plugin.bean.AgentToolStep
import cn.hxy.happy.plugin.bean.AgentUsage
import cn.hxy.happy.plugin.loader.PluginManager
import cn.hxy.happy.plugin.net.AgentConfig
import cn.hxy.happy.plugin.net.AgentMessage
import cn.hxy.happy.plugin.net.AgentService
import cn.hxy.happy.ui.pages.plugin.LocalPluginData
import cn.hxy.happy.utils.io.FileUtils
import cn.hxy.happy.utils.io.ObjectStore
import cn.hxy.happy.utils.log.LogUtils
import cn.hxy.happy.utils.qq.QQCurrentEnv
import cn.hxy.happy.utils.qq.Toasts
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

private const val AGENT_SESSIONS_KEY = "AgentSessions"

/** 历史会话上限，避免长期使用后配置无限膨胀。 */
private const val MAX_AGENT_SESSIONS = 50

/** 流式内容两次视觉刷新之间的最小间隔（约一帧），保证打字机效果。 */
private const val STREAM_FRAME_MS = 16L

/** Agent 工作模式在宿主 SharedPreferences 里的键。 */
private const val AGENT_MODE_KEY = "agent_mode"

/** 带进上下文的历史消息条数上限，再多会把 token 预算吃光。 */
private const val MAX_HISTORY_MESSAGES = 8

/** 单条历史消息的字符上限，超出后截断并标注。 */
private const val MAX_HISTORY_CHARS = 2400

/** 编辑既有脚本时，能作为上下文读进来的文本类文件。 */
private val EDITABLE_SUFFIXES = setOf("java", "json", "txt", "prop", "html", "js", "css", "md")

/** 脚本目录里的运行产物，不属于源码，读上下文时跳过。 */
private val RUNTIME_ARTIFACTS = setOf("log.txt", "error.txt", "boot.txt")

/** 带给模型的既有脚本总字符上限：超出按文件逐个省略，免得撑爆上下文。 */
private const val MAX_CONTEXT_SCRIPT_CHARS = 60_000

class PluginViewModel : ViewModel() {

    var localPlugins by mutableStateOf<List<LocalPluginData>>(emptyList())
        private set

    var showDeleteDialog by mutableStateOf(false)
        private set

    var pendingDeletePluginId by mutableStateOf<String?>(null)
        private set

    var showCreateDialog by mutableStateOf(false)
        private set

    var showSuccessDialog by mutableStateOf(false)
        private set

    var createdPluginPath by mutableStateOf("")
        private set

    var isLocalRefreshing by mutableStateOf(false)
        private set

    var isSearchActive by mutableStateOf(false)

    var searchQuery by mutableStateOf("")

    // ---- Agent State ----

    private val _agentMessages = MutableStateFlow<List<AgentChatMessage>>(emptyList())
    val agentMessages: StateFlow<List<AgentChatMessage>> = _agentMessages.asStateFlow()

    var isAgentGenerating by mutableStateOf(false)
        private set

    var agentConfig by mutableStateOf(
        AgentConfig(
            apiUrl = QQCurrentEnv.globalPreference.getString("agent_api_url", "") ?: "",
            apiKey = QQCurrentEnv.globalPreference.getString("agent_api_key", "") ?: "",
            model = QQCurrentEnv.globalPreference.getString("agent_model", "gpt-4o-mini") ?: "gpt-4o-mini",
            temperature = QQCurrentEnv.globalPreference.getString("agent_temperature", null)
                ?.toDoubleOrNull() ?: AgentConfig.DEFAULT_TEMPERATURE,
            maxTokens = QQCurrentEnv.globalPreference.getInt(
                "agent_max_tokens", AgentConfig.DEFAULT_MAX_TOKENS
            ),
        )
    )
        private set

    var agentEditingScriptId by mutableStateOf<String?>(null)
        private set

    /** 当前工作模式：问答只答疑，任务才产脚本。跟随上次选择持久化。 */
    var agentMode by mutableStateOf(
        AgentMode.of(QQCurrentEnv.globalPreference.getString(AGENT_MODE_KEY, null))
    )
        private set

    /** 当前会话生成出的三件套，代码查看器直接消费。 */
    var agentFiles by mutableStateOf(AgentScriptFiles())
        private set

    /** 历史会话，最新的排在最前。 */
    var agentSessions by mutableStateOf<List<AgentSession>>(emptyList())
        private set

    var agentCurrentSessionId by mutableStateOf<String?>(null)
        private set

    var showAgentHistoryPanel by mutableStateOf(false)
        private set

    var showAgentSettings by mutableStateOf(false)
        private set

    var showAgentSaveDialog by mutableStateOf(false)
        private set

    var pendingAgentScript by mutableStateOf<String?>(null)
        private set

    var agentModelList by mutableStateOf<List<String>>(emptyList())
        private set

    var isFetchingModels by mutableStateOf(false)
        private set

    var isTestingConnection by mutableStateOf(false)
        private set

    var agentConnectionResult by mutableStateOf<String?>(null)
        private set

    var agentModelFetchError by mutableStateOf<String?>(null)
        private set

    // ---- Agent 生成过程状态：工具卡片 / 流式输出 / 人机确认 ----

    /** 当前所处阶段，工具卡片据此决定哪一行在转圈。 */
    var agentStage by mutableStateOf(AgentStage.CONNECTING)
        private set


    /** 本轮生成耗时（毫秒）：流式期间由心跳刷新，结束后定格。 */
    var agentElapsedMs by mutableStateOf(0L)
        private set

    /** 当前产物的能力摘要，结果卡与落盘确认卡共用。 */
    var agentInsight by mutableStateOf(AgentScriptInsight())
        private set

    /** 编辑模式下待覆盖的脚本名；非空即表示确认卡待决。 */
    var pendingOverwriteName by mutableStateOf<String?>(null)
        private set

    /** 待覆盖写入的三件套，用户点「覆盖」后才落盘。 */
    var pendingOverwriteFiles by mutableStateOf<AgentScriptFiles?>(null)
        private set

    /** 待覆盖产物的改动摘要，供确认卡展示「改了什么」。 */
    var pendingOverwriteDiff by mutableStateOf<ScriptDiff?>(null)
        private set

    private var agentJob: Job? = null
    private var agentTickerJob: Job? = null
    private var persistJob: Job? = null
    private var messageSeq = 0L

    private val agentSessionSerializer = ListSerializer(AgentSession.serializer())

    val agentQuickPrompts = listOf(
        "写一个群消息自动回复脚本",
        "实现收到特定关键词自动踢人",
        "写一个定时打卡脚本",
        "做一个消息统计功能",
        "实现闪照自动保存",
        "写一个群成员变动通知脚本"
    )

    /** 问答模式的引导问题：指向 API 用法与示例，而不是需求描述。 */
    val agentQaQuickPrompts = listOf(
        "怎么注册悬浮窗菜单？",
        "怎么发送群消息？",
        "消息回调的签名和线程模型是什么？",
        "配置项用什么 API 持久化？",
        "怎么获取群成员列表？",
        "怎么判断消息来自群聊还是私聊？"
    )

    /** 当前模式对应的引导问题。 */
    val activeQuickPrompts: List<String>
        get() = if (agentMode == AgentMode.QA) agentQaQuickPrompts else agentQuickPrompts

    val filteredLocalPlugins by derivedStateOf {
        if (searchQuery.isEmpty()) {
            localPlugins
        } else {
            localPlugins.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                        it.author.contains(searchQuery, ignoreCase = true) ||
                        it.description.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    init {
        PluginManager.loadAll()
        refreshLocalPlugins()
        loadAgentSessions()
    }

    fun reloadLocalPlugins() {
        if (isLocalRefreshing) return
        isLocalRefreshing = true

        viewModelScope.launch(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            PluginManager.loadAll()
            val diff = System.currentTimeMillis() - startTime
            if (diff < 500L) delay((500L - diff).milliseconds)
            refreshLocalPlugins()
            isLocalRefreshing = false
            Toasts.qqToast(2, "刷新成功")
        }
    }

    fun refreshLocalPlugins() {
        localPlugins = PluginManager.plugins.map { plugin ->
            LocalPluginData(
                plugin.id,
                plugin.name,
                plugin.version,
                plugin.author,
                plugin.desc,
                plugin.isRunning,
                PluginManager.autoLoadList.contains(plugin.id)
            )
        }
    }

    fun showCreatePluginDialog() {
        showCreateDialog = true
    }

    fun dismissCreatePluginDialog() {
        showCreateDialog = false
    }

    fun dismissSuccessDialog() {
        showSuccessDialog = false
    }

    fun createPlugin(id: String, name: String, version: String, author: String) {
        val file = PluginManager.createPlugin(id, name, version, author)
        if (file != null) {
            createdPluginPath = file.absolutePath
            refreshLocalPlugins()
            showCreateDialog = false
            showSuccessDialog = true
        } else {
            Toasts.qqToast(1, "创建失败: ID重复或文件夹(脚本名)已存在")
        }
    }

    fun togglePluginRun(id: String, run: Boolean) {
        val plugin = PluginManager.plugins.find { it.id == id } ?: return
        if (run) {
            if (PluginManager.startPlugin(plugin)) refreshLocalPlugins()
        } else {
            PluginManager.stopPlugin(plugin)
            refreshLocalPlugins()
        }
    }

    fun togglePluginAutoLoad(id: String, autoLoad: Boolean) {
        val plugin = PluginManager.plugins.find { it.id == id } ?: return
        PluginManager.setAutoLoad(plugin, autoLoad)
        refreshLocalPlugins()
    }

    fun reloadPlugin(id: String) {
        val plugin = PluginManager.plugins.find { it.id == id } ?: return
        if (PluginManager.reloadPlugin(plugin)) {
            refreshLocalPlugins()
            Toasts.qqToast(2, "重载成功")
        }
    }

    fun showDeleteConfirm(id: String) {
        pendingDeletePluginId = id
        showDeleteDialog = true
    }

    fun dismissDeleteDialog() {
        showDeleteDialog = false
        pendingDeletePluginId = null
    }

    fun confirmDelete() {
        pendingDeletePluginId?.let { id ->
            PluginManager.plugins.find { it.id == id }?.let { plugin ->
                PluginManager.deletePlugin(plugin)
                refreshLocalPlugins()
            }
        }
        dismissDeleteDialog()
    }

    // ---- Agent Methods ----

    fun sendAgentMessage(text: String) {
        if (isAgentGenerating) return
        if (agentConfig.apiUrl.isBlank() || agentConfig.apiKey.isBlank()) {
            showAgentSettings = true
            Toasts.qqToast(1, "请先配置 API 地址和密钥")
            return
        }

        val mode = agentMode

        // 编辑模式只在任务模式下有意义：问答不产出脚本，也就没有「改哪一份」的说法。
        val editId = if (mode == AgentMode.TASK) agentEditingScriptId else null

        // 把整个脚本目录带给模型，而不是只给 main.java。
        // 分层脚本的逻辑在 core/ 里、配置界面在 settings.html 里，只看到入口文件
        // 就不知道依赖什么，改出来的东西必然和现有代码对不上。
        val existingScript = if (
            editId != null && agentFiles.pluginId == editId && !agentFiles.isEmpty
        ) {
            buildExistingScriptContext(agentFiles)
        } else {
            null
        }

        val displayMsg = if (editId != null) {
            val plugin = PluginManager.plugins.find { it.id == editId }
            "修改「${plugin?.name ?: editId}」：$text"
        } else {
            text
        }

        // 多轮上下文：把之前几轮带上。问答里追问是常态（「那它的第二个参数呢」），
        // 不带上下文的话模型连「它」指什么都不知道。
        // 注意取的是「本轮之前」的消息，所以必须在下面追加本次输入之前算。
        //
        // 条数不是唯一约束：一轮工具往返就能塞进一份完整脚本，几轮下来字符数远超模型的
        // 上下文窗口，而服务端只会回一个语焉不详的 400。所以按字符预算裁剪，
        // 用户消息永不丢（追问里的「它」全靠它们才有意义），丢的是最早的那些。
        val history = AgentContextBudget.trim(
            items = _agentMessages.value
                .filter { it.role == AgentChatMessage.ROLE_USER || it.role == AgentChatMessage.ROLE_ASSISTANT }
                .filter { !it.isStreaming && it.content.isNotBlank() }
                .takeLast(MAX_HISTORY_MESSAGES),
            pinned = { it.role == AgentChatMessage.ROLE_USER },
            sizeOf = { it.content.length + it.reasoningContent.orEmpty().length },
        ).let { trimmed ->
            if (trimmed.wasTrimmed) {
                LogUtils.d("Agent 上下文裁剪：丢弃 ${trimmed.dropped} 条，保留 ${trimmed.chars} 字符")
            }
            trimmed.items.map { AgentMessage(it.role, it.asHistoryContent()) }
        }

        _agentMessages.update {
            it + AgentChatMessage(AgentChatMessage.ROLE_USER, displayMsg, id = newMessageId())
        }

        isAgentGenerating = true
        agentElapsedMs = 0L
        agentInsight = AgentScriptInsight()
        agentStage = AgentStage.CONNECTING

        val streamedReasoning = StringBuilder()
        val streamedContent = StringBuilder()
        var usage: AgentUsage? = null
        var finishReason: String? = null

        /** 流水线累积的最终正文；工具往返时每轮的分片会拼在一起。 */
        var pipelineRaw = ""

        /** 编辑模式下供工具读取的脚本目录；新建脚本时为 null。 */
        val scriptDir = editId?.let { id ->
            PluginManager.plugins.find { it.id == id }?.let { File(it.dirPath) }
        }

        // 工具卡片的流水线：一次生成经过哪几段，逐段点亮并记录耗时。
        // 标题在此时按模式定死并随消息存下，回看历史不会跟着当前模式变。
        val pipeline = AgentStage.PIPELINE
            .map { AgentToolStep(it.name, title = it.labelIn(mode)) }
            .toMutableList()
        val startedAt = System.currentTimeMillis()
        var stageAt = startedAt

        /** 把流水线快照同步进「最后一条流式消息」，卡片随流式实时刷新。 */
        fun publishSteps() {
            val snapshot = pipeline.toList()
            val content = streamedContent.toString()
            val reasoning = streamedReasoning.toString()
            _agentMessages.update { list ->
                val last = list.lastOrNull()
                if (last != null && last.role == AgentChatMessage.ROLE_ASSISTANT && last.isStreaming) {
                    list.dropLast(1) + last.copy(
                        content = content,
                        reasoningContent = reasoning.ifEmpty { null },
                        steps = snapshot,
                    )
                } else {
                    list + AgentChatMessage(
                        AgentChatMessage.ROLE_ASSISTANT,
                        content,
                        id = newMessageId(),
                        reasoningContent = reasoning.ifEmpty { null },
                        isStreaming = true,
                        steps = snapshot,
                    )
                }
            }
        }

        /** 推进到下一阶段：当前段记上耗时的同时点亮后续段。 */
        fun advance(stage: AgentStage, detail: String = "") {
            val now = System.currentTimeMillis()
            val idx = pipeline.indexOfFirst { it.stage == stage.name }
            if (idx >= 0) {
                for (i in pipeline.indices) {
                    when {
                        i < idx && !pipeline[i].done ->
                            pipeline[i] = pipeline[i].copy(done = true, costMs = now - stageAt)
                        i == idx -> pipeline[i] = pipeline[i].copy(detail = detail)
                        else -> Unit
                    }
                }
                stageAt = now
            }
            agentStage = stage
            publishSteps()
        }

        startAgentTicker(startedAt)
        advance(AgentStage.CONNECTING)

        /**
         * 插入一条工具调用。
         *
         * 位置放在「解析产物」之前 —— 工具是在生成过程中用的，
         * 直接 append 到末尾会让卡片顺序看起来像先解析后查文档。
         */
        fun appendToolStep(toolName: String, arguments: String) {
            val step = AgentToolStep(
                stage = AgentStage.TOOL.name,
                title = toolLabel(toolName),
                detail = arguments.take(80),
            )
            val parseIdx = pipeline.indexOfFirst { it.stage == AgentStage.PARSING.name }
            pipeline.add(if (parseIdx >= 0) parseIdx else pipeline.size, step)
        }

        /** 给最近一条未完成的工具记录补上结果预览。 */
        fun finishToolStep(preview: String) {
            val idx = pipeline.indexOfLast { it.stage == AgentStage.TOOL.name && !it.done }
            if (idx < 0) return
            pipeline[idx] = pipeline[idx].copy(
                detail = preview.replace('\n', ' ').take(80),
                done = true,
                costMs = System.currentTimeMillis() - stageAt,
            )
        }

        // 打字机效果的关键：保证两次视觉刷新之间至少隔一帧。
        // 分片有时会成批一次性送达，若不在其间让出时间，一帧里几十次 update
        // 会被 Compose 合并成一次重组 —— 整段文字一次性蹦出来。
        // 网络本身有间隔时分片间隔远大于一帧，这里不会补时，也就不拖慢生成。
        var lastStreamPublishAt = 0L
        suspend fun paceStreamFrame() {
            val wait = STREAM_FRAME_MS - (System.currentTimeMillis() - lastStreamPublishAt)
            if (wait > 0) delay(wait)
            lastStreamPublishAt = System.currentTimeMillis()
        }

        agentJob = viewModelScope.launch {
            try {
                // 走流水线而不是直接订阅 stream：模型可以先查文档、读文件再动手，
                // 结果回灌后继续下一轮。所有时序逻辑都在 AgentPipeline 里。
                val request = AgentRequest(
                    mode = mode,
                    userText = text,
                    history = history,
                    existingScript = existingScript,
                    config = agentConfig,
                    toolContext = AgentToolContext(scriptDir),
                )

                AgentPipeline().run(request).collect { event ->
                    when (event) {
                        is AgentEvent.Stage -> advance(event.stage, event.detail)

                        is AgentEvent.Reasoning -> {
                            if (streamedReasoning.isEmpty()) advance(AgentStage.THINKING)
                            streamedReasoning.append(event.text)
                        }

                        is AgentEvent.Content -> {
                            if (streamedContent.isEmpty()) advance(AgentStage.WRITING)
                            streamedContent.append(event.text)
                        }

                        is AgentEvent.Usage -> usage = AgentUsage(
                            promptTokens = event.promptTokens,
                            completionTokens = event.completionTokens,
                            totalTokens = event.totalTokens,
                        )

                        is AgentEvent.ToolCall -> appendToolStep(event.name, event.arguments)

                        is AgentEvent.ToolResult -> finishToolStep(event.preview)

                        is AgentEvent.Completed -> {
                            finishReason = event.finishReason
                            pipelineRaw = event.raw
                        }

                        is AgentEvent.Failed -> throw IllegalStateException(event.message)
                    }
                    publishSteps()
                    paceStreamFrame()
                }

                val finalContent = pipelineRaw.ifBlank { streamedContent.toString() }
                val finalReasoning = streamedReasoning.toString()
                advance(AgentStage.PARSING, if (mode == AgentMode.QA) "整理回答" else "提取三件套")

                // 问答模式不解析产物：回答里的示例代码只是示例，
                // 一旦走解析就会被当成脚本落盘。
                val parsed = if (mode == AgentMode.TASK) {
                    AgentService.parseScriptFiles(finalContent, text)
                } else {
                    AgentScriptFiles()
                }
                val insight = AgentScriptInsight.analyze(parsed.mainJava)

                // 截断提示。撞上 max_tokens 时产物多半是残的，此时三件套依然「齐全」，
                // 只能靠结束原因判断，必须明确告诉用户。
                // 消息上会打 truncated 标记，对话末尾据此给出「继续输出」按钮。
                val truncated = finishReason == "length"
                val warning = if (mode == AgentMode.TASK && !parsed.isEmpty && truncated) {
                    "回答被 max_tokens 截断，脚本多半不完整：可以点「继续输出」补齐，" +
                            "或在设置里调大「最大输出」"
                } else {
                    null
                }
                if (warning != null) Toasts.qqToast(1, warning)

                val now = System.currentTimeMillis()
                for (i in pipeline.indices) {
                    pipeline[i] = pipeline[i].copy(
                        done = true,
                        costMs = if (pipeline[i].costMs == 0L) now - stageAt else pipeline[i].costMs,
                    )
                }
                val parseIdx = pipeline.indexOfFirst { it.stage == AgentStage.PARSING.name }
                if (parseIdx >= 0) {
                    pipeline[parseIdx] = pipeline[parseIdx].copy(
                        detail = when {
                            warning != null -> warning
                            // 闲聊轮次不该出现「0 行的 main.java」这种误导性读数
                            parsed.isEmpty -> "本轮是对话回复，未产出脚本"
                            else -> buildString {
                                append("main.java ${parsed.mainJava.lines().size} 行")
                                append(" · ").append(insight.summary)
                                // token 用量并进这一行，不再单独占一行版面
                                usage?.takeIf { !it.isEmpty }?.let {
                                    append(" · ").append(it.shortLabel)
                                }
                            }
                        }
                    )
                }

                val elapsed = now - startedAt
                val stepSnapshot = pipeline.toList()
                agentStage = AgentStage.DONE
                agentElapsedMs = elapsed
                agentInsight = insight

                _agentMessages.update { list ->
                    val last = list.lastOrNull()
                    if (last != null && last.role == AgentChatMessage.ROLE_ASSISTANT && last.isStreaming) {
                        list.dropLast(1) + last.copy(
                            content = finalContent,
                            reasoningContent = finalReasoning.ifEmpty { null },
                            isStreaming = false,
                            files = parsed,
                            steps = stepSnapshot,
                            insight = insight,
                            elapsedMs = elapsed,
                            usage = usage?.takeIf { !it.isEmpty },
                            truncated = truncated,
                        )
                    } else {
                        list
                    }
                }

                // 编辑模式：先算差异再落定产物 —— 确认卡要对比的是「改动前」的那一份，
                // applyGeneratedFiles 一跑，agentFiles 就变成新产物了。
                val diff = if (editId != null && !parsed.isEmpty && !agentFiles.isEmpty) {
                    ScriptDiff.between(agentFiles, parsed)
                } else {
                    null
                }

                applyGeneratedFiles(parsed)

                // 编辑模式：产物不直接落盘 —— 覆盖既有脚本必须过用户确认。
                val targetId = editId
                if (targetId != null && !parsed.isEmpty) {
                    val plugin = PluginManager.plugins.find { it.id == targetId }
                    if (plugin != null) {
                        pendingOverwriteName = plugin.name
                        pendingOverwriteFiles = parsed.copy(pluginId = plugin.id, pluginName = plugin.name)
                        pendingOverwriteDiff = diff
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                val now = System.currentTimeMillis()
                val failedIdx = pipeline.indexOfFirst { !it.done }
                if (failedIdx >= 0) {
                    pipeline[failedIdx] = pipeline[failedIdx].copy(
                        detail = e.message?.take(48) ?: "已中断",
                        done = true,
                        costMs = now - stageAt,
                    )
                    for (i in failedIdx + 1 until pipeline.size) {
                        pipeline[i] = pipeline[i].copy(detail = "未执行")
                    }
                }
                val stepSnapshot = pipeline.toList()
                val elapsed = now - startedAt
                agentStage = AgentStage.FAILED
                agentElapsedMs = elapsed

                val fallback = streamedContent.toString().ifBlank { "请求失败: ${e.message}" }
                val failedUsage = usage?.takeIf { !it.isEmpty }
                _agentMessages.update { list ->
                    val last = list.lastOrNull()
                    if (last != null && last.role == AgentChatMessage.ROLE_ASSISTANT && last.isStreaming) {
                        list.dropLast(1) + last.copy(
                            content = fallback,
                            reasoningContent = streamedReasoning.toString().ifEmpty { null },
                            isStreaming = false,
                            steps = stepSnapshot,
                            elapsedMs = elapsed,
                            usage = failedUsage,
                        )
                    } else {
                        list + AgentChatMessage(
                            AgentChatMessage.ROLE_ASSISTANT,
                            fallback,
                            steps = stepSnapshot,
                            elapsedMs = elapsed,
                            usage = failedUsage,
                        )
                    }
                }
            } finally {
                stopAgentTicker()
                isAgentGenerating = false
                persistAgentSessions()
            }
        }
    }

    /** 生成期间每 200ms 刷新一次计时，让流式状态条有活着的读数。 */
    private fun startAgentTicker(startedAt: Long) {
        agentTickerJob?.cancel()
        agentTickerJob = viewModelScope.launch {
            while (true) {
                agentElapsedMs = System.currentTimeMillis() - startedAt
                delay(200)
            }
        }
    }

    private fun stopAgentTicker() {
        agentTickerJob?.cancel()
        agentTickerJob = null
    }

    /** 校验并落定当前会话的产物，同时让代码查看器跳到 main.java。 */
    private fun applyGeneratedFiles(files: AgentScriptFiles) {
        if (files.isEmpty) return
        agentFiles = files
        agentInsight = AgentScriptInsight.analyze(files.mainJava)
    }

    /**
     * 手工改产物里的某个文件。
     *
     * 模型产出的东西通常八九成对，剩下一两个常量、一句文案不该逼用户把需求重述一遍 ——
     * 那正是「只读产物」最大的问题：只能整份重生成，连原本对的部分也可能一起变掉。
     */
    fun updateAgentFile(path: String, content: String) {
        val files = agentFiles
        if (files.isEmpty) return

        agentFiles = when (path) {
            AgentScriptFiles.FILE_MAIN -> files.copy(mainJava = content)
            AgentScriptFiles.FILE_INFO -> files.copy(infoProp = content)
            AgentScriptFiles.FILE_DESC -> files.copy(descTxt = content)
            else -> files.copy(extraFiles = files.extraFiles + (path to content))
        }

        // 手改过 main.java，能力摘要要跟着重新解析，否则卡片还显示旧结构
        if (path == AgentScriptFiles.FILE_MAIN) {
            agentInsight = AgentScriptInsight.analyze(content)
        }

        persistAgentSessions()
    }

    // ---- 模式切换 ----

    /**
     * 切换工作模式。
     *
     * 只换提示词和界面语义，**不清空对话**。问答提示词里会告诉用户「需要脚本就切到
     * 任务模式」，如果一切换就把上下文抹掉，用户得从头再描述一遍需求 —— 等于一边
     * 引导他跨模式，一边把桥拆了。历史照常带过去，切过去后按任务模式的规则产出。
     */
    fun selectAgentMode(mode: AgentMode) {
        if (mode == agentMode) return
        if (isAgentGenerating) {
            Toasts.qqToast(1, "生成中，请先取消或等待完成")
            return
        }

        agentMode = mode
        QQCurrentEnv.globalPreference.edit { putString(AGENT_MODE_KEY, mode.name) }
        persistAgentSessions()
    }

    // ---- 会话管理 ----

    fun toggleAgentHistoryPanel() {
        showAgentHistoryPanel = !showAgentHistoryPanel
        if (showAgentHistoryPanel) persistAgentSessions()
    }

    fun newAgentSession() {
        persistAgentSessions()
        agentCurrentSessionId = null
        clearAgentChat()
        showAgentHistoryPanel = false
    }

    fun switchAgentSession(sessionId: String) {
        val session = agentSessions.find { it.id == sessionId } ?: return
        persistAgentSessions()

        agentCurrentSessionId = session.id
        agentMode = AgentMode.of(session.mode)
        _agentMessages.value = session.messages.map { it.copy(isStreaming = false) }
        agentFiles = session.files
        agentEditingScriptId = session.editingScriptId
        agentInsight = AgentScriptInsight.analyze(session.files.mainJava)
        showAgentHistoryPanel = false
    }

    fun deleteAgentSession(sessionId: String) {
        agentSessions = agentSessions.filterNot { it.id == sessionId }
        if (agentCurrentSessionId == sessionId) {
            agentCurrentSessionId = null
            _agentMessages.value = emptyList()
            agentFiles = AgentScriptFiles()
            agentInsight = AgentScriptInsight()
        }
        persistAgentSessions()
    }

    /** 把当前会话写回列表；标题取首条用户需求。 */
    fun persistAgentSessions() {
        val messages = _agentMessages.value.map { it.copy(isStreaming = false) }
        val currentId = agentCurrentSessionId

        // 只有确实有内容才建立 / 更新会话记录，空会话不占历史位
        if (messages.isNotEmpty() || !agentFiles.isEmpty) {
            val prompt = messages.firstOrNull { it.role == AgentChatMessage.ROLE_USER }?.content.orEmpty()
            val title = prompt.take(24).ifBlank {
                agentFiles.pluginName.ifBlank { "新会话" }
            }
            val id = currentId ?: "session_${System.currentTimeMillis()}"

            val session = AgentSession(
                id = id,
                title = title,
                timestamp = System.currentTimeMillis(),
                prompt = prompt,
                files = agentFiles,
                messages = messages,
                editingScriptId = agentEditingScriptId,
                mode = agentMode.name,
            )

            agentSessions = (listOf(session) + agentSessions.filterNot { it.id == id })
                .sortedByDescending { it.timestamp }
                .take(MAX_AGENT_SESSIONS)
            agentCurrentSessionId = id
        }

        // 无论有没有内容都要写盘：清空、删除这类操作也必须落到磁盘，
        // 否则下次冷启动又会把已经删掉的记录读回来。
        // 序列化 + 写盘放到 IO —— 会话里带着多份脚本，主线程写会卡住流式收尾那一帧。
        // 取消上一个未完成的写，避免旧快照后落地把新快照覆盖掉。
        val snapshot = agentSessions
        persistJob?.cancel()
        persistJob = viewModelScope.launch(Dispatchers.IO) {
            ObjectStore.save("data", AGENT_SESSIONS_KEY, snapshot, agentSessionSerializer)
        }
    }

    private fun loadAgentSessions() {
        // 冷启动时历史里可能躺着几十条会话、每条带整份脚本，读取放到 IO，别卡住打开页面
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                ObjectStore.load("data", AGENT_SESSIONS_KEY, agentSessionSerializer)
            }
            agentSessions = saved.orEmpty().sortedByDescending { it.timestamp }
            agentSessions.firstOrNull()?.let { latest ->
                agentCurrentSessionId = latest.id
                agentMode = AgentMode.of(latest.mode)
                _agentMessages.value = latest.messages.map { it.copy(isStreaming = false) }
                agentFiles = latest.files
                agentInsight = AgentScriptInsight.analyze(latest.files.mainJava)
            }
        }
    }

    fun loadPluginForAgent(pluginId: String) {
        val plugin = PluginManager.plugins.find { it.id == pluginId } ?: return
        val dir = File(plugin.dirPath)

        agentEditingScriptId = pluginId
        agentFiles = readScriptDir(dir, plugin.id, plugin.name)
        agentInsight = AgentScriptInsight.analyze(agentFiles.mainJava)
        _agentMessages.value = listOf(
            AgentChatMessage(
                AgentChatMessage.ROLE_SYSTEM,
                "已加载脚本「${plugin.name}」进入修改模式，左侧切到需求输入。"
            )
        )
        showAgentHistoryPanel = false
    }

    /**
     * 读一个脚本目录下的全部文本文件。
     *
     * 只读三件套是不够的：分层脚本的逻辑在 core 里，配置界面在 settings.html 里，
     * 只把 main.java 交给模型，它不知道这份脚本依赖什么，改出来必然对不上。
     */
    private fun readScriptDir(dir: File, id: String, name: String): AgentScriptFiles {
        val extras = mutableMapOf<String, String>()
        val root = dir.canonicalFile

        if (dir.exists()) {
            dir.walkTopDown()
                .filter { it.isFile }
                .filter { it.extension.lowercase() in EDITABLE_SUFFIXES }
                .forEach { file ->
                    runCatching {
                        val rel = file.canonicalFile.relativeTo(root).path.replace('\\', '/')
                        if (rel in AgentScriptFiles.CORE_FILES) return@forEach
                        if (rel in RUNTIME_ARTIFACTS) return@forEach
                        extras[rel] = file.readText()
                    }
                }
        }

        return AgentScriptFiles(
            pluginId = id,
            pluginName = name,
            mainJava = File(dir, AgentScriptFiles.FILE_MAIN).readTextOrEmpty(),
            infoProp = File(dir, AgentScriptFiles.FILE_INFO).readTextOrEmpty(),
            descTxt = File(dir, AgentScriptFiles.FILE_DESC).readTextOrEmpty(),
            extraFiles = extras,
        )
    }

    /**
     * 把脚本目录拼成一份带文件标记的上下文。
     *
     * 刻意用与产出协议相同的形状，模型看到的既有代码和它要写的东西格式一致。
     * 总量有上限：分层脚本的 core 目录加起来可能几百 KB，整份塞进去会撑爆上下文，
     * 超出的文件按顺序省略，并在末尾点名，方便用户单独追问。
     */
    private fun buildExistingScriptContext(files: AgentScriptFiles): String {
        val blocks = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        var used = 0

        fun add(path: String, content: String) {
            if (content.isBlank()) return
            if (used + content.length > MAX_CONTEXT_SCRIPT_CHARS) {
                skipped.add(path)
                return
            }
            used += content.length
            blocks.add("=== $path ===\n$content")
        }

        add(AgentScriptFiles.FILE_MAIN, files.mainJava)
        files.extraFiles.toSortedMap().forEach { (path, content) -> add(path, content) }

        if (skipped.isNotEmpty()) {
            blocks.add("（以下文件体积过大未附上：${skipped.joinToString("、")}。需要改动它们时请单独说明。）")
        }

        return blocks.joinToString("\n\n")
    }

    /**
     * 退出修改模式。
     *
     * 只把 id 置空是不够的：加载进来的脚本内容、能力摘要和那条「已进入修改模式」
     * 的提示都还在，界面看起来毫无变化。这里把这一份编辑上下文整体放下，
     * 让页面回到可自由开新脚本的状态。
     */
    fun exitEditMode() {
        val pluginName = agentEditingScriptId
            ?.let { id -> PluginManager.plugins.find { it.id == id }?.name }

        agentEditingScriptId = null
        agentCurrentSessionId = null
        _agentMessages.value = emptyList()
        agentFiles = AgentScriptFiles()
        agentInsight = AgentScriptInsight()

        Toasts.qqToast(
            0,
            if (pluginName != null) "已退出「$pluginName」的修改模式" else "已退出修改模式"
        )
    }

    fun openAgentSettings() {
        showAgentSettings = true
    }

    fun dismissAgentSettings() {
        showAgentSettings = false
    }

    fun fetchAgentModels(apiUrl: String, apiKey: String) {
        if (isFetchingModels) return
        if (apiUrl.isBlank() || apiKey.isBlank()) {
            Toasts.qqToast(1, "请先填写 API 地址和密钥")
            return
        }
        isFetchingModels = true
        agentModelFetchError = null
        agentModelList = emptyList()

        viewModelScope.launch(Dispatchers.IO) {
            AgentService.fetchModels(AgentConfig(apiUrl.trim(), apiKey.trim(), ""))
                .onSuccess { models ->
                    agentModelList = models
                    if (models.isEmpty()) {
                        agentModelFetchError = "未获取到模型列表"
                    } else {
                        Toasts.qqToast(2, "已获取 ${models.size} 个模型")
                    }
                }
                .onFailure { e ->
                    agentModelFetchError = e.message ?: "获取模型失败"
                    Toasts.qqToast(1, agentModelFetchError ?: "获取模型失败")
                }
            isFetchingModels = false
        }
    }

    fun testAgentConnection(apiUrl: String, apiKey: String) {
        if (isTestingConnection) return
        if (apiUrl.isBlank() || apiKey.isBlank()) {
            Toasts.qqToast(1, "请先填写 API 地址和密钥")
            return
        }
        isTestingConnection = true
        agentConnectionResult = null

        viewModelScope.launch(Dispatchers.IO) {
            AgentService.testConnection(AgentConfig(apiUrl.trim(), apiKey.trim(), ""))
                .onSuccess { msg ->
                    agentConnectionResult = "连接成功：$msg"
                    Toasts.qqToast(2, msg)
                }
                .onFailure { e ->
                    agentConnectionResult = "连接失败：${e.message ?: "未知原因"}"
                    Toasts.qqToast(1, agentConnectionResult ?: "连接失败")
                }
            isTestingConnection = false
        }
    }

    fun updateAgentConfig(config: AgentConfig) {
        agentConfig = config.copy(
            apiUrl = config.apiUrl.trim(),
            apiKey = config.apiKey.trim(),
            model = config.model.trim().ifBlank { "gpt-4o-mini" },
        )
        QQCurrentEnv.globalPreference.edit {
            putString("agent_api_url", agentConfig.apiUrl)
            putString("agent_api_key", agentConfig.apiKey)
            putString("agent_model", agentConfig.model)
            putString("agent_temperature", agentConfig.temperature.toString())
            putInt("agent_max_tokens", agentConfig.maxTokens)
        }
        showAgentSettings = false
        Toasts.qqToast(2, "Agent 配置已保存")
    }

    fun showSaveAgentDialog() {
        if (agentMode == AgentMode.QA) {
            Toasts.qqToast(1, "问答模式不产出脚本，请切到任务模式")
            return
        }
        if (agentFiles.isEmpty) {
            Toasts.qqToast(1, "没有可保存的脚本内容")
            return
        }
        pendingAgentScript = agentFiles.mainJava
        showAgentSaveDialog = true
    }

    fun dismissAgentSaveDialog() {
        showAgentSaveDialog = false
        pendingAgentScript = null
    }

    /**
     * 保存产物为新脚本，或用一个更高版本覆盖同名脚本。
     *
     * 覆盖判定看版本号：同一脚本（id 或名称对得上）已经存在时，只有新版本更高才放行，
     * 否则会被拒绝 —— 免得手一滑就把已经调好的脚本退回了旧内容。
     */
    fun saveAgentScript(name: String, version: String, author: String, runAfter: Boolean) {
        val files = agentFiles
        if (files.isEmpty) return

        viewModelScope.launch(Dispatchers.IO) {
            val safeName = name.trim().ifBlank { files.pluginName.ifBlank { "Agent脚本" } }
            val safeVersion = version.trim().ifBlank { AgentScriptFiles.DEFAULT_VERSION }
            val safeAuthor = author.trim().ifBlank { AgentScriptFiles.DEFAULT_AUTHOR }

            val existing = PluginManager.plugins.find {
                it.id == files.pluginId || it.name == safeName
            }

            val targetDir: File?
            val finalId: String
            val overwriting: Boolean

            when {
                existing == null -> {
                    finalId = files.pluginId.ifBlank { "agent_${System.currentTimeMillis()}" }
                    targetDir = PluginManager.createPlugin(finalId, safeName, safeVersion, safeAuthor)
                    overwriting = false
                }

                PluginManager.compareVersion(safeVersion, existing.version) > 0 -> {
                    // 版本更高：写进原目录，保留原 id，避免打乱自动加载列表
                    finalId = existing.id
                    targetDir = File(existing.dirPath)
                    overwriting = true
                }

                else -> {
                    Toasts.qqToast(1, "「${existing.name}」已存在 v${existing.version}")
                    return@launch
                }
            }

            if (targetDir == null) {
                Toasts.qqToast(1, "保存失败：脚本名或 ID 不可用")
                return@launch
            }

            writeFilesToDir(
                dir = targetDir,
                files = files.copy(pluginId = finalId, pluginName = safeName),
                version = safeVersion,
                author = safeAuthor,
            )
            refreshLocalPlugins()

            // 保存完还要用户自己切页、找脚本、手动开启 —— 这一步没有存在的理由
            var started = false
            if (runAfter) {
                PluginManager.plugins.find { it.id == finalId }?.let { saved ->
                    if (!saved.isRunning && PluginManager.startPlugin(saved)) {
                        started = true
                        refreshLocalPlugins()
                    }
                }
            }

            showAgentSaveDialog = false
            pendingAgentScript = null
            persistAgentSessions()
            Toasts.qqToast(
                2,
                when {
                    started -> "脚本「$safeName」已保存并运行"
                    overwriting -> "脚本「$safeName」已更新到 v$safeVersion"
                    else -> "脚本「$safeName」已保存 v$safeVersion"
                }
            )
        }
    }

    /**
     * 把产物按 Happy 插件目录规范写入。
     *
     * `info.prop` 在原文上局部改，只覆盖 id / 名称 / 版本号 / 作者 —— 整份重建会抹掉
     * 注释和自定义键；用户起的名字也必须落到这个文件的 pluginName 上，否则只改了文件夹名，
     * 列表读的还是模型自拟的名字，两边对不上。
     *
     * 覆盖既有脚本时不传版本号与作者，默认沿用它在 info.prop 里原本的值。
     *
     * desc.txt 属于必须文件，模型漏写时也要落一个。
     * 附加文件按相对路径展开，多层目录自动创建。
     */
    private fun writeFilesToDir(
        dir: File,
        files: AgentScriptFiles,
        version: String = files.version,
        author: String = files.author,
    ) {
        if (files.mainJava.isNotBlank()) {
            File(dir, AgentScriptFiles.FILE_MAIN).writeText(files.mainJava)
        }
        File(dir, AgentScriptFiles.FILE_INFO).writeText(
            AgentService.buildInfoPropFor(
                id = files.pluginId,
                name = files.pluginName,
                version = version,
                author = author,
                original = files.infoProp,
            )
        )
        File(dir, AgentScriptFiles.FILE_DESC).writeText(
            files.descTxt.ifBlank { files.pluginName.ifBlank { "由 Agent 生成的脚本" } }
        )

        // 路径在解析阶段已经净化过一次，这里再用规范化后的绝对路径复核一遍，
        // 确保任何情况下都写不出脚本目录。
        val root = dir.canonicalFile
        files.extraFiles.forEach { (path, content) ->
            runCatching {
                val target = File(dir, path).canonicalFile
                if (target.path == root.path) return@runCatching
                if (!target.path.startsWith(root.path + File.separator)) return@runCatching
                target.parentFile?.mkdirs()
                target.writeText(content)
            }
        }
    }

    fun cancelAgentGeneration() {
        agentJob?.cancel()
        agentJob = null
        stopAgentTicker()
        isAgentGenerating = false
        agentStage = AgentStage.FAILED

        // 收尾流式气泡：光标停闪，未走完的阶段标注「已取消」。
        // 已经吐出内容的话就打上不完整标记 —— 中途停下和撞上 max_tokens 一样
        // 都是「没说完」，界面据此给出同样的续写入口；一个字都没出就不必了。
        var partial = false
        _agentMessages.update { list ->
            list.map { msg ->
                if (!msg.isStreaming) {
                    msg
                } else {
                    if (msg.content.isNotBlank()) partial = true
                    msg.copy(
                        isStreaming = false,
                        truncated = msg.content.isNotBlank(),
                        steps = msg.steps.map { step ->
                            if (step.done) step else step.copy(detail = "已取消")
                        },
                    )
                }
            }
        }
        persistAgentSessions()
        Toasts.qqToast(0, if (partial) "已暂停，可以点「继续输出」接着生成" else "已取消生成")
    }

    // ---- 人机确认：覆盖既有脚本 ----

    /** 用户确认覆盖：此时才真正写盘。 */
    fun confirmAgentOverwrite() {
        val files = pendingOverwriteFiles
        val name = pendingOverwriteName
        if (files == null || name == null) return

        val plugin = PluginManager.plugins.find { it.id == files.pluginId }
        if (plugin == null) {
            Toasts.qqToast(1, "目标脚本已不存在")
            dismissAgentOverwrite()
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            writeFilesToDir(File(plugin.dirPath), files)
            refreshLocalPlugins()
            dismissAgentOverwrite()
            persistAgentSessions()
            Toasts.qqToast(2, "脚本已更新: $name")
        }
    }

    fun dismissAgentOverwrite() {
        pendingOverwriteName = null
        pendingOverwriteFiles = null
        pendingOverwriteDiff = null
    }

    /** 清空当前工作区。只服务内部会话切换，「清空」在界面上的入口就是「新建会话」。 */
    private fun clearAgentChat() {
        _agentMessages.value = emptyList()
        agentFiles = AgentScriptFiles()
        agentEditingScriptId = null
        agentInsight = AgentScriptInsight()
        agentStage = AgentStage.CONNECTING
        agentElapsedMs = 0L
    }

    private fun File.readTextOrEmpty(): String =
        if (exists()) runCatching { readText() }.getOrDefault("") else ""

    /** 历史消息进上下文前压一下长度：上一轮那份完整脚本不该整份重发。 */
    private fun AgentChatMessage.asHistoryContent(): String =
        if (content.length <= MAX_HISTORY_CHARS) {
            content
        } else {
            content.take(MAX_HISTORY_CHARS) + "\n…（内容过长，已截断）"
        }

    /** 消息稳定 id：列表 key 靠它，不随流式内容变化。 */
    private fun newMessageId(): String = "m${System.currentTimeMillis()}_${messageSeq++}"

    /** 工具在卡片上的中文名。工具自己是英文名，这里只管展示。 */
    private fun toolLabel(toolName: String): String = when (toolName) {
        "search_api" -> "检索 API 文档"
        "list_files" -> "查看脚本目录"
        "read_file" -> "读取文件"
        else -> toolName
    }

    fun handleIconSelection(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val inputStream = context.contentResolver.openInputStream(uri)
                val targetFile = File("${QQCurrentEnv.currentDir}data/plugin")
                FileUtils.ensureFile(targetFile)
                inputStream?.use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Toasts.qqToast(2, "悬浮图标已更新，下次显示生效")
            }.onFailure {
                Toasts.qqToast(1, "图标设置失败: ${it.message}")
            }
        }
    }
}
