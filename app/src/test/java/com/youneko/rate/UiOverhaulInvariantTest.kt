package com.youneko.rate

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UiOverhaulInvariantTest {
    private fun root(): File = generateSequence(File(System.getProperty("user.dir").orEmpty()).absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "app/build.gradle.kts").isFile }
        ?: error("Missing project root")

    @Test
    fun stableKeysAreDeterministicAndDistinct() {
        assertEquals("album:a", com.youneko.rate.ui.stableAlbumKey("a"))
        assertEquals("track:a", com.youneko.rate.ui.stableTrackKey("a"))
        assertTrue(com.youneko.rate.ui.stableAlbumKey("a") != com.youneko.rate.ui.stableTrackKey("a"))
        assertTrue(com.youneko.rate.ui.MIN_SCROLL_FPS >= 55)
    }

    @Test
    fun vietnameseResourcesContainRequiredDiacriticsAndSharedUiIsLocalized() {
        val vi = File(root(), "app/src/main/res/values-vi/strings.xml").readText()
        assertTrue(vi.contains("ắ"))
        assertTrue(vi.contains("ễ"))
        assertTrue(vi.contains("ộ"))
        assertTrue(vi.contains("ữ"))
        assertTrue(vi.contains("retry"))
        assertTrue(!File(root(), "app/src/main/java/com/youneko/rate/ui/ScreenStates.kt").readText().contains("Text(\"Thử lại\""))
    }

    @Test
    fun round2EvidenceFixContractsArePresent() {
        val phase2 = File(root(), "app/src/main/java/com/youneko/rate/ui/rate/Phase2Screens.kt").readText()
        val components = File(root(), "app/src/main/java/com/youneko/rate/ui/components/YnComponents.kt").readText()
        val analyze = File(root(), "app/src/main/java/com/youneko/rate/ui/analyze/AudioAnalysisScreen.kt").readText()
        val access = File(root(), "app/src/main/java/com/youneko/rate/ui/media/MediaAccessGate.kt").readText()
        assertTrue("Library header must be a full-width grid item", phase2.contains("GridItemSpan(maxLineSpan)"))
        assertTrue("Album cover slot must remain square", components.contains("aspectRatio(1f)"))
        assertTrue("Album titles must reserve two lines", components.contains("minLines = 2"))
        assertTrue("Bitrate note must be a caption", analyze.contains("metricCaptionIndent"))
        assertTrue("Analyze content must reserve bottom space", analyze.contains("navigationSafe"))
        assertTrue("Permission callback must enqueue indexing", access.contains("enqueueMediaScan(context, forceFull = true)"))
    }

    @Test
    fun sourceKeepsOfflineNoPlaybackAndNoDestructiveMigrationInvariants() {
        val source = File(root(), "app/src/main").walkTopDown().filter { it.isFile }.joinToString("\n") { it.readText() }
        listOf("MediaPlayer", "ExoPlayer", "androidx.media3", "MediaSession", "AudioTrack", "previewUrl", "MANAGE_EXTERNAL_STORAGE", "fallbackToDestructiveMigration").forEach { forbidden ->
            assertTrue("Forbidden token: $forbidden", !source.contains(forbidden, ignoreCase = true))
        }
    }
}
