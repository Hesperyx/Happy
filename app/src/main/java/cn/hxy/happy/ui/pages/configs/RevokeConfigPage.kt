package cn.hxy.happy.ui.pages.configs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import cn.hxy.happy.conf.RevokeConfig
import cn.hxy.happy.ui.components.atoms.ColorPalette
import cn.hxy.happy.ui.components.listitems.InputItem
import cn.hxy.happy.ui.components.scaffold.ConfigPageScaffold
import cn.hxy.happy.ui.core.theme.AccentRed
import cn.hxy.happy.ui.core.theme.HappyTheme
import androidx.compose.ui.graphics.Color as ComposeColor

/** 不透明色显示成 #RRGGBB，带透明度时才补出 #AARRGGBB */
private fun formatColor(value: Int): String =
    if ((value ushr 24) and 0xFF == 0xFF) {
        String.format("#%06X", value and 0xFFFFFF)
    } else {
        String.format("#%08X", value)
    }

/** 调色盘预设：第一行浅色（背景常用）、后两行标准色系 */
private val ColorPresets: List<ComposeColor> = listOf(
    0xFFFFFFFF, 0xFFF5F6F8, 0xFFDEE6F2, 0xFFE8F5E9, 0xFFFFF8E1, 0xFFFFEBEE, 0xFFEDE7F6, 0xFFE0F7FA, 0xFF3A3A3C,
    0xFFFF3B30, 0xFFFF9500, 0xFFFFCC00, 0xFF34C759, 0xFF00C7BE, 0xFF007AFF, 0xFF5856D6, 0xFFAF52DE, 0xFFFF2D55,
    0xFF8B0000, 0xFFB8860B, 0xFF2E8B57, 0xFF1E90FF, 0xFF4B0082, 0xFFC71585, 0xFF00CED1, 0xFF7AC943, 0xFF000000,
).map { ComposeColor(it) }

@Composable
fun RevokeConfigPage(
    currentConfig: RevokeConfig,
    onSave: (RevokeConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var textColorText by remember(currentConfig) {
        mutableStateOf(formatColor(currentConfig.textColor))
    }
    var bgColorText by remember(currentConfig) {
        mutableStateOf(formatColor(currentConfig.bgColor))
    }
    var topMarginText by remember(currentConfig) { mutableStateOf(currentConfig.topMargin) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val colors = HappyTheme.colors

    fun parseColor(input: String): Int? {
        return try {
            val clean = input.replace("[^a-fA-F0-9]".toRegex(), "")
            when (clean.length) {
                6 -> "#FF$clean".toColorInt()
                8 -> "#$clean".toColorInt()
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun parseMargin(input: String): Int? =
        input.trim().toIntOrNull()?.takeIf { it in 0..200 }

    fun buildConfig(): RevokeConfig = RevokeConfig(
        textColor = parseColor(textColorText) ?: currentConfig.textColor,
        bgColor = parseColor(bgColorText) ?: currentConfig.bgColor,
        topMargin = (parseMargin(topMarginText)?.toString() ?: currentConfig.topMargin),
    )

    val built = buildConfig()
    val previewMargin = parseMargin(topMarginText) ?: 10

    ConfigPageScaffold(
        title = "消息防撤回 - 配置",
        configData = built,
        onSave = onSave,
        onDismiss = onDismiss,
        confirmText = "保存配置并关闭"
    ) { _ ->
        InputItem(
            title = "文字颜色代码：",
            value = textColorText,
            onValueChange = { newValue ->
                textColorText = newValue
                errorMsg = if (newValue.isNotEmpty() && parseColor(newValue) == null) {
                    "颜色格式无效"
                } else {
                    null
                }
            },
            placeholder = "#FF0000"
        )
        ColorPalette(
            presets = ColorPresets,
            selected = parseColor(textColorText)?.let { ComposeColor(it) },
            onPick = { picked ->
                textColorText = formatColor(picked.toArgb())
                errorMsg = null
            }
        )
        InputItem(
            title = "背景颜色代码：",
            value = bgColorText,
            onValueChange = { newValue ->
                bgColorText = newValue
                errorMsg = if (newValue.isNotEmpty() && parseColor(newValue) == null) {
                    "颜色格式无效"
                } else {
                    null
                }
            },
            placeholder = "#DEE6F2"
        )
        ColorPalette(
            presets = ColorPresets,
            selected = parseColor(bgColorText)?.let { ComposeColor(it) },
            onPick = { picked ->
                bgColorText = formatColor(picked.toArgb())
                errorMsg = null
            }
        )
        InputItem(
            title = "文字顶部距离：",
            value = topMarginText,
            onValueChange = { newValue ->
                topMarginText = newValue
                errorMsg = if (newValue.isNotEmpty() && parseMargin(newValue) == null) {
                    "距离请输入 0 ~ 200 的整数"
                } else {
                    null
                }
            },
            placeholder = "10",
            keyboardType = KeyboardType.Number
        )

        Text("预览（整行铺满色带）", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.textPrimary)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ComposeColor(built.bgColor)),
            contentAlignment = Alignment.TopCenter
        ) {
            Text(
                text = "该消息被自己撤回",
                fontSize = 16.sp,
                color = ComposeColor(built.textColor),
                modifier = Modifier.padding(top = previewMargin.dp)
            )
        }

        if (errorMsg != null) {
            Text(errorMsg!!, fontSize = 13.sp, color = AccentRed)
        }
    }
}
