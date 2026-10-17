package cn.hxy.happy.ui.pages.configs

import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.Composable
import cn.hxy.happy.conf.NotificationChannelConfig
import cn.hxy.happy.ui.components.listitems.ActionItem
import cn.hxy.happy.ui.components.listitems.PreferenceSection
import cn.hxy.happy.ui.components.scaffold.ConfigPageScaffold
import cn.hxy.happy.utils.qq.HostInfo

@Composable
fun NotificationChannelPage(
    currentConfig: NotificationChannelConfig,
    onSave: (NotificationChannelConfig) -> Unit,
    onDismiss: () -> Unit
) {
    val packageName = HostInfo.packageName

    fun openSystemIntent(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { HostInfo.hostContext.startActivity(intent) }
    }

    ConfigPageScaffold(
        title = "通知渠道管理",
        configData = currentConfig,
        onSave = onSave,
        onDismiss = onDismiss
    ) { _ ->
        PreferenceSection(title = "渠道入口") {
            ActionItem(
                title = "打开消息通知设置",
                description = "进入宿主的通知分类页面，逐个开关通知渠道",
                onClick = {
                    openSystemIntent(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    )
                }
            )
            ActionItem(
                title = "打开应用通知设置",
                description = "系统通知总开关页面",
                onClick = {
                    openSystemIntent(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(android.net.Uri.parse("package:$packageName"))
                    )
                }
            )
        }
    }
}
