package cn.hxy.happy.ui.pages.plugin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.hxy.happy.BuildConfig
import cn.hxy.happy.R
import cn.hxy.happy.plugin.bean.AgentChatMessage
import cn.hxy.happy.plugin.bean.AgentMode
import cn.hxy.happy.plugin.bean.AgentScriptFiles
import cn.hxy.happy.plugin.bean.AgentScriptInsight
import cn.hxy.happy.plugin.bean.AgentSession
import cn.hxy.happy.plugin.bean.AgentStage
import cn.hxy.happy.plugin.bean.AgentToolStep
import cn.hxy.happy.plugin.agent.ScriptDiff
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import cn.hxy.happy.plugin.net.AgentConfig
import cn.hxy.happy.ui.components.atoms.DialogButton
import cn.hxy.happy.ui.components.atoms.HappySwitch
import cn.hxy.happy.ui.components.atoms.DialogTextField
import cn.hxy.happy.ui.components.dialogs.BaseDialogSurface
import cn.hxy.happy.ui.components.dialogs.CenterDialog
import cn.hxy.happy.ui.components.dialogs.ConfirmDialog
import cn.hxy.happy.ui.core.theme.AccentBlue
import cn.hxy.happy.ui.core.theme.HappyTheme
import cn.hxy.happy.utils.qq.HostInfo
import cn.hxy.happy.utils.qq.Toasts
import cn.hxy.happy.utils.ui.CodeHighlighter

/** 输入区与悬浮 Tab 之间留出的呼吸空间。 */
private val INPUT_TO_TABS_GAP = 14.dp

/** Android 无障碍的最小可点区域。圆形按钮、抽屉里的图标动作都要满足它。 */
private val MIN_TOUCH_TARGET = 48.dp

// 气泡左侧头像占 20dp + 8dp 间距：结果操作、续写提示都按这个缩进对齐
private val AgentContentIndent = 28.dp

// ==================== 本页排版尺度 ====================
//
// 项目里没有全局 Type/Shape 体系（ui/core/theme 只有颜色和少量 Dimens），页面各自写死数值。
// 这里给 AgentPage 立一套最小尺度，收掉 10/11/12/13/14/15/18sp 的无序混用：
// 正文 14、次级标签 13、辅助说明 12、元信息 11，区块标题 17。
private object AgentType {
    val title = 17.sp
    val body = 14.sp
    val label = 13.sp
    val caption = 12.sp
    val micro = 11.sp
    val code = 12.sp
}

/** 4dp 节奏的间距刻度，收掉 3/5/6/7/9/10/11/13 这类随手取值。 */
private object AgentSpace {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
}

/** 圆角只留三档：胶囊内元素 12、卡片与面板 16、底部抽屉 20。 */
private object AgentRadius {
    val chip = 12.dp
    val card = 16.dp
    val sheet = 20.dp
}

/** 动效三档，缓动与 SearchTopBar 的 silkEasing 同一条曲线，避免每处各写一个 tween。 */
private object AgentMotion {
    const val enter = 220
    const val state = 200
    const val exit = 150
    val ease = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
}


/** 把毫秒压成一眼能读的时长。 */
private fun formatElapsed(ms: Long): String =
    if (ms < 1000L) "${ms}ms" else "%.1fs".format(ms / 1000.0)

// ==================== 图标映射 ====================
//
// 放在 UI 这一侧：AgentMode 是纯数据，不该把 Android 资源 id 塞进去，
// 否则 bean 层就得依赖 R，序列化和复用都会跟着变脏。
// AgentStage 的图标映射随生成中胶囊一起下线：那一行现在只用文字表达阶段，
// 图标和右侧文字说的是同一件事，留着只是噪音。

private val AgentMode.iconRes: Int
    get() = when (this) {
        AgentMode.QA -> R.drawable.ic_agent_qa
        AgentMode.TASK -> R.drawable.ic_agent_task
    }

/** 产物文件按扩展名给图标。 */
private fun iconResOfScriptFile(path: String): Int = when {
    path.endsWith(".java", ignoreCase = true) -> R.drawable.ic_agent_code
    path.endsWith(".json", ignoreCase = true) -> R.drawable.ic_agent_parse
    else -> R.drawable.ic_agent_file
}

/**
 * 顶层 AnimatedVisibility 的转发。
 *
 * Column 里再嵌一层 Box 之后直接调 AnimatedVisibility，会撞上 ColumnScope 的成员
 * 重载（它要求一个显式的 ColumnScope 接收者），编译不过。这里站在没有作用域接收者
 * 的位置转发一次，调用点就不用关心自己嵌了多深。
 */
@Composable
private fun AgentAnimatedVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    enter: EnterTransition = fadeIn(),
    exit: ExitTransition = fadeOut(),
    content: @Composable AnimatedVisibilityScope.() -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = enter,
        exit = exit,
        content = content
    )
}

