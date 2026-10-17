package cn.hxy.happy.ui.pages.home

import android.content.Context

/**
 * 主页玻璃卡片的观感参数：不透明度 + 模糊半径。
 *
 * 长按主页标题下的版本号可以打开调节面板，调完直接写进 App 私有偏好，
 * 下次进来还是这个手感。默认值就是当前调好的那套。
 */
internal object HomeLook {

    private const val PREFS = "happy_app_settings"
    private const val KEY_ALPHA = "home_glass_alpha"
    private const val KEY_BLUR = "home_glass_blur"

    const val DEFAULT_ALPHA = 0.62f
    const val DEFAULT_BLUR = 10f
    const val MIN_ALPHA = 0.30f
    const val MAX_ALPHA = 0.95f
    const val MIN_BLUR = 0f
    const val MAX_BLUR = 40f

    data class Look(
        val alpha: Float = DEFAULT_ALPHA,
        val blur: Float = DEFAULT_BLUR
    )

    fun load(context: Context): Look {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Look(
            alpha = sp.getFloat(KEY_ALPHA, DEFAULT_ALPHA).coerceIn(MIN_ALPHA, MAX_ALPHA),
            blur = sp.getFloat(KEY_BLUR, DEFAULT_BLUR).coerceIn(MIN_BLUR, MAX_BLUR)
        )
    }

    fun save(context: Context, look: Look) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_ALPHA, look.alpha)
            .putFloat(KEY_BLUR, look.blur)
            .apply()
    }

    fun reset(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_ALPHA)
            .remove(KEY_BLUR)
            .apply()
    }
}
