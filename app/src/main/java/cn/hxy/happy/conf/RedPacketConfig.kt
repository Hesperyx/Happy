package cn.hxy.happy.conf

import kotlinx.serialization.Serializable

@Serializable
data class RedPacketConfig(
    val delay: Long = 0L,
    val minAverage: Int = 0,
    val keywords: Set<String> = emptySet(),
    val autoReply: List<String> = emptyList(),
    val whiteList: Set<String> = emptySet(),
    val targetTypes: Set<Int> = setOf(1, 32, 1024, 65536),
    /** 抢到语音红包后在群里发送合成的口令语音 */
    val sendVoiceAfterGrab: Boolean = false,
)
