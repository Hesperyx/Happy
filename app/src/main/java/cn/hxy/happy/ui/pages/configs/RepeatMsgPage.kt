package cn.hxy.happy.ui.pages.configs

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import cn.hxy.happy.R
import cn.hxy.happy.activity.SettingActivity
import cn.hxy.happy.conf.RepeatConfig
import cn.hxy.happy.ui.components.listitems.ActionItem
import cn.hxy.happy.ui.components.listitems.PreferenceSection
import cn.hxy.happy.ui.components.listitems.SelectionGroup
import cn.hxy.happy.ui.components.listitems.SelectionItem
import cn.hxy.happy.ui.components.listitems.SwitchItem
import cn.hxy.happy.ui.components.scaffold.ConfigPageScaffold
import cn.hxy.happy.ui.core.theme.HappyTheme
import cn.hxy.happy.utils.qq.QQCurrentEnv

@Composable
fun RepeatMsgPage(
    currentConfig: RepeatConfig,
    bitmap: Bitmap?,
    onSave: (RepeatConfig) -> Unit,
    onDismiss: () -> Unit
) {
    val context = QQCurrentEnv.activity
    var tempConfig by remember(currentConfig) { mutableStateOf(currentConfig) }
    val colors = HappyTheme.colors

    ConfigPageScaffold(
        title = "复读配置",
        configData = tempConfig,
        onSave = onSave,
        onDismiss = onDismiss
    ) { _ ->
        PreferenceSection(title = "触发方式") {
            SelectionGroup {
                SelectionItem(
                    title = "快捷图标",
                    subtitle = "在消息气泡旁显示复读按钮",
                    isSelected = tempConfig.mode == 0,
                    onClick = { tempConfig = tempConfig.copy(mode = 0) }
                )
                SelectionItem(
                    title = "长按菜单",
                    subtitle = "长按消息在菜单中显示复读选项",
                    isSelected = tempConfig.mode == 1,
                    onClick = { tempConfig = tempConfig.copy(mode = 1) }
                )
            }
        }

        AnimatedVisibility(
            visible = tempConfig.mode == 0,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SwitchItem(
                    title = "双击触发",
                    description = "防止误触，需双击图标复读",
                    checked = tempConfig.doubleClick,
                    onCheckedChange = { tempConfig = tempConfig.copy(doubleClick = it) }
                )

                ActionItem(
                    title = "自定义图标",
                    description = if (bitmap != null) "已设置自定义图标" else "当前使用默认图标",
                    onClick = {
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "image/*"
                        }
                        context?.startActivityForResult(
                            intent,
                            SettingActivity.REQUEST_CODE_IMAGE
                        )
                        onDismiss()
                    },
                    trailingContent = {
                        if (bitmap != null) {
                            Icon(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = Color.Unspecified
                            )
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.ic_action_repeat),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = colors.textSecondary
                            )
                        }
                    }
                )
            }
        }
    }
}
