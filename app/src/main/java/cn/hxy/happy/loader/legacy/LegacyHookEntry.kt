package cn.hxy.happy.loader.legacy

import androidx.annotation.Keep
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage
import cn.hxy.happy.BuildConfig
import cn.hxy.happy.common.ModuleLoader
import cn.hxy.happy.loader.hookapi.HookEngineManager
import cn.hxy.happy.utils.qq.HostInfo

@Keep
class LegacyHookEntry : IXposedHookLoadPackage, IXposedHookZygoteInit {

    private var modulePath: String = ""

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        modulePath = startupParam.modulePath
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val packageName = lpparam.packageName

        if (packageName == BuildConfig.APPLICATION_ID) {
            LegacyHookStatusInit.init(lpparam.classLoader)
        }

        if (packageName == HostInfo.PACKAGE_NAME_QQ || packageName == HostInfo.PACKAGE_NAME_TIM) {
            
            if (HookEngineManager.isInitialized) return

            HookEngineManager.engine = LegacyHookEngine()
            
            ModuleLoader.initialize(
                lpparam.classLoader,
                modulePath,
                packageName,
                lpparam.processName
            )
        }
    }
}