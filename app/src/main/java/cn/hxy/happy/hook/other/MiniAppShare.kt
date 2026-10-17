package cn.hxy.happy.hook.other

import android.os.Bundle
import cn.hxy.happy.annotation.HookCategory
import cn.hxy.happy.annotation.HookItemAnnotation
import cn.hxy.happy.hook.base.BaseSwitchHookItem
import cn.hxy.happy.utils.hook.hookBefore
import cn.hxy.happy.utils.reflect.MethodSearcher
import cn.hxy.happy.utils.reflect.clazz
import cn.hxy.happy.utils.reflect.findMethodOrNull
import java.lang.reflect.Method

@HookItemAnnotation(
    "修改小程序分享行为",
    "在小程序中调用分享时，如果取消分享，小程序也会收到分享成功的回调。",
    HookCategory.OTHER,
    "All"
)
object MiniAppShare : BaseSwitchHookItem() {

    private const val EIPC_CLIENT_CLASS = "eipc.EIPCClient"
    private const val EIPC_CALLBACK_CLASS = "eipc.EIPCResultCallback"
    private const val FORWARD_OPTION_CLASS = "com.tencent.mobileqq.forward.ForwardBaseOption"

    private const val MINI_MSG_MODULE = "MiniMsgIPCServer"
    private const val CMD_SHARE_FAIL = "cmd_mini_share_fail"
    private const val CMD_SHARE_SUC = "cmd_mini_share_suc"
    private const val CMD_REPORT_EVENT = "cmd_mini_report_event"
    private const val REPORT_EVENT_RESULT_KEY = "key_mini_report_event_reserves2"

    override fun onHook() {
        hookEipcCallServer()
        hookEndForwardCallback()
    }

    private fun hookEipcCallServer() {
        val callbackClass = EIPC_CALLBACK_CLASS.clazz ?: return
        val clientClass = EIPC_CLIENT_CLASS.clazz ?: return

        findMethodInHierarchy(clientClass) {
            name = "callServer"
            paramCount = 4
            paramTypes = arrayOf<Class<*>?>(
                String::class.java,
                String::class.java,
                Bundle::class.java,
                callbackClass
            )
        }?.hookBefore(this) { param ->
            val module = param.args.getOrNull(0) as? String ?: return@hookBefore
            if (module != MINI_MSG_MODULE) return@hookBefore

            when (param.args.getOrNull(1) as? String) {
                CMD_SHARE_FAIL -> param.args[1] = CMD_SHARE_SUC

                CMD_REPORT_EVENT -> {
                    val bundle = param.args.getOrNull(2) as? Bundle ?: return@hookBefore
                    if (bundle.getString(REPORT_EVENT_RESULT_KEY) == "fail") {
                        bundle.putString(REPORT_EVENT_RESULT_KEY, "success")
                        param.args[2] = bundle
                    }
                }
            }
        }
    }

    private fun hookEndForwardCallback() {
        val forwardBaseOption = FORWARD_OPTION_CLASS.clazz ?: return

        findMethodInHierarchy(forwardBaseOption) {
            name = "endForwardCallback"
            paramCount = 1
            paramTypes = arrayOf<Class<*>?>(Boolean::class.javaPrimitiveType)
        }?.hookBefore(this) { param ->
            param.args[0] = true
        }
    }

    private fun findMethodInHierarchy(
        clazz: Class<*>,
        block: MethodSearcher.() -> Unit
    ): Method? {
        return generateSequence(clazz) { it.superclass }
            .toList()
            .firstNotNullOfOrNull { cls -> cls.findMethodOrNull(block) }
    }
}
