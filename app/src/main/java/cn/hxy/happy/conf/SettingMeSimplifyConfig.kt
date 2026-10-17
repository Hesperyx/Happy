package cn.hxy.happy.conf

import kotlinx.serialization.Serializable

/**
 * 侧滑栏精简（对应 TCQT 的 multiIntOption 位集）。
 */
@Serializable
data class SettingMeSimplifyConfig(
    /** 需要隐藏的条目 id，取值见 [SettingMeEntry]。 */
    val hiddenIds: Set<Int> = emptySet()
)

/**
 * 侧滑栏可精简条目。
 *
 * [hostId] 是宿主条目 bean 里那个 `d_xxx` 字符串，按值匹配，因此对条目类改名不敏感。
 * 建议顺序即设置页展示顺序。
 */
enum class SettingMeEntry(val id: Int, val hostId: String, val label: String) {
    ALBUM(0, "d_album", "相册"),
    FAVORITE(1, "d_favorite", "收藏"),
    DOCUMENT(2, "d_document", "文件"),
    QQ_WALLET(3, "d_qqwallet", "钱包"),
    VIP_IDENTITY(4, "d_vip_identity", "会员中心"),
    DECORATION(5, "d_decoration", "个性装扮"),
    VIP_CARD(6, "d_vip_card", "免流量"),
}
