package cn.hxy.happy.ui.pages.configs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import cn.hxy.happy.conf.SettingMeEntry
import cn.hxy.happy.conf.SettingMeSimplifyConfig
import cn.hxy.happy.ui.components.listitems.SelectionGroup
import cn.hxy.happy.ui.components.listitems.SelectionItem
import cn.hxy.happy.ui.components.scaffold.ConfigPageScaffold

@Composable
fun SettingMeSimplifyPage(
    currentConfig: SettingMeSimplifyConfig,
    onSave: (SettingMeSimplifyConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var hiddenIds by remember(currentConfig) { mutableStateOf(currentConfig.hiddenIds) }

    ConfigPageScaffold(
        title = "侧滑栏精简",
        configData = SettingMeSimplifyConfig(hiddenIds),
        onSave = onSave,
        onDismiss = onDismiss
    ) {
        SelectionGroup {
            SettingMeEntry.entries.forEach { entry ->
                SelectionItem(
                    title = entry.label,
                    subtitle = "隐藏侧滑栏中的${entry.label}入口",
                    isSelected = entry.id in hiddenIds,
                    onClick = {
                        val newSet = hiddenIds.toMutableSet()
                        if (!newSet.add(entry.id)) newSet.remove(entry.id)
                        hiddenIds = newSet
                    }
                )
            }
        }
    }
}
