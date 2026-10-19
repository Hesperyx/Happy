package cn.hxy.happy.utils.log

import cn.hxy.happy.common.DebugSwitches
import cn.hxy.happy.utils.io.FileUtils
import cn.hxy.happy.utils.qq.HostInfo
import cn.hxy.happy.utils.qq.QQCurrentEnv
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CrashAnalysisResult(
    val zipFile: File,
    val blamedModule: String,
    val exceptionType: String,
    val summary: String,
    val stackTrace: String
)

object CrashReporter {

    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
    private val moduleNameCache = mutableMapOf<String, String>()

    /** 同一处崩溃（异常类型 + 首帧）在这个窗口内只落一次盘 */
    private const val DEDUPE_WINDOW_MS = 60_000L

    /** 单次进程生命周期内最多落盘几份，防崩溃循环把存储刷满 */
    private const val MAX_SESSION_REPORTS = 5

    /** crash 目录总量上限（超出后清理到 [PRUNE_TARGET_BYTES]） */
    private const val MAX_CRASH_DIR_BYTES = 100L * 1024 * 1024
    private const val PRUNE_TARGET_BYTES = 70L * 1024 * 1024

    private val writtenSignatures = HashMap<String, Long>()
    private var sessionReports = 0
    private var lastZipFile: File? = null

