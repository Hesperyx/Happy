package cn.hxy.happy.hook.notification

// 思路参考自 QAuxiliary: https://github.com/cinit/QAuxiliary

import android.app.Notification
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import androidx.compose.runtime.Composable
import cn.hxy.happy.annotation.HookCategory
import cn.hxy.happy.annotation.HookItemAnnotation
import cn.hxy.happy.conf.NotificationStyleConfig
import cn.hxy.happy.hook.base.BaseClickableHookItem
import cn.hxy.happy.ui.pages.configs.NotificationStylePage
import cn.hxy.happy.utils.hook.hookAfter
import cn.hxy.happy.utils.hook.hookBefore
import cn.hxy.happy.utils.log.LogUtils
import cn.hxy.happy.utils.qq.HostInfo
import cn.hxy.happy.utils.reflect.clazz

@HookItemAnnotation(
    "MessagingStyle 通知",
    "更加优雅的通知样式，致敬 QQ Helper。",
    HookCategory.NOTIFICATION,
    "All"
)
object MessagingStyleNotification :
    BaseClickableHookItem<NotificationStyleConfig>(NotificationStyleConfig.serializer()) {

    override val isNeedRestart: Boolean = true

    override val defaultConfig: NotificationStyleConfig = NotificationStyleConfig()

    private val notificationCapture = MessagingNotificationCapture(this)

    private val notificationBuilder = MessagingNotificationBuilder(
        disableConversationSubChannel = { config.disableConversationSubChannel },
        disableBubble = { config.disableBubble }
    )

    override fun onHook() {
        if (!createNotificationChannels()) return

        val notificationFacade = "com.tencent.qqnt.notification.NotificationFacade".clazz
            ?: return skip("NotificationFacade not found")
        val appRuntimeClass = "mqq.app.AppRuntime".clazz
            ?: return skip("AppRuntime not found")
        val commonInfoClass = "com.tencent.qqnt.kernel.nativeinterface.NotificationCommonInfo".clazz
            ?: return skip("NotificationCommonInfo not found")
        val recentInfoClass = "com.tencent.qqnt.kernel.nativeinterface.RecentContactInfo".clazz
            ?: return skip("RecentContactInfo not found")
        val postTarget = findPostNotificationMethod(notificationFacade)
            ?: return skip("post notification method not found")

        val buildPathHooked = notificationCapture.hookBuildPaths(
            notificationFacade,
            appRuntimeClass,
            commonInfoClass,
            recentInfoClass
        )
        if (!buildPathHooked) {
            return skip("notification build path not found")
        }

        postTarget.first.hookBefore(this) { param ->
            val oldNotification = param.args[postTarget.second] as? Notification
                ?: return@hookBefore
            val pair = notificationCapture.take(oldNotification) ?: return@hookBefore
            val newNotification = runCatching {
                notificationBuilder.createNotification(pair.first, pair.second, oldNotification)
            }.onFailure {
                LogUtils.e("MessagingStyleNotification replace failed", it)
            }.getOrNull() ?: return@hookBefore

            param.args[postTarget.second] = newNotification
        }

        postTarget.first.hookAfter(this) {
            if (config.autoClearConversationSubChannel) {
                notificationBuilder.clearRedundantConversationChannels()
            }
        }

        "com.tencent.commonsdk.util.notification.QQNotificationManager".clazz
            ?.declaredMethods
            ?.firstOrNull { it.name == "cancelAll" && it.parameterCount == 0 }
            ?.apply { isAccessible = true }
            ?.hookBefore(this) {
                notificationBuilder.clearHistory()
            }
    }

    private fun createNotificationChannels(): Boolean {
        return runCatching {
            val notificationManager =
                HostInfo.hostContext.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannelGroup(
                NotificationChannelGroup(CHANNEL_GROUP_ID, CHANNEL_GROUP_NAME)
            )
            createQAuxNotificationChannels(notificationManager)
        }.onFailure {
            LogUtils.e("MessagingStyleNotification create channels failed", it)
        }.isSuccess
    }

    private fun skip(reason: String) {
        // LogUtils 没有单独的 warning 级别，用 d 打 WARN 前缀日志，等价于 TCQT 的 logger.w
        LogUtils.d("[WARN][MessagingStyleNotification] skipped: $reason")
    }

    @Composable
    override fun ConfigContent(onDismiss: () -> Unit) {
        NotificationStylePage(
            currentConfig = config,
            onSave = ::updateConfig,
            onDismiss = onDismiss
        )
    }
}