@Composable
fun AgentPage(
    messages: List<AgentChatMessage>,
    files: AgentScriptFiles,
    sessions: List<AgentSession>,
    isGenerating: Boolean,
    config: AgentConfig?,
    mode: AgentMode,
    showSettings: Boolean,
    showSaveDialog: Boolean,
    showHistoryPanel: Boolean,
    modelList: List<String>,
    isFetchingModels: Boolean,
    isTestingConnection: Boolean,
    connectionResult: String?,
    modelFetchError: String?,
    insight: AgentScriptInsight,
    stage: AgentStage,
    elapsedMs: Long,
    overwriteTargetName: String?,
    overwriteDiff: ScriptDiff?,
    onSendMessage: (String) -> Unit,
    onSelectMode: (AgentMode) -> Unit,
    onEditFile: (String, String) -> Unit,
    onSaveScript: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismissSettings: () -> Unit,
    onUpdateConfig: (AgentConfig) -> Unit,
    onFetchModels: (String, String) -> Unit,
    onTestConnection: (String, String) -> Unit,
    onDismissSaveDialog: () -> Unit,
    onConfirmSave: (String, String, String, Boolean) -> Unit,
    onConfirmOverwrite: () -> Unit,
    onDismissOverwrite: () -> Unit,
    onCancelGenerate: () -> Unit,
    onToggleHistory: () -> Unit,
    onNewSession: () -> Unit,
    onSwitchSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    /** 底部悬浮 Tab 的实测占位高度：输入区据此上移，避免被 Tab 压住。 */
    reservedBottom: Dp,
    quickPrompts: List<String>,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val colors = HappyTheme.colors

    // 代码抽屉：null = 收起；非空 = 展开并显示该文件。
    //
    // 对话是唯一主线，代码按需从底部升起、看完划走。之前用「整页切换」表达
    // 在看代码，用户得在两个心智模型间反复横跳；而且产物其实已经在对话里
    // 通过工具卡片表达过了，代码页那时只剩一个只读查看器的角色。
    var codeSheetFile by remember { mutableStateOf<String?>(null) }

    // 产物被清空时收起抽屉，避免停在一个已经不存在的文件上。
    LaunchedEffect(files.isEmpty) {
        if (files.isEmpty) codeSheetFile = null
    }

    // 生成中不占着抽屉，让用户看流式输出。
    LaunchedEffect(isGenerating) {
        if (isGenerating) codeSheetFile = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .imePadding()
    ) {
        if (config == null || config.apiUrl.isBlank() || config.apiKey.isBlank()) {
            ConfigPromptView(onOpenSettings = onOpenSettings)
        } else {
            Box(modifier = Modifier.weight(1f)) {
                if (messages.isEmpty()) {
                    WelcomeView(
                        mode = mode,
                        quickPrompts = quickPrompts,
                        sessionCount = sessions.size,
                        onOpenHistory = onToggleHistory,
                        onSendMessage = onSendMessage
                    )
                } else {
                    AgentChatView(
                        messages = messages,
                        canSave = mode == AgentMode.TASK && !files.isEmpty,
                        onSaveScript = onSaveScript,
                        onViewCode = { codeSheetFile = AgentScriptFiles.FILE_MAIN },
                        onContinue = {
                            onSendMessage("上次输出被 max_tokens 截断了，请从中断处继续，把剩余部分补齐。")
                        }
                    )
                }

                AgentAnimatedVisibility(
                    visible = isGenerating,
                    enter = fadeIn(tween(AgentMotion.enter)) + slideInVertically(
                        animationSpec = tween(AgentMotion.enter, easing = AgentMotion.ease),
                        initialOffsetY = { -it / 2 }
                    ),
                    exit = fadeOut(tween(AgentMotion.exit)) + slideOutVertically(
                        animationSpec = tween(AgentMotion.exit),
                        targetOffsetY = { -it / 2 }
                    ),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(top = AgentSpace.sm)
                            .clip(RoundedCornerShape(AgentRadius.sheet))
                            .background(colors.cardBackground)
                            .border(
                                width = 1.dp,
                                color = AccentBlue.copy(alpha = 0.22f),
                                shape = RoundedCornerShape(AgentRadius.sheet)
                            )
                            .padding(horizontal = AgentSpace.md, vertical = AgentSpace.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = AccentBlue
                        )
                        Spacer(modifier = Modifier.width(AgentSpace.sm))
                        // 阶段名是主信息、耗时是副信息：两级字号，先读到「现在在干什么」。
                        // 原先这里还塞了一枚 13dp 的阶段图标，在真机字号下看不清，
                        // 和右边的文字又是同一件事，不如把位置让给文字。
                        Text(
                            text = stage.labelIn(mode),
                            fontSize = AgentType.label,
                            fontWeight = FontWeight.Medium,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.width(AgentSpace.sm))
                        Text(
                            text = formatElapsed(elapsedMs),
                            fontSize = AgentType.micro,
                            color = colors.textSecondary
                        )
                    }
                }

                AgentAnimatedVisibility(
                    visible = codeSheetFile != null && !files.isEmpty,
                    enter = slideInVertically(
                        animationSpec = tween(AgentMotion.enter, easing = AgentMotion.ease),
                        initialOffsetY = { it }
                    ),
                    exit = slideOutVertically(
                        animationSpec = tween(AgentMotion.exit),
                        targetOffsetY = { it }
                    ),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    AgentCodeSheet(
                        files = files,
                        activeFile = codeSheetFile ?: AgentScriptFiles.FILE_MAIN,
                        insight = insight,
                        isDark = colors.isDark,
                        onSelectFile = { codeSheetFile = it },
                        onEditFile = onEditFile,
                        onClose = { codeSheetFile = null }
                    )
                }
            }

            AgentInputBar(
                mode = mode,
                isGenerating = isGenerating,
                onToggleMode = {
                    // 不再弹 toast：模式名现在就写在按钮上，切完立刻能看见，
                    // 再弹一次只是在重复播报用户刚点的东西。
                    onSelectMode(if (mode == AgentMode.QA) AgentMode.TASK else AgentMode.QA)
                },
                onSend = { text ->
                    onSendMessage(text)
                    keyboardController?.hide()
                },
                onCancel = onCancelGenerate
            )

            // 悬浮 Tab 固定贴在 Box 底部，输入区必须始终留在它上方。
            // 这里不再按键盘可见性归零：宿主是寄生 Activity，IME inset 会误报，
            // 一旦误判成「键盘可见」，让位归零，输入框就会滑到 Tab 底下。
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(reservedBottom + INPUT_TO_TABS_GAP)
            )
        }
    }

    AgentSettingsDialog(
        visible = showSettings,
        initial = config,
        modelList = modelList,
        isFetchingModels = isFetchingModels,
        isTestingConnection = isTestingConnection,
        connectionResult = connectionResult,
        modelFetchError = modelFetchError,
        onDismiss = onDismissSettings,
        onConfirm = onUpdateConfig,
        onFetchModels = onFetchModels,
        onTestConnection = onTestConnection
    )

    AgentSaveDialog(
        visible = showSaveDialog,
        files = files,
        insight = insight,
        onDismiss = onDismissSaveDialog,
        onConfirm = onConfirmSave
    )

    AgentOverwriteDialog(
        targetName = overwriteTargetName,
        diff = overwriteDiff,
        files = files,
        insight = insight,
        onConfirm = onConfirmOverwrite,
        onDismiss = onDismissOverwrite
    )

    AgentHistoryDialog(
        visible = showHistoryPanel,
        sessions = sessions,
        onDismiss = onToggleHistory,
        onNewSession = onNewSession,
        onSwitchSession = onSwitchSession,
        onDeleteSession = onDeleteSession
    )
}




// ==================== 代码抽屉 ====================

/**
 * 抽屉占内容区的高度比例。刻意不满屏 —— 上方留出一截，暗示「这是浮层，不是切页」。
 */
private const val CODE_SHEET_HEIGHT = 0.74f

/**
 * 代码抽屉。
 *
 * 从底部升起、看完划走。对话始终是主线，代码是临时查看的对象 —— 之前用整页
 * 切换表达「正在看代码」，用户得在两个心智模型之间反复横跳；而产物其实已经在
 * 对话里通过工具卡片表达过了，那个代码页只剩一个只读查看器的角色。
 */
@Composable
private fun AgentCodeSheet(
    files: AgentScriptFiles,
    activeFile: String,
    insight: AgentScriptInsight,
    isDark: Boolean,
    onSelectFile: (String) -> Unit,
    onEditFile: (String, String) -> Unit,
    onClose: () -> Unit
) {
    val colors = HappyTheme.colors
    val context = LocalContext.current
    val sheetShape = RoundedCornerShape(topStart = AgentRadius.sheet, topEnd = AgentRadius.sheet)

    // 下拉关闭。抽屉只占 74% 高度、上方还露着内容，那条把手在视觉上就是在说
    // 「可以往下拽」——不给手势的话它就只是画着好看。阈值取 88dp：
    // 短促误触不会关，明确下拽才关。
    val scope = rememberCoroutineScope()
    val dragOffset = remember { Animatable(0f) }
    val dismissThreshold = with(LocalDensity.current) { 88.dp.toPx() }

    val original = files.contentOf(activeFile)
    var editing by remember(activeFile) { mutableStateOf(false) }
    var draft by remember(activeFile) { mutableStateOf(original) }

    // 外部内容变了（重新生成、切会话）就把草稿同步过来，避免编辑框里留着旧文本
    LaunchedEffect(activeFile, original) {
        if (!editing) draft = original
    }

    val editScroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(CODE_SHEET_HEIGHT)
            .offset { IntOffset(0, dragOffset.value.roundToInt()) }
            .clip(sheetShape)
            .background(colors.cardBackground)
            .border(1.dp, colors.textSecondary.copy(alpha = 0.12f), sheetShape)
    ) {
        // 头部整条可拖：把手、文件名、动作都在这一块里。
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        scope.launch {
                            dragOffset.snapTo((dragOffset.value + delta).coerceAtLeast(0f))
                        }
                    },
                    onDragStopped = {
                        if (dragOffset.value > dismissThreshold) {
                            onClose()
                        } else {
                            dragOffset.animateTo(
                                targetValue = 0f,
                                animationSpec = tween(AgentMotion.state, easing = AgentMotion.ease)
                            )
                        }
                    }
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = AgentSpace.md, bottom = AgentSpace.xs),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.textSecondary.copy(alpha = 0.3f))
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = AgentSpace.lg, end = AgentSpace.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (editing) "编辑 $activeFile" else activeFile,
                    fontSize = AgentType.label,
                    fontWeight = FontWeight.Bold,
                    // 编辑态不再把标题染红：红色留给真正的破坏性动作，
                    // 「正在编辑」由旁边的取消/保存自己说明。
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (editing) {
                    SheetTextAction("取消") {
                        draft = original
                        editing = false
                    }
                    SheetTextAction("保存", highlight = true) {
                        onEditFile(activeFile, draft)
                        editing = false
                    }
                } else {
                    SheetIconAction(R.drawable.ic_agent_edit, "编辑 $activeFile") {
                        draft = original
                        editing = true
                    }
                    SheetIconAction(R.drawable.ic_agent_copy, "复制 $activeFile") {
                        copyToClipboard(context, original)
                    }
                    SheetIconAction(R.drawable.ic_close, "收起代码面板") { onClose() }
                }
            }
        }

        // 文件标签用相对路径：分层脚本会带 core 目录下的同名文件
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = AgentSpace.sm),
            horizontalArrangement = Arrangement.spacedBy(AgentSpace.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            files.fileNames.forEach { path ->
                SheetFileTab(
                    iconRes = iconResOfScriptFile(path),
                    label = path,
                    active = path == activeFile,
                    onClick = {
                        if (editing) {
                            // 带着未保存的改动切文件会丢内容，先落定
                            onEditFile(activeFile, draft)
                            editing = false
                        }
                        onSelectFile(path)
                    }
                )
            }
        }

        // 能力摘要搬到这里而不是留在对话流：它是对产物的注解，看代码时正好用得上，
        // 放在气泡里则是和工具卡片重复一遍。
        if (!insight.isEmpty && !editing) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = AgentSpace.lg,
                        end = AgentSpace.md,
                        top = AgentSpace.xs,
                        bottom = AgentSpace.sm
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_agent_think),
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(AgentSpace.sm))
                Text(
                    text = insight.summary,
                    fontSize = AgentType.caption,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        HorizontalDivider(color = colors.textSecondary.copy(alpha = 0.1f))

        Box(modifier = Modifier.weight(1f)) {
            if (editing) {
                // 编辑时用等宽纯文本、不做语法高亮 —— 高亮是逐行构造 AnnotatedString，
                // 每敲一个字符全量重算会明显卡顿；等保存回查看态再着色。
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(editScroll)
                ) {
                    BasicTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AgentSpace.lg, vertical = AgentSpace.md),
                        textStyle = TextStyle(
                            fontSize = AgentType.code,
                            lineHeight = 18.sp,
                            fontFamily = FontFamily.Monospace,
                            color = colors.textPrimary
                        ),
                        cursorBrush = SolidColor(AccentBlue)
                    )
                }
            } else {
                AgentCodeViewer(code = original, isDark = isDark)
            }
        }
    }
}

