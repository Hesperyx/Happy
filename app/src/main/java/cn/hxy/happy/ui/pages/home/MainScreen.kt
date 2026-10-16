package cn.hxy.happy.ui.pages.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
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
    val iconButtonAlpha by animateFloatAsState(
        targetValue = if (isIconVisible) 1f else 0.6f,
        animationSpec = tween(durationMillis = 200),
        label = "iconAlpha"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Happy",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text("$versionName($versionCode)", fontSize = 14.sp, color = colors.textSecondary)
            }
            HappyCard(
                modifier = Modifier.alpha(iconButtonAlpha),
                animateContentSize = false,
                onClick = onToggleIcon
            ) {
                Text(
                    if (isIconVisible) "隐藏图标" else "显示图标",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    modifier = Modifier.padding(16.dp, 10.dp)
                )
            }
        }

        HappyCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painterResource(if (isActivated) R.drawable.ic_logo_check else R.drawable.ic_logo_unchecked),
                    null,
                    Modifier.size(52.dp),
                    if (isActivated) AccentGreen else AccentRed
                )
                Spacer(modifier = Modifier.width(20.dp))
                Column {
                    Text(
                        if (isActivated) "已激活" else "未激活",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        frameworkInfo,
                        fontSize = 13.sp,
                        color = colors.textSecondary,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}
