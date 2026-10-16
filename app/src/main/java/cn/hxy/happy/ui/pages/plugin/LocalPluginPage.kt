package cn.hxy.happy.ui.pages.plugin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import cn.hxy.happy.ui.components.atoms.ActionButton
import cn.hxy.happy.ui.components.atoms.ActionButtonStyle
import cn.hxy.happy.ui.components.atoms.HappyCard
import cn.hxy.happy.ui.components.atoms.HappySwitch
import cn.hxy.happy.ui.components.molecules.AnimatedListItem
import cn.hxy.happy.ui.components.molecules.EmptyStateView
import cn.hxy.happy.ui.components.molecules.PullRefreshBox
import cn.hxy.happy.ui.core.theme.Dimens
import cn.hxy.happy.ui.core.theme.HappyTheme
import cn.hxy.happy.utils.ui.HighlightUtils

@Composable
fun LocalPluginPage(
    plugins: List<LocalPluginData>,
    isRefreshing: Boolean,
    searchQuery: String,
    listState: LazyListState,
    onRunToggle: (String, Boolean) -> Unit,
    onAutoLoadToggle: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onReload: (String) -> Unit,
    onEditWithAgent: (String) -> Unit,
    onRefresh: () -> Unit
) {
    val showScrollToTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 1
        }
    }

    val scope = rememberCoroutineScope()

    val colors = HappyTheme.colors

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        PullRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh
        ) {
            if (plugins.isEmpty()) {
                EmptyStateView(
                    message = if (searchQuery.isEmpty()) {
                        "暂无本地脚本"
                    } else {
                        "未找到匹配的本地脚本"
                    }
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = 8.dp,
                        end = 16.dp,
                        bottom = 150.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(
                        space = 12.dp
                    ),
                    userScrollEnabled = true
                ) {
                    items(
                        items = plugins,
                        key = { it.id }
                    ) { plugin ->
                        val hName = HighlightUtils.highlightText(
                            text = plugin.name,
                            query = searchQuery,
                            highlightColor = colors.accentBlue,
                            baseColor = colors.textPrimary
                        )

                        val hAuthor = HighlightUtils.highlightText(
                            text = "作者: ${plugin.author}",
                            query = searchQuery,
                            highlightColor = colors.accentBlue,
                            baseColor = colors.textSecondary,
                        )

                        val hDesc = HighlightUtils.highlightText(
                            text = plugin.description.ifEmpty { "暂无描述" },
                            query = searchQuery,
                            highlightColor = colors.accentBlue,
                            baseColor = colors.textSecondary,
                        )

                        AnimatedListItem(
                            index = plugins.indexOf(element = plugin)
                        ) {
                            LocalPluginCard(
                                plugin = plugin,
                                highlightedName = hName,
                                highlightedAuthor = hAuthor,
                                highlightedDesc = hDesc,
                                onRunToggle = {
                                    onRunToggle(plugin.id, it)
                                },
                                onAutoLoadToggle = {
                                    onAutoLoadToggle(plugin.id, it)
                                },
                                onDelete = {
                                    onDelete(plugin.id)
                                },
                                onReload = {
                                    onReload(plugin.id)
                                },
                                onEditWithAgent = {
                                    onEditWithAgent(plugin.id)
                                }
                            )
                        }
                    }
                }
            }
        }

        ScrollToTopButton(
            visible = showScrollToTop && plugins.isNotEmpty(),
            onClick = {
                scope.launch {
                    listState.animateScrollToItem(index = 0)
                }
            },
            modifier = Modifier
                .align(
                    alignment = Alignment.BottomEnd
                )
                .padding(
                    end = 16.dp,
                    bottom = 100.dp
                )
        )
    }
}

@Composable
private fun LocalPluginCard(
    plugin: LocalPluginData,
    highlightedName: AnnotatedString,
    highlightedAuthor: AnnotatedString,
    highlightedDesc: AnnotatedString,
    onRunToggle: (Boolean) -> Unit,
    onAutoLoadToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onReload: () -> Unit,
    onEditWithAgent: () -> Unit
) {
    val colors = HappyTheme.colors

    var isExpanded by remember {
        mutableStateOf(value = false)
    }

    HappyCard(
        modifier = Modifier.fillMaxWidth(),
        animateContentSize = true
    ) {
        Column(
            modifier = Modifier.padding(
                all = Dimens.PaddingMedium
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember {
                            MutableInteractionSource()
                        },
                        indication = null,
                        onClick = {
                            isExpanded = !isExpanded
                        }
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(
                        weight = 1f
                    )
                ) {
                    Text(
                        text = highlightedName,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(
                        modifier = Modifier.height(
                            height = 4.dp
                        )
                    )

                    Text(
                        text = "${plugin.version} • ${if (plugin.isRunning) "运行中" else "未运行"}",
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )
                }

                HappySwitch(
                    checked = plugin.isRunning,
                    onCheckedChange = onRunToggle
                )
            }

            if (isExpanded) {
                Spacer(
                    modifier = Modifier.height(
                        height = 12.dp
                    )
                )

                HorizontalDivider(
                    color = colors.textSecondary.copy(
                        alpha = 0.1f
                    )
                )

                Spacer(
                    modifier = Modifier.height(
                        height = 12.dp
                    )
                )

                Text(
                    text = highlightedAuthor,
                    fontSize = 13.sp
                )

                Spacer(
                    modifier = Modifier.height(
                        height = Dimens.PaddingSmall
                    )
                )

                Text(
                    text = highlightedDesc,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                Spacer(
                    modifier = Modifier.height(
                        height = 12.dp
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "启动时自动加载",
                        fontSize = 14.sp,
                        color = colors.textPrimary,
                        modifier = Modifier.weight(
                            weight = 1f
                        )
                    )

                    HappySwitch(
                        checked = plugin.isAutoLoad,
                        onCheckedChange = onAutoLoadToggle
                    )
                }

                Spacer(
                    modifier = Modifier.height(
                        height = Dimens.PaddingMedium
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    ActionButton(
                        text = "修改",
                        onClick = onEditWithAgent,
                        style = ActionButtonStyle.Primary
                    )

                    Spacer(
                        modifier = Modifier.width(
                            width = Dimens.PaddingSmall
                        )
                    )

                    ActionButton(
                        text = "删除",
                        onClick = onDelete,
                        style = ActionButtonStyle.Danger
                    )

                    Spacer(
                        modifier = Modifier.width(
                            width = Dimens.PaddingSmall
                        )
                    )

                    ActionButton(
                        text = "重载",
                        onClick = onReload,
                        style = ActionButtonStyle.Primary
                    )
                }
            }
        }
    }
}