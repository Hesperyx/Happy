package cn.hxy.happy.ui.pages.configs

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import cn.hxy.happy.conf.FakePhoneConfig
import cn.hxy.happy.ui.components.listitems.InputItem
import cn.hxy.happy.ui.components.scaffold.ConfigPageScaffold

@Composable
fun FakePhonePage(
    currentConfig: FakePhoneConfig,
    onSave: (FakePhoneConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var phoneText by remember(currentConfig) { mutableStateOf(currentConfig.phone) }

    ConfigPageScaffold(
        title = "伪装手机号码",
        configData = FakePhoneConfig(phone = phoneText),
        onSave = onSave,
        onDismiss = onDismiss
    ) { _ ->
        InputItem(
            title = "要伪装的手机号码",
            value = phoneText,
            onValueChange = { phoneText = it },
            placeholder = "留空则使用 ${FakePhoneConfig.DEFAULT_PHONE}",
            modifier = Modifier.fillMaxWidth()
        )
    }
}
