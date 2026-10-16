package cn.hxy.happy.utils.qq

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.tencent.mobileqq.pttlogic.api.IPttBuffer
import com.tencent.mobileqq.qroute.QRoute
import com.tencent.mobileqq.utils.SilkCodecWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.lang.reflect.Method
import cn.hxy.happy.utils.log.LogUtils
import cn.hxy.happy.utils.net.HttpUtils
import cn.hxy.happy.utils.reflect.findMethod
import cn.hxy.happy.utils.reflect.findMethods

/**
 * 语音红包音频合成：TTS 口令文本 -> PCM -> 腾讯 SILK_V3（QQ 语音消息格式）。
 *
 * 链路要点：
 *  1. PCM 必须重采样到 16kHz 单声道，TTS 源采样率不保证 16k，
 *     一旦不匹配语音会变速变调，服务端 ASR 口令匹配失败；
 *  2. TTS 多源兜底（百度 gettts / 有道 dictvoice / 系统 TTS），单源挂掉不再整链路中断；
 *  3. SILK_V3 文件按规范补 2 字节结束标记。
 *
 * 编码走 QQ 自己的 SilkCodecWrapper + IPttBuffer（与 QQ 录音管线同一条路），
 * 方法按签名反射匹配，规避版本混淆改名。
 */
object AudioConverterUtil {

    private const val TAG = "AudioConverterUtil"
    private const val SILK_SAMPLE_RATE = 16000
    private const val SILK_FRAME_BYTES = 640 // 320 samples * 2B = 20ms@16k

    /** 腾讯 SILK_V3 文件头：0x02 '#!SILK_V3' */
    private val SILK_HEADER = byteArrayOf(2, 35, 33, 83, 73, 76, 75, 95, 86, 51)

    /** SILK_V3 帧序列结束标记 */
    private val SILK_END = byteArrayOf(0, 0)

    /** SilkCodecWrapper.init(int, int, int) */
    private val silkInit: Method by lazy {
        SilkCodecWrapper::class.java.findMethod {
            returnType = void
            paramTypes(int, int, int)
        }
    }

    /**
     * SilkCodecWrapper 编码方法，签名随版本有两种：
     * (byte[], int, int) 或 (int, int, byte[])，返回含 byte[]+int 的结果体。
     * 过滤 void/int 返回值，避免误匹配到解码或占位方法。
     */
    private val silkRead: Method by lazy {
        val byArrayFirst = SilkCodecWrapper::class.java.findMethods { paramTypes(byteArr, int, int) }
            .filter { it.returnType != Void.TYPE && it.returnType != Integer.TYPE }
        val byIntFirst = SilkCodecWrapper::class.java.findMethods { paramTypes(int, int, byteArr) }
            .filter { it.returnType != Void.TYPE && it.returnType != Integer.TYPE }
        byArrayFirst.firstOrNull()
            ?: byIntFirst.firstOrNull()
            ?: throw NoSuchMethodException("SilkCodecWrapper encode method not found")
    }

    /** TTS 合成 text 并落成 SILK 文件，成功返回 true */
    suspend fun ttsToSilk(text: String, outputPath: String): Boolean = withContext(Dispatchers.IO) {
        val pcm = fetchTtsPcm(text)
        if (pcm == null || pcm.isEmpty()) {
            LogUtils.d("[$TAG] ttsToSilk: TTS 合成失败 text=$text")
            return@withContext false
        }
        try {
            File(outputPath).parentFile?.takeIf { !it.exists() }?.mkdirs()
            writeSilk(pcm, outputPath)
            val out = File(outputPath)
            val ok = out.exists() && out.length() > SILK_HEADER.size + SILK_END.size
            LogUtils.d("[$TAG] ttsToSilk: 完成 path=$outputPath, size=${out.length()}, ok=$ok")
            ok
        } catch (t: Throwable) {
            LogUtils.e(TAG, t)
            LogUtils.d("[$TAG] ttsToSilk: SILK 编码失败 ${t.message}")
            false
        }
    }

