package cn.hxy.happy.hook.purify

import com.tencent.mobileqq.profilecard.activity.FriendProfileCardActivity
import cn.hxy.happy.annotation.HookCategory
import cn.hxy.happy.annotation.HookItemAnnotation
import cn.hxy.happy.hook.base.BaseSwitchHookItem
import cn.hxy.happy.utils.hook.doNothing
import cn.hxy.happy.utils.qq.HostInfo

@HookItemAnnotation(
    "屏蔽DIY名片",
    "屏蔽查看好友页面自定义的名片",
    HookCategory.PURIFY
)
object RemoveDIYCard : BaseSwitchHookItem() {

    override fun onInit() = HostInfo.isQQ
    
    override fun onHook() {
        FriendProfileCardActivity::class.java
            .getDeclaredMethod("handleSwitchVasCard")
            .doNothing(this)
    }

}