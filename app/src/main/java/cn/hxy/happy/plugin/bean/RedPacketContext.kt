package cn.hxy.happy.plugin.bean

import cn.hxy.happy.conf.RedPacketConfig

data class RedPacketContext(
    @JvmField val msgData: MsgData,
    @JvmField val listId: String,
    @JvmField val authKey: String,
    @JvmField val channel: Int,
    @JvmField val title: String,
    @JvmField val isAuto: Boolean,
    @JvmField val config: RedPacketConfig,
)
