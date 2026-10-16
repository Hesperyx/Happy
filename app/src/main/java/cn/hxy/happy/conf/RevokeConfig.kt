package cn.hxy.happy.conf

import kotlinx.serialization.Serializable

@Serializable
data class RevokeConfig(
    /** 撤回提示的文字颜色，默认 #FF0000 */
    val textColor: Int = 0xFFFF0000.toInt(),
    /** 撤回提示的背景颜色，默认 #DEE6F2 */
    val bgColor: Int = 0xFFDEE6F2.toInt(),
    /** 文字距提示背景顶部的距离（dp），默认 10 */
    val topMargin: String = "10"
)