    // ------------------------------------------------------------------
    // TTS：多源下载 MP3 -> PCM（16k 单声道 s16le）
    // ------------------------------------------------------------------
    private suspend fun fetchTtsPcm(text: String): ByteArray? {
        val enc = URLEncoder.encode(text, "UTF-8")
        val sources = listOf(
            "https://fanyi.baidu.com/gettts?lan=zh&text=$enc&spd=5&source=web",
            "https://dict.youdao.com/dictvoice?audio=$enc&le=zh",
        )
        sources.forEachIndexed { i, url ->
            val tmp = File(QQCurrentEnv.currentDir, "cache/tts_$i.mp3")
            try {
                val ok = HttpUtils.downloadSuspend(url, tmp.absolutePath)
                if (!ok || tmp.length() < 128) {
                    LogUtils.d("[$TAG] TTS 源 $i 下载失败: $url")
                    return@forEachIndexed
                }
                val pcm = decodeToPcm(tmp)
                if (pcm != null && pcm.isNotEmpty()) {
                    LogUtils.d("[$TAG] TTS 源 $i 成功, pcm=${pcm.size}B")
                    return pcm
                }
                LogUtils.d("[$TAG] TTS 源 $i 解码失败")
            } catch (t: Throwable) {
                LogUtils.d("[$TAG] TTS 源 $i 异常: ${t.message}")
            } finally {
                tmp.delete()
            }
        }
        // 兜底：系统 TTS
        val pcm = systemTtsPcm(text)
        LogUtils.d("[$TAG] 系统 TTS 兜底: ${pcm?.size ?: "null"}")
        return pcm
    }

