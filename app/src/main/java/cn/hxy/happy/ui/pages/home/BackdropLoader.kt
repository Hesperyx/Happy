package cn.hxy.happy.ui.pages.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import cn.hxy.happy.conf.BackdropApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 「试一张」真的抓到图才算成功。
 *
 * [cover] 是为了铺满屏幕要把这张图放大多少倍：<= 1 表示原生像素就够，> 1 越大越糊。
 */
internal data class BackdropProbe(
    val api: BackdropApi,
    val bitmap: Bitmap,
    val bytes: Int,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val cover: Float
)

/**
 * 主页背景图：按接口表里启用的地址随机取一张，落盘缓存，失败时退回旧缓存。
 *
 * 糊不糊只取决于两件事：源图像素够不够铺满屏幕、解码时有没有被缩小。
 * 所以这里按「放大倍数」挑图，并且只解出铺满屏幕需要的那一块，不做整图缩边。
 */
internal object BackdropLoader {

    /** 解出的位图最多这么多像素，别把内存顶穿（10M px ≈ 40 MB）。 */
    private const val MAX_PIXELS = 10_000_000L

    /** 放大倍数不超过它就算清晰，遇到这种图就不必再试别的接口。 */
    private const val SHARP_COVER = 1.15f

    private const val TIMEOUT_MS = 10_000
    private const val FRESH_MILLIS = 6 * 60 * 60 * 1000L
    private const val CACHE_PREFIX = "home_backdrop_"

    private var memoryKey: String? = null
    private var memoryBitmap: Bitmap? = null

    suspend fun load(
        context: Context,
        apis: List<BackdropApi>,
        forceRefresh: Boolean,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap? = withContext(Dispatchers.IO) {
        val candidates = apis.filter { it.enabled && it.url.isNotBlank() }
        val width = targetWidth.coerceAtLeast(1)
        val height = targetHeight.coerceAtLeast(1)

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
            decodeFile(file, width, height)?.let { bitmap ->
                cacheInMemory(key, bitmap)
                return@withContext bitmap
            }
        }

        // 先找「铺满屏幕都不用放大」的那张；都不够大时，留下放大最少的那张兜底
        var fallback: ByteArray? = null
        var fallbackCover = Float.MAX_VALUE

        candidates.shuffled().forEach { api ->
            val bytes = runCatching { fetch(api.url) }.getOrNull() ?: return@forEach
            val cover = coverScale(bytes, width, height)
            if (cover <= SHARP_COVER) {
                val bitmap = runCatching { decodeForScreen(bytes, width, height) }.getOrNull()
                    ?: return@forEach
                writeCache(file, bytes)
                cacheInMemory(key, bitmap)
                return@withContext bitmap
            }
            if (cover < fallbackCover) {
                fallbackCover = cover
                fallback = bytes
            }
        }

        fallback?.let { bytes ->
            runCatching { decodeForScreen(bytes, width, height) }.getOrNull()?.let { bitmap ->
                writeCache(file, bytes)
                cacheInMemory(key, bitmap)
                return@withContext bitmap
            }
        }

        // 网络或解码失败：能读到本次接口的旧缓存就先用着
        decodeFile(file, width, height)?.let { bitmap ->
            cacheInMemory(key, bitmap)
            return@withContext bitmap
        }
        memoryKey = null
        memoryBitmap = null
        null
    }

