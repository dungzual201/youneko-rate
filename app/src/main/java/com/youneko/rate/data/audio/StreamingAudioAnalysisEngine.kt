package com.youneko.rate.data.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import com.youneko.rate.data.local.entity.AudioAnalysisEntity
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Full-file decode-only analysis. It never sends PCM to an audio output. */
class StreamingAudioAnalysisEngine(private val context: Context) {
    fun spectrogramFlow(
        uriString: String,
        stableKey: String? = null,
        shouldStop: () -> Boolean = { false },
    ): Flow<SpectrogramEvent> = flow {
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
            val durationUs = sourceFormat.getLong(MediaFormat.KEY_DURATION).coerceAtLeast(1L)
            val durationMs = durationUs / 1_000L
            val totalFrames = (durationUs * sampleRate / 1_000_000L).coerceAtLeast(1L)
            val metadata = SpectrogramMetadata(
                hopFrames = SpectrogramMath.hopFrames(totalFrames, durationMs),
                sampleRate = sampleRate,
                codec = mime,
                channels = channels,
                bitDepth = bitDepth(sourceFormat),
                bitrate = sourceFormat.getLongOrNull(MediaFormat.KEY_BIT_RATE),
                totalFrames = totalFrames,
                durationMs = durationMs,
                columns = SpectrogramMath.targetColumns(durationMs),
                stableKey = stableKey,
            )
            emit(SpectrogramEvent.Header(metadata))
            val accumulator = StreamingSpectrogramAccumulator(
                sampleRate = sampleRate,
                totalFrames = totalFrames,
                durationMs = durationMs,
                stableKey = stableKey,
                codec = mime,
                channels = channels,
                bitDepth = bitDepth(sourceFormat),
                bitrate = sourceFormat.getLongOrNull(MediaFormat.KEY_BIT_RATE),
            )
            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(sourceFormat, null, null, 0)
            decoder.start()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var outputFormat = sourceFormat
            var lastProgressFrames = 0L
            val decodeStartNanos = System.nanoTime()
            while (!outputDone) {
                if (shouldStop()) throw CancellationException("Đã huỷ phân tích")
                if (!inputDone) {
                    val inputIndex = decoder.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val input = decoder.getInputBuffer(inputIndex)
                        if (input == null) continue
                        input.clear()
                        val size = extractor.readSampleData(input, 0)
                        val timeUs = extractor.sampleTime
                        if (size < 0 || timeUs < 0) {
                            decoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inputIndex, 0, size, timeUs, extractor.sampleFlags)
                            extractor.advance()
                        }
                    }
                }
                when (val outputIndex = decoder.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> if (inputDone) outputDone = true
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> outputFormat = decoder.outputFormat
                    else -> if (outputIndex >= 0) {
                        decoder.getOutputBuffer(outputIndex)?.let { buffer ->
                            if (info.size > 0) {
                                appendPcmMono(buffer, info.offset, info.size, channels, outputFormat, accumulator) { column ->
                                    emit(SpectrogramEvent.Column(column, columnProgress(column.index, metadata.columns)))
                                }
                                val decodedFrames = accumulator.samplesSeen()
                                if (decodedFrames - lastProgressFrames >= sampleRate || inputDone) {
                                    lastProgressFrames = decodedFrames
                                    emit(SpectrogramEvent.Progress(decodedFrames, totalFrames, (decodedFrames.toFloat() / totalFrames).coerceIn(0f, 1f)))
                                }
                            }
                        }
                        decoder.releaseOutputBuffer(outputIndex, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true
                    }
                }
            }
            accumulator.finish().forEach { column ->
                emit(SpectrogramEvent.Column(column, columnProgress(column.index, metadata.columns)))
            }
            val decodeMs = (System.nanoTime() - decodeStartNanos) / 1_000_000L
            emit(SpectrogramEvent.Completed(accumulator.result().copy(decodeMs = decodeMs)))
        } finally {
            runCatching { decoder?.stop() }
            decoder?.release()
            extractor.release()
        }
    }.flowOn(Dispatchers.Default.limitedParallelism(2))

    suspend fun analyze(
        uriString: String,
        trackId: String? = null,
        albumId: String? = null,
        stableKey: String? = null,
        onEvent: suspend (SpectrogramEvent) -> Unit = {},
        shouldStop: () -> Boolean = { false },
    ): AudioAnalysisEntity {
        val effectiveStableKey = stableKey ?: computeStableKey(uriString)
        var completed: SpectrogramResult? = null
        var header: SpectrogramMetadata? = null
        spectrogramFlow(uriString, effectiveStableKey, shouldStop).collect { event ->
            onEvent(event)
            when (event) {
                is SpectrogramEvent.Header -> header = event.metadata
                is SpectrogramEvent.Completed -> completed = event.result
                else -> Unit
            }
        }
        val result = completed ?: error("Không có kết quả spectrogram")
        val metadata = header ?: result.metadata
        val uri = Uri.parse(uriString)
        val sampleRate = metadata.sampleRate
        val averageDb = result.averageDb()
        val cutoff = SpectrogramQuality.estimateCutoff(averageDb, sampleRate)
        val rms = sqrt((result.sumSquares / result.sampleCount.coerceAtLeast(1L)).coerceAtLeast(1e-12))
        val truePeak = 20.0 * log10(result.peak.coerceAtLeast(1e-12))
        val clipping = result.clippedSamples * 100.0 / result.sampleCount.coerceAtLeast(1L)
        val base = AudioQualityMetrics(
            cutoffHz = cutoff.frequencyHz,
            rolloffSlope = cutoff.slopeDbPerKHz,
            dynamicRangeDb = 20.0 * log10((result.peak / rms).coerceAtLeast(1e-12)),
            truePeakDbtp = truePeak,
            clippingPercent = clipping,
            verdict = "KHÔNG XÁC ĐỊNH",
            confidence = 0,
            reasons = emptyList(),
            spectrum = averageDb.toList(),
        )
        val format = AudioDecodedFormat(
            container = null,
            codec = metadata.codec,
            sampleRate = sampleRate,
            channels = metadata.channels,
            bitDepth = metadata.bitDepth,
            bitrate = metadata.bitrate,
            durationMs = metadata.durationMs,
        )
        val metrics = SpectrogramQuality.enrich(format, SpectralAnalyzer.verdict(format, base), averageDb)
        val entity = AudioAnalysisEntity(
            id = "$uriString:${System.currentTimeMillis()}",
            trackId = trackId,
            albumId = albumId,
            fileName = uri.lastPathSegment.orEmpty(),
            fileUriOrPath = uriString,
            fileHash = effectiveStableKey,
            container = null,
            codec = metadata.codec,
            sampleRate = sampleRate,
            bitDepth = metadata.bitDepth,
            bitrate = metadata.bitrate,
            channels = metadata.channels,
            durationMs = metadata.durationMs,
            cutoffHz = metrics.cutoffHz,
            rolloffSlope = metrics.rolloffSlope,
            dynamicRangeDb = metrics.dynamicRangeDb,
            truePeakDbtp = metrics.truePeakDbtp,
            clippingPercent = metrics.clippingPercent,
            verdict = metrics.verdict,
            confidence = metrics.confidence,
            reasonsJson = Json.encodeToString(metrics.reasons),
            spectrumJson = Json.encodeToString(metrics.spectrum),
            engineVersion = "spek-stream-v1",
            analyzedAt = System.currentTimeMillis(),
        )
        val renderStartNanos = System.nanoTime()
        SpectrogramCache(context).write(trackId ?: uriString, result)
        val renderMs = (System.nanoTime() - renderStartNanos) / 1_000_000L
        Log.i("AudioAnalysis", "SPEK: decodeMs=${result.decodeMs} fftMs=${result.fftMs} cols=${result.metadata.columns} rows=${result.metadata.rows} renderMs=$renderMs")
        return entity
    }

    private suspend fun appendPcmMono(
        buffer: ByteBuffer,
        offset: Int,
        size: Int,
        channels: Int,
        format: MediaFormat,
        accumulator: StreamingSpectrogramAccumulator,
        onColumn: suspend (SpectrogramColumn) -> Unit,
    ) {
        val encoding = runCatching { format.getInteger(MediaFormat.KEY_PCM_ENCODING) }.getOrDefault(2)
        val bytesPerSample = when (encoding) {
            3 -> 1
            4 -> 4
            else -> 2
        }
        val frameBytes = bytesPerSample * channels.coerceAtLeast(1)
        val duplicate = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        duplicate.position(offset.coerceIn(0, duplicate.limit()))
        val end = (offset + size).coerceAtMost(duplicate.limit())
        while (duplicate.position() + frameBytes <= end) {
            var sum = 0.0
            repeat(channels.coerceAtLeast(1)) {
                sum += when (encoding) {
                    3 -> duplicate.get().toInt() / 128.0
                    4 -> duplicate.float.toDouble()
                    else -> duplicate.short.toInt() / 32768.0
                }
            }
            val column = accumulator.addSample((sum / channels.coerceAtLeast(1)).toFloat())
            if (column != null) onColumn(column)
        }
    }

    private fun columnProgress(index: Int, columns: Int): Float =
        ((index + 1).toFloat() / columns.coerceAtLeast(1)).coerceIn(0f, 1f)

    /* Legacy helpers remain below for compatibility with older callers; new streaming analysis uses SpectrogramQuality. */
    private fun findCutoffBin(spectrumDb: FloatArray, sampleRate: Int): Int? {
        if (spectrumDb.isEmpty()) return null
        val tailStart = (spectrumDb.size * 0.95).toInt().coerceAtMost(spectrumDb.lastIndex)
        val noise = spectrumDb.copyOfRange(tailStart, spectrumDb.size).average().toFloat()
        val referenceStart = ((1_000.0 / sampleRate) * SPECTROGRAM_FFT_SIZE).toInt().coerceAtLeast(1)
        val referenceEnd = ((4_000.0 / sampleRate) * SPECTROGRAM_FFT_SIZE).toInt().coerceAtMost(spectrumDb.lastIndex)
        val reference = spectrumDb.slice(referenceStart..referenceEnd.coerceAtLeast(referenceStart)).average().toFloat()
        val threshold = max(noise + 10f, reference - 50f)
        var stable = 0
        for (bin in spectrumDb.lastIndex downTo 1) {
            if (spectrumDb[bin] >= threshold) {
                stable++
                if (stable >= 6) return bin + 5
            } else stable = 0
        }
        return null
    }

    private fun localSlope(spectrumDb: FloatArray, cutoffBin: Int, sampleRate: Int): Double? {
        val lower = (cutoffBin - 2_000.0 / sampleRate * SPECTROGRAM_FFT_SIZE).toInt().coerceAtLeast(1)
        if (cutoffBin !in spectrumDb.indices || lower !in spectrumDb.indices) return null
        return (spectrumDb[cutoffBin] - spectrumDb[lower]).toDouble() / 2.0
    }

    private fun computeStableKey(uriString: String): String = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(Uri.parse(uriString))!!.use { input ->
            val buffer = ByteArray(64 * 1024)
            val read = input.read(buffer).coerceAtLeast(0)
            if (read > 0) digest.update(buffer, 0, read)
        }
        digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }.getOrElse { uriString.hashCode().toUInt().toString(16) }
}

private fun MediaFormat.getLongOrNull(key: String): Long? = runCatching { getLong(key) }.getOrNull()

private fun bitDepth(format: MediaFormat): Int? = when (runCatching { format.getInteger(MediaFormat.KEY_PCM_ENCODING) }.getOrNull()) {
    2 -> 16
    3 -> 8
    4 -> 32
    else -> null
}
