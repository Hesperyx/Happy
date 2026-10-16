package cn.hxy.happy.conf

import kotlinx.serialization.Serializable

@Serializable
data class SummaryConfig(
    val key: String = "",
    val summaryOrUrl: String = ""
)
