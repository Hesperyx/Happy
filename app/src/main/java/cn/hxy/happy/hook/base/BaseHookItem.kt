package cn.hxy.happy.hook.base

import cn.hxy.happy.annotation.HookItemAnnotation
import cn.hxy.happy.utils.qq.HostInfo
import cn.hxy.happy.utils.reflect.TAG

abstract class BaseHookItem {

    val name: String = TAG

    open var isEnable = true

    protected val annotation: HookItemAnnotation? by lazy {
        this::class.java.getAnnotation(HookItemAnnotation::class.java)
    }

    fun isInTargetProcess(): Boolean {
        val target = annotation?.process ?: return false
        if (target == "All") return true
        val currentProcess = HostInfo.processName
        return currentProcess == "${HostInfo.packageName}$target"
    }


}