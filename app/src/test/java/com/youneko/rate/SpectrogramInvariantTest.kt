package com.youneko.rate

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpectrogramInvariantTest {
    private fun repoFile(relative: String): File = File(System.getProperty("user.dir"), "src/main/$relative")

    @Test
    fun analysisModuleUsesDecodeOnlyAndContainsNoPlaybackOrNetworkCalls() {
        val sources = listOf(
            repoFile("java/com/youneko/rate/data/audio/StreamingAudioAnalysisEngine.kt"),
            repoFile("java/com/youneko/rate/data/audio/Spectrogram.kt"),
            repoFile("java/com/youneko/rate/data/audio/SpectrogramQuality.kt"),
            repoFile("java/com/youneko/rate/ui/analyze/AudioAnalysisScreen.kt"),
            repoFile("java/com/youneko/rate/ui/analyze/SpectrogramView.kt"),
        ).joinToString("\n") { it.readText() }
        listOf("AudioTrack", "MediaPlayer", "ExoPlayer", "androidx.media3", "MediaSession", "previewUrl").forEach { forbidden ->
            assertFalse("forbidden=$forbidden", sources.contains(forbidden))
        }
        assertFalse(sources.contains("http://"))
        assertFalse(sources.contains("https://"))
        assertTrue(sources.contains("MediaExtractor"))
        assertTrue(sources.contains("MediaCodec"))
        assertTrue(sources.contains("detectTransformGestures"))
        assertTrue(sources.contains("FileProvider"))
    }

    @Test
    fun analysisScreenPreservesStateAcrossRecompositionAndExposesCancelPath() {
        val source = repoFile("java/com/youneko/rate/ui/analyze/AudioAnalysisScreen.kt").readText()
        assertTrue(source.contains("rememberSaveable"))
        assertTrue(source.contains("onCancelClicked"))
        assertTrue(source.contains("Channel<AnalyzeEvent>"))
    }
}
