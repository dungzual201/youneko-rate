package com.youneko.rate.data.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.youneko.rate.data.local.entity.AudioAnalysisEntity
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jtransforms.fft.DoubleFFT_1D

const val AUDIO_ANALYSIS_ENGINE_VERSION = "phase8-v1"
private const val FFT_SIZE = 4096
private const val HOP_SIZE = 2048
private const val SEGMENT_US = 30_000_000L

/** PCM metrics produced entirely in memory; decoded samples are never sent to an audio output. */
data class AudioDecodedFormat(
    val container: String?,
    val codec: String?,
    val sampleRate: Int,
    val channels: Int,
    val bitDepth: Int?,
    val bitrate: Long?,
    val durationMs: Long?,
    val encoderTag: String? = null,
)

data class AudioQualityMetrics(
    val cutoffHz: Double?,
    val rolloffSlope: Double?,
    val dynamicRangeDb: Double?,
    val truePeakDbtp: Double?,
    val clippingPercent: Double?,
    val verdict: String,
    val confidence: Int,
    val reasons: List<String>,
    val spectrum: List<Float>,
)

object SpectralAnalyzer {
    fun hann(size: Int): DoubleArray = DoubleArray(size) { index ->
        0.5 * (1.0 - kotlin.math.cos(2.0 * Math.PI * index / (size - 1)))
    }

    fun peakBin(samples: DoubleArray): Int {
        require(samples.size == FFT_SIZE)
        val fft = DoubleFFT_1D(FFT_SIZE.toLong())
        val data = samples.copyOf()
        fft.realForward(data)
        var bestBin = 0
        var bestPower = Double.NEGATIVE_INFINITY
        for (bin in 1 until FFT_SIZE / 2) {
            val real = data[2 * bin]
            val imag = data[2 * bin + 1]
            val power = real * real + imag * imag
            if (power > bestPower) {
                bestPower = power
                bestBin = bin
            }
        }
        return bestBin
    }

    fun analyze(samples: FloatArray, sampleRate: Int): AudioQualityMetrics {
        if (samples.isEmpty() || sampleRate <= 0) {
            return AudioQualityMetrics(null, null, null, null, null, "KHÔNG XÁC ĐỊNH", 0, listOf("Không đủ mẫu PCM để phân tích."), emptyList())
        }
        val window = hann(FFT_SIZE)
        val frameCount = ((samples.size - FFT_SIZE).coerceAtLeast(0) / HOP_SIZE) + 1
        val averageMagnitude = DoubleArray(FFT_SIZE / 2)
        var sumSquares = 0.0
        var peak = 0.0
        var clipped = 0
        repeat(samples.size) { index ->
            val absolute = abs(samples[index].toDouble())
            sumSquares += absolute * absolute
            peak = max(peak, absolute)
            if (absolute >= 0.999) clipped++
        }
        repeat(frameCount) { frame ->
            val offset = frame * HOP_SIZE
            val fftData = DoubleArray(FFT_SIZE)
            for (i in 0 until FFT_SIZE) fftData[i] = samples[offset + i].toDouble() * window[i]
            DoubleFFT_1D(FFT_SIZE.toLong()).realForward(fftData)
            for (bin in 1 until FFT_SIZE / 2) {
                val real = fftData[2 * bin]
                val imag = fftData[2 * bin + 1]
                averageMagnitude[bin] += sqrt(real * real + imag * imag) / frameCount
            }
        }
        val maxMagnitude = averageMagnitude.maxOrNull()?.coerceAtLeast(1e-12) ?: 1e-12
        val cutoffThreshold = maxMagnitude * 0.01
        val cutoffBin = (1 until averageMagnitude.size).lastOrNull { averageMagnitude[it] >= cutoffThreshold } ?: 0
        val cutoffHz = cutoffBin * sampleRate.toDouble() / FFT_SIZE
        val tailStart = (cutoffBin * 0.7).toInt().coerceAtLeast(1)
        val tailEnd = cutoffBin.coerceAtLeast(tailStart + 1).coerceAtMost(averageMagnitude.lastIndex)
        val slope = if (tailEnd > tailStart) {
            val y1 = 20.0 * log10(averageMagnitude[tailStart].coerceAtLeast(1e-12))
            val y2 = 20.0 * log10(averageMagnitude[tailEnd].coerceAtLeast(1e-12))
            (y2 - y1) / (tailEnd - tailStart).toDouble()
        } else null
        val rms = sqrt(sumSquares / samples.size).coerceAtLeast(1e-12)
        val dynamicRange = 20.0 * log10((peak / rms).coerceAtLeast(1e-12))
        val truePeakDbtp = 20.0 * log10(peak.coerceAtLeast(1e-12))
        val clippingPercent = clipped * 100.0 / samples.size
        val spectrum = averageMagnitude.take(FFT_SIZE / 2).map { (20.0 * log10((it / maxMagnitude).coerceAtLeast(1e-12))).toFloat() }
        return AudioQualityMetrics(cutoffHz, slope, dynamicRange, truePeakDbtp, clippingPercent, "KHÔNG XÁC ĐỊNH", 0, emptyList(), spectrum)
    }

