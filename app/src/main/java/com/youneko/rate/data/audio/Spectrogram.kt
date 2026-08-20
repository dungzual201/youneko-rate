package com.youneko.rate.data.audio

import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import org.jtransforms.fft.DoubleFFT_1D

const val SPECTROGRAM_FFT_SIZE = 4096
const val SPECTROGRAM_ROWS = 1024
const val SPECTROGRAM_DB_FLOOR = -120f
const val SPECTROGRAM_DB_CEILING = 0f

@Serializable
data class SpectrogramMetadata(
    val fftSize: Int = SPECTROGRAM_FFT_SIZE,
    val hopFrames: Long,
    val sampleRate: Int,
    val codec: String? = null,
    val channels: Int = 1,
    val bitDepth: Int? = null,
    val bitrate: Long? = null,
    val totalFrames: Long,
    val durationMs: Long,
    val columns: Int,
    val rows: Int = SPECTROGRAM_ROWS,
    val dbFloor: Float = SPECTROGRAM_DB_FLOOR,
    val dbCeiling: Float = SPECTROGRAM_DB_CEILING,
    val stableKey: String? = null,
)

data class SpectrogramColumn(
    val index: Int,
    val dbQuantized: ByteArray,
)

data class SpectrogramResult(
    val metadata: SpectrogramMetadata,
    val dbMatrix: ByteArray,
    val averageMagnitude: DoubleArray,
    val frameCount: Int,
    val sampleCount: Long,
    val sumSquares: Double,
    val peak: Double,
    val clippedSamples: Long,
    val decodeMs: Long = 0L,
    val fftMs: Long = 0L,
    val percentileSpectrumDb: FloatArray = floatArrayOf(),
    val activeFrameSpectra: List<FloatArray> = emptyList(),
)

sealed interface SpectrogramEvent {
    data class Header(val metadata: SpectrogramMetadata) : SpectrogramEvent
    data class Column(val value: SpectrogramColumn, val progress: Float) : SpectrogramEvent
    data class Progress(val decodedFrames: Long, val totalFrames: Long, val progress: Float) : SpectrogramEvent
    data class Completed(val result: SpectrogramResult) : SpectrogramEvent
}

object SpectrogramMath {
    fun targetColumns(durationMs: Long): Int =
        ((durationMs.coerceAtLeast(1L) / 1_000.0) * 8.0).roundToInt().coerceIn(800, 2_000)

    fun hopFrames(totalFrames: Long, durationMs: Long): Long =
        max(1L, totalFrames.coerceAtLeast(1L) / targetColumns(durationMs))

    fun quantizeDb(db: Float): Byte {
        val normalized = ((db.coerceIn(SPECTROGRAM_DB_FLOOR, SPECTROGRAM_DB_CEILING) - SPECTROGRAM_DB_FLOOR) /
            (SPECTROGRAM_DB_CEILING - SPECTROGRAM_DB_FLOOR)) * 255f
        return normalized.roundToInt().coerceIn(0, 255).toByte()
    }

    fun dequantizeDb(value: Byte): Float =
        SPECTROGRAM_DB_FLOOR + ((value.toInt() and 0xff) / 255f) * (SPECTROGRAM_DB_CEILING - SPECTROGRAM_DB_FLOOR)

    fun frequencyForRow(row: Int, rows: Int, sampleRate: Int, logarithmic: Boolean): Double {
        val nyquist = sampleRate.coerceAtLeast(1) / 2.0
        val normalized = (row.coerceIn(0, rows - 1).toDouble() / (rows - 1).coerceAtLeast(1)).coerceIn(0.0, 1.0)
        return if (!logarithmic) normalized * nyquist
        else {
            val minimum = 20.0
            minimum * (nyquist / minimum).pow(normalized)
        }
    }

    fun timeForColumn(column: Int, columns: Int, durationMs: Long): Long =
        ((column.coerceIn(0, columns - 1).toDouble() / (columns - 1).coerceAtLeast(1)) * durationMs).roundToInt().toLong()

