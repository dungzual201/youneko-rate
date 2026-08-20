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
    val sourceMime: String? = null,
    val codecDetectionSource: String? = null,
    val bitrateNote: String? = null,
    val theoreticalBitrate: Long? = null,
)

enum class AudioAnalysisStep { READING_HEADER, DECODING, FFT, COMPUTING, SAVING }

data class AudioAnalysisProgress(
    val step: AudioAnalysisStep,
    val stepProgress: Float,
    val framesDone: Int = 0,
    val totalFrames: Int = 0,
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
    val noiseFloorDb: Double? = null,
    val cliffDb: Double? = null,
    val quietAboveFraction: Double? = null,
    val analyzedFrames: Int = 0,
    val formatVerdict: String? = null,
    val transcodeVerdict: String? = null,
    val energyAboveCutoffRatio: Double? = null,
    val cutoffRetries: Int = 0,
)

object SpectralAnalyzer {
    private const val HALF_BINS = FFT_SIZE / 2 + 1
    private const val STABLE_BINS = 6

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

    fun analyze(
        samples: FloatArray,
        sampleRate: Int,
        onFrameProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): AudioQualityMetrics {
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
        if (peak < 1e-8) {
            return AudioQualityMetrics(
                null, null, 0.0, -240.0, 0.0,
                "KHÔNG XÁC ĐỊNH", 0,
                listOf("File im lặng: không có năng lượng PCM để xác định cutoff."),
                List(HALF_BINS) { 0f },
            )
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
            findCutoffBin(magnitude, sampleRate)?.let(frameCutoffs::add)
            if ((frame + 1) % 25 == 0 || frame == frameCount - 1) onFrameProgress(frame + 1, frameCount)
        }
        averageMagnitude.indices.forEach { averageMagnitude[it] /= frameCount.toDouble() }
        val maxMagnitude = averageMagnitude.maxOrNull()?.coerceAtLeast(1e-12) ?: 1e-12
        val cutoffBin = percentile95(frameCutoffs) ?: findCutoffBin(averageMagnitude, sampleRate)
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
        val codec = format.codec.orEmpty().lowercase()
        val lossless = listOf("flac", "alac", "audio/raw", "pcm", "wav", "aiff", "wavpack", "ape", "dsd").any(codec::contains)
        val nyquist = format.sampleRate / 2.0
        val confidence = when {
            cutoff == null -> 0
            metrics.analyzedFrames < 3 -> 45
            metrics.cliffDb != null && metrics.cliffDb!! >= 40.0 -> 88
            cutoff >= nyquist * 0.90 -> 86
            else -> 68
        }
        if (cutoff == null || confidence < 70) {
            return metrics.copy(
                verdict = "CHƯA ĐỦ DỮ LIỆU ĐỂ KẾT LUẬN",
                confidence = confidence,
                reasons = listOf("Chưa đủ dữ liệu để kết luận: cần thêm khung âm thanh hoạt động hoặc vách phổ ổn định."),
            )
        }
        if (!lossless) {
            val kbps = format.bitrate?.div(1000L)
            val low = (kbps != null && kbps < 128L) || cutoff < 16_000.0
            val quality = when {
                low -> "LOSSY CHẤT LƯỢNG THẤP"
                kbps != null && kbps >= 256L -> "LOSSY CHẤT LƯỢNG CAO"
                else -> "LOSSY CHẤT LƯỢNG KHÁ"
            }
            val label = if (kbps == null) "$quality — ${format.codec ?: "codec lossy"}" else "$quality — ${format.codec} ${kbps} kbps"
            return metrics.copy(verdict = label, confidence = confidence, reasons = listOf("Codec lossy đúng định dạng; nhãn dựa trên bitrate và cutoff, không coi bản thân codec lossy là lỗi."))
        }
        val suspicious = metrics.cliffDb != null && metrics.cliffDb >= 40.0 && SpectrogramQuality.nearLossyCutoff(cutoff) && (metrics.quietAboveFraction ?: 0.0) >= 0.90
        val verdict = when {
            suspicious -> "CÓ DẤU HIỆU NGUỒN LOSSY"
            format.sampleRate >= 88_200 && cutoff >= 24_000.0 -> "HI-RES THỰC"
            format.sampleRate >= 88_200 && cutoff < 22_050.0 -> "NGHI NGỜ UPSAMPLE"
            cutoff >= 20_000.0 || cutoff >= nyquist * 0.90 -> "LOSSLESS — PHỔ ĐẦY ĐỦ"
            else -> "CHƯA ĐỦ DỮ LIỆU ĐỂ KẾT LUẬN"
        }
        val reason = when {
            suspicious -> "Có vách dốc %.1f dB gần %.1f kHz và phía trên vách im lặng %.0f%% số khung.".format(java.util.Locale.US, metrics.cliffDb, cutoff / 1000.0, (metrics.quietAboveFraction ?: 0.0) * 100.0)
            format.sampleRate >= 88_200 && cutoff >= 24_000.0 -> "Codec lossless và có năng lượng thực trên 24 kHz."
            format.sampleRate >= 88_200 -> "Tần số lấy mẫu hi-res nhưng cutoff chưa đủ để xác nhận phổ thực."
            else -> "Codec lossless và cutoff được đo bằng phổ phân vị 95 trên các khung không im lặng."
        }
        return metrics.copy(verdict = verdict, confidence = confidence, reasons = listOf(reason))
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

    private fun findCutoffBin(magnitude: DoubleArray, sampleRate: Int): Int? {
        val peak = magnitude.maxOrNull()?.coerceAtLeast(1e-12) ?: return null
        val db = magnitude.map { 20.0 * log10((it + 1e-12) / peak) }
        val tailStart = (db.lastIndex * 0.95).toInt().coerceAtLeast(1)
        val noiseFloor = median(db.subList(tailStart, db.size))
        val refStart = ((1_000.0 / sampleRate) * FFT_SIZE).toInt().coerceAtLeast(1)
        val refEnd = ((4_000.0 / sampleRate) * FFT_SIZE).toInt().coerceAtMost(db.lastIndex)
        val reference = db.subList(refStart, refEnd.coerceAtLeast(refStart + 1)).average()
        val threshold = max(noiseFloor + 6.0, reference - 50.0)
        var stable = 0
        for (bin in db.lastIndex downTo 1) {
            if (db[bin] >= threshold) {
                stable++
                if (stable >= STABLE_BINS) return bin + STABLE_BINS - 1
            } else stable = 0
        }
        return null
    }

    private fun localSlopeDbPerKHz(magnitude: DoubleArray, cutoffBin: Int, sampleRate: Int): Double? {
        val peak = magnitude.maxOrNull()?.coerceAtLeast(1e-12) ?: return null
        val cutoffDb = 20.0 * log10((magnitude[cutoffBin] + 1e-12) / peak)
        val lowerBin = (cutoffBin - ((2_000.0 / sampleRate) * FFT_SIZE).toInt()).coerceAtLeast(1)
        val lowerDb = 20.0 * log10((magnitude[lowerBin] + 1e-12) / peak)
        return (cutoffDb - lowerDb) / 2.0
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
    fun analyze(
        uriString: String,
        trackId: String? = null,
        albumId: String? = null,
        onProgress: (AudioAnalysisProgress) -> Unit = {},
        shouldStop: () -> Boolean = { false },
    ): AudioAnalysisEntity {
        val uri = Uri.parse(uriString)
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        try {
            onProgress(AudioAnalysisProgress(AudioAnalysisStep.READING_HEADER, 0f))
            if (shouldStop()) throw kotlinx.coroutines.CancellationException("Analysis interrupted")
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
            val codecInfo = CodecDetector.detect(
                context = context, uri = uri, sourceMime = mime, sampleRate = sampleRate, channels = channels,
                durationMs = durationUs / 1_000L, sourceFormatBitDepth = bitDepth(sourceFormat),
                trackBitrate = sourceFormat.getLongOrNull(MediaFormat.KEY_BIT_RATE),
            )
            onProgress(AudioAnalysisProgress(AudioAnalysisStep.DECODING, -1f))
            val pcm = decodeSegments(extractor, mime, sourceFormat, durationUs, sampleRate, channels, shouldStop)
            onProgress(AudioAnalysisProgress(AudioAnalysisStep.FFT, 0f))
            val baseMetrics = SpectralAnalyzer.analyze(pcm, sampleRate) { done, total ->
                onProgress(AudioAnalysisProgress(AudioAnalysisStep.FFT, done.toFloat() / total.coerceAtLeast(1), done, total))
                if (shouldStop()) throw kotlinx.coroutines.CancellationException("Analysis interrupted")
            }
            onProgress(AudioAnalysisProgress(AudioAnalysisStep.COMPUTING, 0f))
            val metrics = SpectralAnalyzer.verdict(
                AudioDecodedFormat(
                    container = codecInfo.container,
                    codec = codecInfo.codecLabel,
                    sampleRate = sampleRate,
                    channels = channels,
                    bitDepth = codecInfo.bitDepth,
                    bitrate = codecInfo.bitrate,
                    durationMs = durationUs / 1_000L,
                    sourceMime = codecInfo.sourceMime,
                    codecDetectionSource = codecInfo.detectionSource,
                    bitrateNote = codecInfo.bitrateNote,
                    theoreticalBitrate = codecInfo.theoreticalBitrate,
                ),
                baseMetrics,
            )
            return AudioAnalysisEntity(
                id = "$uriString:${System.currentTimeMillis()}",
                trackId = trackId,
                albumId = albumId,
                fileName = uri.lastPathSegment.orEmpty(),
                fileUriOrPath = uriString,
                fileHash = sha256(uriString),
                container = codecInfo.container,
                codec = codecInfo.codecLabel,
                sampleRate = sampleRate,
                bitDepth = codecInfo.bitDepth,
                bitrate = codecInfo.bitrate,
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
                noiseFloorDb = metrics.noiseFloorDb,
                cliffDb = metrics.cliffDb,
                quietAboveFraction = metrics.quietAboveFraction,
                analyzedFrames = metrics.analyzedFrames,
                sourceMime = codecInfo.sourceMime,
                codecDetectionSource = codecInfo.detectionSource,
                bitrateNote = codecInfo.bitrateNote,
                theoreticalBitrate = codecInfo.theoreticalBitrate,
                formatVerdict = metrics.formatVerdict,
                transcodeVerdict = metrics.transcodeVerdict,
                energyAboveCutoffRatio = metrics.energyAboveCutoffRatio,
                cutoffRetries = metrics.cutoffRetries,
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
        shouldStop: () -> Boolean,
    ): FloatArray {
        val output = FloatArrayBuilder()
        val segmentStarts = longArrayOf(durationUs / 4, durationUs / 2, (durationUs * 3) / 4)
        for (segmentStart in segmentStarts) {
            if (shouldStop()) throw kotlinx.coroutines.CancellationException("Analysis interrupted")
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
                    if (shouldStop()) throw kotlinx.coroutines.CancellationException("Analysis interrupted")
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
