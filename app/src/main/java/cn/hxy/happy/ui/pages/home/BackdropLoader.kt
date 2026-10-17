package cn.hxy.happy.ui.pages.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import cn.hxy.happy.conf.BackdropApi
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** 「试一张」真的抓到图才算成功，尺寸与体积照实回报。 */
internal data class BackdropProbe(
    val api: BackdropApi,
    val bitmap: Bitmap,
    val bytes: Int
)

/**
 * 主页背景图：按接口表里启用的地址随机取一张，落盘缓存，失败时退回旧缓存。
 * 图片只作主页背景，不参与主题配色。
 */
internal object BackdropLoader {

    private const val MAX_EDGE = 1440
    private const val TIMEOUT_MS = 8000
    private const val FRESH_MILLIS = 6 * 60 * 60 * 1000L
    private const val CACHE_PREFIX = "home_backdrop_"

    private var memoryKey: String? = null
    private var memoryBitmap: Bitmap? = null

    suspend fun load(
        context: Context,
        apis: List<BackdropApi>,
        forceRefresh: Boolean
    ): Bitmap? = withContext(Dispatchers.IO) {
        val candidates = apis.filter { it.enabled && it.url.isNotBlank() }
        if (candidates.isEmpty()) {
            memoryKey = null
            memoryBitmap = null
            return@withContext null
        }

        // 缓存按「启用集合」分文件：改了接口就该换图，而不是继续拿旧接口的图
        val key = cacheName(candidates)
        val file = File(context.cacheDir, key)
        val fresh = file.isFile && file.length() > 0L &&
            System.currentTimeMillis() - file.lastModified() < FRESH_MILLIS

        if (!forceRefresh && fresh) {
            if (memoryKey == key) memoryBitmap?.let { return@withContext it }
            decodeFile(file)?.let { bitmap ->
                cacheInMemory(key, bitmap)
                return@withContext bitmap
            }
        }

        candidates.shuffled().forEach { api ->
            if (runCatching { download(api.url, file) }.isSuccess) {
                decodeFile(file)?.let { bitmap ->
                    cacheInMemory(key, bitmap)
                    return@withContext bitmap
                }
            }
        }

        // 网络或解码失败：能读到本次接口的旧缓存就先用着
        decodeFile(file)?.let { bitmap ->
            cacheInMemory(key, bitmap)
            return@withContext bitmap
        }
        memoryKey = null
        memoryBitmap = null
        null
    }

    suspend fun probe(api: BackdropApi): Result<BackdropProbe> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = fetch(api.url)
            val bitmap = decodeBytes(bytes) ?: error("返回的内容不是图片")
            BackdropProbe(api, bitmap, bytes.size)
        }
    }

    private fun cacheInMemory(key: String, bitmap: Bitmap) {
        memoryKey = key
        memoryBitmap = bitmap
    }

    private fun cacheName(apis: List<BackdropApi>): String {
        val seed = apis.joinToString("|") { it.url }
        return CACHE_PREFIX + Integer.toHexString(seed.hashCode()) + ".img"
    }

    private fun fetch(url: String): ByteArray {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) Happy")
        }
        try {
            check(connection.responseCode in 200..299) { "HTTP ${connection.responseCode}" }
            val bytes = connection.inputStream.use { it.readBytes() }
            check(bytes.size > 1024) { "返回的数据太小" }
            return bytes
        } finally {
            connection.disconnect()
        }
    }

    private fun download(url: String, target: File) {
        val bytes = fetch(url)
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
        cleanup(target)
    }

    /** 只留当前接口的那一份缓存，换接口后别把 cacheDir 堆满。 */
    private fun cleanup(keep: File) {
        keep.parentFile?.listFiles()?.forEach { file ->
            if (file.name.startsWith(CACHE_PREFIX) && file.name != keep.name) file.delete()
        }
    }

    private fun decodeFile(file: File): Bitmap? {
        if (!file.isFile || file.length() == 0L) return null
        return runCatching { decodeBytes(file.readBytes()) }.getOrNull()
    }

    private fun decodeBytes(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > MAX_EDGE || bounds.outHeight / sampleSize > MAX_EDGE) {
            sampleSize *= 2
        }
        return BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sampleSize }
        )
    }
}