    private fun Double.pow(exponent: Double): Double = kotlin.math.exp(kotlin.math.ln(this) * exponent)
}

/**
 * A bounded ring window. It never retains the complete decoded PCM stream.
 * The caller feeds mono samples and receives one quantized column when a frame is ready.
 */
class StreamingSpectrogramAccumulator(
    private val sampleRate: Int,
    private val totalFrames: Long,
    private val durationMs: Long,
    stableKey: String? = null,
    private val codec: String? = null,
    private val channels: Int = 1,
    private val bitDepth: Int? = null,
    private val bitrate: Long? = null,
) {
    private val fftSize = SPECTROGRAM_FFT_SIZE
    private val halfBins = fftSize / 2 + 1
    private val rows = SPECTROGRAM_ROWS
    private val columnsTarget = SpectrogramMath.targetColumns(durationMs)
    private val hop = SpectrogramMath.hopFrames(totalFrames, durationMs)
    private val window = SpectralAnalyzer.hann(fftSize)
    private val ring = FloatArray(fftSize)
    private val ordered = FloatArray(fftSize)
    private val fft = DoubleArray(fftSize)
    private val magnitude = DoubleArray(halfBins)
    private val db = FloatArray(halfBins)
    private val matrix = ByteArray(columnsTarget * rows)
    private val averageMagnitude = DoubleArray(halfBins)
    private var ringStart = 0
    private var ringSize = 0
    private var skipSamples = 0L
    private var receivedSamples = 0L
    private var frameCount = 0
    private var columnCount = 0
    private var sampleCount = 0L
    private var sumSquares = 0.0
    private var peak = 0.0
    private var clippedSamples = 0L
    private var fftNanos = 0L
    private val activeFrameSpectra = ArrayList<FloatArray>()

    val metadata: SpectrogramMetadata = SpectrogramMetadata(
        hopFrames = hop,
        sampleRate = sampleRate,
        codec = codec,
        channels = channels,
        bitDepth = bitDepth,
        bitrate = bitrate,
        totalFrames = totalFrames,
        durationMs = durationMs,
        columns = columnsTarget,
        rows = rows,
        stableKey = stableKey,
    )

    fun addSample(value: Float): SpectrogramColumn? {
        val sample = value.coerceIn(-1f, 1f)
        val absolute = kotlin.math.abs(sample.toDouble())
        sumSquares += absolute * absolute
        peak = max(peak, absolute)
        if (absolute >= 0.999) clippedSamples++
        sampleCount++
        receivedSamples++
        if (skipSamples > 0L) {
            skipSamples--
            return null
        }
        ring[(ringStart + ringSize) % fftSize] = sample
        ringSize++
        if (ringSize < fftSize) return null
        val column = processFrame()
        consumeHop()
        return column
    }

    fun samplesSeen(): Long = sampleCount

    fun finish(): List<SpectrogramColumn> {
        if (columnCount >= columnsTarget) return emptyList()
        val columns = ArrayList<SpectrogramColumn>(columnsTarget - columnCount)
        while (columnCount < columnsTarget) {
            while (ringSize < fftSize) {
                ring[(ringStart + ringSize) % fftSize] = 0f
                ringSize++
            }
            columns += processFrame()
            consumeHop()
        }
        return columns
    }

    fun result(): SpectrogramResult = SpectrogramResult(
        metadata = metadata.copy(columns = columnCount.coerceAtLeast(1)),
        dbMatrix = matrix.copyOf(columnCount.coerceAtLeast(1) * rows),
        averageMagnitude = averageMagnitude.copyOf(),
        frameCount = frameCount,
        sampleCount = sampleCount,
        sumSquares = sumSquares,
        peak = peak,
        clippedSamples = clippedSamples,
        fftMs = fftNanos / 1_000_000L,
        percentileSpectrumDb = percentile95Db(activeFrameSpectra, halfBins),
        activeFrameSpectra = activeFrameSpectra.map { it.copyOf() },
    )

    private fun processFrame(): SpectrogramColumn {
        for (index in 0 until fftSize) {
            val value = ring[(ringStart + index) % fftSize]
            ordered[index] = value
            fft[index] = value.toDouble() * window[index]
        }
        val fftStart = System.nanoTime()
        DoubleFFT_1D(fftSize.toLong()).realForward(fft)
        for (bin in 0 until halfBins) {
            val magnitudeValue = when (bin) {
                0 -> kotlin.math.abs(fft[0])
                fftSize / 2 -> kotlin.math.abs(fft[1])
                else -> kotlin.math.hypot(fft[2 * bin], fft[2 * bin + 1])
            }
            magnitude[bin] = magnitudeValue
            averageMagnitude[bin] += magnitudeValue
            db[bin] = (20.0 * log10((magnitudeValue / (fftSize / 2.0)).coerceAtLeast(1e-12)))
                .toFloat()
                .coerceIn(SPECTROGRAM_DB_FLOOR, SPECTROGRAM_DB_CEILING)
        }
        val columnIndex = columnCount++
        val column = ByteArray(rows)
        for (row in 0 until rows) {
            val start = floor(row.toDouble() * halfBins / rows).toInt().coerceIn(0, halfBins - 1)
            val end = (ceil((row + 1).toDouble() * halfBins / rows).toInt() - 1).coerceIn(start, halfBins - 1)
            var maxDb = SPECTROGRAM_DB_FLOOR
            for (bin in start..end) maxDb = max(maxDb, db[bin])
            column[row] = SpectrogramMath.quantizeDb(maxDb)
            matrix[columnIndex * rows + row] = column[row]
        }
        fftNanos += System.nanoTime() - fftStart
        val frameRms = kotlin.math.sqrt(ordered.sumOf { it.toDouble() * it.toDouble() } / fftSize.coerceAtLeast(1))
        if (20.0 * kotlin.math.log10(frameRms.coerceAtLeast(1e-12)) >= -60.0) {
            activeFrameSpectra += db.copyOf()
        }
        frameCount++
        return SpectrogramColumn(columnIndex, column)
    }

    private fun consumeHop() {
        if (hop >= fftSize) {
            ringStart = 0
            ringSize = 0
            skipSamples = hop - fftSize
        } else {
            ringStart = (ringStart + hop.toInt()) % fftSize
            ringSize -= hop.toInt()
        }
    }
}

