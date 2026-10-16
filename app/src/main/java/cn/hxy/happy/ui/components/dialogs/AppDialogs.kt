package cn.hxy.happy.ui.components.dialogs

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import cn.hxy.happy.ui.components.atoms.DialogButton
import cn.hxy.happy.ui.components.atoms.DialogTextField
import cn.hxy.happy.ui.core.theme.AccentBlue
import cn.hxy.happy.ui.core.theme.Dimens
import cn.hxy.happy.ui.core.theme.HappyTheme

@Composable
fun BaseDialogSurface(
    title: String,
    modifier: Modifier = Modifier,
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = HappyTheme.colors
    // 弹窗高度封顶到可用高度的 85%：正文再长也只能让正文自己滚，
    // 底部按钮永远留在屏幕里。之前这里没有上限，模型列表一出来，
    // 正文就把「保存 / 取消」顶出可视区了。
    BoxWithConstraints(
        modifier = modifier
            .padding(horizontal = 32.dp)
            .fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight * 0.85f)
            .clip(RoundedCornerShape(24.dp))
            .background(colors.cardBackground)
            .padding(Dimens.PaddingLarge)
    ) {
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary
        )
        Spacer(modifier = Modifier.height(Dimens.PaddingMedium))
        // fill = false：短内容照旧贴着标题，不会被撑到满高
        Column(modifier = Modifier.weight(1f, fill = false)) {
            content()
        }
        if (bottomBar != null) {
            Spacer(modifier = Modifier.height(Dimens.PaddingLarge))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                bottomBar()
            }
        }
    }
    }
}

@Composable
fun CenterDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    if (!visible) return
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
            ) {
                content()
            }
        }
    }
}

@Composable
fun CenterDialogContainer(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmText: String = "确定",
    dismissText: String = "取消",
    content: @Composable () -> Unit
) {
    val scrollState = rememberScrollState()
    BaseDialogSurface(
        title = title,
        bottomBar = {
            if (dismissText.isNotEmpty()) {
                DialogButton(dismissText, onDismiss, false)
                Spacer(modifier = Modifier.width(12.dp))
            }
            DialogButton(confirmText, onConfirm, true)
        }
    ) {
        Column(
            modifier = Modifier
                .weight(1f, false)
                .verticalScroll(scrollState)
        ) {
            content()
        }
    }
}

@Composable
fun CenterDialogContainerNoButton(title: String, content: @Composable () -> Unit) {
    BaseDialogSurface(title = title, content = { content() })
}

@Composable
fun ConfirmDialog(
    visible: Boolean,
    title: String,
    message: String,
    confirmText: String = "确定",
    dismissText: String = "取消",
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    danger: Boolean = false
) {
    CenterDialog(visible = visible, onDismiss = onDismiss) {
        BaseDialogSurface(
            title = title,
            bottomBar = {
                DialogButton(dismissText, onDismiss, false)
                Spacer(modifier = Modifier.width(12.dp))
                DialogButton(confirmText, onConfirm, true, danger = danger)
            }
        ) {
            Text(
                text = message,
                fontSize = 15.sp,
                color = HappyTheme.colors.textSecondary,
                lineHeight = 22.sp
            )
        }
    }
}

@Composable
fun HappyScaffold(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    confirmText: String = "确定",
    dismissText: String = "取消",
    showDismissButton: Boolean = true,
    content: @Composable () -> Unit
) {
    val scrollState = rememberScrollState()
    BaseDialogSurface(
        title = title,
        modifier = modifier,
        bottomBar = {
            if (showDismissButton && dismissText.isNotEmpty()) {
                DialogButton(dismissText, onDismiss, false)
                Spacer(modifier = Modifier.width(12.dp))
            }
            DialogButton(confirmText, onConfirm, true)
        }
    ) {
        Column(
            modifier = Modifier
                .weight(1f, false)
                .verticalScroll(scrollState)
        ) {
            content()
        }
    }
}

@Immutable
data class SelectionItem(val id: String, val name: String)

@Composable
fun SelectorDialogContent(
    title: String,
    items: List<SelectionItem>,
    selectedIds: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val colors = HappyTheme.colors
    var searchQuery by remember { mutableStateOf("") }
    var tempSelection by remember(selectedIds) { mutableStateOf(selectedIds) }
    var filteredItems by remember(items) { mutableStateOf(items) }

    LaunchedEffect(searchQuery, items) {
        filteredItems = withContext(Dispatchers.Default) {
            if (searchQuery.isEmpty()) items
            else items.filter { it.name.contains(searchQuery, true) || it.id.contains(searchQuery) }
        }
    }

    BaseDialogSurface(
        title = title,
        bottomBar = {
            DialogButton("取消", onDismiss, false)
            Spacer(modifier = Modifier.width(12.dp))
            DialogButton("确定", { onSelectionChange(tempSelection); onConfirm() }, true)
        }
    ) {
        DialogTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            hint = "搜索名称或ID"
        )
        Spacer(modifier = Modifier.height(Dimens.PaddingSmall))
        Text(
            text = "已选 ${tempSelection.size} / ${items.size}",
            fontSize = 13.sp,
            color = colors.textSecondary,
            modifier = Modifier.align(Alignment.End)
        )
        Spacer(modifier = Modifier.height(Dimens.PaddingSmall))
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, false)
                .height(280.dp)
        ) {
            items(filteredItems, key = { it.id }) { item ->
                SelectableListItem(item, tempSelection.contains(item.id)) {
                    tempSelection = tempSelection.toMutableSet()
                        .apply { if (contains(item.id)) remove(item.id) else add(item.id) }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ActionChip("全选") { tempSelection = filteredItems.map { it.id }.toMutableSet() }
            ActionChip("全不选") { tempSelection = mutableSetOf() }
            ActionChip("反选") {
                tempSelection =
                    filteredItems.filter { !tempSelection.contains(it.id) }.map { it.id }
                        .toMutableSet()
            }
        }
    }
}

@Composable
private fun SelectableListItem(item: SelectionItem, isSelected: Boolean, onClick: () -> Unit) {
    val colors = HappyTheme.colors
    val textColor by animateColorAsState(
        targetValue = if (isSelected) AccentBlue else colors.textPrimary,
        animationSpec = tween(durationMillis = 200),
        label = "textColor"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(4.dp, 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = textColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = item.id, fontSize = 13.sp, color = colors.textSecondary)
        }
        if (isSelected) Text(
            text = "✓",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = AccentBlue
        )
    }
}

@Composable
private fun ActionChip(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = AccentBlue,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(Dimens.PaddingSmall, 4.dp)
    )
}