    fun verdict(format: AudioDecodedFormat, metrics: AudioQualityMetrics): AudioQualityMetrics {
        val cutoff = metrics.cutoffHz ?: return metrics.copy(verdict = "KHÔNG XÁC ĐỊNH", confidence = 0, reasons = listOf("Không xác định được tần số cắt."))
        val codec = format.codec.orEmpty().lowercase()
        val lossless = codec.contains("flac") || codec.contains("alac") || codec.contains("pcm") || codec.contains("wav")
        val lossy = codec.contains("mp3") || codec.contains("aac") || codec.contains("vorbis") || codec.contains("opus")
        return when {
            lossless && cutoff >= 19_000.0 -> metrics.copy(verdict = "LOSSLESS THẬT", confidence = 88, reasons = listOf("Codec lossless và phổ tần giữ được vùng cao."))
            lossy && cutoff >= 17_000.0 -> metrics.copy(verdict = "LOSSY CHẤT LƯỢNG CAO", confidence = 78, reasons = listOf("Codec lossy nhưng tần số cắt nằm ở vùng cao."))
            lossless && cutoff < 16_000.0 -> metrics.copy(verdict = "NGHI NGỜ NÂNG CẤP GIẢ", confidence = 82, reasons = listOf("Container/codec lossless nhưng phổ tần bị cắt sớm."))
            metrics.clippingPercent != null && metrics.clippingPercent > 5.0 -> metrics.copy(verdict = "KHÔNG XÁC ĐỊNH", confidence = 45, reasons = listOf("Có clipping đáng kể, cần thận trọng khi diễn giải phổ."))
            else -> metrics.copy(verdict = "KHÔNG XÁC ĐỊNH", confidence = 35, reasons = listOf("Các chỉ số không đủ phân biệt chắc chắn nguồn âm thanh."))
        }
    }
}