    /** 系统 TTS 合成（离线兜底），返回 16k 单声道 PCM */
    private fun systemTtsPcm(text: String): ByteArray? {
        return try {
            val outFile = File(QQCurrentEnv.currentDir, "cache/tts_local.wav")
            outFile.parentFile?.takeIf { !it.exists() }?.mkdirs()
            outFile.delete()

            val latch = CountDownLatch(1)
            var ready = false
            var done = false
            val tts = TextToSpeech(HostInfo.hostContext) { status ->
                ready = status == TextToSpeech.SUCCESS
                latch.countDown()
            }
            if (!latch.await(5, TimeUnit.SECONDS) || !ready) {
                tts.shutdown()
                return null
            }
            tts.setLanguage(Locale.CHINA)
            tts.setSpeechRate(1.0f)

            val doneLatch = CountDownLatch(1)
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    done = true
                    doneLatch.countDown()
                }

                override fun onError(utteranceId: String?) {
                    doneLatch.countDown()
                }
            })
            @Suppress("DEPRECATION")
            tts.synthesizeToFile(text, null, outFile, "happy_voice_hb")
            doneLatch.await(15, TimeUnit.SECONDS)
            tts.shutdown()
            if (!done || !outFile.exists()) {
                outFile.delete()
                return null
            }
            decodeToPcm(outFile)?.also { outFile.delete() }
        } catch (t: Throwable) {
            LogUtils.d("[$TAG] 系统 TTS 异常: ${t.message}")
            null
        }
    }

    // ------------------------------------------------------------------
    // 音频解码：WAV 直接解析 / 其他走 MediaExtractor+MediaCodec，统一输出 16k 单声道 s16le
    // ------------------------------------------------------------------
    private fun decodeToPcm(file: File): ByteArray? {
        val raw = file.readBytes()
        if (raw.size < 44) return null

        // WAV：直接解析
        if (raw.size > 12 && String(raw, 0, 4) == "RIFF" && String(raw, 8, 4) == "WAVE") {
            return parseWav(raw)
        }

        // MP3 等：MediaCodec 解码
        return decodeByMediaCodec(file)
    }

    private fun parseWav(raw: ByteArray): ByteArray? = try {
        var pos = 12
        var sampleRate = SILK_SAMPLE_RATE
        var channels = 1
        var bitsPerSample = 16
        var data: ByteArray? = null
        while (pos + 8 <= raw.size) {
            val chunkId = String(raw, pos, 4)
            val chunkSize =
                ((raw[pos + 4].toInt() and 0xff)) or
                    ((raw[pos + 5].toInt() and 0xff) shl 8) or
                    ((raw[pos + 6].toInt() and 0xff) shl 16) or
                    ((raw[pos + 7].toInt() and 0xff) shl 24)
            val body = pos + 8
            when (chunkId) {
                "fmt " -> {
                    channels = ((raw[body + 2].toInt() and 0xff)) or ((raw[body + 3].toInt() and 0xff) shl 8)
                    sampleRate =
                        ((raw[body + 4].toInt() and 0xff)) or
                            ((raw[body + 5].toInt() and 0xff) shl 8) or
                            ((raw[body + 6].toInt() and 0xff) shl 16) or
                            ((raw[body + 7].toInt() and 0xff) shl 24)
                    bitsPerSample =
                        ((raw[body + 14].toInt() and 0xff)) or ((raw[body + 15].toInt() and 0xff) shl 8)
                }

                "data" -> data = raw.copyOfRange(body, minOf(body + chunkSize, raw.size))
            }
            pos = body + chunkSize + (chunkSize and 1)
        }
        val pcm = data ?: return null
        if (bitsPerSample != 16) return null
        resampleTo16kMono(pcm, sampleRate, channels.coerceAtLeast(1))
    } catch (t: Throwable) {
        LogUtils.d("[$TAG] parseWav 失败: ${t.message}")
        null
    }

    private fun decodeByMediaCodec(file: File): ByteArray? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        return try {
            extractor.setDataSource(file.absolutePath)
            var trackIndex = -1
            var mime: String? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val m = fmt.getString(MediaFormat.KEY_MIME)
                if (m != null && m.startsWith("audio/")) {
                    trackIndex = i
                    mime = m
                    break
                }
            }
            if (trackIndex < 0 || mime == null) return null

            extractor.selectTrack(trackIndex)
            val trackFormat = extractor.getTrackFormat(trackIndex)
            var sampleRate = runCatching { trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE) }
                .getOrDefault(SILK_SAMPLE_RATE)
            var channels = runCatching { trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) }
                .getOrDefault(1)

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(trackFormat, null, null, 0)
            codec.start()

            val out = ByteArrayOutputStream()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val buf = codec.getInputBuffer(inIndex)!!
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = codec.outputFormat
                        sampleRate = runCatching { f.getInteger(MediaFormat.KEY_SAMPLE_RATE) }
                            .getOrDefault(sampleRate)
                        channels = runCatching { f.getInteger(MediaFormat.KEY_CHANNEL_COUNT) }
                            .getOrDefault(channels)
                    }

                    outIndex >= 0 -> {
                        if (info.size > 0) {
                            val buf = codec.getOutputBuffer(outIndex)!!
                            buf.position(info.offset)
                            buf.limit(info.offset + info.size)
                            val bytes = ByteArray(info.size)
                            buf.get(bytes)
                            out.write(bytes)
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                    }
                }
            }
            val pcm = out.toByteArray()
            if (pcm.isEmpty()) null
            else resampleTo16kMono(pcm, sampleRate, channels.coerceAtLeast(1))
        } catch (t: Throwable) {
            LogUtils.d("[$TAG] decodeByMediaCodec 失败: ${t.message}")
            null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    /** s16le 交错 PCM -> 16kHz 单声道 s16le（线性插值重采样） */
    private fun resampleTo16kMono(pcm: ByteArray, srcRate: Int, srcChannels: Int): ByteArray {
        val totalFrames = pcm.size / 2 / srcChannels
        if (totalFrames <= 0) return ByteArray(0)

        // 多声道混单声道
        val mono = ShortArray(totalFrames)
        for (i in 0 until totalFrames) {
            var acc = 0
            for (c in 0 until srcChannels) {
                val off = (i * srcChannels + c) * 2
                val s = ((pcm[off].toInt() and 0xff) or (pcm[off + 1].toInt() shl 8)).toShort()
                acc += s.toInt()
            }
            mono[i] = (acc / srcChannels).toShort()
        }

        val outFrames: ShortArray = if (srcRate == SILK_SAMPLE_RATE) {
            mono
        } else {
            val ratio = srcRate.toDouble() / SILK_SAMPLE_RATE
            val n = (mono.size / ratio).toInt().coerceAtLeast(1)
            ShortArray(n).also { out ->
                for (i in 0 until n) {
                    val pos = i * ratio
                    val idx = pos.toInt()
                    val frac = pos - idx
                    val s0 = mono[idx].toInt()
                    val s1 = mono[(idx + 1).coerceAtMost(mono.size - 1)].toInt()
                    out[i] = (s0 + (s1 - s0) * frac).toInt().toShort()
                }
            }
        }

        val bytes = ByteArray(outFrames.size * 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(outFrames)
        return bytes
    }

    // ------------------------------------------------------------------
    // SILK 编码：SilkCodecWrapper + IPttBuffer（与 QQ 录音管线一致）
    // ------------------------------------------------------------------
    private fun writeSilk(pcm: ByteArray, outputPath: String) {
        val codec = SilkCodecWrapper(HostInfo.hostContext)
        try {
            silkInit.invoke(codec, SILK_SAMPLE_RATE, SILK_SAMPLE_RATE, 1)
            val pttBuffer = QRoute.api(IPttBuffer::class.java)
            pttBuffer.createBufferTask(outputPath)
            pttBuffer.appendBuffer(outputPath, SILK_HEADER, SILK_HEADER.size)

            var offset = 0
            while (offset < pcm.size) {
                val end = minOf(offset + SILK_FRAME_BYTES, pcm.size)
                val chunk = pcm.copyOfRange(offset, end)
                val (data, len) = encodeFrame(codec, chunk)
                if (data != null && len > 0) {
                    pttBuffer.appendBuffer(outputPath, data, len)
                }
                offset = end
            }

            pttBuffer.appendBuffer(outputPath, SILK_END, SILK_END.size)
            pttBuffer.flush(outputPath)
        } finally {
            runCatching { codec.release() }
        }
    }

    private fun encodeFrame(codec: SilkCodecWrapper, chunk: ByteArray): Pair<ByteArray?, Int> {
        val raw = try {
            if (silkRead.parameterTypes[0] == ByteArray::class.java) {
                silkRead.invoke(codec, chunk, 0, chunk.size)
            } else {
                silkRead.invoke(codec, 0, chunk.size, chunk)
            }
        } catch (t: Throwable) {
            // 参数顺序兜底
            try {
                silkRead.invoke(codec, 0, chunk.size, chunk)
            } catch (t2: Throwable) {
                LogUtils.d("[$TAG] encodeFrame 失败: ${t2.message}")
                null
            }
        }
        return extractOutput(raw)
    }

    /**
     * 从编码结果体提取 (data, len)。
     * 结果体结构对齐 QQ 的 audioprocessor.c.a：{int 长度, int 偏移, byte[] 数据}。
     */
    private fun extractOutput(raw: Any?): Pair<ByteArray?, Int> {
        when (raw) {
            null -> return null to 0
            is ByteArray -> return raw to raw.size
        }
        var data: ByteArray? = null
        var len = -1
        for (f in raw.javaClass.declaredFields) {
            val v = runCatching { f.apply { isAccessible = true }.get(raw) }.getOrNull() ?: continue
            when (v) {
                is ByteArray -> if (data == null && v.isNotEmpty()) data = v
                is Int -> if (len < 0 && v in 1..Int.MAX_VALUE) len = v
            }
        }
        val bytes = data ?: return null to 0
        if (len <= 0 || len > bytes.size) len = bytes.size
        return bytes to len
    }
}
