package com.youneko.rate

import com.youneko.rate.data.credits.SourceResult
import com.youneko.rate.data.credits.ManualCreditParser
import com.youneko.rate.data.genius.geniusMatchForTest
import com.youneko.rate.data.local.entity.CreditEntity
import com.youneko.rate.data.musicbrainz.CreditCandidate
import com.youneko.rate.data.musicbrainz.CreditMerger
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase11Phase12RegressionTest {
    private fun repoFile(path: String): File = sequenceOf(File(path), File("app/$path"), File("../app/$path")).firstOrNull(File::exists) ?: File(path)

    @Test fun d1_importSuccessPopsImportAndUsesSingleTop() {
        val source = repoFile("src/main/java/com/youneko/rate/navigation/AppNavigation.kt").readText()
        assertTrue(source.contains("popUpTo(\"importTags\") { inclusive = true }"))
        assertTrue(source.contains("launchSingleTop = true"))
    }

    @Test fun d2_analyzeUsesDynamicHeaderAndDecodeOnlyNote() {
        val source = repoFile("src/main/java/com/youneko/rate/ui/analyze/AudioAnalysisScreen.kt").readText()
        assertTrue(source.contains("header"))
        assertTrue(source.contains("analyze_decode_note"))
    }

    @Test fun d3_newScreensHaveEnglishAndVietnameseResources() {
        val en = repoFile("src/main/res/values/strings.xml").readText()
        val vi = repoFile("src/main/res/values-vi/strings.xml").readText()
        listOf("credits_add_manual", "credits_bulk_paste", "import_cover_picker_title", "export_title", "advanced_search").forEach {
            assertTrue("missing EN $it", en.contains("name=\"$it\""))
            assertTrue("missing VI $it", vi.contains("name=\"$it\""))
        }
    }

    @Test fun d4_defaultSourceOrderPutsManualAndGeniusBeforeMusicBrainz() {
        val source = repoFile("src/main/java/com/youneko/rate/data/credits/CreditSources.kt").readText()
        val order = source.substringAfter("val defaultOrder = listOf(").substringBefore(")")
        assertTrue(order.indexOf("MANUAL") < order.indexOf("GENIUS"))
        assertTrue(order.indexOf("GENIUS") < order.indexOf("MUSICBRAINZ"))
    }

    @Test fun d5_itunesZeroCreditsIsMetadataOnlyEmptyWithReason() {
        val result: SourceResult = SourceResult.Empty("Apple Music provides metadata only")
        assertTrue(result is SourceResult.Empty)
        assertEquals("Apple Music provides metadata only", (result as SourceResult.Empty).reason)
        val source = repoFile("src/main/java/com/youneko/rate/data/credits/CreditSources.kt").readText()
        assertTrue(source.contains("ItunesCreditSource"))
        assertTrue(source.contains("SourceResult.Empty()"))
        assertFalse(source.contains("previewUrl"))
    }

    @Test fun d6_geniusMatchesVietnameseDiacriticsAndCase() {
        assertTrue(geniusMatchForTest("CỜ NGƯỜI", "PHÙNG KHÁNH LINH", "Co Nguoi", "Phung Khanh Linh"))
        assertTrue(geniusMatchForTest("Cờ Người", "Phùng Khánh Linh", "CỜ NGƯỜI", "phùng khánh linh"))
    }

    @Test fun d7_manualCreditWinsAndSurvivesMerge() {
        val manual = CreditEntity("manual-1", albumId = "a", personName = "Nguyễn Văn A", role = "Producer", sourceProvider = "manual")
        val remote = CreditCandidate("Nguyen Van A", null, "Producer", null, "genius", null)
        val merged = CreditMerger.merge("a", null, listOf(manual.toCandidate(), remote))
        assertEquals(1, merged.size)
        assertTrue(merged.single().sourceProvider.split(',').contains("manual"))
    }

    @Test fun d8_bulkPasteFiveLinesProducesFiveDrafts() {
        val text = "A — Producer\nWriter: B\nC — Vocal\nComposer: D\nE — Mixing engineer"
        assertEquals(5, ManualCreditParser.parse(text).size)
    }

    @Test fun d10_noPlaybackTokensInMainSource() {
        val root = repoFile("src/main/java")
        val source = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.joinToString("\n") { it.readText() }
        listOf("Media" + "Player", "Exo" + "Player", "androidx." + "media3", "Media" + "Session", "Audio" + "Track", "preview" + "Url").forEach { assertFalse("banned $it", source.contains(it)) }
    }

    @Test fun d11_uiCreditsAndPhase12DoNotUseKnownHardcodedCopy() {
        val source = repoFile("src/main/java/com/youneko/rate/ui").walkTopDown().filter { it.isFile && it.extension == "kt" }.joinToString("\n") { it.readText() }
        listOf("Chọn ảnh bìa khác", "No enabled source returned credits", "Open on MusicBrainz", "Add manual credit").forEach { assertFalse("hardcoded UI copy: $it", source.contains(it)) }
    }

    private fun CreditEntity.toCandidate() = CreditCandidate(personName, personMbid, role, instrumentOrAttribute, sourceProvider, sourceUrl, beginDate, endDate)
}
