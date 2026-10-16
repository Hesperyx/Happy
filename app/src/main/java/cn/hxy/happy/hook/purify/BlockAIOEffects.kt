package cn.hxy.happy.hook.purify

import cn.hxy.happy.annotation.HookCategory
import cn.hxy.happy.annotation.HookItemAnnotation
import cn.hxy.happy.hook.base.BaseSwitchHookItem
import cn.hxy.happy.utils.hook.returnConstant
import cn.hxy.happy.utils.qq.HostInfo
import cn.hxy.happy.utils.reflect.findMethods
import cn.hxy.happy.utils.reflect.toClass

@HookItemAnnotation(
    "屏蔽AIO动画特效",
    "屏蔽聊天界面中的关键词彩蛋、表情连击、超级表情等全屏动画",
    HookCategory.PURIFY
)
object BlockAIOEffects : BaseSwitchHookItem() {

    override fun onInit() = HostInfo.isQQ

    override fun onHook() {
        "com.tencent.mobileqq.aio.animation.AIOAnimationContainer".toClass
            .findMethods {
                returnType = boolean
                paramTypes(int, int, objArr)
            }
            .forEach { it.returnConstant(this, true) }
    }
}