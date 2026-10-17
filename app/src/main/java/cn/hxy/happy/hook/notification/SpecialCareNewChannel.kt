package cn.hxy.happy.hook.notification

// 思路参考自 QAuxiliary: https://github.com/cinit/QAuxiliary

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import cn.hxy.happy.annotation.HookCategory
import cn.hxy.happy.annotation.HookItemAnnotation
import cn.hxy.happy.hook.base.BaseSwitchHookItem
import cn.hxy.happy.utils.hook.hookBefore
import cn.hxy.happy.utils.qq.HostInfo
import cn.hxy.happy.utils.reflect.setObject

@HookItemAnnotation(
    "特别关心通知单独分组",
    "将特别关心发送的消息通知移动到单独的通知渠道",
    HookCategory.NOTIFICATION,
    "All"
)
object SpecialCareNewChannel : BaseSwitchHookItem() {

    override fun onHook() {
        NotificationManager::class.java.declaredMethods
            .filter { it.name == "notify" && it.parameterTypes.lastOrNull() == Notification::class.java }
            .forEach { method ->
                method.isAccessible = true
                method.hookBefore(this) { param ->
                    val notification = param.args.lastOrNull() as? Notification
                        ?: return@hookBefore
                    val title = notification.extras?.getCharSequence(Notification.EXTRA_TITLE)
                        ?.toString()
                        .orEmpty()
                    if (!title.contains(SPECIAL_CARE_MARK)) return@hookBefore

                    ensureSpecialCareChannel()
                    runCatching {
                        notification.setObject("mChannelId", CHANNEL_ID_SPECIALLY_CARE)
                    }
                    param.args[param.args.lastIndex] = notification
                }
            }
    }

    private fun ensureSpecialCareChannel() {
        val notificationManager =
            HostInfo.hostContext.getSystemService(NotificationManager::class.java)
        if (notificationManager.getNotificationChannel(CHANNEL_ID_SPECIALLY_CARE) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID_SPECIALLY_CARE,
            "特别关心",
            NotificationManager.IMPORTANCE_HIGH
        )
        notificationManager.createNotificationChannel(channel)
    }

    private const val SPECIAL_CARE_MARK = "[特别关心]"
    private const val CHANNEL_ID_SPECIALLY_CARE = "CHANNEL_ID_SPECIALLY_CARE"
}
