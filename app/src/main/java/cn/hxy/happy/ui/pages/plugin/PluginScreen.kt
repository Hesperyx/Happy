package cn.hxy.happy.ui.pages.plugin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import cn.hxy.happy.R
import cn.hxy.happy.plugin.agent.AgentPrompts
import cn.hxy.happy.ui.components.atoms.DialogButton
import cn.hxy.happy.ui.components.atoms.DialogTextField
import cn.hxy.happy.ui.components.atoms.HappyCard
import cn.hxy.happy.ui.components.dialogs.BaseDialogSurface
import cn.hxy.happy.ui.components.dialogs.CenterDialog
import cn.hxy.happy.ui.components.molecules.FloatingLiquidTabs
import cn.hxy.happy.ui.components.molecules.SearchTopBar
import cn.hxy.happy.ui.components.molecules.TopBarMenuItem
import cn.hxy.happy.ui.core.theme.AccentGreen
import cn.hxy.happy.ui.core.theme.HappyTheme
import cn.hxy.happy.ui.viewmodel.PluginViewModel
import cn.hxy.happy.utils.qq.Toasts

@Immutable
data class LocalPluginData(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String,
    val isRunning: Boolean,
    val isAutoLoad: Boolean
)

/**
 * 悬浮 Tab 的最小让位高度。
 *
 * 实测值按理已经够用，但底栏药丸在首帧或主题切换瞬间可能量到偏小的尺寸；
 * 取一个下界可避免页面内容临时压到 Tab 底下。
 */
private val MIN_TABS_RESERVED = 88.dp