/** 抽屉头部的图标动作。 */
@Composable
private fun SheetIconAction(
    iconRes: Int,
    contentDescription: String,
    tint: Color? = null,
    onClick: () -> Unit
) {
    val colors = HappyTheme.colors

    Box(
        modifier = Modifier
            .size(MIN_TOUCH_TARGET)
            .clip(RoundedCornerShape(AgentRadius.chip))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            // 之前这里全是 null：抽屉上三个纯图标按钮对读屏用户等于不存在。
            contentDescription = contentDescription,
            tint = tint ?: colors.textPrimary,
            modifier = Modifier.size(18.dp)
        )
    }
}

/** 抽屉头部的文字动作：编辑态下的「保存 / 取消」用。 */
@Composable
private fun SheetTextAction(
    text: String,
    highlight: Boolean = false,
    onClick: () -> Unit
) {
    val colors = HappyTheme.colors

    Box(
        modifier = Modifier
            .heightIn(min = MIN_TOUCH_TARGET)
            .clip(RoundedCornerShape(AgentRadius.chip))
            .clickable { onClick() }
            .padding(horizontal = AgentSpace.md),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = AgentType.label,
            fontWeight = FontWeight.Medium,
            color = if (highlight) AccentBlue else colors.textPrimary
        )
    }
}

@Composable
private fun SheetFileTab(iconRes: Int, label: String, active: Boolean, onClick: () -> Unit) {
    val colors = HappyTheme.colors

    Row(
        modifier = Modifier
            .heightIn(min = MIN_TOUCH_TARGET)
            .clip(RoundedCornerShape(AgentRadius.chip))
            .background(if (active) AccentBlue.copy(alpha = 0.14f) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = AgentSpace.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = if (active) AccentBlue else colors.textSecondary,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(AgentSpace.sm))
        Text(
            text = label,
            fontSize = AgentType.caption,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            color = if (active) AccentBlue else colors.textSecondary,
            maxLines = 1
        )
    }
}

@Composable
private fun ToolbarChip(
    text: String,
    onClick: () -> Unit,
    iconRes: Int? = null,
    enabled: Boolean = true,
    highlight: Boolean = false,
    danger: Boolean = false
) {
    val colors = HappyTheme.colors

    val tint = when {
        !enabled -> colors.textSecondary.copy(alpha = 0.5f)
        highlight -> colors.accentGreen
        danger -> colors.accentRed
        else -> colors.textPrimary
    }

    Row(
        modifier = Modifier
            .heightIn(min = MIN_TOUCH_TARGET)
            .clip(RoundedCornerShape(AgentRadius.chip))
            .background(colors.background)
            .border(
                width = 1.dp,
                color = colors.textSecondary.copy(alpha = 0.15f),
                shape = RoundedCornerShape(AgentRadius.chip)
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = AgentSpace.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (iconRes != null) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(AgentSpace.sm))
        }
        Text(
            text = text,
            fontSize = AgentType.label,
            fontWeight = FontWeight.Medium,
            color = tint,
            maxLines = 1
        )
    }
}

// ==================== 代码查看器 ====================

@Composable
private fun AgentCodeViewer(code: String, isDark: Boolean) {
    val colors = HappyTheme.colors

    val palette = remember(isDark) { CodeHighlighter.palette(isDark) }
    val lines = remember(code) { code.split('\n') }
    val listState = rememberLazyListState()

    if (code.isBlank()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("此文件为空", fontSize = AgentType.label, color = colors.textSecondary)
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
        contentPadding = PaddingValues(vertical = AgentSpace.sm)
    ) {
        itemsIndexed(lines) { index, line ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = (index + 1).toString(),
                    fontSize = AgentType.code,
                    fontFamily = FontFamily.Monospace,
                    color = palette.lineNumber,
                    modifier = Modifier
                        .width(44.dp)
                        .padding(end = AgentSpace.sm),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                )
                Text(
                    text = CodeHighlighter.highlightLine(line, palette),
                    fontSize = AgentType.code,
                    lineHeight = 18.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = AgentSpace.md)
                )
            }
        }
    }
}

// ==================== 对话流 ====================

@Composable
private fun AgentChatView(
    messages: List<AgentChatMessage>,
    canSave: Boolean,
    onSaveScript: () -> Unit,
    onViewCode: () -> Unit,
    onContinue: () -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length, messages.lastOrNull()?.reasoningContent?.length) {
        if (messages.isNotEmpty()) {
            listState.scrollToItem(messages.size - 1, scrollOffset = Int.MAX_VALUE)
        }
    }

    // 产物是否存在由会话级状态判断（canSave）—— 消息上的 files 不落盘，
    // 切回历史会话时它是空的，拿它判断会漏掉结果卡。
    val settled = messages.lastOrNull()
        ?.let { it.role == AgentChatMessage.ROLE_ASSISTANT && !it.isStreaming } == true
    val showResult = settled && canSave
    // 只对最后一条提示续写：早先那几轮的截断多半已经被补过了
    val showContinue = settled && messages.lastOrNull()?.truncated == true

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = AgentSpace.lg, vertical = AgentSpace.sm),
        verticalArrangement = Arrangement.spacedBy(AgentSpace.md)
    ) {
        // key 必须与内容无关：以前用 hashCode()，流式每来一个 token key 就变，
        // LazyColumn 每帧重建气泡，思考区反复重排 —— 就是那个上下抖动。
        itemsIndexed(
            items = messages,
            key = { index, msg -> msg.id.ifBlank { "legacy_$index" } }
        ) { _, msg ->
            AgentBubble(message = msg, modifier = Modifier.animateItem())
        }

        // 结果卡挂在对话末尾：保存入口跟着产物走，不再占一行常驻工具栏。
        if (canSave && showResult) {
            item(key = "result_actions") {
                AgentResultActions(
                    onSaveScript = onSaveScript,
                    onViewCode = onViewCode,
                    modifier = Modifier.animateItem()
                )
            }
        }

        // 上一轮被截断：给一个明确的续写入口，而不是让用户自己打「继续」
        if (showContinue) {
            item(key = "continue_action") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem()
                        .padding(start = AgentContentIndent),
                    horizontalArrangement = Arrangement.spacedBy(AgentSpace.sm)
                ) {
                    ToolbarChip(
                        text = "继续输出",
                        onClick = onContinue,
                        iconRes = R.drawable.ic_agent_continue,
                        highlight = true
                    )
                }
            }
        }
    }
}

/** 产物就绪后的下一步动作。 */
@Composable
private fun AgentResultActions(
    onSaveScript: () -> Unit,
    onViewCode: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = AgentContentIndent),
        horizontalArrangement = Arrangement.spacedBy(AgentSpace.sm)
    ) {
        ToolbarChip(
            text = "保存为脚本",
            onClick = onSaveScript,
            iconRes = R.drawable.ic_agent_save,
            highlight = true
        )
        ToolbarChip(
            text = "查看代码",
            onClick = onViewCode,
            iconRes = R.drawable.ic_agent_code
        )
    }
}

