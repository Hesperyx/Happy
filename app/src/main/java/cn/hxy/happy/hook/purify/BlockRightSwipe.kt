package cn.hxy.happy.hook.purify

import com.tencent.aio.frame.drawer.DrawerFrameViewGroup
import cn.hxy.happy.annotation.HookCategory
import cn.hxy.happy.annotation.HookItemAnnotation
import cn.hxy.happy.hook.base.BaseSwitchHookItem
import cn.hxy.happy.utils.hook.replaceFirstParam
import cn.hxy.happy.utils.qq.HostInfo
import cn.hxy.happy.utils.reflect.findMethod
import java.lang.reflect.Method

@HookItemAnnotation(
    "屏蔽聊天右滑",
    "屏蔽聊天界面右滑显示的界面",
    HookCategory.PURIFY
)
object BlockRightSwipe : BaseSwitchHookItem() {

    private lateinit var index: Method

    override fun onInit(): Boolean {
        if (HostInfo.isTIM) return false
        index = DrawerFrameViewGroup::class.java
            .findMethod {
                visibility = private
                returnType = void
                paramTypes(int)
            }
        return super.onInit()
    }

    override fun onHook() {
        index.replaceFirstParam(0, this)
    }

}