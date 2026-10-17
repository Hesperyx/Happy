package cn.hxy.happy.ui.pages.home

import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import cn.hxy.happy.R
import cn.hxy.happy.conf.BackdropApiConfig
import cn.hxy.happy.ui.components.atoms.DialogButton
import cn.hxy.happy.ui.components.atoms.HappySwitch
import cn.hxy.happy.ui.components.dialogs.BaseDialogSurface
import cn.hxy.happy.ui.components.dialogs.CenterDialog
import cn.hxy.happy.ui.core.theme.AccentBlue
import cn.hxy.happy.ui.core.theme.AccentGreen
import cn.hxy.happy.ui.core.theme.AccentRed
import cn.hxy.happy.ui.core.theme.HappyTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur

/** 主页卡片的圆角，磨砂卡和无图时的白卡共用。 */
private val CARD_SHAPE: Shape = RoundedCornerShape(24.dp)

/** 叠在图上时卡片文字的颜色：白卡上压深色字，不用跟随系统深浅色。 */
private val CARD_TEXT = Color(0xFF111114)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainScreen(
    versionName: String,
    versionCode: Int,
    isActivated: Boolean,
    frameworkInfo: String,
    isIconVisible: Boolean,
    backdropConfig: BackdropApiConfig,
    onToggleIcon: () -> Unit,
    onOpenBackdropConfig: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = HappyTheme.colors
    val context = LocalContext.current

    // ---------- 背景图 ----------
    var refreshKey by remember { mutableIntStateOf(0) }
    var isLoadingBackdrop by remember { mutableStateOf(true) }
    var backdropFailed by remember { mutableStateOf(false) }
    var backdrop by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(refreshKey, backdropConfig) {
        isLoadingBackdrop = true
        backdropFailed = false
        val metrics = context.resources.displayMetrics
        val loaded = BackdropLoader.load(
            context = context,
            apis = backdropConfig.apis,
            forceRefresh = refreshKey > 0,
            targetWidth = metrics.widthPixels,
            targetHeight = metrics.heightPixels
        )
        when {
            loaded != null -> {
                backdrop = loaded.asImageBitmap()
                backdropFailed = false
            }
            // 接口全关：退回纯色底，不算失败
            backdropConfig.enabledApis.isEmpty() -> backdrop = null
            // 有接口但没取到：保留旧图，只在按钮上提示可重试
            else -> backdropFailed = true
        }
        isLoadingBackdrop = false
    }

    // ---------- 一言 ----------
    var quote by remember { mutableStateOf(HitokotoStore.loadQuote(context)) }
    var quoteTypes by remember { mutableStateOf(HitokotoStore.loadTypes(context)) }
    var quoteLoading by remember { mutableStateOf(false) }
    var quoteTick by remember { mutableIntStateOf(0) }
    var showTypeSheet by remember { mutableStateOf(false) }
    var tempTypes by remember { mutableStateOf(quoteTypes) }

    LaunchedEffect(quoteTick, quoteTypes) {
        quoteLoading = true
        val fetched = HitokotoLoader.fetch(quoteTypes)
        if (fetched != null) {
            quote = fetched
            HitokotoStore.saveQuote(context, fetched)
        }
        quoteLoading = false
    }

    val hasBackdrop = backdrop != null
    // 只有叠在图上时才真的画磨砂；无图时同一套卡片退回普通白卡
    val glassLayer = rememberLayerBackdrop()
    val glass: Backdrop? = if (hasBackdrop) glassLayer else null

    val apiSummary = when {
        backdropConfig.apis.isEmpty() -> "没有接口，主页不取图"
        backdropConfig.enabledApis.isEmpty() -> "已全部关闭，使用纯色背景"
        else -> "${backdropConfig.apis.size} 个接口 · ${backdropConfig.enabledApis.size} 个在用"
    }

    // 有背景图时状态栏落在图上，图标转白；无图时白底配深色图标
    val view = LocalView.current
    val activity = context as? Activity
    SideEffect {
        val window = activity?.window ?: return@SideEffect
        if (view.isInEditMode) return@SideEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !hasBackdrop
        controller.isAppearanceLightNavigationBars = !hasBackdrop
    }

    val headerColor = if (hasBackdrop) Color.White else colors.textPrimary
    val headerSubColor = if (hasBackdrop) Color.White.copy(alpha = 0.86f) else colors.textSecondary
    val onCardPrimary = if (hasBackdrop) CARD_TEXT else colors.textPrimary
    val onCardSecondary = if (hasBackdrop) CARD_TEXT.copy(alpha = 0.66f) else colors.textSecondary
    val textShadow = if (hasBackdrop) {
        TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.55f), Offset(0f, 2f), 10f))
    } else {
        TextStyle.Default
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        if (backdrop != null) {
            // 这一层既是画面来源，也是卡片磨砂的取样来源
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(glassLayer)
            ) {
                Image(
                    bitmap = backdrop!!,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    // 默认双线性放大后会糊；这里让 GPU 走高质量缩放，并给一点饱和
                    filterQuality = FilterQuality.High,
                    colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.15f) })
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.0f to Color.Black.copy(alpha = 0.50f),
                                0.30f to Color.Black.copy(alpha = 0.20f),
                                0.70f to Color.Black.copy(alpha = 0.22f),
                                1.0f to Color.Black.copy(alpha = 0.46f)
                            )
                        )
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 10.dp, bottom = 84.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Hero：标题 + 版本 + 换一张
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = "Happy",
                        fontSize = 32.sp,
                        lineHeight = 38.sp,
                        fontWeight = FontWeight.Bold,
                        color = headerColor,
                        style = textShadow
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$versionName ($versionCode)",
                        fontSize = 13.sp,
                        color = headerSubColor,
                        style = textShadow
                    )
                }
                RefreshChip(
                    state = when {
                        isLoadingBackdrop -> ChipState.Loading
                        backdropConfig.enabledApis.isEmpty() -> ChipState.Disabled
                        backdropFailed -> ChipState.Failed
                        else -> ChipState.Idle
                    },
                    glass = glass,
                    onClick = {
                        // 没开任何接口时按钮是禁用态，这里只是兜底
                        if (backdropConfig.enabledApis.isNotEmpty()) refreshKey++
                    }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 状态卡
            GlassCard(glass = glass) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(
                            if (isActivated) R.drawable.ic_logo_check else R.drawable.ic_logo_unchecked
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                        tint = if (isActivated) AccentGreen else AccentRed
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = if (isActivated) "已激活" else "未激活",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = onCardPrimary
                        )
                        if (frameworkInfo.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = frameworkInfo,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = onCardSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (!isActivated) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "在 LSPosed 中启用模块，并勾选 QQ / TIM",
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                color = onCardSecondary
                            )
                        }
                    }
                }
            }

            // 一言卡
            GlassCard(glass = glass) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "一言",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = onCardSecondary
                        )
                        PillButton(
                            text = "类型 ›",
                            color = onCardSecondary,
                            onClick = {
                                tempTypes = quoteTypes
                                showTypeSheet = true
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = quote?.text ?: "正在取一句…",
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        color = onCardPrimary,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "—— ${quote?.source ?: "一言"}",
                            fontSize = 12.sp,
                            color = onCardSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        PillButton(
                            text = "换一条",
                            color = onCardSecondary,
                            leading = "↻",
                            loading = quoteLoading,
                            onClick = { quoteTick++ }
                        )
                    }
                }
            }

            // 快捷操作
            Text(
                text = "快捷操作",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (hasBackdrop) Color.White.copy(alpha = 0.95f) else colors.textSecondary,
                style = textShadow,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )

            GlassCard(glass = glass) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "桌面图标",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = onCardPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "关闭后请从 LSPosed 管理器进入设置",
                            fontSize = 12.sp,
                            color = onCardSecondary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    // 开关只表达状态；点击仍走原来的流程（隐藏前会先确认）
                    HappySwitch(
                        checked = isIconVisible,
                        onCheckedChange = { onToggleIcon() }
                    )
                }
            }

            GlassCard(glass = glass, onClick = onOpenBackdropConfig) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "背景接口",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = onCardPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = apiSummary,
                            fontSize = 12.sp,
                            color = onCardSecondary
                        )
                    }
                    Text(
                        text = "›",
                        fontSize = 22.sp,
                        color = if (hasBackdrop) CARD_TEXT.copy(alpha = 0.45f) else colors.textSecondary
                    )
                }
            }
        }

        // 底部说明：模块设置只能从 QQ 里进，主页给个指路
        Text(
            text = "完整设置在 QQ 内打开：设置 → 模块 → Happy",
            fontSize = 12.sp,
            color = if (hasBackdrop) Color.White.copy(alpha = 0.76f) else colors.textSecondary,
            style = textShadow,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 18.dp)
        )
    }

    // 类型多选：至少保留一种，改动后立刻换一句
    LaunchedEffect(showTypeSheet) {
        if (showTypeSheet) tempTypes = quoteTypes
    }
    CenterDialog(visible = showTypeSheet, onDismiss = { showTypeSheet = false }) {
        BaseDialogSurface(
            title = "句子类型",
            bottomBar = {
                DialogButton("取消", { showTypeSheet = false }, false)
                Spacer(modifier = Modifier.width(12.dp))
                DialogButton(
                    "完成",
                    {
                        if (tempTypes.isNotEmpty()) {
                            quoteTypes = tempTypes
                            HitokotoStore.saveTypes(context, tempTypes)
                            quoteTick++
                        }
                        showTypeSheet = false
                    },
                    true
                )
            }
        ) {
            Text(
                text = "可多选，至少保留一种",
                fontSize = 13.sp,
                color = colors.textSecondary
            )
            Spacer(modifier = Modifier.height(14.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HitokotoType.entries.forEach { type ->
                    TypeChip(
                        label = type.label,
                        selected = tempTypes.contains(type.code),
                        onClick = {
                            tempTypes = when {
                                !tempTypes.contains(type.code) -> tempTypes + type.code
                                tempTypes.size > 1 -> tempTypes - type.code
                                else -> tempTypes
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * 主页卡片：有背景图时是磨砂玻璃（取图层面做模糊 + 半透明白），
 * 没图时退回普通白卡，两套外观共用同一份内容布局。
 */
@Composable
private fun GlassCard(
    glass: Backdrop?,
    modifier: Modifier = Modifier,
    shape: Shape = CARD_SHAPE,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = HappyTheme.colors
    var cardModifier = modifier.fillMaxWidth()
    cardModifier = if (glass != null) {
        cardModifier
            .drawBackdrop(
                backdrop = glass,
                shape = { shape },
                effects = { blur(26f) },
                highlight = null,
                shadow = null
            )
            .background(Color.White.copy(alpha = 0.55f))
            .border(1.dp, Color.White.copy(alpha = 0.62f), shape)
    } else {
        cardModifier
            .shadow(2.dp, shape, clip = false)
            .background(colors.cardBackground, shape)
    }
    cardModifier = cardModifier.clip(shape)
    if (onClick != null) {
        cardModifier = cardModifier.clickable(onClick = onClick)
    }
    Column(modifier = cardModifier, content = content)
}

private enum class ChipState { Idle, Loading, Failed, Disabled }

/** 标题右侧的「换一张」：磨砂胶囊，40dp 高，够 48dp 的触控区靠外扩留白补足。 */
@Composable
private fun RefreshChip(
    state: ChipState,
    glass: Backdrop?,
    onClick: () -> Unit
) {
    val colors = HappyTheme.colors
    val shape = RoundedCornerShape(50)
    val enabled = state == ChipState.Idle || state == ChipState.Failed

    var chipModifier = Modifier.height(40.dp)
    chipModifier = if (glass != null) {
        chipModifier
            .drawBackdrop(
                backdrop = glass,
                shape = { shape },
                effects = { blur(20f) },
                highlight = null,
                shadow = null
            )
            .background(Color.White.copy(alpha = if (state == ChipState.Disabled) 0.42f else 0.58f))
            .border(1.dp, Color.White.copy(alpha = 0.66f), shape)
    } else {
        chipModifier.background(
            if (state == ChipState.Disabled) colors.ripple.copy(alpha = 0.55f) else colors.ripple,
            shape
        )
    }
    chipModifier = chipModifier.clip(shape)
    if (enabled) chipModifier = chipModifier.clickable(onClick = onClick)

    val label = when (state) {
        ChipState.Idle -> "换一张"
        ChipState.Loading -> "加载中"
        ChipState.Failed -> "重试"
        ChipState.Disabled -> "背景已关闭"
    }
    val textColor = if (glass != null) {
        when (state) {
            ChipState.Failed -> Color(0xFFD70015)
            ChipState.Disabled -> CARD_TEXT.copy(alpha = 0.45f)
            else -> CARD_TEXT
        }
    } else {
        when (state) {
            ChipState.Failed -> colors.accentRed
            ChipState.Disabled -> colors.textSecondary.copy(alpha = 0.5f)
            else -> colors.textPrimary
        }
    }

    Row(
        modifier = chipModifier.padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (state == ChipState.Loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(13.dp),
                strokeWidth = 1.8.dp,
                color = textColor
            )
        } else {
            Text(text = "↻", fontSize = 13.sp, color = textColor)
        }
        Text(text = label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = textColor)
    }
}

/** 卡片里的小胶囊按钮（类型 / 换一条）。 */
@Composable
private fun PillButton(
    text: String,
    color: Color,
    onClick: () -> Unit,
    leading: String? = null,
    loading: Boolean = false
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(color.copy(alpha = 0.10f))
            .clickable(enabled = !loading, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(11.dp),
                strokeWidth = 1.6.dp,
                color = color
            )
        } else if (leading != null) {
            Text(text = leading, fontSize = 12.sp, color = color)
        }
        Text(text = text, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = color)
    }
}

/** 类型面板里的选项胶囊。 */
@Composable
private fun TypeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = HappyTheme.colors
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) AccentBlue else colors.ripple)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = if (selected) Color.White else colors.textPrimary
        )
    }
}