@Composable
private fun AgentBubble(message: AgentChatMessage, modifier: Modifier = Modifier) {
    val colors = HappyTheme.colors

    if (message.role == AgentChatMessage.ROLE_SYSTEM) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = message.content,
                fontSize = AgentType.label,
                color = colors.textSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(AgentRadius.chip))
                    .background(colors.cardBackground)
                    .padding(horizontal = AgentSpace.lg, vertical = AgentSpace.sm)
            )
        }
        return
    }

    val isUser = message.role == AgentChatMessage.ROLE_USER

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Icon(
                painter = painterResource(R.drawable.ic_agent_bot),
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.padding(top = AgentSpace.xs).size(20.dp)
            )
            Spacer(modifier = Modifier.width(AgentSpace.sm))
        }

        Column(
            modifier = Modifier.fillMaxWidth(0.85f),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            // 工具卡片放在气泡之外：它描述的是「本地做了什么」，不是模型说的话。
            if (!isUser && message.steps.isNotEmpty()) {
                AgentStepsCard(steps = message.steps, elapsedMs = message.elapsedMs)
                Spacer(modifier = Modifier.height(AgentSpace.sm))
            }

            Column(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = AgentRadius.card,
                            topEnd = AgentRadius.card,
                            bottomStart = if (isUser) AgentRadius.card else AgentSpace.xs,
                            bottomEnd = if (isUser) AgentSpace.xs else AgentRadius.card
                        )
                    )
                    .background(if (isUser) AccentBlue else colors.cardBackground)
                    .padding(AgentSpace.md)
            ) {
                if (!isUser && !message.reasoningContent.isNullOrBlank()) {
                    ReasoningSection(
                        reasoning = message.reasoningContent,
                        isStreaming = message.isStreaming
                    )
                    Spacer(modifier = Modifier.height(AgentSpace.sm))
                }
                if (message.content.isNotBlank()) {
                    Text(
                        text = message.content,
                        fontSize = AgentType.body,
                        color = if (isUser) Color.White else colors.textPrimary,
                        lineHeight = 20.sp
                    )
                }
                if (message.isStreaming) {
                    Spacer(modifier = Modifier.height(AgentSpace.sm))
                    StreamingCursor()
                }
            }

            // 闲聊轮次解析结果为空，这里不冒「已生成」的假消息
            if (!isUser && message.files != null && !message.files.isEmpty && !message.isStreaming) {
                Spacer(modifier = Modifier.height(AgentSpace.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.ic_agent_file),
                        contentDescription = null,
                        tint = colors.accentGreen,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(AgentSpace.xs))
                    Text(
                        text = "已生成 ${message.files.pluginName}（${message.files.pluginId}）",
                        fontSize = AgentType.caption,
                        color = colors.accentGreen
                    )
                }
            }
        }

        if (isUser) {
            Spacer(modifier = Modifier.width(AgentSpace.sm))
            Icon(
                painter = painterResource(R.drawable.ic_agent_user),
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.padding(top = AgentSpace.xs).size(20.dp)
            )
        }
    }
}

/**
 * 流式光标。
 *
 * 以前这里是 `Text("▌")` —— 拿 Unicode 字符当图标，字号不同字宽还会跳；
 * 换成一个自己呼吸的竖条，用哪个字体都不变形。
 */
@Composable
private fun StreamingCursor() {
    val transition = rememberInfiniteTransition(label = "streaming")
    val caretAlpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 620, easing = AgentMotion.ease),
            repeatMode = RepeatMode.Reverse
        ),
        label = "caretAlpha"
    )

    Box(
        modifier = Modifier
            .size(width = 3.dp, height = 14.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(AccentBlue.copy(alpha = caretAlpha))
    )
}

/**
 * 思考过程。
 *
 * 流式期间默认展开并跟随最新内容 —— 模型在思考时用户不该只看见转圈；
 * 收流后自动收起，不长期占用版面。
 */
@Composable
private fun ReasoningSection(reasoning: String, isStreaming: Boolean = false) {
    val colors = HappyTheme.colors

    // 这里不能把 isStreaming 当 remember 的 key：收流那一刻状态会被重置，
    // 思考区突然收起，整条消息跟着弹一下。改成只由流式状态单向驱动展开。
    var expanded by remember { mutableStateOf(false) }
    val reasoningScroll = rememberScrollState()

    LaunchedEffect(isStreaming) {
        if (isStreaming) expanded = true
    }

    LaunchedEffect(reasoning.length, expanded) {
        if (expanded) {
            // 等一帧让布局量到新高度，否则 maxValue 还是上一帧的
            withFrameNanos { }
            reasoningScroll.scrollTo(reasoningScroll.maxValue)
        }
    }

    // 折叠指示改成真的会转的 chevron：以前是「▾ / ▸」两个字符换着显示，
    // 塌陷感全无，还依赖字体里恰好有这两个字形。
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        animationSpec = tween(AgentMotion.state, easing = AgentMotion.ease),
        label = "reasoningChevron"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MIN_TOUCH_TARGET)
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_agent_think),
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(AgentSpace.sm))
            Text(
                text = "思考过程",
                fontSize = AgentType.caption,
                color = colors.textSecondary,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                painter = painterResource(R.drawable.ic_agent_chevron_down),
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier
                    .size(16.dp)
                    .rotate(chevronRotation)
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                animationSpec = tween(AgentMotion.state, easing = AgentMotion.ease)
            ) + fadeIn(animationSpec = tween(AgentMotion.state)),
            exit = shrinkVertically(
                animationSpec = tween(AgentMotion.state, easing = AgentMotion.ease)
            ) + fadeOut(animationSpec = tween(AgentMotion.exit))
        ) {
            Text(
                text = reasoning,
                fontSize = AgentType.caption,
                color = colors.textSecondary.copy(alpha = 0.85f),
                lineHeight = 17.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 160.dp)
                    .verticalScroll(reasoningScroll)
                    .clip(RoundedCornerShape(AgentRadius.chip))
                    .background(colors.background.copy(alpha = 0.6f))
                    .padding(AgentSpace.sm)
            )
        }
    }
}

// ==================== 工具卡片 ====================

/**
 * 生成流水线的可视化。
 *
 * 模型只吐文本，「连接 → 思考 → 落码 → 解析」都是本地做的；
 * 陈列出来才知道卡在哪一段，而不是对着一个转圈瞎猜。
 */
@Composable
private fun AgentStepsCard(
    steps: List<AgentToolStep>,
    elapsedMs: Long = 0L
) {
    val colors = HappyTheme.colors

    val doneCount = steps.count { it.done }
    val activeIndex = steps.indexOfFirst { !it.done }
    // 整条轨道就是进度条：跑到哪一段，颜色跟到哪一段。全线跑完才转绿，
    // 不再是一块从第一帧起就定死的静态边框。
    val accent by animateColorAsState(
        targetValue = if (activeIndex < 0) colors.accentGreen else AccentBlue,
        animationSpec = tween(AgentMotion.state, easing = AgentMotion.ease),
        label = "stepsAccent"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AgentRadius.card))
            .background(colors.background)
            .border(1.dp, accent.copy(alpha = 0.22f), RoundedCornerShape(AgentRadius.card))
            .padding(horizontal = AgentSpace.md, vertical = AgentSpace.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ic_agent_tool),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(AgentSpace.sm))
            Text(
                text = "执行过程",
                fontSize = AgentType.caption,
                fontWeight = FontWeight.Medium,
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = buildString {
                    append(doneCount).append('/').append(steps.size)
                    if (elapsedMs > 0L) append(" · ").append(formatElapsed(elapsedMs))
                },
                fontSize = AgentType.micro,
                color = accent
            )
        }

        Spacer(modifier = Modifier.height(AgentSpace.sm))

        steps.forEachIndexed { index, step ->
            StepRailRow(
                step = step,
                isFirst = index == 0,
                isLast = index == steps.size - 1,
                isActive = index == activeIndex,
                accent = accent
            )
        }
    }
}

/**
 * 流水线里的一步 —— 轨道上的一段。
 *
 * 以前每行塞两枚 11/12dp 的小图标（状态一枚、阶段一枚）再加三行文字，
 * 真机上图标根本看不清，状态得靠猜。现在状态由轨道节点承担，
 * 行里只留「做了什么 / 耗时」。
 */
