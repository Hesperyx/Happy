package cn.hxy.happy.ui.pages.configs

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import cn.hxy.happy.conf.BackdropApi
import cn.hxy.happy.conf.BackdropApiConfig
import cn.hxy.happy.ui.components.atoms.ActionButton
import cn.hxy.happy.ui.components.atoms.ActionButtonStyle
import cn.hxy.happy.ui.components.atoms.ConfigButton
import cn.hxy.happy.ui.components.atoms.DialogTextField
import cn.hxy.happy.ui.components.atoms.HappyCard
import cn.hxy.happy.ui.components.atoms.HappySwitch
import cn.hxy.happy.ui.components.listitems.ActionItem
import cn.hxy.happy.ui.components.listitems.PreferenceSection
import cn.hxy.happy.ui.components.scaffold.ConfigPageScaffold
import cn.hxy.happy.ui.core.theme.AccentRed
import cn.hxy.happy.ui.core.theme.HappyTheme
import cn.hxy.happy.ui.pages.home.BackdropLoader
import cn.hxy.happy.ui.pages.home.BackdropProbe

/**
 * 主页背景接口设置页。
 *
 * 这里的顺序是刻意的：先给证据（真抓到的图 + 真实尺寸与体积），再谈清单。
 * 一串地址本身不需要被相信，能抓出图才是。
 */
@Composable
fun BackdropApiPage(
    currentConfig: BackdropApiConfig,
    onSave: (BackdropApiConfig) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = HappyTheme.colors
    val scope = rememberCoroutineScope()

    var apis by remember(currentConfig) {
        mutableStateOf(currentConfig.apis.ifEmpty { BackdropApiConfig.DEFAULT_APIS })
    }
    var expandedIndex by remember { mutableStateOf(-1) }
    var probe by remember { mutableStateOf<BackdropProbe?>(null) }
    var probeText by remember { mutableStateOf("") }
    var probeFailed by remember { mutableStateOf(false) }
    var isProbing by remember { mutableStateOf(false) }

    val enabledApis = apis.filter { it.enabled && it.url.isNotBlank() }

    fun updateAt(index: Int, block: (BackdropApi) -> BackdropApi) {
        apis = apis.toMutableList().also { it[index] = block(it[index]) }
    }

    fun probeOne() {
        val target = enabledApis.randomOrNull() ?: return
        isProbing = true
        probeFailed = false
        scope.launch {
            val result = BackdropLoader.probe(target)
            isProbing = false
            result.onSuccess { hit ->
                probe = hit
                probeText = buildString {
                    append(hit.api.displayLabel)
                    append(" · ")
                    append(hit.bitmap.width)
                    append("×")
                    append(hit.bitmap.height)
                    append(" · ")
                    append(hit.bytes / 1024)
                    append(" KB")
                }
            }.onFailure { error ->
                probeFailed = true
                probeText = "${target.displayLabel}：${error.message ?: "取图失败"}"
            }
        }
    }

    ConfigPageScaffold(
        title = "主页背景接口",
        configData = BackdropApiConfig(apis),
        onSave = { config ->
            onSave(
                BackdropApiConfig(
                    config.apis
                        .filter { it.url.isNotBlank() }
                        .map { it.copy(url = it.url.trim(), label = it.label.trim()) }
                )
            )
        },
        onDismiss = onDismiss
    ) { _ ->

        // 一、证据：抓到的就是主页会看到的那张
        val current = probe
        if (current != null) {
            Image(
                bitmap = current.bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(156.dp)
                    .clip(RoundedCornerShape(16.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.background),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (enabledApis.isEmpty()) "没有启用的接口" else "还没抓过图",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (enabledApis.isEmpty()) {
                            "至少打开一个接口，主页才有背景图"
                        } else {
                            "点下面的「试一张」，真抓一次看结果"
                        },
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            ConfigButton(
                text = if (isProbing) "抓取中…" else "试一张",
                onClick = { if (!isProbing && enabledApis.isNotEmpty()) probeOne() },
                modifier = Modifier.width(96.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = probeText.ifEmpty {
                    if (enabledApis.isEmpty()) {
                        "接口全关时，主页只剩纯色底"
                    } else {
                        "从 ${enabledApis.size} 个启用接口里随机挑一个"
                    }
                },
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = if (probeFailed) AccentRed else colors.textSecondary,
                modifier = Modifier.weight(1f)
            )
        }

        // 二、清单：每一条都是用户自己贴进来的地址
        PreferenceSection(title = "接口") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                apis.forEachIndexed { index, api ->
                    val expanded = expandedIndex == index
                    HappyCard(
                        modifier = Modifier.fillMaxWidth(),
                        animateContentSize = true,
                        onClick = { expandedIndex = if (expanded) -1 else index }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = api.displayLabel,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = api.url.ifBlank { "还没填地址" },
                                        fontSize = 12.sp,
                                        color = colors.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                HappySwitch(
                                    checked = api.enabled,
                                    onCheckedChange = { checked ->
                                        updateAt(index) { it.copy(enabled = checked) }
                                    }
                                )
                            }

                            if (expanded) {
                                Spacer(modifier = Modifier.height(16.dp))
                                DialogTextField(
                                    value = api.label,
                                    onValueChange = { text -> updateAt(index) { it.copy(label = text) } },
                                    label = "名称",
                                    hint = "用来区分这几条接口"
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                DialogTextField(
                                    value = api.url,
                                    onValueChange = { text -> updateAt(index) { it.copy(url = text) } },
                                    label = "地址",
                                    hint = "https://… 直接返回图片的地址",
                                    keyboardType = KeyboardType.Uri
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                ActionButton(
                                    text = "删除这个接口",
                                    style = ActionButtonStyle.Danger,
                                    onClick = {
                                        apis = apis.toMutableList().also { it.removeAt(index) }
                                        expandedIndex = -1
                                    }
                                )
                            }
                        }
                    }
                }

                if (apis.isEmpty()) {
                    Text(
                        text = "清单是空的：主页不会去网上取图。点下面「添加接口」把地址贴回来即可。",
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }

        // 三、动手
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionItem(
                title = "添加接口",
                description = "贴一个能直接返回图片的地址",
                onClick = {
                    apis = apis + BackdropApi(enabled = true)
                    expandedIndex = apis.lastIndex
                },
                trailingContent = {
                    Text(
                        text = "＋",
                        fontSize = 20.sp,
                        color = colors.textSecondary
                    )
                }
            )
            if (!BackdropApiConfig(apis).isSameAsDefault()) {
                ActionItem(
                    title = "恢复默认接口",
                    description = "换回内置的四条（loliapi、樱道）",
                    onClick = {
                        apis = BackdropApiConfig.DEFAULT_APIS
                        expandedIndex = -1
                        probeText = ""
                        probe = null
                    }
                )
            }
        }
    }
}