class AudioAnalysisEngine(private val context: Context) {
    fun analyze(uriString: String, trackId: String? = null, albumId: String? = null): AudioAnalysisEntity {
        val uri = Uri.parse(uriString)
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, emptyMap())
            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME).orEmpty().startsWith("audio/")
            } ?: error("Không tìm thấy track audio")
            extractor.selectTrack(trackIndex)
            val sourceFormat = extractor.getTrackFormat(trackIndex)
            val mime = sourceFormat.getString(MediaFormat.KEY_MIME) ?: error("Thiếu MIME audio")
            val sampleRate = sourceFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = sourceFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val durationUs = sourceFormat.getLong(MediaFormat.KEY_DURATION)
            val pcm = decodeSegments(extractor, mime, sourceFormat, durationUs, sampleRate, channels)
            val metrics = SpectralAnalyzer.verdict(
                AudioDecodedFormat(
                    container = null,
                    codec = mime,
                    sampleRate = sampleRate,
                    channels = channels,
                    bitDepth = bitDepth(sourceFormat),
                    bitrate = sourceFormat.getLongOrNull(MediaFormat.KEY_BIT_RATE),
                    durationMs = durationUs / 1_000L,
                ),
                SpectralAnalyzer.analyze(pcm, sampleRate),
            )
            return AudioAnalysisEntity(
                id = "$uriString:${System.currentTimeMillis()}",
                trackId = trackId,
                albumId = albumId,
                fileName = uri.lastPathSegment.orEmpty(),
                fileUriOrPath = uriString,
                fileHash = sha256(uriString),
                container = null,
                codec = mime,
                sampleRate = sampleRate,
                bitDepth = bitDepth(sourceFormat),
                bitrate = sourceFormat.getLongOrNull(MediaFormat.KEY_BIT_RATE),
                channels = channels,
                durationMs = durationUs / 1_000L,
                encoderTag = null,
                cutoffHz = metrics.cutoffHz,
                rolloffSlope = metrics.rolloffSlope,
                dynamicRangeDb = metrics.dynamicRangeDb,
                truePeakDbtp = metrics.truePeakDbtp,
                clippingPercent = metrics.clippingPercent,
                verdict = metrics.verdict,
                confidence = metrics.confidence,
                reasonsJson = Json.encodeToString(metrics.reasons),
                spectrumJson = Json.encodeToString(metrics.spectrum),
                engineVersion = AUDIO_ANALYSIS_ENGINE_VERSION,
                analyzedAt = System.currentTimeMillis(),
            )
        } finally {
            runCatching { decoder?.stop() }
            decoder?.release()
            extractor.release()
        }
    }

    private fun decodeSegments(
        extractor: MediaExtractor,
        mime: String,
        sourceFormat: MediaFormat,
        durationUs: Long,
        sampleRate: Int,
        channels: Int,
    ): FloatArray {
        val output = FloatArrayBuilder()
        val segmentStarts = longArrayOf(durationUs / 4, durationUs / 2, (durationUs * 3) / 4)
        for (segmentStart in segmentStarts) {
            extractor.seekTo(segmentStart, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            val segmentDecoder = MediaCodec.createDecoderByType(mime)
            val bufferInfo = MediaCodec.BufferInfo()
            try {
                segmentDecoder.configure(sourceFormat, null, null, 0)
                segmentDecoder.start()
                val segmentEnd = (segmentStart + SEGMENT_US).coerceAtMost(durationUs)
                var inputDone = false
                var outputDone = false
                while (!outputDone) {
                    if (!inputDone) {
                        val inputIndex = segmentDecoder.dequeueInputBuffer(10_000)
                        if (inputIndex >= 0) {
                            val input = segmentDecoder.getInputBuffer(inputIndex) ?: continue
                            input.clear()
                            val size = extractor.readSampleData(input, 0)
                            val timeUs = extractor.sampleTime
                            if (size < 0 || timeUs < 0 || timeUs > segmentEnd) {
                                segmentDecoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                segmentDecoder.queueInputBuffer(inputIndex, 0, size, timeUs, 0)
                                extractor.advance()
                            }
                        }
                    }
                    when (val outputIndex = segmentDecoder.dequeueOutputBuffer(bufferInfo, 10_000)) {
                        MediaCodec.INFO_TRY_AGAIN_LATER -> if (inputDone) outputDone = true
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                        else -> if (outputIndex >= 0) {
                            val buffer = segmentDecoder.getOutputBuffer(outputIndex)
                            if (buffer != null && bufferInfo.size > 0) appendPcm16(output, buffer, bufferInfo.offset, bufferInfo.size, channels)
                            segmentDecoder.releaseOutputBuffer(outputIndex, false)
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true
                        }
                    }
                }
            } finally {
                runCatching { segmentDecoder.stop() }
                segmentDecoder.release()
            }
        }
        return output.toArray()
    }

    private fun appendPcm16(output: FloatArrayBuilder, buffer: ByteBuffer, offset: Int, size: Int, channels: Int) {
        val duplicate = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        duplicate.position(offset)
        val end = (offset + size).coerceAtMost(duplicate.limit())
        while (duplicate.position() + 2 * channels <= end) {
            var sum = 0.0
            repeat(channels) { sum += duplicate.short / 32768.0 }
            output.add((sum / channels).toFloat())
        }
    }

    private fun bitDepth(format: MediaFormat): Int? = when (format.getIntegerOrNull(MediaFormat.KEY_PCM_ENCODING)) {
        2 -> 16
        3 -> 8
        4 -> 32
        else -> null
    }

    private fun sha256(uriString: String): String = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(Uri.parse(uriString))!!.use { input ->
            val buffer = ByteArray(64 * 1024)
            var read: Int
            while (input.read(buffer).also { read = it } >= 0) if (read > 0) digest.update(buffer, 0, read)
        }
        digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }.getOrElse { uriString.hashCode().toUInt().toString(16) }
}

private class FloatArrayBuilder(initialCapacity: Int = 8192) {
    private var values = FloatArray(initialCapacity)
    private var size = 0
    fun add(value: Float) {
        if (size == values.size) values = values.copyOf(values.size * 2)
        values[size++] = value
    }
    fun toArray(): FloatArray = values.copyOf(size)
}

private fun MediaFormat.getLongOrNull(key: String): Long? = runCatching { getLong(key) }.getOrNull()
private fun MediaFormat.getIntegerOrNull(key: String): Int? = runCatching { getInteger(key) }.getOrNull()