@Composable
fun PluginScreen(
    localPlugins: List<LocalPluginData>,
    isLocalRefreshing: Boolean,
    themeMode: Int,
    isDarkTheme: Boolean,
    onThemeToggle: () -> Unit,
    onBackClick: () -> Unit,
    onCreatePlugin: () -> Unit,
    onRefreshLocal: () -> Unit,
    onPluginRunToggle: (String, Boolean) -> Unit,
    onPluginAutoLoadToggle: (String, Boolean) -> Unit,
    onPluginDelete: (String) -> Unit,
    onPluginReload: (String) -> Unit,
    onPickIcon: () -> Unit,
    showCreateDialog: Boolean,
    onDismissCreateDialog: () -> Unit,
    onConfirmCreatePlugin: (String, String, String, String) -> Unit,
    showSuccessDialog: Boolean,
    createdPluginPath: String,
    onDismissSuccessDialog: () -> Unit,
    isSearchActive: Boolean,
    onSearchActiveChange: (Boolean) -> Unit,
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    viewModel: PluginViewModel
) {
    val colors = HappyTheme.colors
    
    var selectedTab by remember { 
        mutableIntStateOf(value = 0) 
    }

    val localListState = rememberLazyListState()

    val density = LocalDensity.current

    /** 悬浮 Tab 实测占位高度，供页面内容让位；首帧测量前给个保守初值。 */
    var tabsReservedHeight by remember { mutableStateOf(MIN_TABS_RESERVED) }

    /** 脚本 API 文档弹窗；文档随 APK 打包在 assets 里，不走网络。 */
    var showApiDoc by remember { mutableStateOf(false) }

    BackHandler(
        enabled = isSearchActive
    ) { 
        onSearchActiveChange(false) 
    }

    val screenBackdrop = rememberLayerBackdrop()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                color = colors.background
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    insets = WindowInsets.statusBars
                )
        ) {
            // 编辑态由标题表达，不再往输入框上方塞一行徽章 ——
            // 「正在改哪份脚本」属于页面级状态，标题是它该待的地方。
            // 列表可能正被搜索词筛掉，所以查不到名字时退回 id：
            // 否则「退出修改」这个唯一出口会跟着搜索结果一起消失。
            val editingName = viewModel.agentEditingScriptId?.let { id ->
                localPlugins.find { it.id == id }?.name ?: id
            }

            SearchTopBar(
                title = when {
                    selectedTab != 1 -> "Java Plugin"
                    editingName != null -> "修改 · $editingName"
                    else -> "Plugin Agent"
                },
                searchQuery = searchQuery,
                onQueryChange = onQueryChange,
                isSearchActive = isSearchActive,
                onSearchActiveChange = onSearchActiveChange,
                showBackButton = true,
                onBackClick = onBackClick,
                themeMode = themeMode,
                isDarkTheme = isDarkTheme,
                onThemeToggle = onThemeToggle,
                searchHint = "搜索脚本...",
                // 顶栏只有一个「更多」菜单，所以两个页面的动作都收进 menuItems：
                // 本地脚本页是「创建脚本 / 设置图标」，
                // Agent 页是「退出修改（仅编辑态）/ 新建会话 / 历史会话 / Agent 设置」。
                menuItems = if (selectedTab == 1) buildList {
                    // 退出修改模式的入口挪到这里：编辑态下才出现
                    if (editingName != null) {
                        add(
                            TopBarMenuItem(
                                title = "退出修改",
                                iconRes = R.drawable.ic_close,
                                onClick = viewModel::exitEditMode
                            )
                        )
                    }
                    add(
                        TopBarMenuItem(
                            title = "新建会话",
                            iconRes = R.drawable.ic_add_circle,
                            onClick = viewModel::newAgentSession
                        )
                    )
                    add(
                        TopBarMenuItem(
                            title = "历史会话",
                            iconRes = R.drawable.ic_agent_history,
                            onClick = viewModel::toggleAgentHistoryPanel
                        )
                    )
                    add(
                        TopBarMenuItem(
                            title = "Agent 设置",
                            iconRes = R.drawable.ic_settings_gear,
                            onClick = viewModel::openAgentSettings
                        )
                    )
                } else listOf(
                    TopBarMenuItem(
                        title = "创建脚本", 
                        iconRes = R.drawable.ic_add_circle, 
                        onClick = onCreatePlugin
                    ),
                    TopBarMenuItem(
                        title = "设置图标", 
                        iconRes = R.drawable.ic_settings_gear, 
                        onClick = onPickIcon
                    ),
                    // 脚本 API 文档直接读随包资源，不再打开任何作者服务器地址。
                    TopBarMenuItem(
                        title = "脚本 API", 
                        iconRes = R.drawable.ic_agent_file, 
                        onClick = { showApiDoc = true }
                    )
                )
            )

            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = { 
                    fadeIn(
                        animationSpec = tween(durationMillis = 300)
                    ) togetherWith fadeOut(
                        animationSpec = tween(durationMillis = 300)
                    ) 
                },
                modifier = Modifier
                    .weight(1f)
                    .layerBackdrop(screenBackdrop),
                label = "PluginTabContent"
            ) { tabIndex ->
                when (tabIndex) {
                    0 -> LocalPluginPage(
                        plugins = localPlugins,
                        isRefreshing = isLocalRefreshing,
                        searchQuery = searchQuery,
                        listState = localListState,
                        onRunToggle = onPluginRunToggle,
                        onAutoLoadToggle = onPluginAutoLoadToggle,
                        onDelete = onPluginDelete,
                        onReload = onPluginReload,
                        // 从脚本卡片直接进 Agent 编辑：以前只能切到 Agent 页、
                        // 再从欢迎页那个只显示前 6 个的列表里找，超过 6 个就找不到。
                        onEditWithAgent = { id ->
                            viewModel.loadPluginForAgent(id)
                            selectedTab = 1
                        },
                        onRefresh = onRefreshLocal
                    )
                    
                    1 -> {
                        val agentMessages by viewModel.agentMessages.collectAsState()
                        AgentPage(
                            messages = agentMessages,
                            files = viewModel.agentFiles,
                            sessions = viewModel.agentSessions,
                            isGenerating = viewModel.isAgentGenerating,
                            config = viewModel.agentConfig,
                            mode = viewModel.agentMode,
                            showSettings = viewModel.showAgentSettings,
                            showSaveDialog = viewModel.showAgentSaveDialog,
                            showHistoryPanel = viewModel.showAgentHistoryPanel,
                            modelList = viewModel.agentModelList,
                            isFetchingModels = viewModel.isFetchingModels,
                            isTestingConnection = viewModel.isTestingConnection,
                            connectionResult = viewModel.agentConnectionResult,
                            modelFetchError = viewModel.agentModelFetchError,
                            insight = viewModel.agentInsight,
                            stage = viewModel.agentStage,
                            elapsedMs = viewModel.agentElapsedMs,
                            overwriteTargetName = viewModel.pendingOverwriteName,
                            overwriteDiff = viewModel.pendingOverwriteDiff,
                            onSendMessage = viewModel::sendAgentMessage,
                            onSelectMode = viewModel::selectAgentMode,
                            onEditFile = viewModel::updateAgentFile,
                            onSaveScript = viewModel::showSaveAgentDialog,
                            onOpenSettings = viewModel::openAgentSettings,
                            onDismissSettings = viewModel::dismissAgentSettings,
                            onUpdateConfig = viewModel::updateAgentConfig,
                            onFetchModels = viewModel::fetchAgentModels,
                            onTestConnection = viewModel::testAgentConnection,
                            onDismissSaveDialog = viewModel::dismissAgentSaveDialog,
                            onConfirmSave = viewModel::saveAgentScript,
                            onConfirmOverwrite = viewModel::confirmAgentOverwrite,
                            onDismissOverwrite = viewModel::dismissAgentOverwrite,
                            onCancelGenerate = viewModel::cancelAgentGeneration,
                            onToggleHistory = viewModel::toggleAgentHistoryPanel,
                            onNewSession = viewModel::newAgentSession,
                            onSwitchSession = viewModel::switchAgentSession,
                            onDeleteSession = viewModel::deleteAgentSession,
                            // Agent 输入区让位给底部悬浮 Tab，高度由本页实测传入
                            reservedBottom = tabsReservedHeight,
                            quickPrompts = viewModel.activeQuickPrompts
                        )
                    }
                }
            }
        }

        // 两个页面共用同一枚：液态玻璃、悬浮于 Box 底部、居中。
        // onSizeChanged 必须留在 modifier 链最外层，否则量到的是减去 padding 后的尺寸，
        // 页面按偏小的值让位，输入框就会被 Tab 压住。
        FloatingLiquidTabs(
            options = listOf("本地脚本", "Agent"),
            selectedIndex = selectedTab,
            onOptionSelected = { selectedTab = it },
            backdrop = screenBackdrop,
            modifier = Modifier
                .onSizeChanged { size ->
                    val measured = with(density) { size.height.toDp() }
                    tabsReservedHeight = maxOf(measured, MIN_TABS_RESERVED)
                }
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp)
                .navigationBarsPadding()
        )
    }

    CreatePluginDialog(
        visible = showCreateDialog, 
        onDismiss = onDismissCreateDialog, 
        onConfirm = onConfirmCreatePlugin
    )

    PluginCreatedDialog(
        visible = showSuccessDialog, 
        path = createdPluginPath, 
        onDismiss = onDismissSuccessDialog
    )

    PluginApiDocDialog(
        visible = showApiDoc, 
        onDismiss = { showApiDoc = false }
    )
}

