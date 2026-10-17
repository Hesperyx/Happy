package cn.hxy.happy.conf

import kotlinx.serialization.Serializable

/**
 * 账号与安全页面手机号伪装配置。
 */
@Serializable
data class FakePhoneConfig(
    val phone: String = ""
) {
    /** 留空时使用默认占位号码。 */
    val effectivePhone: String
        get() = phone.ifEmpty { DEFAULT_PHONE }

    companion object {
        const val DEFAULT_PHONE = "1145141919810"
    }
}
