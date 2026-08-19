package com.youneko.rate

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HotfixRound9InvariantTest {
    private fun repoFile(path: String): File = sequenceOf(File(path), File("app/$path"), File("../app/$path")).firstOrNull(File::exists) ?: File(path)

    @Test
    fun cancellationIsOnlyEmittedForUserRequestedCancel() {
        val source = repoFile("src/main/java/com/youneko/rate/ui/analyze/AudioAnalysisScreen.kt").readText()
        assertTrue(source.contains("Channel<AnalyzeEvent>"))
        assertTrue(source.contains("if (userRequestedCancel) _events.trySend(AnalyzeEvent.Cancelled)"))
        assertTrue(source.contains("fun onCancelClicked()"))
        assertFalse(source.contains("onDispose { cancel"))
        assertFalse(source.contains("onDispose { viewModel.cancel"))
    }

    @Test
    fun successfulAnalysisHasNoCancelledStateSnackbarPath() {
        val source = repoFile("src/main/java/com/youneko/rate/ui/analyze/AudioAnalysisScreen.kt").readText()
        assertTrue(source.contains("WorkInfo.State.SUCCEEDED, WorkInfo.State.FAILED"))
        assertFalse(source.contains("if (hadRunning && analyzeState is AnalyzeUiState.Idle)"))
        assertFalse(source.contains("Analysis cancelled"))
    }

    @Test
    fun stalePreviewDeveloperTextIsAbsentFromProductionSource() {
        val sourceRoot = repoFile("src/main/java")
        val resourceRoot = repoFile("src/main/res")
        val source = sequenceOf(sourceRoot, resourceRoot).flatMap { root -> root.walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "xml") } }.joinToString("\n") { it.readText() }
        assertFalse(source.contains("Read-only preview"))
        assertFalse(source.contains("part of phase 5"))
    }

    @Test
    fun exactlyOneOnlinePreviewDialogAndOneLocalImportRouteRemain() {
        val uiRoot = repoFile("src/main/java/com/youneko/rate/ui")
        val source = uiRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.joinToString("\n") { it.readText() }
        assertEquals(1, Regex("private fun MusicBrainzPreviewDialog\\(").findAll(source).count())
        val nav = repoFile("src/main/java/com/youneko/rate/navigation/AppNavigation.kt").readText()
        assertEquals(1, Regex("composable\\(\"importTags\"\\)").findAll(nav).count())
        assertTrue(repoFile("src/main/java/com/youneko/rate/ui/musicbrainz/MusicBrainzSearchPanel.kt").readText().contains("viewModel.clearImportUi()"))
    }

    @Test
    fun importSuccessRemovesImportRouteAndUsesSingleTop() {
        val nav = repoFile("src/main/java/com/youneko/rate/navigation/AppNavigation.kt").readText()
        assertTrue(nav.contains("popUpTo(\"importTags\") { inclusive = true }"))
        assertTrue(nav.contains("launchSingleTop = true"))
        assertTrue(nav.contains("Log.d(\"NAVSTACK\""))
    }

    @Test
    fun previewDialogHasBackPropertiesAndLifecycleDiagnostics() {
        val source = repoFile("src/main/java/com/youneko/rate/ui/musicbrainz/MusicBrainzSearchPanel.kt").readText()
        assertTrue(source.contains("dismissOnBackPress = true"))
        assertTrue(source.contains("dismissOnClickOutside = false"))
        assertTrue(source.contains("enter MusicBrainzPreviewDialog"))
        assertTrue(source.contains("exit MusicBrainzPreviewDialog"))
    }

    @Test
    fun noPlaybackTokensRemain() {
        val source = repoFile("src/main/java").walkTopDown().filter { it.isFile && it.extension == "kt" }.joinToString("\n") { it.readText() }
        listOf("Media" + "Player", "Exo" + "Player", "androidx." + "media3", "Media" + "Session", "Audio" + "Track", "preview" + "Url", "Play" + " preview").forEach { assertFalse("banned token $it", source.contains(it)) }
    }
}
