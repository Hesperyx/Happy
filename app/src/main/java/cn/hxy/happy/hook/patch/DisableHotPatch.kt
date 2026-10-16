package cn.hxy.happy.hook.patch

import cn.hxy.happy.annotation.HookCategory
import cn.hxy.happy.annotation.HookItemAnnotation
import cn.hxy.happy.hook.base.BaseSwitchHookItem
import cn.hxy.happy.utils.hook.doNothing
import cn.hxy.happy.utils.reflect.clazz
import cn.hxy.happy.utils.reflect.findMethod
import cn.hxy.happy.utils.reflect.toClass

@HookItemAnnotation(
    "屏蔽热更新",
    "禁用宿主云控热补丁/热更新",
    HookCategory.OTHER,
    "All"
)
object DisableHotPatch : BaseSwitchHookItem() {

    override val isNeedRestart: Boolean = true

    override fun onHook() {

        "com.tencent.rfix.lib.download.PatchDownloadTask".clazz
            ?.getDeclaredMethod("run")
            ?.doNothing(this)

        val configClass = "com.tencent.rfix.lib.config.PatchConfig".toClass
        "com.tencent.rfix.lib.engine.PatchEngineBase".clazz
            ?.findMethod {
                returnType = void
                paramTypes(String::class.java, configClass)
            }
            ?.doNothing(this)

        "com.tencent.mobileqq.msf.core.net.patch.PatchReporter".clazz
            ?.declaredMethods
            ?.filter {
                it.name.startsWith("report") && it.returnType == Void.TYPE
            }
            ?.forEach { it.doNothing(this) }


    }


}