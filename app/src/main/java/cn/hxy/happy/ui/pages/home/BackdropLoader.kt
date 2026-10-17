package cn.hxy.happy.ui.pages.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 主页背景图：从 loliapi 的随机二次元图接口取图，落盘缓存，失败时退回旧缓存。
 * 图片只作主页 Hero 背景，不参与主题配色。
 */
internal object BackdropLoader {

    /** 接口直接返回图片（带 302 跳转，由 HttpURLConnection 自动跟随） */
    private const val ENDPOINT = "https://www.loliapi.com/acg/pe/"
    private const val CACHE_NAME = "home_backdrop.jpg"
    private const val MAX_EDGE = 1440
    private const val TIMEOUT_MS = 8000
    private const val FRESH_MILLIS = 6 * 60 * 60 * 1000L

    private var memoryCache: Bitmap? = null

    suspend fun load(context: Context, forceRefresh: Boolean): Bitmap? = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, CACHE_NAME)
        val fresh = file.isFile && file.length() > 0L &&
            System.currentTimeMillis() - file.lastModified() < FRESH_MILLIS

        if (!forceRefresh && fresh) {
            memoryCache?.let { return@withContext it }
            decode(file)?.let {
                memoryCache = it
                return@withContext it
            }
        }

        if (runCatching { download(file) }.isSuccess) {
            decode(file)?.let {
                memoryCache = it
                return@withContext it
            }
        }

        // 网络或解码失败：能读到任何旧缓存就先用着
        decode(file)?.let {
            memoryCache = it
            return@withContext it
        }
        memoryCache
    }

    private fun download(target: File) {
        val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) Happy")
        }
        try {
            check(connection.responseCode in 200..299) { "HTTP ${connection.responseCode}" }
            val bytes = connection.inputStream.use { it.readBytes() }
            check(bytes.size > 1024) { "图片数据异常" }
            val temp = File(target.path + ".tmp")
            temp.writeBytes(bytes)
            if (target.exists() && !target.delete()) {
                temp.delete()
                error("旧缓存无法覆盖")
            }
            if (!temp.renameTo(target)) {
                temp.delete()
                error("缓存写入失败")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun decode(file: File): Bitmap? {
        if (!file.isFile || file.length() == 0L) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > MAX_EDGE || bounds.outHeight / sampleSize > MAX_EDGE) {
            sampleSize *= 2
        }
        return runCatching {
            BitmapFactory.decodeFile(
                file.path,
                BitmapFactory.Options().apply { inSampleSize = sampleSize }
            )
        }.getOrNull()
    }
}
