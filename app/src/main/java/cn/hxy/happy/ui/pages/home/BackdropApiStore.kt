package cn.hxy.happy.ui.pages.home

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.json.Json
import cn.hxy.happy.conf.BackdropApiConfig

/**
 * 主页背景接口属于 App 自己的设置：不进宿主进程、不跟 QQ 账号走，
 * 所以存在 App 自己的 SharedPreferences，而不是 ObjectStore 的宿主数据目录。
 */
internal object BackdropApiStore {

    private const val PREFS_NAME = "happy_app_settings"
    private const val KEY_BACKDROP_APIS = "backdrop_apis"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun load(context: Context): BackdropApiConfig {
        val raw = prefs(context).getString(KEY_BACKDROP_APIS, null)
            ?: return BackdropApiConfig.default()
        return runCatching { json.decodeFromString(BackdropApiConfig.serializer(), raw) }
            .getOrDefault(BackdropApiConfig.default())
    }

    fun save(context: Context, config: BackdropApiConfig) {
        prefs(context).edit {
            putString(KEY_BACKDROP_APIS, json.encodeToString(BackdropApiConfig.serializer(), config))
        }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