    suspend fun probe(
        api: BackdropApi,
        targetWidth: Int,
        targetHeight: Int
    ): Result<BackdropProbe> = withContext(Dispatchers.IO) {
        runCatching {
            val width = targetWidth.coerceAtLeast(1)
            val height = targetHeight.coerceAtLeast(1)
            val bytes = fetch(api.url)
            val (sourceWidth, sourceHeight) = boundsOf(bytes) ?: error("返回的内容不是图片")
            val bitmap = decodeForScreen(bytes, width, height) ?: error("这张图解不出来")
            BackdropProbe(
                api = api,
                bitmap = bitmap,
                bytes = bytes.size,
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight,
                cover = coverScale(sourceWidth, sourceHeight, width, height)
            )
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

    private fun writeCache(target: File, bytes: ByteArray) {
        runCatching {
            val temp = File(target.path + ".tmp")
            temp.writeBytes(bytes)
            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) {
                temp.delete()
                return@runCatching
            }
            // 只留当前接口那一份，换接口后别把 cacheDir 堆满
            target.parentFile?.listFiles()?.forEach { file ->
                if (file.name.startsWith(CACHE_PREFIX) && file.name != target.name) file.delete()
            }
        }
    }

    private fun decodeFile(file: File, targetWidth: Int, targetHeight: Int): Bitmap? {
        if (!file.isFile || file.length() == 0L) return null
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
        return decodeForScreen(bytes, targetWidth, targetHeight)
    }

    /**
     * 只解出「铺满屏幕」需要的那一块：按屏幕比例居中裁剪，再按内存上限降采样。
     * 屏幕 1272×2800 的机器上，一张 3000×1600 的横图以前会被压到 960×540 再拉大，
     * 现在直接解中间 1272×2800 比例的那条，尺寸一点不少。
     */
    private fun decodeForScreen(bytes: ByteArray, targetWidth: Int, targetHeight: Int): Bitmap? {
        val (sourceWidth, sourceHeight) = boundsOf(bytes) ?: return null
        val targetRatio = targetWidth.toFloat() / targetHeight
        val region = if (sourceWidth.toFloat() / sourceHeight > targetRatio) {
            val cropWidth = (sourceHeight * targetRatio).toInt().coerceIn(1, sourceWidth)
            Rect((sourceWidth - cropWidth) / 2, 0, (sourceWidth + cropWidth) / 2, sourceHeight)
        } else {
            val cropHeight = (sourceWidth / targetRatio).toInt().coerceIn(1, sourceHeight)
            Rect(0, (sourceHeight - cropHeight) / 2, sourceWidth, (sourceHeight + cropHeight) / 2)
        }
        val sampleSize = sampleSize(region.width(), region.height())
        regionDecode(bytes, region, sampleSize)?.let { return it }
        // 少数编码（例如无损 WebP）不支持区域解码，退回整图解码——此时按整图算降采样
        return plainDecode(bytes, sampleSize(sourceWidth, sourceHeight))
    }

    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (width.toLong() / sample * (height.toLong() / sample) > MAX_PIXELS && sample < 16) {
            sample *= 2
        }
        return sample
    }

    private fun regionDecode(bytes: ByteArray, region: Rect, sampleSize: Int): Bitmap? {
        val decoder = runCatching {
            BitmapRegionDecoder.newInstance(bytes, 0, bytes.size, false)
        }.getOrNull() ?: return null
        return try {
            runCatching {
                decoder.decodeRegion(
                    region,
                    BitmapFactory.Options().apply { inSampleSize = sampleSize }
                )
            }.getOrNull()
        } finally {
            runCatching { decoder.recycle() }
        }
    }

    private fun plainDecode(bytes: ByteArray, sampleSize: Int): Bitmap? = runCatching {
        BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sampleSize }
        )
    }.getOrNull()

    private fun coverScale(bytes: ByteArray, targetWidth: Int, targetHeight: Int): Float {
        val (sourceWidth, sourceHeight) = boundsOf(bytes) ?: return Float.MAX_VALUE
        return coverScale(sourceWidth, sourceHeight, targetWidth, targetHeight)
    }

    private fun coverScale(
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
        targetHeight: Int
    ): Float = maxOf(
        targetWidth.toFloat() / sourceWidth,
        targetHeight.toFloat() / sourceHeight
    )

    private fun boundsOf(bytes: ByteArray): Pair<Int, Int>? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        return if (width > 0 && height > 0) width to height else null
    }
}
