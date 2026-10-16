package cn.hxy.happy.utils.dexkit

import android.content.Context
import android.os.Bundle
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.tencent.mobileqq.activity.SplashActivity
import cn.hxy.happy.common.ModuleScope
import cn.hxy.happy.generated.HookRegistry
import cn.hxy.happy.ui.components.dialogs.CenterDialogContainerNoButton
import cn.hxy.happy.ui.core.compatibility.HappyCenterDialog
import cn.hxy.happy.ui.core.theme.HappyTheme
import cn.hxy.happy.utils.hook.hookAfter
import cn.hxy.happy.utils.json.MessageTool
import cn.hxy.happy.utils.log.LogUtils
import cn.hxy.happy.utils.qq.AppRestartUtils
import cn.hxy.happy.utils.qq.HostInfo
import cn.hxy.happy.utils.qq.MsgTool
import cn.hxy.happy.utils.reflect.ClassUtils
import cn.hxy.happy.utils.reflect.TAG
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.base.BaseMatcher
import org.luckypray.dexkit.wrap.DexClass
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.reflect.Method

object DexKitFinder {

    private var progressText by mutableStateOf("准备开始查找...")

    fun doFind() {
        System.loadLibrary("dexkit")
        showFindDialog()
    }

    @Suppress("DEPRECATION")
    private fun showFindDialog() {
        SplashActivity::class.java
            .getDeclaredMethod("doOnCreate", Bundle::class.java)
            .hookAfter {
                val context = it.thisObject as Context

                HappyCenterDialog(context) {
                    CenterDialogContainerNoButton(title = "查找方法中") {
                        val colors = HappyTheme.colors
                        Text(
                            text = progressText,
                            fontSize = 15.sp,
                            color = colors.textSecondary,
                            lineHeight = 22.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }.apply {
                    setCanceledOnTouchOutside(false)
                    setCancelable(false)
                    show()
                }

                startFind(context)
            }
    }

    private fun startFind(context: Context) {
        ModuleScope.launchIO(TAG) {
            val tasks = HookRegistry.hookItems.filterIsInstance<DexKitTask>().toMutableList()
            tasks.apply {
                add(MsgTool)
                add(MessageTool)
            }

            val sourceDir = context.applicationInfo.sourceDir
            DexKitBridge.create(sourceDir).use { bridge ->
                tasks.forEach { task ->
                    runCatching {
                        task.getQueryMap().forEach { (name, query) ->
                            val tip = "${task.TAG}->$name"
                            progressText = tip
                            when (query) {
                                is FindClass -> bridge.findClass(query).singleOrNull()?.let {
                                    DexKitCache.cacheMap[tip] = it.descriptor
                                }

                                is FindMethod -> bridge.findMethod(query).singleOrNull()?.let {
                                    DexKitCache.cacheMap[tip] = it.descriptor
                                }
                            }
                        }
                    }.onFailure { LogUtils.e(task.TAG, it) }
                }
            }
            progressText = "查找完成，保存并重启应用"
            DexKitCache.saveCache()
            ModuleScope.launchMain {
                AppRestartUtils.restartApp(context)
            }
        }
    }

    /**
     * 现场执行单个查询（缓存缺项时使用）
     * 命中即返回 descriptor，并已回写缓存
     */
    fun findLive(query: BaseMatcher): String? {
        val context = runCatching { HostInfo.hostContext }.getOrNull() ?: return null
        System.loadLibrary("dexkit")
        return DexKitBridge.create(context.applicationInfo.sourceDir).use { bridge ->
            when (query) {
                is FindClass -> bridge.findClass(query).singleOrNull()?.descriptor
                is FindMethod -> bridge.findMethod(query).singleOrNull()?.descriptor
                else -> null
            }
        }
    }
}

interface DexKitTask {

    fun getQueryMap(): Map<String, BaseMatcher>

    /**
     * 缓存优先；缺项时现场查询并回写
     * （QQ 更新后缓存残缺时不再让功能静默失效）
     */
    fun requireDescriptor(name: String): String? {
        val key = "$TAG->$name"
        DexKitCache.cacheMap[key]?.let { return it }

        val query = getQueryMap()[name] ?: return null
        LogUtils.d("DexKit 缓存缺少 $key，现场查找")

        return runCatching { DexKitFinder.findLive(query) }
            .onFailure { LogUtils.e("DexKit 现场查找失败: $key", it) }
            .getOrNull()
            ?.also {
                DexKitCache.cacheMap[key] = it
                DexKitCache.saveCache()
            }
    }

    fun requireClass(name: String): Class<*> {
        val descriptor = requireDescriptor(name)
            ?: throw ClassNotFoundException("$TAG->$name")
        return DexClass(descriptor).getInstance(ClassUtils.hostClassLoader)
    }

    fun requireMethod(name: String): Method {
        val descriptor = requireDescriptor(name)
            ?: throw NoSuchMethodException("$TAG->$name")
        return DexMethod(descriptor).getMethodInstance(ClassUtils.hostClassLoader)
    }
}
