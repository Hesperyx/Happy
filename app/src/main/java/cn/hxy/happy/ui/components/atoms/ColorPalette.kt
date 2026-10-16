package cn.hxy.happy.ui.components.atoms

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.hxy.happy.ui.core.theme.AccentBlue
import cn.hxy.happy.ui.core.theme.Dimens
import cn.hxy.happy.ui.core.theme.HappyTheme

/**
 * 调色盘：预设色铺成可点选的色块网格，选中项描边高亮。
 *
 * 配合「颜色代码」输入框使用——点色块快速取色，输入框里仍可手填任意色值。
 */
@Composable
fun ColorPalette(
    presets: List<Color>,
    selected: Color?,
    onPick: (Color) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 9,
    swatchSize: Dp = 26.dp
) {
    val colors = HappyTheme.colors
    val selectedArgb = selected?.toArgb()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.PaddingSmall)
    ) {
        presets.chunked(columns).forEach { rowColors ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.PaddingSmall)
            ) {
                rowColors.forEach { color ->
                    val isSelected = selectedArgb != null && selectedArgb == color.toArgb()
                    Box(
                        modifier = Modifier
                            .size(swatchSize)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) {
                                    AccentBlue
                                } else {
                                    colors.textSecondary.copy(alpha = 0.3f)
                                },
                                shape = CircleShape
                            )
                            .clickable { onPick(color) }
                    )
                }
            }
        }
    }
}
