package cn.hxy.happy.hook.msg

import com.tencent.mobileqq.aio.msglist.holder.component.ptt.AIOPttContentComponent
import cn.hxy.happy.annotation.HookCategory
import cn.hxy.happy.annotation.HookItemAnnotation
import cn.hxy.happy.hook.base.BaseSwitchHookItem
import cn.hxy.happy.utils.hook.returnConstant
import cn.hxy.happy.utils.qq.HostInfo
import cn.hxy.happy.utils.reflect.findMethod

@HookItemAnnotation(
    "语音消息自动转文本",
    "收到语音消息时自动转换为文字显示",
    HookCategory.MSG
)
object AutoSpeechToText : BaseSwitchHookItem() {

    override fun onInit() = HostInfo.isQQ

    override fun onHook() {
        AIOPttContentComponent::class.java.findMethod {
            returnType = boolean
            paramTypes(boolean)
        }.returnConstant(this, true)
    }
}