@Composable
private fun StepRailRow(
    step: AgentToolStep,
    isFirst: Boolean,
    isLast: Boolean,
    isActive: Boolean,
    accent: Color
) {
    val colors = HappyTheme.colors

    val nodeColor = when {
        step.done -> colors.accentGreen
        isActive -> accent
        else -> colors.textSecondary.copy(alpha = 0.35f)
    }
    val trackColor = colors.textSecondary.copy(alpha = 0.18f)

    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
        Column(
            modifier = Modifier
                .width(14.dp)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 首尾不留悬空线头：轨道从第一个节点起、到最后一个节点止。
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(if (isFirst) 0.dp else 6.dp)
                    .background(if (isFirst) Color.Transparent else trackColor)
            )
            StepNode(done = step.done, active = isActive, tint = nodeColor)
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .weight(1f)
                    .background(if (isLast) Color.Transparent else trackColor)
            )
        }

        Spacer(modifier = Modifier.width(AgentSpace.sm))

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(
                    top = if (isFirst) 0.dp else AgentSpace.xs,
                    bottom = if (isLast) 0.dp else AgentSpace.xs
                )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = step.displayTitle,
                    fontSize = AgentType.caption,
                    fontWeight = if (step.done) FontWeight.Normal else FontWeight.Medium,
                    color = if (step.done) colors.textPrimary else colors.textSecondary
                )
                Spacer(modifier = Modifier.weight(1f))
                if (step.costMs > 0L) {
                    Text(
                        text = formatElapsed(step.costMs),
                        fontSize = AgentType.micro,
                        color = colors.textSecondary
                    )
                }
            }
            if (!step.detail.isNullOrBlank()) {
                Text(
                    text = step.detail,
                    fontSize = AgentType.micro,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
        }
    }
}

/**
 * 轨道节点。
 *
 * 进行中的那一步多一圈会呼吸的光晕 —— 全页唯一一处循环动效，
 * 只用来回答一个问题：「现在卡在哪一段」。
 */
@Composable
private fun StepNode(done: Boolean, active: Boolean, tint: Color) {
    val haloAlpha = if (!active) {
        0f
    } else {
        val transition = rememberInfiniteTransition(label = "stepNode")
        val alpha by transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1100, easing = AgentMotion.ease),
                repeatMode = RepeatMode.Reverse
            ),
            label = "stepHalo"
        )
        alpha
    }

    Box(modifier = Modifier.size(14.dp), contentAlignment = Alignment.Center) {
        if (active) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = haloAlpha))
            )
        }
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(if (done) tint else Color.Transparent)
                .border(
                    width = 1.5.dp,
                    color = if (done) tint else tint.copy(alpha = 0.7f),
                    shape = CircleShape
                )
        )
    }
}

// ==================== 欢迎页 ====================

@Composable
private fun WelcomeView(
    mode: AgentMode,
    quickPrompts: List<String>,
    sessionCount: Int,
    onOpenHistory: () -> Unit,
    onSendMessage: (String) -> Unit
) {
    val colors = HappyTheme.colors

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = AgentSpace.lg, vertical = AgentSpace.lg),
        verticalArrangement = Arrangement.spacedBy(AgentSpace.sm)
    ) {
        // 头部说清「这一页能干什么」：模式名当标题、解释当副文案。
        // 页面标题已经在宿主顶栏，这里不重复它。
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(AccentBlue.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(mode.iconRes),
                        contentDescription = null,
                        tint = AccentBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(AgentSpace.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when (mode) {
                            AgentMode.QA -> "问 API 用法"
                            AgentMode.TASK -> "生成插件脚本"
                        },
                        fontSize = AgentType.title,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(AgentSpace.xs))
                    Text(
                        text = when (mode) {
                            AgentMode.QA -> "提问 API 用法与参数，回答会带上可直接运行的示例"
                            AgentMode.TASK -> "描述需求，生成 main.java + info.prop + desc.txt"
                        },
                        fontSize = AgentType.caption,
                        lineHeight = 18.sp,
                        color = colors.textSecondary
                    )
                }
            }
        }

        // 只在真有历史会话时出现：这是「接着上次那件事说」的入口，
        // 过去只能从宿主顶栏的菜单里进，空态页对此没有任何提示。
        if (sessionCount > 0) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = MIN_TOUCH_TARGET)
                        .clip(RoundedCornerShape(AgentRadius.card))
                        .background(colors.cardBackground)
                        .clickable { onOpenHistory() }
                        .padding(horizontal = AgentSpace.md, vertical = AgentSpace.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_agent_history),
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(AgentSpace.sm))
                    Text(
                        text = "历史会话",
                        fontSize = AgentType.label,
                        fontWeight = FontWeight.Medium,
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = sessionCount.toString(),
                        fontSize = AgentType.caption,
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.width(AgentSpace.xs))
                    Icon(
                        painter = painterResource(R.drawable.ic_agent_chevron_down),
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier
                            .size(16.dp)
                            .rotate(-90f)
                    )
                }
            }
        }

        item { SectionTitle(text = if (mode == AgentMode.QA) "常见问题" else "快速开始") }

        items(quickPrompts.chunked(2), key = { it.first() }) { pair ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // 同一行的两枚 chip 拉成等高：长短不一时底边才是齐的。
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(AgentSpace.sm)
            ) {
                pair.forEach { prompt ->
                    QuickPromptChip(
                        text = prompt,
                        onClick = { onSendMessage(prompt) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
                if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

/** 分区标题。样式对齐 SettingListItems 的 PreferenceSection：正文级字号、Bold、次级色，不画线。 */
@Composable
private fun SectionTitle(text: String) {
    val colors = HappyTheme.colors

    Text(
        text = text,
        fontSize = AgentType.body,
        fontWeight = FontWeight.Bold,
        color = colors.textSecondary,
        modifier = Modifier.padding(
            start = AgentSpace.xs,
            top = AgentSpace.md,
            bottom = AgentSpace.sm
        )
    )
}

/** 轻量提示标签：细边框胶囊，明确表达「可点击的示例需求」。 */
@Composable
private fun QuickPromptChip(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // 空态里的示例需求是这个页面唯一的入口按钮，按正文级 13sp 排，
    // 而不是按注释级小字；触摸高度一并跟到 48dp。
    Box(
        modifier = modifier
            .heightIn(min = MIN_TOUCH_TARGET)
            .clip(RoundedCornerShape(AgentRadius.chip))
            .border(
                width = 1.dp,
                color = AccentBlue.copy(alpha = 0.28f),
                shape = RoundedCornerShape(AgentRadius.chip)
            )
            .clickable { onClick() }
            .padding(horizontal = AgentSpace.md, vertical = AgentSpace.sm),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            fontSize = AgentType.label,
            lineHeight = 18.sp,
            color = AccentBlue,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ==================== 输入区 ====================

@Composable
private fun AgentInputBar(
    mode: AgentMode,
    isGenerating: Boolean,
    onToggleMode: () -> Unit,
    onSend: (String) -> Unit,
    onCancel: () -> Unit
) {
    val colors = HappyTheme.colors

    var text by remember { mutableStateOf("") }
    val canSend = text.isNotBlank()
    // 发送键的可用态用颜色过渡表达，而不是一帧之间从灰跳到蓝。
    val sendColor by animateColorAsState(
        targetValue = if (canSend) AccentBlue else colors.textSecondary.copy(alpha = 0.28f),
        animationSpec = tween(AgentMotion.state),
        label = "sendColor"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.cardBackground)
            .padding(horizontal = AgentSpace.md, vertical = AgentSpace.sm)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            // 模式切换从整整一行挪到这里：它是一开始选一次的东西，
            // 不该长期占着页面最显眼的位置。
            // 但这枚按钮现在带字：只有一个图标时，「问答 / 任务」这种非默认态
            // 用户猜不出来，切完还要弹一句 toast 才能让人确认切成功了。
            Row(
                modifier = Modifier
                    .sizeIn(minWidth = MIN_TOUCH_TARGET, minHeight = MIN_TOUCH_TARGET)
                    .clip(CircleShape)
                    .background(colors.background)
                    .clickable { onToggleMode() }
                    .padding(horizontal = AgentSpace.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(mode.iconRes),
                    contentDescription = null,
                    tint = AccentBlue,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(AgentSpace.sm))
                Text(
                    text = mode.label,
                    fontSize = AgentType.label,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary
                )
            }

            Spacer(modifier = Modifier.width(AgentSpace.sm))

            BasicTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .weight(1f)
                    // 上限 5 行：再长就让输入框自己滚，否则它会把整屏撑掉，
                    // 上面正在看的回复会被顶得只剩一条缝。
                    .heightIn(min = MIN_TOUCH_TARGET, max = 132.dp)
                    .clip(RoundedCornerShape(AgentRadius.card))
                    .background(colors.background)
                    .padding(horizontal = AgentSpace.lg, vertical = AgentSpace.md),
                textStyle = TextStyle(
                    fontSize = AgentType.body,
                    color = colors.textPrimary,
                    lineHeight = 19.sp
                ),
                cursorBrush = SolidColor(AccentBlue),
                maxLines = 5,
                decorationBox = { inner ->
                    Box {
                        if (text.isEmpty()) {
                            Text(
                                text = when (mode) {
                                    AgentMode.QA -> "询问 API 用法，例如：怎么注册悬浮窗菜单？"
                                    AgentMode.TASK -> "请描述你的插件需求"
                                },
                                fontSize = AgentType.body,
                                color = colors.textSecondary
                            )
                        }
                        inner()
                    }
                }
            )

            Spacer(modifier = Modifier.width(AgentSpace.sm))

            if (isGenerating) {
                Box(
                    modifier = Modifier
                        .size(MIN_TOUCH_TARGET)
                        .clip(CircleShape)
                        .background(colors.accentRed)
                        .clickable { onCancel() }
                        .semantics { contentDescription = "停止生成" },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.White)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(MIN_TOUCH_TARGET)
                        .clip(CircleShape)
                        .background(sendColor)
                        .clickable(enabled = canSend) {
                            onSend(text)
                            text = ""
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_up),
                        contentDescription = "发送",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// ==================== 未配置引导 ====================

@Composable
private fun ConfigPromptView(onOpenSettings: () -> Unit) {
    val colors = HappyTheme.colors

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(AgentSpace.xl)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(AccentBlue.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_settings_gear),
                    contentDescription = null,
                    tint = AccentBlue,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(AgentSpace.lg))
            Text(
                text = "需要配置 API",
                fontSize = AgentType.title,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(AgentSpace.sm))
            Text(
                text = "请先配置 OpenAI 兼容的 API 地址和密钥",
                fontSize = AgentType.body,
                color = colors.textSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(AgentSpace.xl))
            // 原来这里是一行蓝字 —— 看着像说明文字，其实是这一页唯一的出口。
            // 做成按钮，点得动这件事就不用猜。
            Text(
                text = "前往设置",
                fontSize = AgentType.body,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                modifier = Modifier
                    .clip(RoundedCornerShape(AgentRadius.card))
                    .background(AccentBlue)
                    .clickable { onOpenSettings() }
                    .padding(horizontal = AgentSpace.xl, vertical = AgentSpace.md)
            )
        }
    }
}

// ==================== 会话历史 ====================

@Composable
private fun AgentHistoryDialog(
    visible: Boolean,
    sessions: List<AgentSession>,
    onDismiss: () -> Unit,
    onNewSession: () -> Unit,
    onSwitchSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit
) {
    if (!visible) return

    val colors = HappyTheme.colors

    var query by remember(visible) { mutableStateOf("") }
    // 删除会话是不可撤销的：以前在列表里点一下就没了。先落一个待删对象，
    // 走一次确认再真删。
    var pendingDelete by remember(visible) { mutableStateOf<AgentSession?>(null) }
    val filtered = if (query.isBlank()) {
        sessions
    } else {
        sessions.filter {
            it.title.contains(query, ignoreCase = true) ||
                    it.prompt.contains(query, ignoreCase = true)
        }
    }

    CenterDialog(visible = visible, onDismiss = onDismiss) {
        BaseDialogSurface(
            title = "历史记录",
            bottomBar = {
                DialogButton("新建会话", onNewSession, true)
                Spacer(modifier = Modifier.width(AgentSpace.md))
                DialogButton("关闭", onDismiss, false)
            }
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 会话攒到几十条之后，翻列表比搜索慢得多
                DialogTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = "搜索",
                    hint = "按需求内容或标题查找",
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(AgentSpace.md))

                if (filtered.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = AgentSpace.xl),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_agent_history),
                            contentDescription = null,
                            tint = colors.textSecondary.copy(alpha = 0.4f),
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(AgentSpace.sm))
                        Text(
                            text = if (sessions.isEmpty()) "还没有历史记录" else "没有匹配的会话",
                            fontSize = AgentType.body,
                            color = colors.textSecondary
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 340.dp)
                    ) {
                        items(filtered, key = { it.id }) { session ->
                            AgentHistoryRow(
                                session = session,
                                onSwitch = onSwitchSession,
                                onDelete = { pendingDelete = session }
                            )
                        }
                    }
                }
            }
        }
    }

    val deleteTitle = pendingDelete?.title?.takeIf { it.isNotBlank() } ?: "未命名会话"
    ConfirmDialog(
        visible = pendingDelete != null,
        title = "删除这条会话？",
        message = "「$deleteTitle」的对话记录会被删掉，删掉之后找不回来。",
        confirmText = "删除",
        onDismiss = { pendingDelete = null },
        onConfirm = {
            pendingDelete?.let { onDeleteSession(it.id) }
            pendingDelete = null
        },
        danger = true
    )
}