    fun generateReport(t: Thread, e: Throwable): CrashAnalysisResult {
        val baseDir = try {
            QQCurrentEnv.currentDir
        } catch (_: Exception) {
            if (HostInfo.moduleDataPath.isNotEmpty()) {
                HostInfo.moduleDataPath + "global/"
            } else {
                "/storage/emulated/0/Android/data/${HostInfo.packageName}/Happy/global/"
            }
        }

        val crashRootDir = File(baseDir, "crash")
        val stackTraceStr = getStackTraceString(e)
        val (blamedModule, analysisReport) = analyzeStackTrace(e)

        // 用户关掉了崩溃报告（或本次崩溃被节流）：不写盘，只把现场交给崩溃页展示
        if (!DebugSwitches.crashReport || !shouldWrite(e)) {
            return CrashAnalysisResult(
                zipFile = lastZipFile ?: File(crashRootDir, ""),
                blamedModule = blamedModule,
                exceptionType = e.javaClass.simpleName,
                summary = e.message ?: "No error message",
                stackTrace = stackTraceStr
            )
        }

        val timestamp = dateFormat.format(Date()) + "_" + System.currentTimeMillis() % 1000
        val tempDir = File(crashRootDir, "temp_$timestamp")
        val finalZipFile = File(crashRootDir, "crash_$timestamp.zip")

        FileUtils.ensureDir(tempDir)

        try {
            writeFile(tempDir, "crash_hit_flag", "1")
            writeFile(tempDir, "summary", "${e.javaClass.name}: ${e.message}")
            writeFile(tempDir, "java_trace", "Thread:${t.name}\n$stackTraceStr")
            writeFile(tempDir, "java_trace_analysis", analysisReport)
            writeFile(tempDir, "trace_module", "Blamed Module: $blamedModule")

            writeFile(tempDir, "logcat", getLogcat())

            val envInfo = LogUtils.getEnvironmentInfo()
            writeFile(tempDir, "env_info", envInfo)

            // 整份线程栈和 /proc/self/maps 一份就 200KB+700KB，绝大多数排查根本用不到，
            // 只留主线程 + 进程 status，报告体积从 ~1.3MB 降到几十 KB
            writeJavaThreads(tempDir)
            copySystemFile("/proc/self/status", File(tempDir, "status"))

            if (FileUtils.zip(tempDir, finalZipFile)) {
                FileUtils.delete(tempDir)
                lastZipFile = finalZipFile
            } else {
                finalZipFile.delete()
                // 压缩失败时以前只删 zip，temp 目录会永久留在那（实测清出 2.6GB 就是这些）
                FileUtils.delete(tempDir)
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            FileUtils.delete(tempDir)
        }

        prune(crashRootDir)

        val resultFile = if (finalZipFile.exists()) finalZipFile else tempDir

        return CrashAnalysisResult(
            zipFile = resultFile,
            blamedModule = blamedModule,
            exceptionType = e.javaClass.simpleName,
            summary = e.message ?: "No error message",
            stackTrace = stackTraceStr
        )
    }

    /**
     * 崩溃循环节流：同一个异常签名 1 分钟内只落一份；整个进程最多 [MAX_SESSION_REPORTS] 份。
     * 之前 QQ 主线程被我们的 bug 打崩 → 崩溃报告 → 重启 → 再崩，循环里攒了 2800 多份。
     */
    private fun shouldWrite(e: Throwable): Boolean {
        val frame = e.stackTrace.firstOrNull()?.let { "${it.className}.${it.methodName}" } ?: "unknown"
        val key = "${e.javaClass.name}|$frame"
        val now = System.currentTimeMillis()
        synchronized(writtenSignatures) {
            val last = writtenSignatures[key]
            if (last != null && now - last < DEDUPE_WINDOW_MS) return false
            if (sessionReports >= MAX_SESSION_REPORTS) return false
            writtenSignatures[key] = now
            sessionReports++
            return true
        }
    }

    /**
     * crash 目录清理：超过 [MAX_CRASH_DIR_BYTES] 就按时间删到 [PRUNE_TARGET_BYTES]。
     * 启动时也会调一次，把历史垃圾自动收掉。
     */
    fun prune(crashRootDir: File) {
        runCatching {
            if (!crashRootDir.isDirectory) return
            val entries = crashRootDir.listFiles()?.toMutableList() ?: return
            var total = entries.sumOf { sizeOf(it) }
            if (total <= MAX_CRASH_DIR_BYTES) return

            entries.sortBy { it.lastModified() }
            for (entry in entries) {
                if (total <= PRUNE_TARGET_BYTES) break
                val size = sizeOf(entry)
                if (FileUtils.delete(entry)) total -= size
            }
        }
    }

    private fun sizeOf(file: File): Long {
        if (file.isFile) return file.length()
        return file.listFiles()?.sumOf { sizeOf(it) } ?: 0L
    }

    private fun getLogcat(): String {
        return try {
            // 3000 行 ≈ 320KB，排查崩溃看最后 600 行足够
            val process =
                Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "threadtime", "-t", "600"))
            process.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            "Logcat capture failed: ${e.message}"
        }
    }

    private fun analyzeStackTrace(e: Throwable): Pair<String, String> {
        val sbAnalysis = StringBuilder()
        sbAnalysis.append("----Traces Analysis-----\n\n")

        var blamedModule = "Unknown"
        val stackTrace = e.stackTrace

        for (element in stackTrace) {
            val className = element.className
            val clazz = try {
                val ctxLoader = Thread.currentThread().contextClassLoader
                Class.forName(className, false, ctxLoader)
            } catch (_: ClassNotFoundException) {
                null
            }

            val classLoader = clazz?.classLoader
            val loaderStr = classLoader?.toString() ?: "BootClassLoader/Unknown"

            sbAnalysis.append("at $className.${element.methodName}(${element.fileName}:${element.lineNumber})\n")
            sbAnalysis.append("   Loader: $loaderStr\n")

            if (blamedModule == "Unknown" && classLoader != null) {
                if (className.startsWith("cn.hxy.happy")) {
                    blamedModule = "Happy"
                } else if (loaderStr.contains("/data/app/") && !loaderStr.contains(HostInfo.packageName)) {
                    blamedModule = resolveModuleNameFromLoader(loaderStr)
                }
            }
        }

        if (blamedModule == "Unknown") {
            blamedModule = HostInfo.hostName
        }

        return Pair(blamedModule, sbAnalysis.toString())
    }

    private fun resolveModuleNameFromLoader(loaderStr: String): String {
        val regex = Regex("/data/app/(?:~~[^/]+/)?([^/-]+)-")
        val match = regex.find(loaderStr)
        val packageName = match?.groupValues?.get(1)

        if (packageName != null) {
            if (moduleNameCache.containsKey(packageName)) {
                return moduleNameCache[packageName]!!
            }
            val appName = getAppName(packageName)
            moduleNameCache[packageName] = appName
            return appName
        }
        return "Unknown Module"
    }

    private fun getAppName(packageName: String): String {
        return try {
            val pm = HostInfo.hostContext.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val label = pm.getApplicationLabel(appInfo).toString()
            label.ifEmpty { packageName }
        } catch (_: Exception) {
            packageName
        }
    }

    /** 只留主线程栈：全量线程栈一份就 200KB+，崩溃时几乎不看 */
    private fun writeJavaThreads(dir: File) {
        val sb = StringBuilder()
        val main = Thread.getAllStackTraces().entries.firstOrNull { it.key.name == "main" }
        if (main != null) {
            sb.append("Thread:${main.key.name} (State: ${main.key.state})\n")
            main.value.forEach { sb.append(" at $it\n") }
        }
        writeFile(dir, "java_threads", sb.toString())
    }

    private fun writeFile(dir: File, name: String, content: String) {
        FileUtils.writeText(File(dir, name), content)
    }

    private fun getStackTraceString(e: Throwable): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        e.printStackTrace(pw)
        return sw.toString()
    }

    private fun copySystemFile(srcPath: String, destFile: File) {
        try {
            val srcFile = File(srcPath)
            if (srcFile.canRead()) {
                val text = srcFile.readText()
                FileUtils.writeText(destFile, text)
            }
        } catch (_: Exception) {
        }
    }
}