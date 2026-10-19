package cn.hxy.happy.utils.scheduler

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import com.tencent.mobileqq.msf.core.NetConnInfoCenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import cn.hxy.happy.BuildConfig
import cn.hxy.happy.common.ModuleScope
import cn.hxy.happy.utils.qq.HostInfo
import java.util.Calendar
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

object PrecisionScheduler {

    private const val ACTION_ALARM = "${BuildConfig.APPLICATION_ID}.action.PRECISION_ALARM"
    private const val WAKELOCK_TAG = "Happy:Scheduler"
    private const val PRE_WAKE_SECONDS = 120

    private val context: Context get() = HostInfo.hostContext

    /**
     * 协程作用域不能在 <clinit> 里创建。
     *
     * 真机日志里有 1400+ 条 `ExceptionInInitializerError: PrecisionScheduler.<clinit>`，
     * cause 是 `ClassLoader.loadClass returned null for kotlinx.coroutines.CoroutineScopeKt`：
     * 模块在 Application.onCreate 早期初始化时，类加载器偶尔还没就绪，静态初始化一旦失败，
     * 这个类在本进程内就永久不可用了（异常初始化错误会缓存），顺带让「自动续火 / 自动打卡」全挂。
     * 改成用到时再建，失败就下一次重试。
     */
    private var scope: CoroutineScope? = null

    private fun coroutineScope(): CoroutineScope? {
        scope?.let { return it }
        return runCatching { CoroutineScope(Dispatchers.IO + SupervisorJob()) }
            .getOrNull()
            ?.also { scope = it }
    }

    private val taskQueue = PriorityBlockingQueue<ScheduledTask>()

    private val isProcessing = AtomicBoolean(false)

    private var processingJob: Job? = null

    
    private val diffTime: Long 
        get() = NetConnInfoCenter.servetTimeSecondInterv * 1000
        
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_ALARM) {
                handleAlarmTrigger()
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun init() {
        try {
            val filter = IntentFilter(ACTION_ALARM)
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
        } catch (_: Exception) {
        }
    }

    fun currentServerTime(): Long = NetConnInfoCenter.getServerTimeMillis()

    fun getNextMidnight(): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = currentServerTime()
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }


    fun addTask(task: ScheduledTask) {
        if (task.targetTime < currentServerTime()) {
            return
        }

        taskQueue.removeIf { it.id == task.id }
        taskQueue.add(task)

        val head = taskQueue.peek()
        if (head != null && head.id == task.id && isProcessing.get()) {
            processingJob?.cancel()
        }

        scheduleNextAlarm()
    }

    @SuppressLint("MissingPermission")
    private fun scheduleNextAlarm() {
        val nextTask = taskQueue.peek() ?: return
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val targetLocalTime = nextTask.targetTime - diffTime
        val triggerTime = targetLocalTime - (PRE_WAKE_SECONDS * 1000)

        val intent = Intent(ACTION_ALARM)
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val pi = PendingIntent.getBroadcast(context, 10086, intent, flags)

        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pi)
        } catch (_: Exception) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pi)
        }
    }

    @Suppress("DEPRECATION")
    private fun handleAlarmTrigger() {
        if (!isProcessing.compareAndSet(false, true)) {
            return
        }

        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, WAKELOCK_TAG)
        wakeLock.acquire(10 * 60 * 1000L)

        val coroutineScope = coroutineScope()
        if (coroutineScope == null) {
            // 协程类还没加载出来（早期初始化偶发）：释放标志，等下次闹钟再试，
            // 不再像原来那样让整个类初始化失败、功能永久失效
            isProcessing.set(false)
            if (wakeLock.isHeld) wakeLock.release()
            return
        }

        processingJob = coroutineScope.launch {
            try {

                while (isActive) {
                    val nextTask = taskQueue.peek()

                    if (nextTask == null || (nextTask.targetTime - currentServerTime() > (PRE_WAKE_SECONDS + 10) * 1000)) {
                        break
                    }

                    val targetTime = nextTask.targetTime

                    while (targetTime - currentServerTime() > 2000) {
                        if (taskQueue.peek()?.id != nextTask.id) break
                        delay(100)
                    }

                    if (taskQueue.peek()?.id != nextTask.id) continue

                    @Suppress("ControlFlowWithEmptyBody")
                    while (currentServerTime() < targetTime) {
                    }

                    taskQueue.poll()
                    ModuleScope.launchIO(nextTask.id) {
                        nextTask.action()
                    }
                }
            } finally {
                if (wakeLock.isHeld) wakeLock.release()
                isProcessing.set(false)
                scheduleNextAlarm()
            }
        }
    }
}