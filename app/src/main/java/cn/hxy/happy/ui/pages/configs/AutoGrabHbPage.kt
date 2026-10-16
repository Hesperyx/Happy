package cn.hxy.happy.ui.pages.configs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import cn.hxy.happy.conf.RedPacketConfig
import cn.hxy.happy.plugin.bean.GroupInfo
import cn.hxy.happy.ui.components.biz.AsyncSelectorDialog
import cn.hxy.happy.ui.components.dialogs.CenterDialog
import cn.hxy.happy.ui.components.dialogs.CenterDialogContainer
import cn.hxy.happy.ui.components.dialogs.SelectionItem
import cn.hxy.happy.ui.components.listitems.ActionItem
import cn.hxy.happy.ui.components.listitems.InputItem
import cn.hxy.happy.ui.components.listitems.PreferenceSection
import cn.hxy.happy.ui.components.listitems.SelectionGroup
import cn.hxy.happy.ui.components.listitems.SwitchItem
import cn.hxy.happy.ui.components.listitems.SelectionItem as ListSelectionItem
import cn.hxy.happy.ui.components.scaffold.ConfigPageScaffold
import cn.hxy.happy.utils.qq.TroopTool

/** 红包类型：channel -> 名称/说明 */
private val RED_PACKET_TYPES = listOf(
    Triple(1, "普通/拼手气红包", "群内可直接领取的普通红包"),
    Triple(32, "口令红包", "需要发送口令后再领取"),
    Triple(1024, "专属红包", "仅指定收件人可领取"),
    Triple(65536, "语音红包", "需要先上传匹配的口令语音"),
)

/**
 * 自动领取红包配置页。
 *
 * 延迟、最低平均金额以字符串保存临时输入，避免输入过程中被规范化；
 * 关键词与自动回复用逗号分隔的字符串编辑，保存时拆分成集合/列表。
 */
@Composable
fun AutoGrabHbPage(
    config: RedPacketConfig,
    onSave: (RedPacketConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var delayText by remember(config) { mutableStateOf(config.delay.toString()) }
    var minAverageText by remember(config) { mutableStateOf(config.minAverage.toString()) }
    var keywordsText by remember(config) { mutableStateOf(config.keywords.joinToString(",")) }
    var autoReplyText by remember(config) { mutableStateOf(config.autoReply.joinToString(",")) }
    var targetTypes by remember(config) { mutableStateOf(config.targetTypes) }
    var sendVoiceAfterGrab by remember(config) { mutableStateOf(config.sendVoiceAfterGrab) }
    var whiteList by remember(config) { mutableStateOf(config.whiteList) }

    var showTypeDialog by remember { mutableStateOf(false) }
    var showWhiteListDialog by remember { mutableStateOf(false) }

    fun buildConfig() = RedPacketConfig(
        delay = delayText.toLongOrNull() ?: 0L,
        minAverage = minAverageText.toIntOrNull() ?: 0,
        keywords = keywordsText.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
        autoReply = autoReplyText.split(",").map { it.trim() }.filter { it.isNotEmpty() },
        whiteList = whiteList,
        targetTypes = targetTypes,
        sendVoiceAfterGrab = sendVoiceAfterGrab,
    )

    ConfigPageScaffold(
        title = "领取配置",
        configData = buildConfig(),
        onSave = onSave,
        onDismiss = onDismiss
    ) { _ ->
        PreferenceSection(title = "抢包参数") {
            InputItem(
                title = "延迟时间（毫秒）",
                value = delayText,
                onValueChange = { delayText = it },
                placeholder = "0",
                keyboardType = KeyboardType.Number
            )
            InputItem(
                title = "最低平均金额（分）",
                value = minAverageText,
                onValueChange = { minAverageText = it },
                placeholder = "0（不限制）",
                keyboardType = KeyboardType.Number
            )
            InputItem(
                title = "关键词过滤（逗号分隔）",
                value = keywordsText,
                onValueChange = { keywordsText = it },
                placeholder = "标题命中则跳过，例如：专属,感谢"
            )
            InputItem(
                title = "自动回复（逗号分隔，随机选择）",
                value = autoReplyText,
                onValueChange = { autoReplyText = it },
                placeholder = "抢到后随机发送其中一条，支持脚本图文"
            )
        }

        PreferenceSection(title = "红包类型") {
            ActionItem(
                title = "红包类型",
                description = "已选择 ${targetTypes.size} 种类型",
                onClick = { showTypeDialog = true }
            )
        }

        PreferenceSection(title = "其他") {
            SwitchItem(
                title = "抢语音红包后发送语音",
                checked = sendVoiceAfterGrab,
                onCheckedChange = { sendVoiceAfterGrab = it },
                description = "抢到语音红包后在群里发送合成的口令语音"
            )
            ActionItem(
                title = "白名单群聊",
                description = "已选择 ${whiteList.size} 个群",
                onClick = { showWhiteListDialog = true }
            )
        }
    }

    if (showTypeDialog) {
        CenterDialog(
            visible = true,
            onDismiss = { showTypeDialog = false }
        ) {
            CenterDialogContainer(
                title = "选择红包类型",
                onDismiss = { showTypeDialog = false },
                onConfirm = { showTypeDialog = false }
            ) {
                SelectionGroup {
                    RED_PACKET_TYPES.forEach { (type, name, desc) ->
                        ListSelectionItem(
                            title = name,
                            subtitle = desc,
                            isSelected = targetTypes.contains(type),
                            onClick = {
                                val newSet = targetTypes.toMutableSet()
                                if (newSet.contains(type)) {
                                    newSet.remove(type)
                                } else {
                                    newSet.add(type)
                                }
                                targetTypes = newSet
                            }
                        )
                    }
                }
            }
        }
    }

    if (showWhiteListDialog) {
        AsyncSelectorDialog(
            title = "选择白名单群聊",
            currentSelection = whiteList,
            dataLoader = { TroopTool.getGroupList() },
            mapper = { group: GroupInfo -> SelectionItem(group.group, group.groupName) },
            onDismiss = { showWhiteListDialog = false },
            onConfirm = { selected ->
                whiteList = selected
                showWhiteListDialog = false
            }
        )
    }
}
