package com.youneko.rate

import com.youneko.rate.data.audio.AudioDecodedFormat
import com.youneko.rate.data.audio.SpectralAnalyzer
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
    fun losslessWithEarlyCutoffIsSuspicious() {
        val base = SpectralAnalyzer.analyze(FloatArray(4096) { 0.1f }, 48_000)
        val metrics = base.copy(cutoffHz = 12_000.0, rolloffSlope = -180.0)
        val result = SpectralAnalyzer.verdict(
            AudioDecodedFormat("wav", "audio/flac", 48_000, 2, 24, null, 1_000),
            metrics,
        )
        assertEquals("NGHI NGỜ NÂNG CẤP GIẢ", result.verdict)
    }
}
