package cn.hxy.happy.common

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import cn.hxy.happy.utils.qq.HostInfo

/**
 * 调试类落盘开关。
 *
 * 和普通功能项不同，这几个开关要在「日志/崩溃」最底层被读到，不能反过来依赖 hook 项实例，
 * 所以单独放在一个与 uin 无关的 SharedPreferences 里，宿主进程和模块设置页（同进程）都能立即读到。
 *
 * 默认值：调试日志关（log.txt 之前能攒到 3 万行），错误日志开、崩溃报告开（都要配合上限使用）。
 */
object DebugSwitches {

    private const val PREFS_NAME = "Happy_Debug"
    private const val KEY_DEBUG_LOG = "debug_log"
    private const val KEY_ERROR_LOG = "error_log"
    private const val KEY_CRASH_REPORT = "crash_report"

    private val prefs: SharedPreferences?
        get() = runCatching {
            HostInfo.hostContext.getSharedPreferences(PREFS_NAME, Context.MODE_MULTI_PROCESS)
        }.getOrNull()

    var debugLog: Boolean
        get() = runCatching { prefs?.getBoolean(KEY_DEBUG_LOG, false) ?: false }.getOrDefault(false)
        set(value) = write(KEY_DEBUG_LOG, value)

    var errorLog: Boolean
        get() = runCatching { prefs?.getBoolean(KEY_ERROR_LOG, true) ?: true }.getOrDefault(true)
        set(value) = write(KEY_ERROR_LOG, value)

    var crashReport: Boolean
        get() = runCatching { prefs?.getBoolean(KEY_CRASH_REPORT, true) ?: true }.getOrDefault(true)
        set(value) = write(KEY_CRASH_REPORT, value)

    private fun write(key: String, value: Boolean) {
        runCatching { prefs?.edit { putBoolean(key, value) } }
    }
}
