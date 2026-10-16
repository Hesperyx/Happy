package cn.hxy.happy.hook.troop

import androidx.compose.runtime.Composable
import cn.hxy.happy.annotation.HookCategory
import cn.hxy.happy.annotation.HookItemAnnotation
import cn.hxy.happy.common.ModuleScope
import cn.hxy.happy.conf.TroopSetConfig
import cn.hxy.happy.hook.base.BaseClickableHookItem
import cn.hxy.happy.ui.pages.configs.TroopSelectorPage
import cn.hxy.happy.utils.qq.TroopTool
import cn.hxy.happy.utils.scheduler.PrecisionScheduler
import cn.hxy.happy.utils.scheduler.ScheduledTask

@HookItemAnnotation(
    "群打卡",
    "点击选择群聊",
    HookCategory.GROUP
)
object AutoGroupClockIn : BaseClickableHookItem<TroopSetConfig>(TroopSetConfig.serializer()) {

    override val defaultConfig: TroopSetConfig = TroopSetConfig()

    private fun scheduleClockIn() {
        val targetMidnight = PrecisionScheduler.getNextMidnight()

        val preciseTask = ScheduledTask(
            "DailyClockIn_Exact",
            targetMidnight
        ) {
            if (!isEnable) return@ScheduledTask
            doClockIn()
        }

        val backupTask = ScheduledTask(
            "DailyClockIn_Backup",
            targetMidnight + 500
        ) {
            if (!isEnable) return@ScheduledTask
            doClockIn()
            scheduleClockIn()
        }

        PrecisionScheduler.addTask(preciseTask)
        PrecisionScheduler.addTask(backupTask)
    }

    private fun doClockIn() {
        config.selectedSet.forEach {
            ModuleScope.launchIO(name) {
                TroopTool.clockIn(it)
            }
        }
    }

    override fun onHook() {
        PrecisionScheduler.init()
        scheduleClockIn()
    }

    @Composable
    override fun ConfigContent(onDismiss: () -> Unit) {
        TroopSelectorPage(
            title = "选择你要打卡的群聊",
            currentConfig = config,
            onSave = ::updateConfig,
            onDismiss = onDismiss
        )
    }
}