/**
 * 历史会话的一行。
 *
 * 时间、模式、产物名都要露出来 —— 会话记录里这些字段本来就存着，
 * 只显示「N 条 · 脚本名」等于把能帮用户辨认的信息全藏起来了。
 */
@Composable
private fun AgentHistoryRow(
    session: AgentSession,
    onSwitch: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    val colors = HappyTheme.colors
    val sessionMode = AgentMode.of(session.mode)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MIN_TOUCH_TARGET)
            .clickable { onSwitch(session.id) }
            .padding(vertical = AgentSpace.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = session.title.ifBlank { "未命名会话" },
                fontSize = AgentType.body,
                fontWeight = FontWeight.Medium,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(AgentSpace.xs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(sessionMode.iconRes),
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(AgentSpace.xs))
                Text(
                    text = buildString {
                        append(sessionMode.label)
                        append(" · ").append(formatRelativeTime(session.timestamp))
                        append(" · ").append(session.messages.size).append(" 条")
                        session.files.pluginName.takeIf { it.isNotBlank() }?.let {
                            append(" · ").append(it)
                        }
                    },
                    fontSize = AgentType.micro,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        // 破坏性动作给足 48dp 触摸区：以前是 12sp 的两字，点偏一点就切进会话去了
        Box(
            modifier = Modifier
                .size(MIN_TOUCH_TARGET)
                .clip(RoundedCornerShape(AgentRadius.chip))
                .clickable { onDelete(session.id) },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "删除",
                fontSize = AgentType.caption,
                color = colors.accentRed
            )
        }
    }
}

/** 会话列表用相对时间：几十条记录里，一串绝对时间戳反而难扫。 */
private fun formatRelativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000L -> "刚刚"
        diff < 3_600_000L -> "${diff / 60_000L} 分钟前"
        diff < 86_400_000L -> "${diff / 3_600_000L} 小时前"
        diff < 7 * 86_400_000L -> "${diff / 86_400_000L} 天前"
        else -> HISTORY_DATE_FORMAT.format(Date(timestamp))
    }
}

private val HISTORY_DATE_FORMAT = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

// ==================== API 设置 ====================

/**
 * 检测/拉取的结果条。
 *
 * 以前「连通性检测」的结果是按钮旁边一行 13sp 裸文字，颜色之外没有任何形状，
 * 在这么长的表单里扫过去基本看不见；转圈的小图标也孤零零挂在按钮右侧。
 * 现在状态和结果共用一条带底色和图标的信息条，位置固定在触发它的按钮下方。
 */
@Composable
private fun DialogStatusStrip(
    text: String,
    tint: Color,
    iconRes: Int? = null,
    loading: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AgentRadius.chip))
            .background(tint.copy(alpha = 0.10f))
            .padding(horizontal = AgentSpace.md, vertical = AgentSpace.sm)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = tint
            )
        } else if (iconRes != null) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.width(AgentSpace.sm))
        Text(
            text = text,
            fontSize = AgentType.label,
            lineHeight = 18.sp,
            color = tint
        )
    }
}

