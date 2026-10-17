package cn.hxy.happy.conf

import kotlinx.serialization.Serializable

/**
 * 一个主页背景图接口：能直接返回图片（带 302 也行）的地址。
 * 列表为空表示主页不出背景图，`enabled=false` 表示这一条暂时不参与取图。
 */
@Serializable
data class BackdropApi(
    val url: String = "",
    val enabled: Boolean = true,
    val label: String = ""
) {
    /** 没起名字时退回主机名，列表里不至于只剩一长串路径。 */
    val displayLabel: String
        get() = label.ifBlank {
            url.substringAfter("://", url).substringBefore('/').ifBlank { url }
        }
}

/** 主页背景图接口表，存于 App 自己的 SharedPreferences（与宿主无关）。 */
@Serializable
data class BackdropApiConfig(
    val apis: List<BackdropApi> = emptyList()
) {

    val enabledApis: List<BackdropApi>
        get() = apis.filter { it.enabled && it.url.isNotBlank() }

    fun isSameAsDefault(): Boolean =
        apis.size == DEFAULT_APIS.size && apis.indices.all { index ->
            val a = apis[index]
            val d = DEFAULT_APIS[index]
            a.url == d.url && a.enabled == d.enabled && a.label == d.label
        }

    companion object {

        /**
         * 默认接口，按「铺满手机屏幕后还清不清晰」排序：越靠前分辨率越大。
         * 手机屏幕普遍是 1272×2800 这种比例，横图只要本身够大，居中裁一条竖的仍然清晰。
         */
        val DEFAULT_APIS = listOf(
            BackdropApi("https://t.alcy.cc/pc", true, "樱道 · 二次元横图（3000px 以上）"),
            BackdropApi("https://t.alcy.cc/mp", true, "樱道 · 二次元竖图（约 1810×2560）"),
            BackdropApi("https://picsum.photos/1440/3120", true, "picsum · 真实摄影（固定尺寸）"),
            BackdropApi("https://www.loliapi.com/acg/pe/", true, "loliapi · 二次元竖图"),
            BackdropApi("https://www.loliapi.com/acg/pc/", true, "loliapi · 二次元横图（1920×1080）")
        )

        fun default(): BackdropApiConfig = BackdropApiConfig(DEFAULT_APIS)
    }
}
