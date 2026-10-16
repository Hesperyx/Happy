package cn.hxy.happy.utils.log

import android.os.Build
import android.util.Log
import cn.hxy.happy.BuildConfig
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

    fun d(msg: String) {
        FileUtils.writeText(File(QQCurrentEnv.currentDir, "log.txt"), "$msg\n", true)
    }

    fun e(tag: String, t: Throwable) {
        val stackTrace = Log.getStackTraceString(t)
        val msg = "[$tag] Error:\n$stackTrace"
        HookEngineManager.engine.log(Log.ERROR, "[$TAG] [$tag]", "Error occurred:", t)

        saveCrashLog(tag, msg)
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
            FileUtils.writeText(file, logContent, true)
        }
    }
}
