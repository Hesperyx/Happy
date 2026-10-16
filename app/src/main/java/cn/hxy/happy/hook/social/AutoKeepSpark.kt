package cn.hxy.happy.hook.social

import androidx.compose.runtime.Composable
import cn.hxy.happy.annotation.HookCategory
import cn.hxy.happy.annotation.HookItemAnnotation
import cn.hxy.happy.common.ModuleScope
import cn.hxy.happy.conf.SparkConfig
import cn.hxy.happy.hook.base.BaseClickableHookItem
import cn.hxy.happy.ui.pages.configs.AutoKeepSparkPage
import cn.hxy.happy.utils.qq.MsgTool
import cn.hxy.happy.utils.scheduler.PrecisionScheduler
import cn.hxy.happy.utils.scheduler.ScheduledTask
import java.util.Calendar

@HookItemAnnotation(
    "自动续火",
    "点击选择群聊和好友，支持脚本图文复合消息，可自定义具体执行时间",
    HookCategory.SOCIAL
)
object AutoKeepSpark : BaseClickableHookItem<SparkConfig>(SparkConfig.serializer()) {

    override val defaultConfig: SparkConfig = SparkConfig()

    private const val TASK_ID = "AutoKeepSpark_Task"

    private fun getNextTargetTime(): Long {
        val now = PrecisionScheduler.currentServerTime()
        val c = Calendar.getInstance()
        c.timeInMillis = now
        c.set(Calendar.HOUR_OF_DAY, config.hour)
        c.set(Calendar.MINUTE, config.minute)
        c.set(Calendar.SECOND, config.second)
        c.set(Calendar.MILLISECOND, 0)

        if (c.timeInMillis <= now) {
            c.add(Calendar.DAY_OF_YEAR, 1)
        }
        return c.timeInMillis
    }

    fun scheduleSend() {
        val targetTime = getNextTargetTime()
        val task = ScheduledTask(
            TASK_ID,
            targetTime
        ) {
            if (!isEnable) return@ScheduledTask
            sendMsg()
            scheduleSend()
        }
        PrecisionScheduler.addTask(task)
    }

    private fun sendMsg() {
        config.contacts.forEach { contactStr ->
            ModuleScope.launchIO(name) {
                val uin = contactStr.dropLast(4)
                val chatType = if (contactStr.endsWith("(好友)")) 1 else 2
                MsgTool.sendMsg(uin, config.message, chatType)
            }
        }
    }

    override fun onHook() {
        PrecisionScheduler.init()
        scheduleSend()
    }

    @Composable
    override fun ConfigContent(onDismiss: () -> Unit) {
        AutoKeepSparkPage(
            currentConfig = config,
            onSave = {
                updateConfig(it)
                scheduleSend()
            },
            onDismiss = onDismiss
        )
    }
}
