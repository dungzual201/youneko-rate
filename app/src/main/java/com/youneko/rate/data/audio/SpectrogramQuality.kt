package com.youneko.rate.data.audio

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

 data class CutoffEstimate(
    val frequencyHz: Double?,
    val confidence: Int,
    val noiseFloorDb: Float,
    val thresholdDb: Float,
    val slopeDbPerKHz: Double?,
)

object SpectrogramQuality {
    fun estimateCutoff(spectrumDb: FloatArray, sampleRate: Int): CutoffEstimate {
        if (spectrumDb.isEmpty() || sampleRate <= 0) return CutoffEstimate(null, 0, -120f, -110f, null)
        val tailStart = (spectrumDb.size * 0.95).toInt().coerceIn(0, spectrumDb.lastIndex)
        val noiseFloor = spectrumDb.copyOfRange(tailStart, spectrumDb.size).average().toFloat()
        val referenceStart = ((1_000.0 / sampleRate) * SPECTROGRAM_FFT_SIZE).toInt().coerceAtLeast(1)
        val referenceEnd = ((4_000.0 / sampleRate) * SPECTROGRAM_FFT_SIZE).toInt().coerceIn(referenceStart, spectrumDb.lastIndex)
        val reference = spectrumDb.slice(referenceStart..referenceEnd).average().toFloat()
        val threshold = max(noiseFloor + 10f, reference - 50f)
        var stable = 0
        var cutoffBin: Int? = null
        for (bin in spectrumDb.lastIndex downTo 1) {
            if (spectrumDb[bin] >= threshold) {
                stable++
                if (stable >= 6) {
                    cutoffBin = bin + 5
                    break
                }
            } else stable = 0
        }
        val cutoffHz = cutoffBin?.let { SpectralAnalyzer.binToFrequencyHz(it.coerceAtMost(SPECTROGRAM_FFT_SIZE / 2), sampleRate) }
        val slope = cutoffBin?.let { index ->
            val lower = (index - 2_000.0 / sampleRate * SPECTROGRAM_FFT_SIZE).toInt().coerceAtLeast(1)
            if (lower in spectrumDb.indices && index in spectrumDb.indices) (spectrumDb[index] - spectrumDb[lower]).toDouble() / 2.0 else null
        }
        val confidence = when {
            cutoffHz == null -> 0
            slope != null && slope <= -30.0 -> 90
            cutoffHz >= sampleRate / 2.0 * 0.95 -> 88
            else -> 60
        }
        return CutoffEstimate(cutoffHz, confidence, noiseFloor, threshold, slope)
    }

    fun enrich(
        format: AudioDecodedFormat,
        base: AudioQualityMetrics,
        spectrumDb: FloatArray,
        lsbNonZeroRatio: Double? = null,
    ): AudioQualityMetrics {
        val estimate = estimateCutoff(spectrumDb, format.sampleRate)
        val metrics = base.copy(
            cutoffHz = estimate.frequencyHz ?: base.cutoffHz,
            rolloffSlope = estimate.slopeDbPerKHz ?: base.rolloffSlope,
            confidence = max(base.confidence, estimate.confidence),
        )
        val verdicted = SpectralAnalyzer.verdict(format, metrics)
        val warnings = buildList {
            val cutoff = metrics.cutoffHz
            val slope = metrics.rolloffSlope
            if (cutoff != null && slope != null && slope <= -30.0 && nearLossyCutoff(cutoff)) {
                add("Có dấu hiệu nguồn lossy quanh %.1f kHz; đây là cảnh báo heuristic, không phải kết luận tuyệt đối.".format(java.util.Locale.US, cutoff / 1_000.0))
            }
            if (cutoff != null && (format.sampleRate == 48_000 || format.sampleRate == 96_000) && cutoff < 22_050.0) {
                add("Có dấu hiệu upsample giả: file ${format.sampleRate / 1_000} kHz không có năng lượng đáng kể trên 22.05 kHz.")
            }
            if (format.bitDepth == 24 && lsbNonZeroRatio != null && lsbNonZeroRatio < 0.01) {
                add("File khai 24-bit nhưng LSB gần như luôn bằng 0; có thể là 24-bit giả.")
            }
            if (cutoff != null) {
                add("Cutoff phổ trung bình %.1f kHz, độ tin cậy %d%% (ngưỡng: nền nhiễu + 10 dB).".format(java.util.Locale.US, cutoff / 1_000.0, estimate.confidence))
            }
        }
        return verdicted.copy(
            confidence = max(verdicted.confidence, estimate.confidence),
            reasons = (verdicted.reasons + warnings).distinct(),
        )
    }

    fun nearLossyCutoff(cutoffHz: Double): Boolean {
        val candidates = doubleArrayOf(16_000.0, 19_000.0, 20_000.0, 20_500.0, 22_000.0)
        return candidates.any { abs(cutoffHz - it) <= 700.0 }
    }

    fun fake24BitLsbRatio(samples: IntArray): Double? {
        if (samples.isEmpty()) return null
        val nonZero = samples.count { (it and 0xff) != 0 }
        return nonZero.toDouble() / samples.size
    }
}
