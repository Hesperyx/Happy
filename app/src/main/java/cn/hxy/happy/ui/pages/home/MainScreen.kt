package cn.hxy.happy.ui.pages.home

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        BackdropHero(
            versionName = versionName,
            versionCode = versionCode,
            isActivated = isActivated,
            frameworkInfo = frameworkInfo,
            backdrop = backdrop,
            isLoadingBackdrop = isLoadingBackdrop,
            onRefresh = { refreshKey++ }
        )

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

@Composable
private fun BackdropHero(
    versionName: String,
    versionCode: Int,
    isActivated: Boolean,
    frameworkInfo: String,
    backdrop: ImageBitmap?,
    isLoadingBackdrop: Boolean,
    onRefresh: () -> Unit
) {
    val colors = HappyTheme.colors
    val hasBackdrop = backdrop != null
    val titleColor = if (hasBackdrop) Color.White else colors.textPrimary
    val subColor = if (hasBackdrop) Color.White.copy(alpha = 0.84f) else colors.textSecondary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(colors.cardBackground)
    ) {
        if (backdrop != null) {
            Image(
                bitmap = backdrop,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color.Black.copy(alpha = 0.34f),
                            0.40f to Color.Transparent,
                            1.0f to Color.Black.copy(alpha = 0.74f)
                        )
                    )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = "Happy",
                        fontSize = 34.sp,
                        lineHeight = 38.sp,
                        fontWeight = FontWeight.Bold,
                        color = titleColor
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "$versionName ($versionCode)",
                        fontSize = 13.sp,
                        color = subColor
                    )
                }
                RefreshChip(
                    hasBackdrop = hasBackdrop,
                    isLoading = isLoadingBackdrop,
                    contentColor = titleColor,
                    onRefresh = onRefresh
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(
                        if (isActivated) R.drawable.ic_logo_check else R.drawable.ic_logo_unchecked
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = if (isActivated) AccentGreen else AccentRed
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (isActivated) "已激活" else "未激活",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = titleColor
                    )
                    if (frameworkInfo.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = frameworkInfo,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = subColor,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RefreshChip(
    hasBackdrop: Boolean,
    isLoading: Boolean,
    contentColor: Color,
    onRefresh: () -> Unit
) {
    val colors = HappyTheme.colors
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (hasBackdrop) Color.White.copy(alpha = 0.22f) else colors.ripple)
            .clickable(enabled = !isLoading, onClick = onRefresh)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text = if (isLoading) "加载中" else "换一张",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = contentColor
        )
    }
}
