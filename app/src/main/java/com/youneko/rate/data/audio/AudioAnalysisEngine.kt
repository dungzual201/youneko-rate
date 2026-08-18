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
    private const val HALF_BINS = FFT_SIZE / 2 + 1
    private const val STABLE_BINS = 10

    fun hann(size: Int): DoubleArray = DoubleArray(size) { index ->
        0.5 * (1.0 - kotlin.math.cos(2.0 * Math.PI * index / (size - 1)))
    }

    /** Converts a real FFT bin to Hz. The real spectrum has bins 0..fftSize/2. */
    fun binToFrequencyHz(binIndex: Int, sampleRate: Int, fftSize: Int = FFT_SIZE): Double {
        require(binIndex in 0..fftSize / 2) { "binIndex must be in the real FFT half-spectrum" }
        require(sampleRate > 0 && fftSize > 0)
        return binIndex * sampleRate.toDouble() / fftSize
    }

    fun peakBin(samples: DoubleArray): Int {
        require(samples.size == FFT_SIZE)
        val magnitude = magnitudeSpectrum(samples)
        return (1..FFT_SIZE / 2).maxByOrNull { magnitude[it] } ?: 0
    }

    fun peakFrequencyHz(samples: DoubleArray, sampleRate: Int): Double =
        binToFrequencyHz(peakBin(samples), sampleRate)

    fun analyze(samples: FloatArray, sampleRate: Int): AudioQualityMetrics {
        if (samples.isEmpty() || sampleRate <= 0) {
            return AudioQualityMetrics(null, null, null, null, null, "KHÔNG XÁC ĐỊNH", 0, listOf("Không đủ mẫu PCM để phân tích."), emptyList())
        }
        val window = hann(FFT_SIZE)
        val frameCount = ((samples.size - 1).coerceAtLeast(0) / HOP_SIZE) + 1
        val averageMagnitude = DoubleArray(HALF_BINS)
        val frameCutoffs = mutableListOf<Int>()
        var sumSquares = 0.0
        var peak = 0.0
        var clipped = 0
        samples.forEach { value ->
            val absolute = abs(value.toDouble())
            sumSquares += absolute * absolute
            peak = max(peak, absolute)
            if (absolute >= 0.999) clipped++
        }
        repeat(frameCount) { frame ->
            val offset = frame * HOP_SIZE
            val fftData = DoubleArray(FFT_SIZE)
            for (i in 0 until FFT_SIZE) {
                val sample = samples.getOrNull(offset + i)?.toDouble() ?: 0.0
                fftData[i] = sample * window[i]
            }
            val magnitude = magnitudeSpectrum(fftData)
            for (bin in 0 until HALF_BINS) averageMagnitude[bin] += magnitude[bin]
            findCutoffBin(magnitude)?.let(frameCutoffs::add)
        }
        averageMagnitude.indices.forEach { averageMagnitude[it] /= frameCount.toDouble() }
        val maxMagnitude = averageMagnitude.maxOrNull()?.coerceAtLeast(1e-12) ?: 1e-12
        val cutoffBin = percentile95(frameCutoffs) ?: findCutoffBin(averageMagnitude)
        val cutoffHz = cutoffBin?.let { binToFrequencyHz(it, sampleRate) }
        val slope = cutoffBin?.let { localSlopeDbPerKHz(averageMagnitude, it, sampleRate) }
        val rms = sqrt(sumSquares / samples.size).coerceAtLeast(1e-12)
        val truePeakDbtp = 20.0 * log10(peak.coerceAtLeast(1e-12))
        val dynamicRange = 20.0 * log10((peak / rms).coerceAtLeast(1e-12))
        val clippingPercent = clipped * 100.0 / samples.size
        val spectrum = averageMagnitude.map { (20.0 * log10((it / maxMagnitude).coerceAtLeast(1e-12))).toFloat() }
        return AudioQualityMetrics(cutoffHz, slope, dynamicRange, truePeakDbtp, clippingPercent, "KHÔNG XÁC ĐỊNH", 0, emptyList(), spectrum)
    }

    fun verdict(format: AudioDecodedFormat, metrics: AudioQualityMetrics): AudioQualityMetrics {
        val cutoff = metrics.cutoffHz
        if (cutoff == null) {
            return metrics.copy(verdict = "KHÔNG XÁC ĐỊNH", confidence = 0, reasons = listOf("Không tìm được vách cắt ổn định trên phổ trung bình."))
        }
        val codec = format.codec.orEmpty().lowercase()
        val lossless = codec.contains("flac") || codec.contains("alac") || codec.contains("pcm") || codec.contains("wav")
        val slope = metrics.rolloffSlope
        val steep = slope != null && slope <= -150.0
        val codecEvidence = when {
            lossless || codec.contains("mp3") || codec.contains("aac") || codec.contains("vorbis") || codec.contains("opus") -> 100.0
            else -> 50.0
        }
        val slopeEvidence = ((abs(slope ?: 0.0) / 200.0) * 100.0).coerceIn(0.0, 100.0)
        val cutoffEvidence = when {
            cutoff >= 20_500.0 -> 100.0
            cutoff >= 19_500.0 -> 85.0
            cutoff >= 18_500.0 -> 75.0
            cutoff >= 17_000.0 -> 65.0
            cutoff >= 15_500.0 -> 55.0
            else -> 85.0
        }
        val confidence = (slopeEvidence * 0.5 + cutoffEvidence * 0.35 + codecEvidence * 0.15).toInt().coerceIn(0, 100)
        val descriptor = "Phổ cắt tại %.1f kHz với độ dốc %.1f dB/kHz; codec %s.".format(java.util.Locale.US, cutoff / 1000.0, slope ?: 0.0, format.codec ?: "không rõ")
        val reasons = listOf(
            when {
                lossless && cutoff < 20_000.0 && steep -> "$descriptor File khai là lossless nhưng có vách cắt dốc, có khả năng đã chuyển đổi từ nguồn nén."
                cutoff >= 20_500.0 -> "$descriptor Phổ giữ được vùng cao đến Nyquist, phù hợp nguồn lossless thật."
                cutoff >= 19_500.0 && steep -> "$descriptor Vách cắt dựng đứng, phù hợp lossy chất lượng cao."
                cutoff >= 18_500.0 && steep -> "$descriptor Vách cắt dựng đứng, phù hợp lossy khoảng 192–256 kbps."
                cutoff >= 15_500.0 && steep -> "$descriptor Vách cắt dựng đứng, phù hợp lossy chất lượng thấp."
                !steep -> "$descriptor Không có vách cắt đủ dốc; bản thu có thể suy giảm tần cao tự nhiên."
                else -> "$descriptor Các chỉ số chưa khớp một ngưỡng phân loại chắc chắn."
            },
        )
        val verdict = when {
            lossless && cutoff < 20_000.0 && steep -> "NGHI NGỜ NÂNG CẤP GIẢ"
            cutoff >= 20_500.0 -> "LOSSLESS THẬT"
            cutoff >= 19_500.0 && steep -> "LOSSY CHẤT LƯỢNG CAO"
            cutoff >= 18_500.0 && steep -> "LOSSY"
            cutoff >= 15_500.0 && steep -> "LOSSY CHẤT LƯỢNG THẤP"
            else -> "KHÔNG XÁC ĐỊNH"
        }
        return metrics.copy(verdict = verdict, confidence = confidence, reasons = reasons)
    }

    private fun magnitudeSpectrum(samples: DoubleArray): DoubleArray {
        require(samples.size == FFT_SIZE)
        val data = samples.copyOf()
        DoubleFFT_1D(FFT_SIZE.toLong()).realForward(data)
        return DoubleArray(HALF_BINS) { bin ->
            when (bin) {
                0 -> abs(data[0])
                FFT_SIZE / 2 -> abs(data[1])
                else -> hypot(data[2 * bin], data[2 * bin + 1])
            }
        }
    }

    private fun findCutoffBin(magnitude: DoubleArray): Int? {
        val peak = magnitude.maxOrNull()?.coerceAtLeast(1e-12) ?: return null
        val db = magnitude.map { 20.0 * log10((it / peak).coerceAtLeast(1e-12)) }
        val tailStart = (db.lastIndex * 0.95).toInt().coerceAtLeast(1)
        val noiseFloor = median(db.subList(tailStart, db.size))
        val threshold = max(noiseFloor + 6.0, -75.0)
        var stable = 0
        for (bin in db.lastIndex downTo 1) {
            if (db[bin] >= threshold) {
                stable++
                if (stable >= STABLE_BINS) return bin + STABLE_BINS - 1
            } else {
                stable = 0
            }
        }
        return null
    }

    private fun localSlopeDbPerKHz(magnitude: DoubleArray, cutoffBin: Int, sampleRate: Int): Double? {
        val peak = magnitude.maxOrNull()?.coerceAtLeast(1e-12) ?: return null
        val start = (cutoffBin - 40).coerceAtLeast(1)
        val end = (cutoffBin + 40).coerceAtMost(magnitude.lastIndex)
        if (end <= start) return null
        val xs = (start..end).map { binToFrequencyHz(it, sampleRate) / 1000.0 }
        val ys = (start..end).map { 20.0 * log10((magnitude[it] / peak).coerceAtLeast(1e-12)) }
        val meanX = xs.average()
        val meanY = ys.average()
        val denominator = xs.sumOf { (it - meanX) * (it - meanX) }
        if (denominator <= 0.0) return null
        return xs.indices.sumOf { (xs[it] - meanX) * (ys[it] - meanY) } / denominator
    }

    private fun percentile95(values: List<Int>): Int? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        return sorted[((sorted.lastIndex) * 0.95).toInt()]
    }

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return -100.0
        val sorted = values.sorted()
        return sorted[sorted.size / 2]
    }

    private fun hypot(real: Double, imaginary: Double): Double = sqrt(real * real + imaginary * imaginary)
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
