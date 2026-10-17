package cn.hxy.happy.conf

import kotlinx.serialization.Serializable

/**
 * 通知渠道管理（入口型功能，仅承载一个可序列化的占位配置）。
 */
@Serializable
data class NotificationChannelConfig(
    val reserved: Boolean = false
)
