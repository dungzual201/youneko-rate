package com.youneko.rate

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiRound3ContractTest {
    private fun root(): File = generateSequence(File(System.getProperty("user.dir").orEmpty()).absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "app/build.gradle.kts").isFile }
        ?: error("Missing project root")

    @Test
    fun libraryOnlineKeepsSharedHeaderAndFourBodyStates() {
        val library = File(root(), "app/src/main/java/com/youneko/rate/ui/rate/Phase2Screens.kt").readText()
        val online = File(root(), "app/src/main/java/com/youneko/rate/ui/musicbrainz/MusicBrainzSearchPanel.kt").readText()
        assertTrue(library.indexOf("LibraryListHeader(") < library.indexOf("when {") )
        assertTrue(online.contains("YounekoShimmer"))
        assertTrue(online.contains("YounekoEmptyState"))
        assertTrue(online.contains("YounekoErrorState"))
        assertTrue(online.contains("no_results_for_query"))
        assertFalse(online.contains("Text(stringResource(R.string.online_results)"))
    }

    @Test
    fun spectrogramUsesOneAspectRatioPanelAndOneLabeledLegend() {
        val panel = File(root(), "app/src/main/java/com/youneko/rate/ui/analyze/AudioAnalysisScreen.kt").readText()
        val view = File(root(), "app/src/main/java/com/youneko/rate/ui/analyze/SpectrogramView.kt").readText()
        assertTrue(panel.contains("aspectRatio(16f / 10f)"))
        assertTrue(panel.contains("DbLegend("))
        assertEquals(1, panel.lines().count { it.contains("DbLegend(") && !it.contains("private fun DbLegend") })
        assertTrue(panel.contains("horizontalScroll"))
        assertTrue(panel.contains("spectrogram_hide_axes"))
        assertTrue(panel.contains("spectrogram_db_range_short"))
        assertTrue(panel.contains("spectrogram_db_sheet_title"))
        assertTrue(panel.contains("setSpectrogramLogarithmic"))
        assertTrue(panel.contains("setSpectrogramShowAxes"))
        assertTrue(panel.contains("setSpectrogramDbFloor"))
        assertFalse(panel.contains("spectrogram_linear_short"))
        assertFalse(view.contains("height(360.dp)"))
        assertFalse(view.contains("drawLegend("))
    }

    @Test
    fun childScreensUseIconNavigationInsteadOfPlainBackText() {
        val files = listOf(
            "app/src/main/java/com/youneko/rate/ui/export/ExportScreen.kt",
            "app/src/main/java/com/youneko/rate/ui/phase12/Phase12Screens.kt",
            "app/src/main/java/com/youneko/rate/ui/credits/CreditsScreen.kt",
        ).map { File(root(), it).readText() }
        files.forEach { source ->
            assertTrue(source.contains("navigationIcon"))
            assertTrue(source.contains("a11y_back"))
            assertFalse(source.contains("TextButton(onClick = onBack)"))
        }
    }

    @Test
    fun bilingualAxisLabelsAndDbLegendKeysStayInParity() {
        val en = File(root(), "app/src/main/res/values/strings.xml").readText()
        val vi = File(root(), "app/src/main/res/values-vi/strings.xml").readText()
        val keyRegex = Regex("<string name=\\\"([^\\\"]+)\\\">")
        assertEquals(keyRegex.findAll(en).map { it.groupValues[1] }.toSet(), keyRegex.findAll(vi).map { it.groupValues[1] }.toSet())
        listOf("spectrogram_db_zero", "spectrogram_db_mid", "spectrogram_db_floor", "spectrogram_frequency_zero", "spectrogram_frequency_khz").forEach { key ->
            assertTrue(en.contains("name=\"$key\""))
            assertTrue(vi.contains("name=\"$key\""))
        }
    }
}

/**
 * Device evidence matrix for the five required screenshot configurations.
 * Real screenshots and FPS measurements are intentionally not generated in the
 * sandbox because no physical device, emulator, WSA, or ADB connection exists.
 */
object UiRound3DeviceEvidence {
    const val STATUS = "CHƯA LÀM: không có thiết bị thật/emulator/ADB trong sandbox"
    val configurations = listOf(
        "360dp dark EN — Library Online empty/results and Analyze scrolled",
        "360dp light VI — Library Online and Analyze",
        "411dp dark VI — Analyze fullscreen spectrogram",
        "fontScale 2.0 — Analyze metric chips",
        "WSA ~720dp — supplementary only, not a real-device substitute",
    )
}

private fun String.windowed(token: String, partialWindows: Boolean): Sequence<String> =
    windowed(token.length, 1, partialWindows).asSequence().filter { it == token }
