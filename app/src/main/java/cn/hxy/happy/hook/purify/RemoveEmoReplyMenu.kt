package cn.hxy.happy.hook.purify

import com.tencent.mobileqq.aio.msg.AIOMsgItem
import cn.hxy.happy.annotation.HookCategory
import cn.hxy.happy.annotation.HookItemAnnotation
import cn.hxy.happy.hook.base.BaseSwitchHookItem
import cn.hxy.happy.utils.hook.doNothing
import cn.hxy.happy.utils.qq.HostInfo
import cn.hxy.happy.utils.reflect.findMethod
import cn.hxy.happy.utils.reflect.toClass
import java.lang.reflect.Method

@HookItemAnnotation(
    "移除表情回应",
    "移除长按出现的表情回应菜单以及消息下方表情回应",
    HookCategory.PURIFY
)
object RemoveEmoReplyMenu : BaseSwitchHookItem() {

    private lateinit var handleUIState: Method

    private lateinit var getMenuView: Method

    override fun onInit(): Boolean {
        handleUIState = "com.tencent.mobileqq.aio.msglist.holder.component.msgtail.AIOGeneralMsgTailContentComponent".toClass
            .findMethod {
                returnType = void
                paramTypes(int, AIOMsgItem::class.java, list)
            }
        getMenuView = "com.tencent.qqnt.aio.api.impl.AIOEmoReplyMenuApiImpl".toClass
            .findMethod {
                name =
                    if (HostInfo.versionCode >= 9898) "getSeparateEmoReplyMenuView" else "getEmoReplyMenuView"
            }
        return super.onInit()
    }

    override fun onHook() {
        getMenuView.doNothing(this)
        handleUIState.doNothing(this)
    }

}