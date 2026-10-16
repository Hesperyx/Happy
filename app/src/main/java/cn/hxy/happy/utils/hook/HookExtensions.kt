package cn.hxy.happy.utils.hook

import cn.hxy.happy.hook.base.BaseHookItem
import cn.hxy.happy.utils.log.LogUtils
import cn.hxy.happy.utils.reflect.isCompatibleWith
import java.lang.reflect.Member
import java.lang.reflect.Method

import cn.hxy.happy.loader.hookapi.HookParam
import cn.hxy.happy.loader.hookapi.Chain
import cn.hxy.happy.loader.hookapi.Unhook
import cn.hxy.happy.loader.hookapi.HookEngineManager
import cn.hxy.happy.utils.reflect.callOriginal

fun Member.hookBefore(
    owner: BaseHookItem? = null,
    block: (HookParam) -> Unit
): Unhook {
    return HookEngineManager.engine.hookBefore(this) { param ->
        if (owner?.isEnable == false) return@hookBefore
        runCatching { block(param) }.onFailure { LogUtils.e("HookBeforeError: ${owner?.name ?: "Unknown"}", it) }
    }
}
fun Member.hookAfter(
    owner: BaseHookItem? = null,
    block: (HookParam) -> Unit
): Unhook {
    return HookEngineManager.engine.hookAfter(this) { param ->
        if (owner?.isEnable == false) return@hookAfter
        runCatching { block(param) }.onFailure { LogUtils.e("HookAfterError: ${owner?.name ?: "Unknown"}", it) }
    }
}

fun Member.hookReplace(
    owner: BaseHookItem? = null,
    block: (Chain) -> Any?
): Unhook {
    return HookEngineManager.engine.hookReplace(this) { chain ->
        try {
            if (owner?.isEnable == false) {
                chain.proceed()
            } else {
                block(chain)
            }
        } catch (t: Throwable) {
            LogUtils.e("HookReplaceError: ${owner?.name ?: "Unknown"}", t)
            chain.proceed() 
        }
    }
}

fun Chain.invokeOriginal(args: Array<Any?> = this.args): Any? {
    return method.callOriginal(thisObject, *args)
}

fun Member.returnConstant(owner: BaseHookItem? = null, constant: Any?): Unhook {
    return this.hookReplace(owner) { constant }
}

fun Member.doNothing(owner: BaseHookItem? = null): Unhook {
    return this.hookReplace(owner) { null }
}

inline fun <reified T> HookParam.getFirstArg(): T? {
    return args.find { it is T } as? T
}

inline fun <reified T> HookParam.getArgByType(): T? {
    val methodObj = this.method as? Method ?: return null
    val index = methodObj.parameterTypes.indexOfFirst { T::class.java.isAssignableFrom(it) }
    if (index != -1 && index < args.size) {
        return args[index] as? T
    }
    return null
}

inline fun <reified T> Member.replaceFirstParam(
    newValue: T,
    owner: BaseHookItem? = null
): Unhook {
    return this.hookBefore(owner) { param ->
        val method = param.method as? Method ?: return@hookBefore
        val index = method.parameterTypes.indexOfFirst { it.isCompatibleWith(T::class.java) }
        if (index != -1 && index < param.args.size) {
            param.args[index] = newValue
        }
    }
}

fun Member.replaceParam(
    index: Int,
    value: Any?,
    owner: BaseHookItem? = null
): Unhook {
    return this.hookBefore(owner) { param ->
        if (index in param.args.indices) {
            param.args[index] = value
        }
    }
}