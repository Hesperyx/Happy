package cn.hxy.happy.hook.purify

import cn.hxy.happy.annotation.HookCategory
import cn.hxy.happy.annotation.HookItemAnnotation
import cn.hxy.happy.hook.base.BaseSwitchHookItem
import cn.hxy.happy.utils.hook.hookAfter
import cn.hxy.happy.utils.qq.HostInfo
import cn.hxy.happy.utils.qq.QQCurrentEnv
import cn.hxy.happy.utils.reflect.toClass

@HookItemAnnotation(
    "屏蔽QQ秀",
    "屏蔽新版QQ秀头像",
    HookCategory.PURIFY
)
object RemoveQQShow : BaseSwitchHookItem() {

    override fun onInit() = HostInfo.versionCode >= 11820

    override fun onHook() {

        "com.tencent.mobileqq.ai.avatar.api.impl.AIAvatarSwitchApiImpl".toClass
            .getDeclaredMethod(
                "hasAvatarUrlOrInfo",
                Long::class.java
            )
            .hookAfter(this) {
                if (it.args[0] != QQCurrentEnv.currentUin.toLong()) it.result = false
            }
    }
}