@Composable
private fun AgentSettingsDialog(
    visible: Boolean,
    initial: AgentConfig?,
    modelList: List<String>,
    isFetchingModels: Boolean,
    isTestingConnection: Boolean,
    connectionResult: String?,
    modelFetchError: String?,
    onDismiss: () -> Unit,
    onConfirm: (AgentConfig) -> Unit,
    onFetchModels: (String, String) -> Unit,
    onTestConnection: (String, String) -> Unit
) {
    if (!visible) return

    val colors = HappyTheme.colors

    var apiUrl by remember(visible) { mutableStateOf(initial?.apiUrl ?: "") }
    var apiKey by remember(visible) { mutableStateOf(initial?.apiKey ?: "") }
    var model by remember(visible) { mutableStateOf(initial?.model ?: "gpt-4o-mini") }
    // Key 默认遮起来：这是过肩看一眼就能抄走的字符串，要看得见得自己点开
    var keyVisible by remember(visible) { mutableStateOf(false) }
    var temperature by remember(visible) {
        mutableStateOf((initial?.temperature ?: AgentConfig.DEFAULT_TEMPERATURE).toString())
    }
    var maxTokens by remember(visible) {
        mutableStateOf((initial?.maxTokens ?: AgentConfig.DEFAULT_MAX_TOKENS).toString())
    }

    CenterDialog(visible = visible, onDismiss = onDismiss) {
        BaseDialogSurface(
            title = "Agent API 配置",
            bottomBar = {
                DialogButton("取消", onDismiss, false)
                Spacer(modifier = Modifier.width(AgentSpace.md))
                DialogButton(
                    "保存",
                    {
                        onConfirm(
                            AgentConfig(
                                apiUrl = apiUrl.trim(),
                                apiKey = apiKey.trim(),
                                model = model.trim().ifBlank { "gpt-4o-mini" },
                                // 越界或写错就回落到默认值，不让一个手滑把请求参数带偏
                                temperature = temperature.toDoubleOrNull()
                                    ?.coerceIn(AgentConfig.MIN_TEMPERATURE, AgentConfig.MAX_TEMPERATURE)
                                    ?: AgentConfig.DEFAULT_TEMPERATURE,
                                maxTokens = maxTokens.toIntOrNull()
                                    ?.coerceIn(AgentConfig.MIN_MAX_TOKENS, AgentConfig.MAX_MAX_TOKENS)
                                    ?: AgentConfig.DEFAULT_MAX_TOKENS,
                            )
                        )
                    },
                    true
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                SectionTitle("连接")
                DialogTextField(
                    value = apiUrl,
                    onValueChange = { apiUrl = it },
                    label = "API 地址",
                    hint = "https://api.openai.com/v1/chat/completions",
                    helper = "填到 chat completions 为止，服务商给的路径不一样就按它给的填。",
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(AgentSpace.md))
                DialogTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = "API Key",
                    hint = "sk-...",
                    singleLine = true,
                    visualTransformation = if (keyVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailing = {
                        Box(
                            modifier = Modifier
                                .size(MIN_TOUCH_TARGET)
                                .clip(RoundedCornerShape(AgentRadius.chip))
                                .clickable { keyVisible = !keyVisible },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(
                                    if (keyVisible) R.drawable.ic_agent_eye_off else R.drawable.ic_agent_eye
                                ),
                                contentDescription = if (keyVisible) "隐藏 API Key" else "显示 API Key",
                                tint = colors.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.height(AgentSpace.md))
                DialogButton("连通性检测", { onTestConnection(apiUrl, apiKey) }, true)
                // 检测中、检测结果共用同一块位置：以前结果只是按钮旁边一行裸文字，扫过去根本看不见
                val testResult = connectionResult
                when {
                    isTestingConnection -> {
                        Spacer(modifier = Modifier.height(AgentSpace.sm))
                        DialogStatusStrip(text = "正在检测连接…", tint = colors.textSecondary, loading = true)
                    }
                    testResult != null -> {
                        Spacer(modifier = Modifier.height(AgentSpace.sm))
                        val ok = testResult.startsWith("连接成功")
                        DialogStatusStrip(
                            text = testResult,
                            tint = if (ok) colors.accentGreen else colors.accentRed,
                            iconRes = if (ok) R.drawable.ic_agent_done else R.drawable.ic_agent_error
                        )
                    }
                }

                SectionTitle("模型")
                DialogTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = "模型",
                    hint = "gpt-4o-mini",
                    helper = "服务商支持哪些就先拉一遍，也可以直接把模型名填进来。",
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(AgentSpace.md))
                DialogButton("获取模型", { onFetchModels(apiUrl, apiKey) }, false)
                val fetchError = modelFetchError
                when {
                    isFetchingModels -> {
                        Spacer(modifier = Modifier.height(AgentSpace.sm))
                        DialogStatusStrip(text = "正在拉取模型列表…", tint = colors.textSecondary, loading = true)
                    }
                    fetchError != null -> {
                        Spacer(modifier = Modifier.height(AgentSpace.sm))
                        DialogStatusStrip(
                            text = "获取失败：$fetchError",
                            tint = colors.accentRed,
                            iconRes = R.drawable.ic_agent_error
                        )
                    }
                }

                if (modelList.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(AgentSpace.md))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "选一个模型",
                            fontSize = AgentType.label,
                            fontWeight = FontWeight.Medium,
                            color = colors.textSecondary,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${modelList.size} 个",
                            fontSize = AgentType.caption,
                            color = colors.textSecondary
                        )
                    }
                    Spacer(modifier = Modifier.height(AgentSpace.sm))
                    // 列表随数量伸缩，不再固定占掉 160dp 的空盒子
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .clip(RoundedCornerShape(AgentRadius.chip))
                            .background(colors.background)
                    ) {
                        items(modelList, key = { it }) { m ->
                            val selected = m == model
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = MIN_TOUCH_TARGET)
                                    .clickable { model = m }
                                    .padding(horizontal = AgentSpace.md),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = m,
                                    fontSize = AgentType.body,
                                    color = if (selected) colors.accentBlue else colors.textPrimary,
                                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                // 只有颜色和字重的话，暗色下几乎看不出选了哪个
                                if (selected) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_agent_done),
                                        contentDescription = "当前模型",
                                        tint = colors.accentBlue,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 生成参数：默认值对代码生成够用，一般不用动，所以并排收在最后一段
                SectionTitle("生成参数")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AgentSpace.md)
                ) {
                    DialogTextField(
                        value = temperature,
                        onValueChange = { temperature = it },
                        modifier = Modifier.weight(1f),
                        label = "温度",
                        hint = AgentConfig.DEFAULT_TEMPERATURE.toString(),
                        keyboardType = KeyboardType.Decimal,
                        helper = "${AgentConfig.MIN_TEMPERATURE} ~ ${AgentConfig.MAX_TEMPERATURE}，越低越稳",
                        singleLine = true
                    )
                    DialogTextField(
                        value = maxTokens,
                        onValueChange = { maxTokens = it },
                        modifier = Modifier.weight(1f),
                        label = "最大输出",
                        hint = AgentConfig.DEFAULT_MAX_TOKENS.toString(),
                        keyboardType = KeyboardType.Number,
                        helper = "${AgentConfig.MIN_MAX_TOKENS} ~ ${AgentConfig.MAX_MAX_TOKENS} tokens",
                        singleLine = true
                    )
                }
                Spacer(modifier = Modifier.height(AgentSpace.sm))
                Text(
                    text = "脚本较长时调大「最大输出」，否则容易写到一半被截断。",
                    fontSize = AgentType.micro,
                    lineHeight = 16.sp,
                    color = colors.textSecondary
                )

                // 运行环境信息收纳在这里，不再占用主界面底部
                SectionTitle("运行环境")
                Text(
                    text = "Happy ${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE}) · BeanShell · Java 8",
                    fontSize = AgentType.micro,
                    color = colors.textSecondary
                )
                Spacer(modifier = Modifier.height(AgentSpace.xs))
                Text(
                    text = "宿主 ${HostInfo.hostName} ${HostInfo.versionName}(${HostInfo.versionCode})",
                    fontSize = AgentType.micro,
                    color = colors.textSecondary
                )
            }
        }
    }
}

