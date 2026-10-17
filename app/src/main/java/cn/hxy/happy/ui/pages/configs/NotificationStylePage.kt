package cn.hxy.happy.ui.pages.configs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import cn.hxy.happy.conf.NotificationStyleConfig
import cn.hxy.happy.conf.NotificationStyleConfig.Companion.OPTION_AUTO_CLEAR_SUB_CHANNEL
import cn.hxy.happy.conf.NotificationStyleConfig.Companion.OPTION_DISABLE_BUBBLE
import cn.hxy.happy.conf.NotificationStyleConfig.Companion.OPTION_DISABLE_SUB_CHANNEL
import cn.hxy.happy.ui.components.listitems.PreferenceSection
import cn.hxy.happy.ui.components.listitems.SelectionGroup
import cn.hxy.happy.ui.components.listitems.SelectionItem
import cn.hxy.happy.ui.components.scaffold.ConfigPageScaffold

@Composable
fun NotificationStylePage(
    currentConfig: NotificationStyleConfig,
    onSave: (NotificationStyleConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var tempOptions by remember(currentConfig) { mutableStateOf(currentConfig.options) }

    val entries = remember {
        listOf(
            Triple(OPTION_DISABLE_SUB_CHANNEL, "禁用子渠道发送通知", "所有消息都走统一渠道，不再按会话拆分"),
            Triple(OPTION_DISABLE_BUBBLE, "禁用通知气泡", "通知不再弹出悬浮气泡"),
            Triple(OPTION_AUTO_CLEAR_SUB_CHANNEL, "自动清除多余通知子渠道", "发布通知后清掉不存在会话的子渠道")
        )
    }

    ConfigPageScaffold(
        title = "MessagingStyle 通知",
        configData = NotificationStyleConfig(tempOptions),
        onSave = onSave,
        onDismiss = onDismiss
    ) {
        PreferenceSection(title = "可选项") {
            SelectionGroup {
                entries.forEach { (id, label, desc) ->
                    SelectionItem(
                        title = label,
                        subtitle = desc,
                        isSelected = tempOptions.contains(id),
                        onClick = {
                            val newSet = tempOptions.toMutableSet()
                            if (newSet.contains(id)) {
                                newSet.remove(id)
                                if (id == OPTION_DISABLE_SUB_CHANNEL) {
                                    newSet.remove(OPTION_AUTO_CLEAR_SUB_CHANNEL)
                                }
                            } else {
                                newSet.add(id)
                                NotificationStyleConfig.FORCED_SELECTIONS[id]?.let(newSet::addAll)
                            }
                            tempOptions = newSet
                        }
                    )
                }
            }
        }
    }
}
