package com.youneko.rate

import com.youneko.rate.data.audio.AudioDecodedFormat
import com.youneko.rate.data.audio.SpectralAnalyzer
import org.jtransforms.fft.DoubleFFT_1D
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioAnalysisTest {
    @Test
    fun fftFindsOneKilohertzNearExpectedBin() {
        val sampleRate = 48_000
        val samples = DoubleArray(4096) { index -> sin(2.0 * PI * 1_000.0 * index / sampleRate) }
        val bin = SpectralAnalyzer.peakBin(samples)
        assertTrue("bin=$bin", bin in 84..86)
    }

    @Test
    fun realFftBinToHzUsesSampleRateDividedByFftSize() {
        val oneK = DoubleArray(4096) { index -> sin(2.0 * PI * 1_000.0 * index / 44_100.0) }
        val fifteenK = DoubleArray(4096) { index -> sin(2.0 * PI * 15_000.0 * index / 44_100.0) }
        assertEquals(1_000.0, SpectralAnalyzer.peakFrequencyHz(oneK, 44_100), 11.0)
        assertEquals(15_000.0, SpectralAnalyzer.peakFrequencyHz(fifteenK, 44_100), 11.0)
        assertEquals(1_000.0, SpectralAnalyzer.peakFrequencyHz(DoubleArray(4096) { index -> sin(2.0 * PI * 1_000.0 * index / 48_000.0) }, 48_000), 11.0)
        assertEquals(22_050.0, SpectralAnalyzer.binToFrequencyHz(2048, 44_100), 0.001)
        assertEquals(24_000.0, SpectralAnalyzer.binToFrequencyHz(2048, 48_000), 0.001)
    }

    @Test
    fun sineSweepToNyquistProducesHighCutoff() {
        val sampleRate = 44_100
        val seconds = 5
        val total = sampleRate * seconds
        val startHz = 20.0
        val endHz = 22_000.0
        val samples = FloatArray(total)
        var phase = 0.0
        for (index in samples.indices) {
            val progress = index.toDouble() / (total - 1)
            val frequency = startHz + (endHz - startHz) * progress
            phase += 2.0 * PI * frequency / sampleRate
            samples[index] = sin(phase).toFloat()
        }
        val metrics = SpectralAnalyzer.analyze(samples, sampleRate)
        assertTrue("cutoff=${metrics.cutoffHz}", (metrics.cutoffHz ?: 0.0) >= 21_000.0)
    }

    @Test
    fun lowPassSixteenKilohertzProducesMediumLossyCutoff() {
        val sampleRate = 48_000
        val frame = DoubleArray(4096)
        val maxBin = (16_000.0 / sampleRate * 4096).toInt()
        for (bin in 1..maxBin) {
            val phase = (bin * 37 % 360) * PI / 180.0
            frame[2 * bin] = kotlin.math.cos(phase) * 0.01
            frame[2 * bin + 1] = kotlin.math.sin(phase) * 0.01
        }
        DoubleFFT_1D(4096L).realInverse(frame, true)
        val samples = FloatArray(sampleRate * 2) { frame[it % frame.size].toFloat() }
        val metrics = SpectralAnalyzer.analyze(samples, sampleRate)
        val final = SpectralAnalyzer.verdict(AudioDecodedFormat("mp3", "audio/mpeg", sampleRate, 2, null, 128, 2_000), metrics)
        assertTrue("cutoff=${final.cutoffHz}", (final.cutoffHz ?: 0.0) in 15_500.0..16_500.0)
        assertTrue(final.reasons.isNotEmpty())
    }

    @Test
    fun metricsDetectClippingAndTruePeak() {
        val samples = FloatArray(8192) { if (it % 31 == 0) 1f else 0.2f }
        val metrics = SpectralAnalyzer.analyze(samples, 48_000)
        assertTrue(metrics.clippingPercent!! > 0.0)
        assertEquals(0.0, metrics.truePeakDbtp!!, 0.01)
    }

    @Test
    fun emptyInputIsUnknownWithReason() {
        val metrics = SpectralAnalyzer.analyze(FloatArray(0), 48_000)
        assertEquals("KHÔNG XÁC ĐỊNH", metrics.verdict)
        assertTrue(metrics.reasons.isNotEmpty())
    }

    @Test
    fun silenceIsUnknownWithExplicitSilenceReason() {
        val metrics = SpectralAnalyzer.analyze(FloatArray(48_000), 48_000)
        assertEquals("KHÔNG XÁC ĐỊNH", metrics.verdict)
        assertTrue(metrics.reasons.any { it.contains("im lặng", ignoreCase = true) })
    }

    @Test
    fun decodedNonSilentWithoutSteepWallStaysUndecidedBelowConfidenceThreshold() {
        val metrics = SpectralAnalyzer.analyze(FloatArray(4096) { if (it % 2 == 0) 0.2f else -0.2f }, 48_000)
            .copy(cutoffHz = 18_200.0, rolloffSlope = 0.0)
        val result = SpectralAnalyzer.verdict(AudioDecodedFormat("mp3", "audio/mpeg", 48_000, 2, null, 192, 1_000), metrics)
        assertEquals("CHƯA ĐỦ DỮ LIỆU ĐỂ KẾT LUẬN", result.verdict)
        assertTrue(result.confidence < 70)
        assertTrue(result.reasons.any { it.contains("Chưa đủ dữ liệu", ignoreCase = true) })
    }

    @Test
    fun losslessWithThreeConditionsNearLossyCutoffIsSuspicious() {
        val base = SpectralAnalyzer.analyze(FloatArray(4096) { 0.1f }, 48_000)
        val metrics = base.copy(cutoffHz = 20_000.0, rolloffSlope = -180.0, cliffDb = 45.0, quietAboveFraction = 0.95, analyzedFrames = 10)
        val result = SpectralAnalyzer.verdict(
            AudioDecodedFormat("wav", "audio/flac", 48_000, 2, 24, null, 1_000),
            metrics,
        )
        assertEquals("CÓ DẤU HIỆU NGUỒN LOSSY", result.verdict)
    }
}
