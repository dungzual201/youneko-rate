package com.youneko.rate

import com.youneko.rate.data.importer.AudioTag
import com.youneko.rate.data.importer.ImportDedupe
import com.youneko.rate.data.importer.ImportGrouping
import com.youneko.rate.data.importer.ImportMergePolicy
import com.youneko.rate.data.importer.ImportedTrack
import com.youneko.rate.data.importer.extensionFromDisplayName
import com.youneko.rate.data.importer.extensionFromMagic
import com.youneko.rate.data.local.entity.TrackEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportModelsTest {
    @Test
    fun displayNameExtensionHandlesUnicodeMultipleDotsNoExtensionAndNull() {
        assertEquals("flac", extensionFromDisplayName("a.b.flac"))
        assertEquals("mp3", extensionFromDisplayName("tên có dấu.mp3"))
        assertEquals("wav", extensionFromDisplayName("音楽.wav"))
        assertEquals(null, extensionFromDisplayName("noext"))
        assertEquals(null, extensionFromDisplayName(null))
        assertEquals(null, extensionFromDisplayName("cover.jpg"))
    }

    @Test
    fun magicBytesDetectSupportedAudioContainers() {
        assertEquals("flac", extensionFromMagic("fLaC".encodeToByteArray()))
        assertEquals("mp3", extensionFromMagic(byteArrayOf(0x49, 0x44, 0x33)))
        assertEquals("wav", extensionFromMagic("RIFFxxxxWAVE".encodeToByteArray()))
        assertEquals("ogg", extensionFromMagic("OggS........".encodeToByteArray()))
        assertEquals("opus", extensionFromMagic("OggS....OpusHead".encodeToByteArray()))
        assertEquals("m4a", extensionFromMagic(byteArrayOf(0, 0, 0, 0, 0x66, 0x74, 0x79, 0x70)))
        assertEquals("aiff", extensionFromMagic("FORMxxxxAIFF".encodeToByteArray()))
        assertEquals(null, extensionFromMagic("not audio".encodeToByteArray()))
    }

    @Test
    fun groupingUsesAlbumArtistAlbumYearAndSortsDiscThenTrack() {
        val tags = listOf(
            tag(title = "Disc 2 Track 1", album = "Album", albumArtist = "Artist", year = 2020, disc = 2, track = 1),
            tag(title = "Disc 1 Track 2", album = "Album", albumArtist = "Artist", year = 2020, disc = 1, track = 2),
            tag(title = "Disc 1 Track 1", album = "Album", albumArtist = "Artist", year = 2020, disc = 1, track = 1),
        )

        val group = ImportGrouping.group(tags).single()

        assertEquals(listOf("Disc 1 Track 1", "Disc 1 Track 2", "Disc 2 Track 1"), group.tracks.map { it.title })
        assertEquals("Artist", group.artist)
        assertEquals(2020, group.year)
    }

    @Test
    fun missingAlbumBecomesStandaloneGroupAndMissingAlbumArtistFallsBackToArtist() {
        val groups = ImportGrouping.group(listOf(tag(title = "Loose", album = null, albumArtist = null, artist = "Singer")))

        assertEquals(1, groups.size)
        assertTrue(groups.single().isStandalone)
        assertEquals("Singer", groups.single().artist)
    }

    @Test
    fun dedupeNormalizesAccentsAndSkipsRepeatedIncomingTitles() {
        assertTrue(ImportDedupe.sameAlbum("Kýougen", "Ado", 2021, "Kyougen", "Ado", 2021))
        val incoming = listOf(
            imported("Track"),
            imported("track"),
            imported("New"),
        )

        assertEquals(listOf("Track", "New"), ImportDedupe.newTracks(emptyList(), incoming).map { it.title })
        assertEquals(listOf("New"), ImportDedupe.newTracks(listOf("TRACK"), incoming).map { it.title })
    }

    @Test
    fun mergePolicyPreservesUserRatingAndReviewWhileFillingMissingMetadata() {
        val existing = TrackEntity(
            id = "id",
            albumId = "album",
            title = "Track",
            trackNumber = null,
            durationMs = null,
            stars = 4.5,
            reviewText = "User review",
            isSkip = true,
            isHighlight = true,
            createdAt = 1,
            updatedAt = 2,
        )
        val merged = ImportMergePolicy.preserveUserData(
            existing,
            imported("Track", trackNumber = 7, durationMs = 180_000),
        )

        assertEquals(7, merged.trackNumber)
        assertEquals(180_000L, merged.durationMs)
        assertEquals(4.5, merged.stars ?: -1.0, 0.0)
        assertEquals("User review", merged.reviewText)
        assertTrue(merged.isSkip)
        assertTrue(merged.isHighlight)
    }

    private fun tag(
        title: String,
        album: String?,
        albumArtist: String?,
        year: Int? = null,
        disc: Int? = null,
        track: Int? = null,
        artist: String? = albumArtist,
    ) = AudioTag("uri:$title", "$title.flac", artist, albumArtist, album, title, track, disc, year, null, null)

    private fun imported(title: String, trackNumber: Int? = null, durationMs: Long? = null) =
        ImportedTrack("uri:$title", "$title.flac", title, "Artist", trackNumber, 1, durationMs, null)
}
