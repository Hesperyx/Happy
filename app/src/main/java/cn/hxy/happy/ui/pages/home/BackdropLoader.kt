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
 * [native] 表示这次是按 1:1 原始像素解出来的，没有经过任何降采样。
 */
internal data class BackdropProbe(
    val api: BackdropApi,
    val bitmap: Bitmap,
    val bytes: Int,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val cover: Float,
    val native: Boolean
)

/**
 * 主页背景图：按接口表里启用的地址随机取一张，落盘缓存，失败时退回旧缓存。
 *
 * 原则：**不缩放**。解码只做一件事——把源图里能铺满屏幕的那一块按 1:1 抠出来，
 * 剩下的交给 GPU。宁可不显示一张图，也不用缩小后的像素去糊满屏幕。
 */
internal object BackdropLoader {

    /**
     * 1:1 解码能接受的最大像素数（14M px ≈ 56 MB）。
     * 超过它说明这张图大到解不动，直接换下一条接口，而不是把它缩小了用。
     */
    private const val NATIVE_BUDGET = 14_000_000L

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
                val bitmap = decodeNative(bytes, width, height) ?: return@forEach
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
            // 兜底也是 1:1；只有 1:1 解不动时才允许降采样，否则宁可换接口
            val bitmap = decodeNative(bytes, width, height) ?: decodeScaled(bytes, width, height)
            if (bitmap != null) {
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
            val native = decodeNative(bytes, width, height)
            val bitmap = native ?: decodeScaled(bytes, width, height) ?: error("这张图解不出来")
            BackdropProbe(
                api = api,
                bitmap = bitmap,
                bytes = bytes.size,
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight,
                cover = coverScale(sourceWidth, sourceHeight, width, height),
                native = native != null
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
        return decodeNative(bytes, targetWidth, targetHeight)
            ?: decodeScaled(bytes, targetWidth, targetHeight)
    }

    /**
     * 1:1 解码：只把源图里「铺满屏幕需要的那一条」抠出来，尺寸一点不减。
     * 屏幕 1272×2800 上铺一张 7408×4012 的横图，抠出的是 1822×4012 原始像素，
     * 显示时是缩小（0.70×），不是放大，所以清晰。
     */
    private fun decodeNative(bytes: ByteArray, targetWidth: Int, targetHeight: Int): Bitmap? {
        val (sourceWidth, sourceHeight) = boundsOf(bytes) ?: return null
        val region = coverRegion(sourceWidth, sourceHeight, targetWidth, targetHeight)
        if (region.width().toLong() * region.height() > NATIVE_BUDGET) return null
        regionDecode(bytes, region, 1)?.let { return it }
        // 有些编码（例如无损 WebP）不支持区域解码：整图 1:1 也还在预算内就整图来
        if (sourceWidth.toLong() * sourceHeight > NATIVE_BUDGET) return null
        return plainDecode(bytes, 1)
    }

    /** 只有 1:1 超出内存预算时才走这条路，属于最后的兜底。 */
    private fun decodeScaled(bytes: ByteArray, targetWidth: Int, targetHeight: Int): Bitmap? {
        val (sourceWidth, sourceHeight) = boundsOf(bytes) ?: return null
        val region = coverRegion(sourceWidth, sourceHeight, targetWidth, targetHeight)
        val sample = biggestSampleWithin(region.width(), region.height())
        regionDecode(bytes, region, sample)?.let { return it }
        return plainDecode(bytes, biggestSampleWithin(sourceWidth, sourceHeight))
    }

    /** 按屏幕比例，在源图里居中取一块面积最大的可铺满区域。 */
    private fun coverRegion(
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
        targetHeight: Int
    ): Rect {
        val targetRatio = targetWidth.toFloat() / targetHeight
        return if (sourceWidth.toFloat() / sourceHeight > targetRatio) {
            val cropWidth = (sourceHeight * targetRatio).toInt().coerceIn(1, sourceWidth)
            Rect((sourceWidth - cropWidth) / 2, 0, (sourceWidth + cropWidth) / 2, sourceHeight)
        } else {
            val cropHeight = (sourceWidth / targetRatio).toInt().coerceIn(1, sourceHeight)
            Rect(0, (sourceHeight - cropHeight) / 2, sourceWidth, (sourceHeight + cropHeight) / 2)
        }
    }

    private fun biggestSampleWithin(width: Int, height: Int): Int {
        var sample = 1
        while (
            width.toLong() / sample * (height.toLong() / sample) > NATIVE_BUDGET && sample < 16
        ) {
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
