package cn.hxy.happy.hook.device

import android.app.Activity
import cn.hxy.happy.annotation.HookCategory
import cn.hxy.happy.annotation.HookItemAnnotation
import cn.hxy.happy.hook.base.BaseSwitchHookItem
import cn.hxy.happy.utils.hook.returnConstant
import cn.hxy.happy.utils.reflect.findMethod

@HookItemAnnotation(
    "伪装处于非多窗口模式",
    "解除分屏状态下扫码等功能的使用限制",
    HookCategory.DEVICE,
    "All"
)
object DisableSplitScreenLimit : BaseSwitchHookItem() {

    override fun onHook() {
        Activity::class.java
            .findMethod {
                name = "isInMultiWindowMode"
            }
            .returnConstant(this, false)
    }
}