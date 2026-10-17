package cn.hxy.happy.hook.notification

// 思路参考自 QAuxiliary: https://github.com/cinit/QAuxiliary

import androidx.compose.runtime.Composable
import cn.hxy.happy.annotation.HookCategory
import cn.hxy.happy.annotation.HookItemAnnotation
import cn.hxy.happy.conf.NotificationChannelConfig
import cn.hxy.happy.hook.base.BaseClickableHookItem
import cn.hxy.happy.ui.pages.configs.NotificationChannelPage

@HookItemAnnotation(
    "通知渠道管理",
    "管理应用内的通知渠道。",
    HookCategory.NOTIFICATION,
    ""
)
object NotificationChannelManager :
    BaseClickableHookItem<NotificationChannelConfig>(NotificationChannelConfig.serializer()) {

    override val defaultConfig: NotificationChannelConfig = NotificationChannelConfig()

    override fun onHook() {
        // 入口型功能：不 hook 任何方法，具体跳转由 NotificationChannelPage 承担。
    }

    @Composable
    override fun ConfigContent(onDismiss: () -> Unit) {
        NotificationChannelPage(
            currentConfig = config,
            onSave = ::updateConfig,
            onDismiss = onDismiss
        )
    }
}
