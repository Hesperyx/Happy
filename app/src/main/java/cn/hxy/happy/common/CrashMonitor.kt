package cn.hxy.happy.common

import android.content.Intent
import android.util.Log
import cn.hxy.happy.activity.CrashActivity
import cn.hxy.happy.loader.hookapi.HookEngineManager
import cn.hxy.happy.utils.hook.hookBefore
import cn.hxy.happy.utils.log.CrashReporter
import cn.hxy.happy.utils.qq.HostInfo
import kotlin.system.exitProcess

object CrashMonitor {

    private var isInitialized = false

    fun init() {
        if (isInitialized) return
        isInitialized = true

        try {
            Thread::class.java.getDeclaredMethod(
                "setDefaultUncaughtExceptionHandler",
                Thread.UncaughtExceptionHandler::class.java
            ).hookBefore { param ->
                val originalHandler = param.args[0] as? Thread.UncaughtExceptionHandler
                if (originalHandler !is CrashHandler) {
                    param.args[0] = CrashHandler(originalHandler)
                }
            }

            val currentHandler = Thread.getDefaultUncaughtExceptionHandler()
            if (currentHandler !is CrashHandler) {
                Thread.setDefaultUncaughtExceptionHandler(CrashHandler(currentHandler))
            }
        } catch (t: Throwable) {
            HookEngineManager.engine.log(Log.ERROR, "[Happy]", "CrashMonitor init failed: ", t)
        }
    }

    class CrashHandler(private val originalHandler: Thread.UncaughtExceptionHandler?) :
        Thread.UncaughtExceptionHandler {

        override fun uncaughtException(t: Thread, e: Throwable) {
            val stackTrace = Log.getStackTraceString(e)
            // 先打一行 logcat：崩溃包生成失败时（写盘异常、进程被抢杀）这是唯一的现场
            Log.e("HappyCrash", "uncaught in thread [${t.name}]: $stackTrace")
            try {
                val result = CrashReporter.generateReport(t, e)

                val context = HostInfo.hostContext
                val intent = Intent(context, CrashActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    putExtra("blamedModule", result.blamedModule)
                    putExtra("exceptionType", result.exceptionType)
                    putExtra("summary", result.summary)
                    putExtra("reportPath", result.zipFile.absolutePath)
                    putExtra("stackTrace", result.stackTrace)
                    putExtra("hostName", HostInfo.hostName)
                }

                context.startActivity(intent)
                exitProcess(10)

            } catch (innerEx: Throwable) {
                Log.e("HappyCrash", "CrashReporter 失败，交回原处理器", innerEx)
                innerEx.printStackTrace()
                originalHandler?.uncaughtException(t, e)
            }
        }
    }
}