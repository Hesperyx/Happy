package cn.hxy.happy.ui.pages.configs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import cn.hxy.happy.conf.FakeNetworkConfig
import cn.hxy.happy.ui.components.listitems.PreferenceSection
import cn.hxy.happy.ui.components.listitems.SelectionGroup
import cn.hxy.happy.ui.components.listitems.SelectionItem
import cn.hxy.happy.ui.components.scaffold.ConfigPageScaffold

@Composable
fun FakeNetworkPage(
    currentConfig: FakeNetworkConfig,
    onSave: (FakeNetworkConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var tempMode by remember(currentConfig) { mutableStateOf(currentConfig.mode) }

    val descriptions = mapOf(
        FakeNetworkConfig.MODE_WIFI to "伪装成 WIFI 网络环境",
        FakeNetworkConfig.MODE_5G to "伪装成移动数据 5G",
        FakeNetworkConfig.MODE_4G to "伪装成移动数据 4G"
    )

    ConfigPageScaffold(
        title = "伪装网络状态",
        configData = FakeNetworkConfig(tempMode),
        onSave = onSave,
        onDismiss = onDismiss
    ) {
        PreferenceSection(title = "伪装网络类型") {
            SelectionGroup {
                FakeNetworkConfig.LABELS.forEach { (mode, label) ->
                    SelectionItem(
                        title = label,
                        subtitle = descriptions[mode].orEmpty(),
                        isSelected = tempMode == mode,
                        onClick = { tempMode = mode }
                    )
                }
            }
        }
    }
}
