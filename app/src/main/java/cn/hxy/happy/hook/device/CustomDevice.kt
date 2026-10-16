package cn.hxy.happy.hook.device

import androidx.compose.runtime.Composable
import com.tencent.qmethod.pandoraex.monitor.DeviceInfoMonitor
import cn.hxy.happy.annotation.HookCategory
import cn.hxy.happy.annotation.HookItemAnnotation
import cn.hxy.happy.conf.DeviceConfig
import cn.hxy.happy.hook.base.BaseClickableHookItem
import cn.hxy.happy.ui.pages.configs.CustomDevicePage
import cn.hxy.happy.utils.hook.returnConstant

@HookItemAnnotation(
    "伪装设备在线状态",
    "点击设置机型，可用于设置在线状态机型（包含文字可能无效）",
    HookCategory.DEVICE,
    "All"
)
object CustomDevice : BaseClickableHookItem<DeviceConfig>(DeviceConfig.serializer()) {

    override val isNeedRestart: Boolean = true

    override val defaultConfig: DeviceConfig = DeviceConfig()

    override fun onHook() {
        DeviceInfoMonitor::class.java
            .getDeclaredMethod("getModel")
            .returnConstant(this, config.fakeModel)
    }

    @Composable
    override fun ConfigContent(onDismiss: () -> Unit) {
        CustomDevicePage(
            currentConfig = config,
            onSave = ::updateConfig,
            onDismiss = onDismiss
        )
    }
}
