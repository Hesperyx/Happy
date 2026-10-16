package cn.hxy.happy.hook.social

import com.tencent.mobileqq.data.Card
import com.tencent.mobileqq.profilecard.activity.FriendProfileCardActivity
import cn.hxy.happy.annotation.HookCategory
import cn.hxy.happy.annotation.HookItemAnnotation
import cn.hxy.happy.hook.base.BaseSwitchHookItem
import cn.hxy.happy.utils.hook.hookBefore
import cn.hxy.happy.utils.qq.HostInfo
import cn.hxy.happy.utils.reflect.findMethod
import cn.hxy.happy.utils.reflect.toClass

@HookItemAnnotation(
    "查看封号资料卡",
    "去除查看封禁账号资料卡的限制",
    HookCategory.SOCIAL
)
object CheckForbidCard : BaseSwitchHookItem() {

    override fun onHook() {
        val activity = if (HostInfo.isTIM)
            "com.tencent.mobileqq.profilecard.activity.TimFriendProfileCardActivity".toClass
        else FriendProfileCardActivity::class.java

        activity.findMethod {
                name = "onCardUpdate"
            }
            .hookBefore(this) { param ->
                val card = param.args[0] as Card
                card.forbidCode = 0
                card.isForbidAccount = false
            }

    }
}