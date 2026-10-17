package cn.hxy.happy.hook.beauty

import cn.hxy.happy.annotation.HookCategory
import cn.hxy.happy.annotation.HookItemAnnotation
import cn.hxy.happy.hook.base.BaseSwitchHookItem
import cn.hxy.happy.utils.hook.hookBefore
import cn.hxy.happy.utils.qq.HostInfo
import cn.hxy.happy.utils.reflect.clazz
import cn.hxy.happy.utils.reflect.findMethodOrNull

@HookItemAnnotation(
    "转移设置页入口",
    "将抽屉设置页面入口移动到下方我的Tab页面",
    HookCategory.BEAUTY,
    ""
)
object SettingMeTab : BaseSwitchHookItem() {

    private const val MIN_VERSION: Long = 10068L

    private const val DRAWER_API_IMPL = "com.tencent.mobileqq.api.impl.DrawerApiImpl"

    override fun onHook() {
        if (HostInfo.versionCode < MIN_VERSION) return

        DRAWER_API_IMPL.clazz?.let { drawerApiImpl ->
            drawerApiImpl.findMethodOrNull {
                name = "needUsedSettingMeTab"
                returnType = boolean
                paramCount = 0
            }?.hookBefore(this) { param ->
                param.result = true
            }
        }
    }
}
