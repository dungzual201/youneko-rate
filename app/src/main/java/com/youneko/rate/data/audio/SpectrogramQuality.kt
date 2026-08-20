package com.youneko.rate.data.audio

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

 data class CutoffEstimate(
    val frequencyHz: Double?,
    val confidence: Int,
    val noiseFloorDb: Float,
    val thresholdDb: Float,
    val slopeDbPerKHz: Double?,
    val cliffDb: Double? = null,
    val quietAboveFraction: Double = 0.0,
    val analyzedFrames: Int = 0,
    val energyAboveCutoffRatio: Double? = null,
    val retries: Int = 0,
)

object SpectrogramQuality {
    private const val LREF_LOW_HZ = 1_000.0
    private const val LREF_HIGH_HZ = 5_000.0
    private const val THRESHOLD_DROP_DB = 55.0
    private const val MIN_RUN_BINS = 5
    private const val MAX_RETRIES = 3

    fun estimateCutoff(
        spectrumDb: FloatArray,
        sampleRate: Int,
        activeFrames: List<FloatArray> = emptyList(),
    ): CutoffEstimate {
        if (spectrumDb.isEmpty() || sampleRate <= 0) return CutoffEstimate(null, 0, -120f, -175f, null)
        val measured = percentile95Spectrum(spectrumDb, activeFrames)
        val smoothed = medianSmooth(measured)
        val lref = referenceMedian(smoothed, sampleRate)
        val baseThreshold = lref - THRESHOLD_DROP_DB
        var selected: Candidate? = null
        var retries = 0
        while (retries <= MAX_RETRIES) {
            val threshold = (baseThreshold + retries * 6.0).toFloat()
            val cutoffBin = scanFromNyquist(smoothed, threshold)
            val ratio = cutoffBin?.let { energyAboveCutoffRatio(smoothed, it) }
            selected = Candidate(cutoffBin, threshold, ratio)
            if (ratio == null || ratio <= 0.01 || retries == MAX_RETRIES) break
            retries++
        }
        val cutoffBin = selected?.cutoffBin
        val threshold = selected?.threshold ?: baseThreshold.toFloat()
        val frequency = cutoffBin?.let { SpectralAnalyzer.binToFrequencyHz(it, sampleRate, SPECTROGRAM_FFT_SIZE) }
        val cliff = cutoffBin?.let { cliffDb(smoothed, it, sampleRate) }
        val quiet = cutoffBin?.let { quietAboveFraction(activeFrames, it, threshold) } ?: 0.0
        val noiseFloor = median(smoothed.takeLast((smoothed.size * 0.05).roundToInt().coerceAtLeast(1)))
        val confidence = when {
            frequency == null -> 0
            activeFrames.size < 50 -> 40
            frequency >= sampleRate / 2.0 - 600.0 -> 92
            quiet >= 0.90 && (selected?.energyRatio ?: 1.0) <= 0.01 -> 88
            else -> 64
        }
        val slope = cliff?.div(1.6)
        return CutoffEstimate(
            frequencyHz = frequency,
            confidence = confidence,
            noiseFloorDb = noiseFloor,
            thresholdDb = threshold,
            slopeDbPerKHz = slope,
            cliffDb = cliff,
            quietAboveFraction = quiet,
            analyzedFrames = activeFrames.size,
            energyAboveCutoffRatio = selected?.energyRatio,
            retries = retries,
        )
    }

    fun enrich(
        format: AudioDecodedFormat,
        base: AudioQualityMetrics,
        spectrumDb: FloatArray,
        lsbNonZeroRatio: Double? = null,
        activeFrames: List<FloatArray> = emptyList(),
    ): AudioQualityMetrics {
        val estimate = estimateCutoff(spectrumDb, format.sampleRate, activeFrames)
        val metrics = base.copy(
            cutoffHz = estimate.frequencyHz ?: base.cutoffHz,
            rolloffSlope = estimate.slopeDbPerKHz ?: base.rolloffSlope,
            confidence = estimate.confidence,
            noiseFloorDb = estimate.noiseFloorDb.toDouble(),
            cliffDb = estimate.cliffDb,
            quietAboveFraction = estimate.quietAboveFraction,
            analyzedFrames = estimate.analyzedFrames,
            energyAboveCutoffRatio = estimate.energyAboveCutoffRatio,
            cutoffRetries = estimate.retries,
        )
        val verdicted = SpectralAnalyzer.verdict(format, metrics)
        val warnings = buildList {
            if (verdicted.verdict == "CÓ DẤU HIỆU NGUỒN LOSSY") {
                add("Có vách phổ dốc và vùng cao im lặng; đây là dấu hiệu nghi ngờ nguồn lossy, không phải kết luận tuyệt đối.")
            }
            if (verdicted.verdict == "NGHI NGỜ UPSAMPLE") {
                add("Tần số lấy mẫu cao nhưng không có năng lượng đáng kể trên 22.05 kHz.")
            }
            if (format.bitDepth == 24 && lsbNonZeroRatio != null && lsbNonZeroRatio < 0.01) {
                add("Đã kiểm tra LSB trên PCM 24-bit thực và thấy phần thấp gần như luôn bằng 0; có thể là 24-bit giả.")
            }
            if ((estimate.energyAboveCutoffRatio ?: 0.0) > 0.01 || estimate.quietAboveFraction < 0.90) {
                add("Không đo được tần số cắt rõ ràng: năng lượng phía trên tần số cắt hoặc số khung im lặng chưa đạt ngưỡng.")
            }
        }
        return verdicted.copy(reasons = (verdicted.reasons + warnings).distinct())
    }

