package cn.hxy.happy.utils.log

import android.os.Build
import android.os.SystemClock
import android.util.Log
import cn.hxy.happy.BuildConfig
import cn.hxy.happy.common.DebugSwitches
import cn.hxy.happy.common.ModuleScope
import cn.hxy.happy.hook.base.BaseHookItem
import cn.hxy.happy.loader.hookapi.HookEngineManager
import cn.hxy.happy.utils.io.FileUtils
import cn.hxy.happy.utils.qq.HostInfo
import cn.hxy.happy.utils.qq.QQCurrentEnv
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogUtils {

    private const val TAG = "Happy"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    /** log.txt 上限：超过就清空重写，避免长期挂机攒成几十 MB */
    private const val DEBUG_LOG_MAX_BYTES = 2L * 1024 * 1024

    /** error_log.txt 上限：超限时用新内容覆盖（真正根治靠下面的去重） */
    private const val ERROR_LOG_MAX_BYTES = 1024L * 1024

    /** 同一处异常在这个窗口内只落盘一次：之前 AntiRevoke 解析失败能刷出 14MB */
    private const val DEDUPE_WINDOW_MS = 10 * 60 * 1000L
    private const val DEDUPE_MAX_ENTRIES = 200

    private val recentErrors = HashMap<String, Long>()
    private val errorLock = Any()

    fun d(msg: String) {
        if (!DebugSwitches.debugLog) return
        runCatching {
            val file = File(QQCurrentEnv.currentDir, "log.txt")
            if (file.length() > DEBUG_LOG_MAX_BYTES) file.delete()
            FileUtils.writeText(file, "$msg\n", true)
        }
    }

    fun e(tag: String, t: Throwable) {
        val stackTrace = Log.getStackTraceString(t)
        val msg = "[$tag] Error:\n$stackTrace"
        HookEngineManager.engine.log(Log.ERROR, "[$TAG] [$tag]", "Error occurred:", t)

        if (!DebugSwitches.errorLog) return
        if (!shouldWrite(tag, t)) return

        saveCrashLog(tag, msg)
    }

    /**
     * 按「tag + 异常类 + 首个栈帧」去重。重复异常仍会进 logcat（上面 engine.log 已经写过），
     * 只是不再往 error_log.txt 里堆同样的一段。
     */
    private fun shouldWrite(tag: String, t: Throwable): Boolean {
        val frame = t.stackTrace.firstOrNull()?.let { "${it.className}.${it.methodName}" } ?: "unknown"
        val key = "$tag|${t.javaClass.name}|$frame"
        val now = SystemClock.elapsedRealtime()
        synchronized(errorLock) {
            val last = recentErrors[key]
            if (last != null && now - last < DEDUPE_WINDOW_MS) {
                recentErrors[key] = now
                return false
            }
            recentErrors[key] = now
            if (recentErrors.size > DEDUPE_MAX_ENTRIES) {
                val threshold = now - DEDUPE_WINDOW_MS
                recentErrors.entries.removeAll { it.value < threshold }
            }
            return true
        }
    }

    fun e(hookItem: BaseHookItem, t: Throwable) {
        e(hookItem.name, t)
    }

    fun getEnvironmentInfo(): String {
        val engine = HookEngineManager.engine
        return """
                === Environment Information ===
                Record Time: ${dateFormat.format(Date())}

                --- Device Information ---
                Brand: ${Build.BRAND}
                Model: ${Build.MODEL}
                Device: ${Build.DEVICE}
                Product: ${Build.PRODUCT}
                Manufacturer: ${Build.MANUFACTURER}
                Android Version: ${Build.VERSION.RELEASE}
                API Level: ${Build.VERSION.SDK_INT}
                Build ID: ${Build.ID}
                Fingerprint: ${Build.FINGERPRINT}
                Supported ABIs: ${Build.SUPPORTED_ABIS.joinToString(", ")}

                --- Xposed Framework Information ---
                Framework Name: ${engine.frameworkName}
                Framework Version: ${engine.frameworkVersion}
                Framework Version Code: ${engine.frameworkVersionCode}
                API Version: ${engine.apiLevel}

                --- Host Application Information ---
                Package Name: ${HostInfo.packageName}
                Version Name: ${HostInfo.versionName}
                Version Code: ${HostInfo.versionCode}

                --- Module Information ---
                Module Version Name: ${BuildConfig.VERSION_NAME}
                Module Version Code: ${BuildConfig.VERSION_CODE}

                ======================================
            """.trimIndent()
    }

    fun logEnvironment() {
        ModuleScope.launchIO("EnvLog") {
            val info = getEnvironmentInfo()
            val file = File("${HostInfo.moduleDataPath}global/log", "environment_info.txt")
            FileUtils.writeText(file, info)
        }
    }

    private fun saveCrashLog(tag: String, content: String) {
        val dir = QQCurrentEnv.currentDir
        ModuleScope.launchIO("WriteLog") {
            val time = dateFormat.format(Date())
            val logContent = "\n=== $time [$tag] ===\n$content\n"
            val file = File("${dir}log", "error_log.txt")
            // 超过上限就整文件重来：日志只用于最近一段时间的排查，留旧的没用
            val append = file.length() <= ERROR_LOG_MAX_BYTES
            FileUtils.writeText(file, logContent, append)
        }
    }
}
