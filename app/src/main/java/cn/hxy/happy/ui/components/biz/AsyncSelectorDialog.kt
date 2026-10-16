package cn.hxy.happy.ui.components.biz

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.hxy.happy.common.ModuleScope
import cn.hxy.happy.ui.components.atoms.LoadingIndicator
import cn.hxy.happy.utils.reflect.TAG
import cn.hxy.happy.ui.components.dialogs.BaseDialogSurface
import cn.hxy.happy.ui.components.dialogs.CenterDialog
import cn.hxy.happy.ui.components.dialogs.SelectionItem
import cn.hxy.happy.ui.components.dialogs.SelectorDialogContent

@Composable
fun <T> AsyncSelectorDialog(
    title: String,
    currentSelection: Set<String>,
    dataLoader: () -> List<T>,
    mapper: (T) -> SelectionItem,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    var items by remember { mutableStateOf<List<SelectionItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var tempSelection by remember(currentSelection) { mutableStateOf(currentSelection) }

    LaunchedEffect(Unit) {
        ModuleScope.launchIO(TAG) {
            val rawData = dataLoader()
            val mappedItems = rawData.map(mapper)
            items = mappedItems.sortedByDescending { tempSelection.contains(it.id) }
            ModuleScope.launchMain { isLoading = false }
        }
    }

    CenterDialog(visible = true, onDismiss = onDismiss) {
        if (isLoading) {
            BaseDialogSurface(title = title) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator(message = "数据加载中...")
                }
            }
        } else {
            SelectorDialogContent(
                title = title,
                items = items,
                selectedIds = tempSelection,
                onSelectionChange = { tempSelection = it },
                onDismiss = onDismiss,
                onConfirm = { onConfirm(tempSelection) }
            )
        }
    }
}