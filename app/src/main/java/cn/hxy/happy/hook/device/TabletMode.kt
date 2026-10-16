package cn.hxy.happy.hook.device

import com.tencent.common.config.pad.DeviceType
import com.tencent.common.config.pad.PadUtil
import cn.hxy.happy.annotation.HookCategory
import cn.hxy.happy.annotation.HookItemAnnotation
import cn.hxy.happy.hook.base.BaseSwitchHookItem
import cn.hxy.happy.utils.hook.returnConstant
import cn.hxy.happy.utils.reflect.findMethod
import java.lang.reflect.Method

@HookItemAnnotation(
    "平板模式",
    "让QQ将当前设备识别为平板",
    HookCategory.DEVICE,
    ":MSF"
)
object TabletMode : BaseSwitchHookItem() {

    private lateinit var getDeviceType: Method

    override val isNeedRestart: Boolean = true

    override fun onInit(): Boolean {
        getDeviceType = PadUtil::class.java
            .findMethod {
                returnType = DeviceType::class.java
            }
        return super.onInit()
    }

    override fun onHook() {
        getDeviceType.returnConstant(this, DeviceType.TABLET)
    }
}