package com.youneko.rate

import androidx.work.Data
import com.youneko.rate.data.AlbumDraft
import com.youneko.rate.data.importer.AudioTag
import com.youneko.rate.data.importer.ImportDedupe
import com.youneko.rate.data.importer.ImportGrouping
import com.youneko.rate.data.importer.ImportWorker
import com.youneko.rate.data.musicbrainz.MbMedium
import com.youneko.rate.data.musicbrainz.MbRelease
import com.youneko.rate.data.musicbrainz.MbTrack
import com.youneko.rate.data.musicbrainz.toPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class Phase5HotfixAndLocaleTest {
    @Test
    fun workerInputContainsOnlySessionIdAndStaysBelowWorkManagerLimit() {
        val data = Data.Builder().putString(ImportWorker.KEY_SESSION_ID, "session-id").build()
        assertTrue(data.toByteArray().size < 10_000)
    }

    @Test
    fun groupingHandlesMultipleDiscsMissingArtistAndMissingTrackNumber() {
        val groups = ImportGrouping.group(
            listOf(
                AudioTag("u1", "one.flac", "Artist", null, "Album", "One", 1, 1, 2024, null, null),
                AudioTag("u2", "two.flac", "Artist", null, "Album", "Two", null, 2, 2024, null, null),
                AudioTag("u3", "three.flac", "Artist", null, "Album", "Three", 3, 1, 2024, null, null),
            ),
        )
        assertEquals(1, groups.size)
        assertEquals(listOf("One", "Three", "Two"), groups.single().tracks.map { it.title })
        assertEquals("Artist", groups.single().artist)
    }

    @Test
    fun dedupeMatchesExistingAlbumByNormalizedMetadata() {
        assertTrue(ImportDedupe.sameAlbum("Beyoncé", "Artist", 2024, "Beyonce", "Artist", 2024))
    }

    @Test
    fun defaultAndVietnameseStringKeysStayInParity() {
        val defaultKeys = resourceKeys(File("src/main/res/values/strings.xml"), "string")
        val vietnameseKeys = resourceKeys(File("src/main/res/values-vi/strings.xml"), "string")
        assertEquals(defaultKeys, vietnameseKeys)
    }

    @Test
    fun defaultAndVietnamesePluralKeysStayInParity() {
        val defaultKeys = resourceKeys(File("src/main/res/values/plurals.xml"), "plurals")
        val vietnameseKeys = resourceKeys(File("src/main/res/values-vi/plurals.xml"), "plurals")
        assertEquals(defaultKeys, vietnameseKeys)
    }

    @Test
    fun musicBrainzReleaseMapsDiscTrackDurationAndMbids() {
        val release = MbRelease(
            id = "release-mbid",
            title = "Album",
            date = "2024-01-01",
            country = "VN",
            media = listOf(
                MbMedium(position = 2, tracks = listOf(MbTrack(id = "t", position = 1, title = "Track", length = 1234, recording = com.youneko.rate.data.musicbrainz.MbRecording("recording-mbid", "Track")))),
            ),
        )
        val preview = release.toPreview(releaseGroupId = "group-mbid")
        assertEquals("release-mbid", preview.releaseId)
        assertEquals("group-mbid", preview.releaseGroupId)
        assertEquals(2, preview.tracks.single().discNumber)
        assertEquals(1234L, preview.tracks.single().durationMs)
        assertEquals("recording-mbid", preview.tracks.single().recordingMbid)
        val draft = AlbumDraft("Album", "Artist", 2024, "ALBUM", emptyList(), null, null, emptyList(), preview.releaseId, preview.releaseGroupId)
        assertEquals("release-mbid", draft.mbid)
        assertEquals("group-mbid", draft.releaseGroupMbid)
    }

    private fun resourceKeys(file: File, tag: String): Set<String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        return buildSet {
            val nodes = document.getElementsByTagName(tag)
            for (index in 0 until nodes.length) add(nodes.item(index).attributes.getNamedItem("name").nodeValue)
        }
    }
}
