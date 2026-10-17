package cn.hxy.happy.hook.other

import android.os.Bundle
import androidx.compose.runtime.Composable
import cn.hxy.happy.annotation.HookCategory
import cn.hxy.happy.annotation.HookItemAnnotation
import cn.hxy.happy.conf.FakePhoneConfig
import cn.hxy.happy.hook.base.BaseClickableHookItem
import cn.hxy.happy.ui.pages.configs.FakePhonePage
import cn.hxy.happy.utils.dexkit.DexKitTask
import cn.hxy.happy.utils.hook.hookBefore
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.base.BaseMatcher
import java.lang.reflect.Method

@HookItemAnnotation(
    "伪装手机号码",
    "伪装账号与安全设置页面中的手机号码。",
    HookCategory.OTHER,
    ""
)
object FakePhone : BaseClickableHookItem<FakePhoneConfig>(FakePhoneConfig.serializer()), DexKitTask {

    private const val QUERY_NAME = "fake_phone"
    private const val BIND_PHONE_STATUS = 5
    private const val PHONE_KEY = "phone"

    override val isNeedRestart: Boolean = true

    override val defaultConfig: FakePhoneConfig = FakePhoneConfig()

    private lateinit var queryAccountStatusMethod: Method

    override fun onInit(): Boolean {
        queryAccountStatusMethod = requireMethod(QUERY_NAME)
        return super.onInit()
    }

    override fun onHook() {
        queryAccountStatusMethod.hookBefore(this) { param ->
            val status = (param.args.getOrNull(0) as? Number)?.toInt() ?: return@hookBefore
            if (status != BIND_PHONE_STATUS) return@hookBefore

            val bundle = param.args.getOrNull(2) as? Bundle ?: return@hookBefore
            bundle.putString(PHONE_KEY, config.effectivePhone)
            param.args[2] = bundle
        }
    }

    @Composable
    override fun ConfigContent(onDismiss: () -> Unit) {
        FakePhonePage(
            currentConfig = config,
            onSave = ::updateConfig,
            onDismiss = onDismiss
        )
    }

    override fun getQueryMap(): Map<String, BaseMatcher> = mapOf(
        QUERY_NAME to FindMethod().apply {
            searchPackages("com.tencent.mobileqq.app")
            matcher {
                paramTypes = listOf("int", "boolean", "java.lang.Object")
                usingEqStrings("status", "wording", "target_desc", "target_name")
            }
        }
    )
}
