package com.youneko.rate.data.audio

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
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
)

object SpectrogramQuality {
    fun estimateCutoff(
        spectrumDb: FloatArray,
        sampleRate: Int,
        activeFrames: List<FloatArray> = emptyList(),
    ): CutoffEstimate {
        if (spectrumDb.isEmpty() || sampleRate <= 0) return CutoffEstimate(null, 0, -120f, -108f, null)
        val measured = percentile95Spectrum(spectrumDb, activeFrames)
        val tailStart = (measured.size * 0.95).toInt().coerceIn(0, measured.lastIndex)
        val highTail = measured.copyOfRange(tailStart, measured.size).sorted()
        val noiseFloor = highTail.getOrElse(highTail.size / 2) { -120f }
        val threshold = noiseFloor + 12f
        val minRun = 3
        var run = 0
        var cutoffBin: Int? = null
        for (bin in measured.lastIndex downTo 0) {
            if (measured[bin] >= threshold) {
                run++
                if (run >= minRun) {
                    cutoffBin = (bin + minRun - 1).coerceAtMost(measured.lastIndex)
                    break
                }
            } else run = 0
        }
        val cutoffHz = cutoffBin?.let { SpectralAnalyzer.binToFrequencyHz(it.coerceAtMost(SPECTROGRAM_FFT_SIZE / 2), sampleRate) }
        val cliff = cutoffBin?.let { cliffDb(measured, it, sampleRate) }
        val quietFraction = if (cutoffBin == null || activeFrames.isEmpty()) 0.0 else {
            activeFrames.count { frame ->
                val start = (cutoffBin + 1).coerceAtMost(frame.lastIndex)
                val high = frame.copyOfRange(start, frame.size).maxOrNull() ?: SPECTROGRAM_DB_FLOOR
                high <= -90f
            }.toDouble() / activeFrames.size.toDouble()
        }
        val confidence = when {
            cutoffHz == null -> 0
            activeFrames.size < 3 -> 45
            cliff != null && cliff >= 40.0 -> 88
            cutoffHz >= sampleRate / 2.0 * 0.90 -> 86
            else -> 68
        }
        return CutoffEstimate(cutoffHz, confidence, noiseFloor, threshold, cliff, cliff, quietFraction, activeFrames.size)
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

    private fun percentile95Spectrum(spectrumDb: FloatArray, activeFrames: List<FloatArray>): FloatArray {
        if (activeFrames.isEmpty()) return spectrumDb
        return FloatArray(spectrumDb.size) { bin ->
            val values = activeFrames.map { it.getOrElse(bin) { SPECTROGRAM_DB_FLOOR } }.sorted()
            values[((values.lastIndex) * 0.95).toInt().coerceIn(0, values.lastIndex)]
        }
    }

    private fun cliffDb(spectrumDb: FloatArray, cutoffBin: Int, sampleRate: Int): Double? {
        val span = (500.0 / sampleRate * SPECTROGRAM_FFT_SIZE).roundToInt().coerceAtLeast(1)
        val leftStart = (cutoffBin - span).coerceAtLeast(0)
        val rightEnd = (cutoffBin + span).coerceAtMost(spectrumDb.lastIndex)
        if (leftStart >= cutoffBin || cutoffBin >= rightEnd) return null
        val left = spectrumDb.copyOfRange(leftStart, cutoffBin + 1).average()
        val right = spectrumDb.copyOfRange(cutoffBin, rightEnd + 1).average()
        return (left - right).toDouble()
    }

}
