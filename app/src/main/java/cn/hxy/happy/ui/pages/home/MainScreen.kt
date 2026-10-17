package cn.hxy.happy.ui.pages.home

import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
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
import cn.hxy.happy.ui.components.atoms.HappyCard
import cn.hxy.happy.ui.core.theme.AccentGreen
import cn.hxy.happy.ui.core.theme.AccentRed
import cn.hxy.happy.ui.core.theme.HappyTheme

@Composable
fun MainScreen(
    versionName: String,
    versionCode: Int,
    isActivated: Boolean,
    frameworkInfo: String,
    isIconVisible: Boolean,
    onToggleIcon: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = HappyTheme.colors
    val context = LocalContext.current

    var refreshKey by remember { mutableIntStateOf(0) }
    var isLoadingBackdrop by remember { mutableStateOf(true) }
    var backdrop by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(refreshKey) {
        isLoadingBackdrop = true
        val loaded = BackdropLoader.load(context, forceRefresh = refreshKey > 0)?.asImageBitmap()
        if (loaded != null) backdrop = loaded
        isLoadingBackdrop = false
    }

    val hasBackdrop = backdrop != null

    // 有背景图时状态栏落在暗色遮罩上，图标转白；无图时白底配深色图标
    val view = LocalView.current
    val activity = LocalContext.current as? Activity
    SideEffect {
        val window = activity?.window ?: return@SideEffect
        if (view.isInEditMode) return@SideEffect
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !hasBackdrop
    }

    val textColor = if (hasBackdrop) Color.White else colors.textPrimary
    val subTextColor = if (hasBackdrop) Color.White.copy(alpha = 0.86f) else colors.textSecondary
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
            Image(
                bitmap = backdrop!!,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color.Black.copy(alpha = 0.44f),
                            0.30f to Color.Black.copy(alpha = 0.08f),
                            0.72f to Color.Black.copy(alpha = 0.08f),
                            1.0f to Color.Black.copy(alpha = 0.46f)
                        )
                    )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = "Happy",
                        fontSize = 30.sp,
                        lineHeight = 34.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        style = textShadow
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$versionName ($versionCode)",
                        fontSize = 13.sp,
                        color = subTextColor,
                        style = textShadow
                    )
                }
                RefreshChip(
                    hasBackdrop = hasBackdrop,
                    isLoading = isLoadingBackdrop,
                    onRefresh = { refreshKey++ }
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
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
                        color = textColor,
                        style = textShadow
                    )
                    if (frameworkInfo.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = frameworkInfo,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = subTextColor,
                            style = textShadow,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            HappyCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = onToggleIcon
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isIconVisible) "隐藏图标" else "显示图标",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )
                    Text(
                        text = if (isIconVisible) "桌面显示中" else "桌面已隐藏",
                        fontSize = 13.sp,
                        color = colors.textSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun RefreshChip(
    hasBackdrop: Boolean,
    isLoading: Boolean,
    onRefresh: () -> Unit
) {
    val colors = HappyTheme.colors
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (hasBackdrop) Color.Black.copy(alpha = 0.34f) else colors.ripple
            )
            .clickable(enabled = !isLoading, onClick = onRefresh)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text = if (isLoading) "加载中" else "换一张",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = if (hasBackdrop) Color.White else colors.textPrimary
        )
    }
}
