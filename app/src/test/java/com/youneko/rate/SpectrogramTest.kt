package com.youneko.rate

import com.youneko.rate.data.audio.AudioDecodedFormat
import com.youneko.rate.data.audio.AudioQualityMetrics
import com.youneko.rate.data.audio.averageDb
import com.youneko.rate.data.audio.SPECTROGRAM_FFT_SIZE
import com.youneko.rate.data.audio.SpectrogramMath
import com.youneko.rate.data.audio.SpectrogramQuality
import com.youneko.rate.data.audio.StreamingSpectrogramAccumulator
import com.youneko.rate.data.audio.SpectralAnalyzer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpectrogramTest {
    @Test
    fun targetColumnsClampAndAdaptiveHopAreStable() {
        assertEquals(800, SpectrogramMath.targetColumns(1_000))
        assertEquals(1_920, SpectrogramMath.targetColumns(240_000))
        assertEquals(2_000, SpectrogramMath.targetColumns(600_000))
        assertEquals(6_000L, SpectrogramMath.hopFrames(11_520_000L, 240_000))
    }

    @Test
    fun oneKilohertzToneProducesOneStablePooledLine() {
        val sampleRate = 48_000
        val accumulator = StreamingSpectrogramAccumulator(sampleRate, sampleRate.toLong(), 1_000)
        repeat(sampleRate) { index -> accumulator.addSample(sin(2.0 * PI * 1_000.0 * index / sampleRate).toFloat()) }
        accumulator.finish()
        val result = accumulator.result()
        val middle = result.dbMatrix.copyOfRange((result.metadata.columns / 2) * result.metadata.rows, (result.metadata.columns / 2 + 1) * result.metadata.rows)
        val peakRow = middle.indices.maxByOrNull { middle[it].toInt() and 0xff } ?: 0
        val peakHz = peakRow.toDouble() / (result.metadata.rows - 1) * sampleRate / 2.0
        println("SPEK_TONE_1K: peakHz=$peakHz")
        assertTrue("peakHz=$peakHz", abs(peakHz - 1_000.0) < 250.0)
    }

    @Test
    fun sineSweepTracksTimeAndFrequencyWithinPooledResolution() {
        val sampleRate = 48_000
        val durationMs = 2_000L
        val total = sampleRate * 2
        val accumulator = StreamingSpectrogramAccumulator(sampleRate, total.toLong(), durationMs)
        var phase = 0.0
        repeat(total) { index ->
            val frequency = 20.0 + 20_000.0 * index / (total - 1).toDouble()
            phase += 2.0 * PI * frequency / sampleRate
            accumulator.addSample(sin(phase).toFloat())
        }
        accumulator.finish()
        val result = accumulator.result()
        val checkpoints = listOf(0.15, 0.5, 0.85)
        checkpoints.forEach { position ->
            val column = (position * (result.metadata.columns - 1)).toInt()
            val start = column * result.metadata.rows
            val peakRow = (0 until result.metadata.rows).maxByOrNull { result.dbMatrix[start + it].toInt() and 0xff } ?: 0
            val measured = peakRow.toDouble() / (result.metadata.rows - 1) * sampleRate / 2.0
            val expected = 20.0 + 20_000.0 * position
            assertTrue("position=$position expected=$expected measured=$measured", abs(measured - expected) < 1_500.0)
        }
        println("SPEK_SWEEP: columns=${result.metadata.columns} rows=${result.metadata.rows} checkpoints=PASS")
    }

    @Test
    fun lowpassSixteenKilohertzCutoffErrorIsAtMostThreeHundredHertz() {
        val sampleRate = 48_000
        val spectrum = FloatArray(SPECTROGRAM_FFT_SIZE / 2 + 1) { bin ->
            val frequency = bin.toDouble() * sampleRate / SPECTROGRAM_FFT_SIZE
            if (frequency <= 16_000.0) 0f else -100f
        }
        val estimate = SpectrogramQuality.estimateCutoff(spectrum, sampleRate)
        val error = abs((estimate.frequencyHz ?: 0.0) - 16_000.0)
        println("SPEK_LOWPASS_16K: cutoffHz=${estimate.frequencyHz} errorHz=$error")
        assertTrue("cutoff=${estimate.frequencyHz} error=$error", error <= 300.0)
    }

    @Test
    fun silenceHasNoNaNOrInfinityAndMapsToBackground() {
        val accumulator = StreamingSpectrogramAccumulator(48_000, 48_000, 1_000)
        repeat(48_000) { accumulator.addSample(0f) }
        accumulator.finish()
        val result = accumulator.result()
        assertTrue(result.dbMatrix.all { (it.toInt() and 0xff) == 0 })
        assertFalse(result.averageDb().any { it.isNaN() || it.isInfinite() })
    }

    @Test
    fun brokenOrEmptyInputFallsBackToVietnameseUnknownWithoutNaN() {
        val metrics = SpectralAnalyzer.analyze(FloatArray(0), 48_000)
        println("SPEK_BROKEN_INPUT: verdict=${metrics.verdict} reason=${metrics.reasons.firstOrNull()}")
        assertEquals("KHÔNG XÁC ĐỊNH", metrics.verdict)
        assertTrue(metrics.reasons.any { it.contains("Không đủ mẫu", ignoreCase = true) })
    }

    @Test
    fun realLosslessFormatAt48k24bitIsNotMarkedLossyWhenSpectrumReaches235k() {
        val sampleRate = 48_000
        val spectrum = FloatArray(SPECTROGRAM_FFT_SIZE / 2 + 1) { bin ->
            val frequency = bin.toDouble() * sampleRate / SPECTROGRAM_FFT_SIZE
            if (frequency <= 23_500.0) 0f else -100f
        }
        val format = AudioDecodedFormat("flac", "audio/flac", sampleRate, 2, 24, null, 1_000)
        val base = AudioQualityMetrics(null, null, null, null, null, "KHÔNG XÁC ĐỊNH", 0, emptyList(), spectrum.toList())
        val result = SpectrogramQuality.enrich(format, SpectralAnalyzer.verdict(format, base), spectrum, lsbNonZeroRatio = 0.5)
        println("SPEK_FLAC_48_24: verdict=${result.verdict} cutoffHz=${result.cutoffHz} warnings=${result.reasons.size}")
        assertFalse(result.verdict.contains("LOSSY"))
        assertFalse(result.verdict.contains("NÂNG CẤP GIẢ"))
    }
}