    fun nearLossyCutoff(cutoffHz: Double): Boolean {
        val candidates = doubleArrayOf(16_000.0, 19_000.0, 19_500.0, 20_000.0, 20_500.0, 21_000.0)
        return candidates.any { abs(cutoffHz - it) <= 400.0 }
    }

    fun fake24BitLsbRatio(samples: IntArray): Double? {
        if (samples.isEmpty()) return null
        val nonZero = samples.count { (it and 0xff) != 0 }
        return nonZero.toDouble() / samples.size
    }

    private data class Candidate(val cutoffBin: Int?, val threshold: Float, val energyRatio: Double?)

    private fun percentile95Spectrum(spectrumDb: FloatArray, activeFrames: List<FloatArray>): FloatArray {
        if (activeFrames.isEmpty()) return spectrumDb
        return FloatArray(spectrumDb.size) { bin ->
            val values = activeFrames.map { it.getOrElse(bin) { SPECTROGRAM_DB_FLOOR } }.sorted()
            values[((values.lastIndex) * 0.95).roundToInt().coerceIn(0, values.lastIndex)]
        }
    }

    private fun medianSmooth(values: FloatArray): FloatArray = FloatArray(values.size) { index ->
        val start = (index - 1).coerceAtLeast(0)
        val end = (index + 1).coerceAtMost(values.lastIndex)
        median(values.copyOfRange(start, end + 1).toList())
    }

    private fun referenceMedian(spectrumDb: FloatArray, sampleRate: Int): Float {
        val low = (LREF_LOW_HZ / sampleRate * SPECTROGRAM_FFT_SIZE).roundToInt().coerceIn(0, spectrumDb.lastIndex)
        val high = (LREF_HIGH_HZ / sampleRate * SPECTROGRAM_FFT_SIZE).roundToInt().coerceIn(low, spectrumDb.lastIndex)
        return median(spectrumDb.copyOfRange(low, high + 1).toList())
    }

    private fun scanFromNyquist(spectrumDb: FloatArray, threshold: Float): Int? {
        var run = 0
        for (bin in spectrumDb.lastIndex downTo 0) {
            if (spectrumDb[bin] >= threshold) {
                run++
                if (run >= MIN_RUN_BINS) return (bin + MIN_RUN_BINS - 1).coerceAtMost(spectrumDb.lastIndex)
            } else run = 0
        }
        return null
    }

    private fun energyAboveCutoffRatio(spectrumDb: FloatArray, cutoffBin: Int): Double {
        val total = spectrumDb.sumOf { 10.0.pow(it / 10.0) }.coerceAtLeast(1e-18)
        val above = spectrumDb.drop((cutoffBin + 1).coerceAtMost(spectrumDb.size)).sumOf { 10.0.pow(it / 10.0) }
        return (above / total).coerceIn(0.0, 1.0)
    }

    private fun quietAboveFraction(activeFrames: List<FloatArray>, cutoffBin: Int, threshold: Float): Double {
        if (activeFrames.isEmpty()) return 0.0
        val start = (cutoffBin + 1).coerceAtMost(activeFrames.first().size)
        if (start >= activeFrames.first().size) return 1.0
        val quiet = activeFrames.count { frame ->
            frame.copyOfRange(start, frame.size).maxOrNull()?.let { it < threshold } ?: true
        }
        return quiet.toDouble() / activeFrames.size.toDouble()
    }

    private fun cliffDb(spectrumDb: FloatArray, cutoffBin: Int, sampleRate: Int): Double? {
        val span = (800.0 / sampleRate * SPECTROGRAM_FFT_SIZE).roundToInt().coerceAtLeast(1)
        val leftStart = (cutoffBin - span).coerceAtLeast(0)
        val rightEnd = (cutoffBin + span).coerceAtMost(spectrumDb.lastIndex)
        if (leftStart >= cutoffBin || cutoffBin >= rightEnd) return null
        val left = spectrumDb.copyOfRange(leftStart, cutoffBin).average()
        val right = spectrumDb.copyOfRange(cutoffBin, rightEnd + 1).average()
        return (left - right).coerceAtLeast(0.0)
    }

    private fun median(values: List<Float>): Float {
        if (values.isEmpty()) return -120f
        val sorted = values.sorted()
        return sorted[sorted.size / 2]
    }
}
