package cn.hxy.happy.ui.pages.configs

import androidx.compose.runtime.Composable
import cn.hxy.happy.conf.TroopSetConfig
import cn.hxy.happy.ui.components.biz.AsyncSelectorDialog
import cn.hxy.happy.ui.components.dialogs.SelectionItem
import cn.hxy.happy.utils.qq.TroopTool

@Composable
fun TroopSelectorPage(
    title: String,
    currentConfig: TroopSetConfig,
    onSave: (TroopSetConfig) -> Unit,
    onDismiss: () -> Unit
) {
    AsyncSelectorDialog(
        title = title,
        currentSelection = currentConfig.selectedSet,
        dataLoader = TroopTool::getGroupList,
        mapper = { SelectionItem(it.group, it.groupName) },
        onDismiss = onDismiss,
        onConfirm = {
            onSave(TroopSetConfig(it))
            onDismiss()
        }
    )
}