/**
 * 脚本 API 文档弹窗。
 *
 * 正文来自 Agent 用的同一份随包资源（`resources/cn/hxy/happy/plugin/agent/api.md`），
 * 打开时只读本地内容，不访问任何远程地址；也不另存一份 assets 副本，
 * 免得用户看到的文档和 Agent 依据的文档对不上。
 */
@Composable
private fun PluginApiDocDialog(
    visible: Boolean, 
    onDismiss: () -> Unit
) {
    if (!visible) return

    val docText = remember {
        AgentPrompts.apiDocumentText().ifBlank { "文档读取失败：未找到随包分发的 API 文档资源。" }
    }

    CenterDialog(
        visible = visible, 
        onDismiss = onDismiss
    ) {
        BaseDialogSurface(
            title = "脚本 API", 
            bottomBar = {
                DialogButton(
                    text = "关闭", 
                    onClick = onDismiss, 
                    isPrimary = true
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(
                        height = 420.dp
                    )
                    .verticalScroll(
                        state = rememberScrollState()
                    )
            ) {
                Text(
                    text = docText,
                    color = HappyTheme.colors.textPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
internal fun ScrollToTopButton(
    visible: Boolean, 
    onClick: () -> Unit, 
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(
            initialScale = 0.8f
        ),
        exit = fadeOut() + scaleOut(
            targetScale = 0.8f
        ),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .shadow(
                    elevation = 8.dp, 
                    shape = CircleShape
                )
                .clip(
                    shape = CircleShape
                )
                .background(
                    color = HappyTheme.colors.cardBackground
                )
                .size(
                    size = 48.dp
                )
                .clickable(
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(
                    id = R.drawable.ic_arrow_up
                ), 
                contentDescription = null, 
                modifier = Modifier.size(
                    size = 22.dp
                ),
                tint = HappyTheme.colors.textSecondary
            )
        }
    }
}

@Composable
private fun CreatePluginDialog(
    visible: Boolean, 
    onDismiss: () -> Unit, 
    onConfirm: (String, String, String, String) -> Unit
) {
    if (!visible) return
    
    var id by remember { 
        mutableStateOf(value = "example_${System.currentTimeMillis()}") 
    }
    
    var name by remember { 
        mutableStateOf(value = "示例脚本") 
    }
    
    var version by remember { 
        mutableStateOf(value = "1.0") 
    }
    
    var author by remember { 
        mutableStateOf(value = "HappyDeveloper") 
    }

    CenterDialog(
        visible = visible, 
        onDismiss = onDismiss
    ) {
        BaseDialogSurface(
            title = "创建脚本", 
            bottomBar = {
                DialogButton(
                    text = "取消", 
                    onClick = onDismiss, 
                    isPrimary = false
                )
                
                Spacer(
                    modifier = Modifier.width(
                        width = 12.dp
                    )
                )
                
                DialogButton(
                    text = "创建", 
                    onClick = { 
                        onConfirm(id, name, version, author) 
                    }, 
                    isPrimary = true
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(
                        state = rememberScrollState()
                    )
            ) {
                DialogTextField(
                    value = id, 
                    onValueChange = { id = it }, 
                    label = "脚本ID", 
                    hint = "唯一标识"
                )
                
                Spacer(
                    modifier = Modifier.height(
                        height = 16.dp
                    )
                )
                
                DialogTextField(
                    value = name, 
                    onValueChange = { name = it }, 
                    label = "脚本名称", 
                    hint = "文件夹名"
                )
                
                Spacer(
                    modifier = Modifier.height(
                        height = 16.dp
                    )
                )
                
                DialogTextField(
                    value = version, 
                    onValueChange = { version = it }, 
                    label = "版本号", 
                    hint = "1.0"
                )
                
                Spacer(
                    modifier = Modifier.height(
                        height = 16.dp
                    )
                )
                
                DialogTextField(
                    value = author, 
                    onValueChange = { author = it }, 
                    label = "作者", 
                    hint = "作者名称"
                )
            }
        }
    }
}

@Composable
private fun PluginCreatedDialog(
    visible: Boolean, 
    path: String, 
    onDismiss: () -> Unit
) {
    if (!visible) return
    
    val context = LocalContext.current
    
    CenterDialog(
        visible = visible, 
        onDismiss = onDismiss
    ) {
        BaseDialogSurface(
            title = "创建成功", 
            bottomBar = { 
                DialogButton(
                    text = "关闭", 
                    onClick = onDismiss, 
                    isPrimary = false
                ) 
            }
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    painter = painterResource(
                        id = R.drawable.ic_logo_check
                    ), 
                    contentDescription = null, 
                    modifier = Modifier.size(
                        size = 60.dp
                    ), 
                    tint = AccentGreen
                )
                
                Spacer(
                    modifier = Modifier.height(
                        height = 16.dp
                    )
                )
                
                Text(
                    text = "脚本文件夹已创建", 
                    fontWeight = FontWeight.Bold, 
                    fontSize = 18.sp, 
                    color = HappyTheme.colors.textPrimary
                )
                
                Spacer(
                    modifier = Modifier.height(
                        height = 24.dp
                    )
                )
                
                HappyCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cb.setPrimaryClip(
                                ClipData.newPlainText(
                                    "Plugin Path", 
                                    path
                                )
                            )
                            Toasts.qqToast(2, "路径已复制")
                        }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                all = 16.dp
                            )
                    ) {
                        Text(
                            text = "存储路径 (点击复制)", 
                            fontSize = 12.sp, 
                            color = HappyTheme.colors.textSecondary
                        )
                        
                        Spacer(
                            modifier = Modifier.height(
                                height = 4.dp
                            )
                        )
                        
                        Text(
                            text = path, 
                            fontSize = 13.sp, 
                            fontFamily = FontFamily.Monospace, 
                            color = HappyTheme.colors.textPrimary, 
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }
}