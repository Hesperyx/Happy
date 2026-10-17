package cn.hxy.happy.ui.pages.home

import android.content.Context
import androidx.core.content.edit
import cn.hxy.happy.utils.net.HttpUtils
import org.json.JSONObject

/**
 * 一言的句子类型，对应 https://v1.hitokoto.cn 的 c 参数。
 * 顺序就是主页「类型」面板里的排列顺序。
 */
internal enum class HitokotoType(val code: String, val label: String) {
    ANIME("a", "动画"),
    COMIC("b", "漫画"),
    GAME("c", "游戏"),
    LITERATURE("d", "文学"),
    ORIGINAL("e", "原创"),
    INTERNET("f", "网络"),
    OTHER("g", "其他"),
    MOVIE("h", "影视"),
    POEM("i", "诗词"),
    NETEASE("j", "网易云"),
    PHILOSOPHY("k", "哲学"),
    JOKE("l", "抖机灵");

    companion object {
        /** 默认勾选：动画 / 文学 / 诗词 / 网络，和主页调性最接近的几类。 */
        val DEFAULT_CODES: Set<String> = setOf("a", "d", "i", "f")
    }
}

/** 主页展示的一句一言。 */
internal data class HitokotoQuote(
    val text: String,
    val from: String = "",
    val fromWho: String = ""
) {
    /** 出处：作者和书名都有就拼起来，都没有就退回「一言」。 */
    val source: String
        get() = listOf(fromWho, from)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
            .ifBlank { "一言" }
}

/**
 * 主页的一言也属于 App 自己的设置（跟背景接口一样不进宿主进程），
 * 存在 App 自己的 SharedPreferences：类型选择 + 最近一句缓存。
 */
internal object HitokotoStore {

    private const val PREFS_NAME = "happy_app_settings"
    private const val KEY_TYPES = "hitokoto_types"
    private const val KEY_TEXT = "hitokoto_text"
    private const val KEY_FROM = "hitokoto_from"
    private const val KEY_FROM_WHO = "hitokoto_from_who"

    fun loadTypes(context: Context): Set<String> {
        val raw = prefs(context).getString(KEY_TYPES, null) ?: return HitokotoType.DEFAULT_CODES
        val codes = raw.split(',').filter { it.isNotBlank() }.toSet()
        return codes.ifEmpty { HitokotoType.DEFAULT_CODES }
    }

    fun saveTypes(context: Context, codes: Set<String>) {
        prefs(context).edit { putString(KEY_TYPES, codes.joinToString(",")) }
    }

    fun loadQuote(context: Context): HitokotoQuote? {
        val text = prefs(context).getString(KEY_TEXT, null)?.takeIf { it.isNotBlank() } ?: return null
        return HitokotoQuote(
            text = text,
            from = prefs(context).getString(KEY_FROM, null).orEmpty(),
            fromWho = prefs(context).getString(KEY_FROM_WHO, null).orEmpty()
        )
    }

    fun saveQuote(context: Context, quote: HitokotoQuote) {
        prefs(context).edit {
            putString(KEY_TEXT, quote.text)
            putString(KEY_FROM, quote.from)
            putString(KEY_FROM_WHO, quote.fromWho)
        }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

/** 抓一句一言；拿不到就返回 null，调用方继续显示上一句。 */
internal object HitokotoLoader {

    private const val ENDPOINT = "https://v1.hitokoto.cn/"

    suspend fun fetch(types: Set<String>): HitokotoQuote? {
        val codes = types.ifEmpty { HitokotoType.DEFAULT_CODES }
        val url = buildString {
            append(ENDPOINT)
            append("?")
            codes.forEach { append("c=").append(it).append("&") }
            append("max_length=40")
            append("&encode=json&charset=utf-8")
        }
        // 接口挂了/断网都返回空串，按「这次没拿到」处理，不弹错
        val body = HttpUtils.getSuspend(url, mapOf("Accept" to "application/json"))
        if (body.isBlank()) return null
        return runCatching {
            val json = JSONObject(body)
            val text = json.optString("hitokoto").trim()
            if (text.isEmpty()) {
                null
            } else {
                HitokotoQuote(
                    text = text,
                    from = json.optString("from"),
                    fromWho = json.optString("from_who")
                )
            }
        }.getOrNull()
    }
}
