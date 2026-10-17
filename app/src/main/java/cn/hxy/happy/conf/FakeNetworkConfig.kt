package cn.hxy.happy.conf

import kotlinx.serialization.Serializable

/**
 * 网络状态伪装配置（对应 TCQT 的 intOption mode）。
 */
@Serializable
data class FakeNetworkConfig(
    /** 1=WIFI，2=5G，3=4G */
    val mode: Int = MODE_WIFI
) {
    companion object {
        const val MODE_WIFI = 1
        const val MODE_5G = 2
        const val MODE_4G = 3

        val LABELS = linkedMapOf(
            MODE_WIFI to "WIFI",
            MODE_5G to "5G",
            MODE_4G to "4G"
        )
    }
}
