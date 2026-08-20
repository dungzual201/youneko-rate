package com.youneko.rate.data.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecList
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
        var sourcePfd: android.os.ParcelFileDescriptor? = null
        try {
            val openedPfd = context.contentResolver.openFileDescriptor(uri, "r")
                ?: throw AnalyzeInputException.AccessDenied
            sourcePfd = openedPfd
            extractor.setDataSource(openedPfd.fileDescriptor)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME).orEmpty().startsWith("audio/")
            } ?: error("Không tìm thấy track audio")
            extractor.selectTrack(trackIndex)
            val sourceFormat = extractor.getTrackFormat(trackIndex)
            val mime = sourceFormat.getString(MediaFormat.KEY_MIME) ?: throw AnalyzeInputException.UnsupportedFormat("audio/không rõ")
            copyCodecConfigBuffers(sourceFormat)
            val sampleRate = sourceFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = sourceFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val codecName = MediaCodecList(MediaCodecList.REGULAR_CODECS).findDecoderForFormat(sourceFormat)
                ?: throw AnalyzeInputException.NoDecoder(mime)
            val durationUs = sourceFormat.getLong(MediaFormat.KEY_DURATION).coerceAtLeast(1L)
            val durationMs = durationUs / 1_000L
            val totalFrames = (durationUs * sampleRate / 1_000_000L).coerceAtLeast(1L)
            val codecInfo = CodecDetector.detect(
                context = context, uri = uri, sourceMime = mime, sampleRate = sampleRate, channels = channels,
                durationMs = durationMs, sourceFormatBitDepth = bitDepth(sourceFormat),
                trackBitrate = sourceFormat.getLongOrNull(MediaFormat.KEY_BIT_RATE),
            )
            val metadata = SpectrogramMetadata(
                hopFrames = SpectrogramMath.hopFrames(totalFrames, durationMs),
                sampleRate = sampleRate,
                container = codecInfo.container,
                codec = codecInfo.codecLabel,
                sourceMime = codecInfo.sourceMime,
                codecDetectionSource = codecInfo.detectionSource,
                channels = channels,
                bitDepth = codecInfo.bitDepth,
                bitrate = codecInfo.bitrate,
                bitrateNote = codecInfo.bitrateNote,
                theoreticalBitrate = codecInfo.theoreticalBitrate,
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
                codec = codecInfo.codecLabel,
                sourceMime = codecInfo.sourceMime,
                codecDetectionSource = codecInfo.detectionSource,
                channels = channels,
                bitDepth = codecInfo.bitDepth,
                bitrate = codecInfo.bitrate,
                bitrateNote = codecInfo.bitrateNote,
                theoreticalBitrate = codecInfo.theoreticalBitrate,
            )
            decoder = MediaCodec.createByCodecName(codecName)
            decoder.configure(sourceFormat, null, null, 0)
            decoder.start()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var outputFormat = sourceFormat
            var outputSampleRate = sampleRate
            var outputChannels = channels
            var outputPcmEncoding = pcmEncoding(sourceFormat)
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
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        outputFormat = decoder.outputFormat
                        outputSampleRate = runCatching { outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE) }.getOrNull() ?: outputSampleRate
                        outputChannels = runCatching { outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) }.getOrNull() ?: outputChannels
                        outputPcmEncoding = pcmEncoding(outputFormat) ?: outputPcmEncoding
                        Log.i("ANALYZE", "format_changed uri=$uri mime=$mime sampleRate=$outputSampleRate ch=$outputChannels pcmEnc=${outputPcmEncoding ?: "unknown"}")
                    }
                    else -> if (outputIndex >= 0) {
                        decoder.getOutputBuffer(outputIndex)?.let { buffer ->
                            if (info.size > 0) {
                                appendPcmMono(buffer, info.offset, info.size, outputChannels, outputFormat, accumulator) { column ->
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
            val decodedResult = accumulator.result()
            val decodedMetadata = decodedResult.metadata.copy(
                sampleRate = outputSampleRate,
                channels = outputChannels,
                bitDepth = codecInfo.bitDepth ?: decodedResult.metadata.bitDepth,
                codec = codecInfo.codecLabel,
                sourceMime = codecInfo.sourceMime,
                codecDetectionSource = codecInfo.detectionSource,
                bitrate = codecInfo.bitrate,
                bitrateNote = codecInfo.bitrateNote,
                theoreticalBitrate = codecInfo.theoreticalBitrate,
            )
            emit(SpectrogramEvent.Completed(decodedResult.copy(metadata = decodedMetadata, decodeMs = decodeMs)))
        } finally {
            runCatching { decoder?.stop() }
            decoder?.release()
            extractor.release()
            sourcePfd?.close()
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
        val spectrumDb = result.percentileSpectrumDb.takeIf { it.isNotEmpty() } ?: averageDb
        val cutoff = SpectrogramQuality.estimateCutoff(spectrumDb, sampleRate, result.activeFrameSpectra)
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
            spectrum = spectrumDb.toList(),
            noiseFloorDb = cutoff.noiseFloorDb.toDouble(),
            cliffDb = cutoff.cliffDb,
            quietAboveFraction = cutoff.quietAboveFraction,
            analyzedFrames = cutoff.analyzedFrames,
        )
        val format = AudioDecodedFormat(
            container = containerFromUri(uri),
            codec = metadata.codec,
            sourceMime = metadata.sourceMime,
            codecDetectionSource = metadata.codecDetectionSource,
            sampleRate = sampleRate,
            channels = metadata.channels,
            bitDepth = metadata.bitDepth,
            bitrate = metadata.bitrate,
            bitrateNote = metadata.bitrateNote,
            theoreticalBitrate = metadata.theoreticalBitrate,
            durationMs = metadata.durationMs,
        )
        val metrics = SpectrogramQuality.enrich(format, SpectralAnalyzer.verdict(format, base), spectrumDb, activeFrames = result.activeFrameSpectra)
        val entity = AudioAnalysisEntity(
            id = "$uriString:${System.currentTimeMillis()}",
            trackId = trackId,
            albumId = albumId,
            fileName = uri.lastPathSegment.orEmpty(),
            fileUriOrPath = uriString,
            fileHash = effectiveStableKey,
            container = metadata.container ?: containerFromUri(uri),
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
            noiseFloorDb = metrics.noiseFloorDb,
            cliffDb = metrics.cliffDb,
            quietAboveFraction = metrics.quietAboveFraction,
            analyzedFrames = metrics.analyzedFrames,
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
            0x1000 -> 3
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
                    0x1000 -> readPcm24Packed(duplicate)
                    else -> duplicate.short.toInt() / 32768.0
                }
            }
            val normalized = (sum / channels.coerceAtLeast(1)).coerceIn(-1.0, 1.0)
            val column = accumulator.addSample(normalized.toFloat())
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

private fun containerFromUri(uri: Uri): String? = uri.lastPathSegment?.substringAfterLast('.', "")?.takeIf { it.isNotBlank() }?.lowercase()

private fun bitDepth(format: MediaFormat): Int? = pcmBitDepth(pcmEncoding(format))

private fun pcmEncoding(format: MediaFormat): Int? = runCatching { format.getInteger(MediaFormat.KEY_PCM_ENCODING) }.getOrNull()

private fun readPcm24Packed(buffer: ByteBuffer): Double {
    val b0 = buffer.get().toInt() and 0xff
    val b1 = buffer.get().toInt() and 0xff
    val b2 = buffer.get().toInt()
    val signed = (b0 or (b1 shl 8) or (b2 shl 16)).let { value -> if ((value and 0x800000) != 0) value or -0x1000000 else value }
    return signed / 8_388_608.0
}

private fun pcmBitDepth(encoding: Int?): Int? = when (encoding) {
    2 -> 16
    3 -> 8
    4 -> 32
    0x1000 -> 24
    else -> null
}

private fun copyCodecConfigBuffers(format: MediaFormat) {
    listOf("csd-0", "csd-1").forEach { key ->
        if (format.containsKey(key)) {
            format.getByteBuffer(key)?.let { source ->
                val copy = java.nio.ByteBuffer.allocateDirect(source.remaining())
                val duplicate = source.duplicate()
                copy.put(duplicate)
                copy.flip()
                format.setByteBuffer(key, copy)
            }
        }
    }
}

sealed class AnalyzeInputException(message: String) : Exception(message) {
    data object AccessDenied : AnalyzeInputException("Không có quyền truy cập tệp")
    data class NoDecoder(val mime: String) : AnalyzeInputException("Thiết bị không có bộ giải mã cho định dạng $mime")
    data class UnsupportedFormat(val mime: String) : AnalyzeInputException("Không hỗ trợ định dạng $mime")
}