@Composable
private fun AgentSaveDialog(
    visible: Boolean,
    files: AgentScriptFiles,
    insight: AgentScriptInsight,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, Boolean) -> Unit
) {
    if (!visible) return

    val colors = HappyTheme.colors

    var name by remember(visible) { mutableStateOf("") }

    // 版本号与作者预填脚本现有的值：编辑既有脚本时不该被悄悄改回 1.0 / Happy Agent，
    // 否则下一个版本号没地方往上加，"高版本覆盖低版本"也就无从谈起。
    var version by remember(visible) { mutableStateOf(files.version) }
    var author by remember(visible) { mutableStateOf(files.author) }

    // 默认勾上：保存完还要用户自己切页、找到脚本、手动开启，这一步没有存在的理由
    var runAfter by remember(visible) { mutableStateOf(true) }

    val finalName = name.trim().ifBlank { files.pluginName.ifBlank { "Agent脚本" } }

    CenterDialog(visible = visible, onDismiss = onDismiss) {
        BaseDialogSurface(
            title = "确认写入脚本",
            bottomBar = {
                DialogButton("取消", onDismiss, false)
                Spacer(modifier = Modifier.width(AgentSpace.md))
                DialogButton("确认写入", { onConfirm(name, version, author, runAfter) }, true)
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                DialogTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "脚本名称",
                    hint = files.pluginName.ifBlank { "例如：自动回复" },
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(AgentSpace.md))
                DialogTextField(
                    value = version,
                    onValueChange = { version = it },
                    label = "版本号",
                    hint = AgentScriptFiles.DEFAULT_VERSION,
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(AgentSpace.md))
                DialogTextField(
                    value = author,
                    onValueChange = { author = it },
                    label = "作者",
                    hint = AgentScriptFiles.DEFAULT_AUTHOR,
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(AgentSpace.sm))
                Text(
                    text = "同名脚本已存在时，版本号高过它才会覆盖。",
                    fontSize = AgentType.micro,
                    lineHeight = 16.sp,
                    color = colors.textSecondary
                )

                Spacer(modifier = Modifier.height(AgentSpace.lg))
                Text(
                    text = "将写入的文件",
                    fontSize = AgentType.caption,
                    fontWeight = FontWeight.Medium,
                    color = colors.textSecondary
                )
                Spacer(modifier = Modifier.height(AgentSpace.sm))
                FileManifest(files = files, insight = insight)

                Spacer(modifier = Modifier.height(AgentSpace.sm))
                Text(
                    text = "目录  plugin/$finalName/",
                    fontSize = AgentType.micro,
                    fontFamily = FontFamily.Monospace,
                    color = colors.textSecondary
                )

                Spacer(modifier = Modifier.height(AgentSpace.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "保存后立即运行",
                            fontSize = AgentType.label,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "跳过「切到本地脚本页再手动开启」这一步",
                            fontSize = AgentType.micro,
                            color = colors.textSecondary
                        )
                    }
                    HappySwitch(runAfter, { runAfter = it })
                }
            }
        }
    }
}

/**
 * 人机确认：编辑模式下覆盖既有脚本前的最后一道闸。
 *
 * 之前模型一产出就自动 writeText 覆盖，用户没有反悔的机会；
 * 现在产物先停在 ViewModel 里，只有点「覆盖写入」才落盘。
 */
@Composable
private fun AgentOverwriteDialog(
    targetName: String?,
    files: AgentScriptFiles,
    insight: AgentScriptInsight,
    diff: ScriptDiff?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    if (targetName == null) return

    val colors = HappyTheme.colors

    CenterDialog(visible = true, onDismiss = onDismiss) {
        BaseDialogSurface(
            title = "覆盖既有脚本？",
            bottomBar = {
                DialogButton("取消", onDismiss, false)
                Spacer(modifier = Modifier.width(AgentSpace.md))
                // 覆盖是不可撤销的，确认键用红色，跟「保存为脚本」区分开
                DialogButton("覆盖写入", onConfirm, true, danger = true)
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "「$targetName」的 main.java / info.prop / desc.txt 将被本次生成结果替换。",
                    fontSize = AgentType.body,
                    lineHeight = 21.sp,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(AgentSpace.sm))
                Text(
                    text = "原内容不可撤销。想保留原版，请先取消，再用「保存为脚本」另存新脚本。",
                    fontSize = AgentType.micro,
                    lineHeight = 16.sp,
                    color = colors.accentRed
                )
                Spacer(modifier = Modifier.height(AgentSpace.md))
                AgentDiffSummary(diff)

                Spacer(modifier = Modifier.height(AgentSpace.md))
                FileManifest(files = files, insight = insight)
            }
        }
    }
}

/**
 * 改动摘要。
 *
 * 覆盖既有脚本前唯一能让人判断「这次改对了没有」的依据 —— 只报「将被替换」
 * 等于让人闭着眼睛按确认。没有变化时整块不出现。
 */
@Composable
private fun AgentDiffSummary(diff: ScriptDiff?) {
    if (diff == null || !diff.hasChanges) return

    val colors = HappyTheme.colors

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "本次改动",
            fontSize = AgentType.caption,
            fontWeight = FontWeight.Medium,
            color = colors.textSecondary
        )
        Spacer(modifier = Modifier.height(AgentSpace.sm))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(AgentRadius.chip))
                .background(colors.background)
                .padding(horizontal = AgentSpace.sm, vertical = AgentSpace.sm)
        ) {
            diff.files.forEach { change ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = AgentSpace.xs / 2),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = change.path,
                        fontSize = AgentType.micro,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (change.isNew) {
                        Text("新增", fontSize = AgentType.micro, color = colors.accentGreen)
                    } else {
                        Text("+${change.addedLines}", fontSize = AgentType.micro, color = colors.accentGreen)
                        Spacer(modifier = Modifier.width(AgentSpace.sm))
                        Text("-${change.removedLines}", fontSize = AgentType.micro, color = colors.accentRed)
                    }
                }
            }

            if (diff.addedMethods.isNotEmpty()) {
                Spacer(modifier = Modifier.height(AgentSpace.sm))
                Text(
                    text = "新增方法  " + diff.addedMethods.joinToString("、"),
                    fontSize = AgentType.micro,
                    lineHeight = 16.sp,
                    color = colors.accentGreen
                )
            }
            if (diff.removedMethods.isNotEmpty()) {
                Spacer(modifier = Modifier.height(AgentSpace.xs))
                Text(
                    text = "删除方法  " + diff.removedMethods.joinToString("、"),
                    fontSize = AgentType.micro,
                    lineHeight = 16.sp,
                    color = colors.accentRed
                )
            }
        }
    }
}

/** 三件套的落盘清单，保存卡与覆盖确认卡共用。 */
@Composable
private fun FileManifest(files: AgentScriptFiles, insight: AgentScriptInsight) {
    val colors = HappyTheme.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AgentRadius.chip))
            .background(colors.background)
            .padding(horizontal = AgentSpace.sm, vertical = AgentSpace.sm)
    ) {
        ManifestLine(AgentScriptFiles.FILE_MAIN, files.mainJava, blankHint = "为空，不写入")
        ManifestLine(AgentScriptFiles.FILE_INFO, files.infoProp, blankHint = "为空，按模板生成")
        ManifestLine(AgentScriptFiles.FILE_DESC, files.descTxt, blankHint = "为空，写入默认描述")
        // 分层脚本带来的附加文件也一并列出，让用户在落盘前看清会写出哪些目录
        files.extraFiles.forEach { (path, content) ->
            ManifestLine(path, content, blankHint = "为空，不写入")
        }
    }

    if (!insight.isEmpty) {
        Spacer(modifier = Modifier.height(AgentSpace.sm))
        Text(
            text = "能力摘要 · ${insight.summary}",
            fontSize = AgentType.micro,
            color = colors.accentGreen,
            lineHeight = 16.sp
        )
    }
}

@Composable
private fun ManifestLine(name: String, content: String, blankHint: String) {
    val colors = HappyTheme.colors

    val blank = content.isBlank()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AgentSpace.xs / 2),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 状态点用 Box 画：原来是 ○ / ● 两个字符当图标，字体一换大小和基线全乱
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (blank) colors.textSecondary.copy(alpha = 0.35f) else colors.accentGreen)
        )
        Spacer(modifier = Modifier.width(AgentSpace.sm))
        Text(
            text = if (blank) "$name（$blankHint）" else name,
            fontSize = AgentType.caption,
            color = if (blank) colors.textSecondary else colors.textPrimary
        )
        Spacer(modifier = Modifier.weight(1f))
        if (!blank) {
            Text(
                text = "${content.lines().size} 行 · ${content.length} 字",
                fontSize = AgentType.micro,
                color = colors.textSecondary
            )
        }
    }
}

// ==================== 剪贴板 ====================

private fun copyToClipboard(context: Context, text: String) {
    if (text.isBlank()) {
        Toasts.qqToast(1, "当前文件为空")
        return
    }
    runCatching {
        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cb.setPrimaryClip(ClipData.newPlainText("HappyScript", text))
        Toasts.qqToast(2, "已复制到剪贴板")
    }.onFailure {
        Toasts.qqToast(1, "复制失败: ${it.message}")
    }
}
