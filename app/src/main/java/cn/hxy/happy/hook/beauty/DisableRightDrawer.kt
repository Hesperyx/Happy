package cn.hxy.happy.hook.beauty

import cn.hxy.happy.annotation.HookCategory
import cn.hxy.happy.annotation.HookItemAnnotation
import cn.hxy.happy.hook.base.BaseSwitchHookItem
import cn.hxy.happy.utils.hook.hookBefore
import cn.hxy.happy.utils.reflect.Visibility
import cn.hxy.happy.utils.reflect.clazz
import cn.hxy.happy.utils.reflect.findMethodOrNull

@HookItemAnnotation(
    "侧滑栏精简",
    "屏蔽在聊天界面向左滑动呼出右侧面板（如群应用、亲密关系等）",
    HookCategory.BEAUTY,
    ""
)
object DisableRightDrawer : BaseSwitchHookItem() {

    private const val DRAWER_FRAME_VIEW_GROUP = "com.tencent.aio.frame.drawer.DrawerFrameViewGroup"

    override fun onHook() {
        DRAWER_FRAME_VIEW_GROUP.clazz?.let { drawerFrameViewGroup ->
            drawerFrameViewGroup.findMethodOrNull {
                visibility = Visibility.PRIVATE
                paramCount = 2
                paramTypes = arrayOf<Class<*>?>(Float::class.java, String::class.java)
            }?.hookBefore(this) { param ->
                val dx = param.args[0] as? Float ?: return@hookBefore
                if (dx < 0.0f) {
                    param.result = false
                }
            }
        }
    }
}