private fun percentile95Db(frames: List<FloatArray>, bins: Int): FloatArray {
    if (frames.isEmpty()) return FloatArray(bins) { SPECTROGRAM_DB_FLOOR }
    return FloatArray(bins) { bin ->
        val values = FloatArray(frames.size) { index -> frames[index].getOrElse(bin) { SPECTROGRAM_DB_FLOOR } }
        values.sort()
        values[((values.lastIndex) * 0.95).roundToInt().coerceIn(0, values.lastIndex)]
    }
}

fun SpectrogramResult.averageDb(): FloatArray {
    val divisor = frameCount.coerceAtLeast(1).toDouble()
    val peakMagnitude = averageMagnitude.maxOrNull()?.coerceAtLeast(1e-12) ?: 1e-12
    return averageMagnitude.map { value ->
        (20.0 * kotlin.math.log10((value / divisor / peakMagnitude).coerceAtLeast(1e-12)))
            .toFloat()
            .coerceIn(SPECTROGRAM_DB_FLOOR, SPECTROGRAM_DB_CEILING)
    }.toFloatArray()
}

fun Flow<SpectrogramEvent>.columnsOnly(): Flow<SpectrogramColumn> = flow {
    collect { event -> if (event is SpectrogramEvent.Column) emit(event.value) }
}

fun formatSpectrogramTime(ms: Long): String {
    val seconds = (ms / 1_000L).coerceAtLeast(0L)
    return String.format(Locale.US, "%d:%02d", seconds / 60L, seconds % 60L)
}
