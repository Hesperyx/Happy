package cn.hxy.happy.conf

import kotlinx.serialization.Serializable

@Serializable
data class DeviceConfig(
    val fakeModel: String = ""
)
