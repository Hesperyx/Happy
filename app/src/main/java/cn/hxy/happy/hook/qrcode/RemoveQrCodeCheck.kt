package cn.hxy.happy.hook.qrcode

import com.tencent.open.agent.QrAgentLoginManager
import cn.hxy.happy.annotation.HookCategory
import cn.hxy.happy.annotation.HookItemAnnotation
import cn.hxy.happy.hook.base.BaseSwitchHookItem
import cn.hxy.happy.utils.hook.replaceFirstParam
import cn.hxy.happy.utils.reflect.findMethod
import cn.hxy.happy.utils.reflect.findMethodOrNull
import java.lang.reflect.Method

@HookItemAnnotation(
    "解除扫码限制",
    "解除长按识别或从相册中扫描二维码时的风险检查",
    HookCategory.OTHER
)
object RemoveQrCodeCheck : BaseSwitchHookItem() {

    private lateinit var check: Method

    override fun onInit(): Boolean {

        val manager = QrAgentLoginManager::class.java

        check = manager.findMethodOrNull {
            returnType = void
            paramTypes(boolean, string, bundle)
        } ?: manager.findMethod {
            returnType = void
            paramTypes(manager, boolean, string, bundle)
        }
        return super.onInit()
    }

    override fun onHook() {
        check.replaceFirstParam(false, this)
    }
}