package cn.hxy.happy.hook.debug

import cn.hxy.happy.annotation.HookCategory
import cn.hxy.happy.annotation.HookItemAnnotation
import cn.hxy.happy.common.DebugSwitches
import cn.hxy.happy.hook.base.BaseSwitchHookItem

/**
 * 调试类开关：本身不 hook 任何东西，只是把值落到 [DebugSwitches]。
 *
 * 之前这些输出没有开关，log.txt 能攒到 3 万行、error_log.txt 到 14MB、崩溃报告目录到 2.6GB，
 * 用户完全没法自己关掉，所以单独开一个「调试」分类集中管理。
 */
@HookItemAnnotation(
    "调试日志",
    "把运行过程写进 QQ 目录下的 Happy/<uin>/log.txt。排查问题时才需要打开，会持续增长",
    HookCategory.DEBUG
)
object DebugLogSwitch : BaseSwitchHookItem() {

    override var isEnable: Boolean
        get() = DebugSwitches.debugLog
        set(value) {
            DebugSwitches.debugLog = value
        }
}

@HookItemAnnotation(
    "错误日志",
    "把 hook 异常写进 Happy/<uin>/log/error_log.txt，单个文件超过 1MB 会自动截断重来",
    HookCategory.DEBUG
)
object ErrorLogSwitch : BaseSwitchHookItem() {

    override var isEnable: Boolean
        get() = DebugSwitches.errorLog
        set(value) {
            DebugSwitches.errorLog = value
        }
}

@HookItemAnnotation(
    "崩溃报告",
    "崩溃时生成 crash_*.zip（含堆栈、logcat 摘要）。目录总量控制在 100MB 以内，超出自动清理最旧的",
    HookCategory.DEBUG
)
object CrashReportSwitch : BaseSwitchHookItem() {

    override var isEnable: Boolean
        get() = DebugSwitches.crashReport
        set(value) {
            DebugSwitches.crashReport = value
        }
}
