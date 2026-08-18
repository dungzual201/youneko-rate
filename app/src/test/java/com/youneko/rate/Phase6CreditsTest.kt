package com.youneko.rate

import com.youneko.rate.data.local.dao.CreditDao
import com.youneko.rate.data.local.dao.RemoteMetadataCacheDao
import com.youneko.rate.data.local.dao.TrackDao
import com.youneko.rate.data.local.entity.AlbumEntity
import com.youneko.rate.data.local.entity.CreditEntity
import com.youneko.rate.data.local.entity.RemoteMetadataCacheEntity
import com.youneko.rate.data.local.entity.TrackEntity
import com.youneko.rate.data.musicbrainz.CreditCandidate
import com.youneko.rate.data.musicbrainz.CreditGroup
import com.youneko.rate.data.musicbrainz.CreditMerger
import com.youneko.rate.data.musicbrainz.MbRelease
import com.youneko.rate.data.musicbrainz.MusicBrainzApi
import com.youneko.rate.data.musicbrainz.MusicBrainzCreditsService
import com.youneko.rate.data.musicbrainz.MbRecording
import com.youneko.rate.data.musicbrainz.MbSearchResponse
import com.youneko.rate.data.musicbrainz.MbWork
import com.youneko.rate.data.musicbrainz.creditGroupForRole
import java.io.FileNotFoundException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase6CreditsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parsesCreditsFixtureWithReleaseAndRecordingRelations() {
        val fixture = javaClass.classLoader?.getResourceAsStream("fixtures/credits_release.json")
            ?.bufferedReader()?.use { it.readText() }
            ?: throw FileNotFoundException("credits_release.json")
        val release = json.decodeFromString<MbRelease>(fixture)

        assertEquals("release-1", release.id)
        assertEquals("producer", release.relations.first().type)
        assertEquals("Producer", release.relations.first().artist?.name)
        assertEquals("recording-1", release.media.single().tracks.single().recording?.id)
        assertEquals("composer", release.media.single().tracks.single().recording?.relations?.single()?.type)
    }

    @Test
    fun mapsCommonRolesAndUnknownRole() {
        assertEquals(CreditGroup.WRITING, creditGroupForRole("composer"))
        assertEquals(CreditGroup.PRODUCTION, creditGroupForRole("producer"))
        assertEquals(CreditGroup.OTHER, creditGroupForRole("invented role"))
    }

    @Test
    fun mergerDeduplicatesDiacriticsJapaneseAndNumericSuffix() {
        val merged = CreditMerger.merge(
            albumId = "album-1",
            trackId = null,
            candidates = listOf(
                CreditCandidate("Beyoncé", "artist-1", "composer", null, "musicbrainz", null),
                CreditCandidate(" Beyonce (2) ", null, "COMPOSER", "piano", "musicbrainz", null),
                CreditCandidate("山田 太郎", "artist-2", "composer", null, "musicbrainz", null),
                CreditCandidate("山田  太郎", null, "composer", "guitar", "musicbrainz", null),
            ),
        )

        assertEquals(2, merged.size)
        assertTrue(merged.any { it.personName == "Beyoncé" && it.instrumentOrAttribute == "piano" })
        assertTrue(merged.any { it.personName == "山田 太郎" && it.instrumentOrAttribute == "guitar" })
    }

    @Test
    fun cacheWithinThirtyDaysReturnsWithoutRecordingRequest() = runBlocking {
        val now = System.currentTimeMillis()
        val cache = FakeCacheDao()
        cache.values["credits:track:recording-1"] = RemoteMetadataCacheEntity(
            key = "credits:track:recording-1",
            provider = "musicbrainz",
            jsonBody = "{\"values\":[{\"id\":\"credit-1\",\"albumId\":\"album-1\",\"trackId\":\"track-1\",\"personName\":\"Cached Producer\",\"personMbid\":\"person-1\",\"role\":\"producer\",\"instrumentOrAttribute\":null,\"sourceProvider\":\"musicbrainz\",\"sourceUrl\":\"https://musicbrainz.org/artist/person-1\",\"sortOrder\":0}]}",
            fetchedAt = now - 1_000,
            expiresAt = now + 30L * 24L * 60L * 60L * 1_000L,
        )
        val api = FakeApi()
        val creditDao = FakeCreditDao()
        val service = MusicBrainzCreditsService(api, creditDao, cache, FakeTrackDao(), json)
        val result = service.loadTrackCredits(album(), "track-1")

        assertNotNull(result)
        assertEquals(0, api.recordingCalls)
        assertEquals(1, creditDao.saved.single().size)
        assertEquals("Cached Producer", creditDao.saved.single().single().personName)
    }

    @Test
    fun expiredCacheIsIgnored() = runBlocking {
        val cache = FakeCacheDao()
        cache.values["credits:track:recording-1"] = RemoteMetadataCacheEntity(
            key = "credits:track:recording-1", provider = "musicbrainz", jsonBody = "{\"values\":[]}",
            fetchedAt = 0L, expiresAt = 1L,
        )
        val service = MusicBrainzCreditsService(FakeApi(), FakeCreditDao(), cache, FakeTrackDao(), json)
        assertNull(service.readCache("credits:track:recording-1"))
    }

    private fun album() = AlbumEntity("album-1", "Album", "artist-1", mbid = "release-1", createdAt = 0L, updatedAt = 0L)

    private class FakeApi : MusicBrainzApi {
        var recordingCalls = 0
        override suspend fun search(entity: String, query: String, format: String, limit: Int, offset: Int) = MbSearchResponse()
        override suspend fun lookupRelease(mbid: String, includes: String, format: String) = MbRelease(mbid)
        override suspend fun lookupRecording(mbid: String, includes: String, format: String): MbRecording {
            recordingCalls++
            return MbRecording(mbid)
        }
        override suspend fun lookupWork(mbid: String, includes: String, format: String) = MbWork(mbid)
    }

    private class FakeCacheDao : RemoteMetadataCacheDao {
        val values = mutableMapOf<String, RemoteMetadataCacheEntity>()
        override suspend fun find(key: String) = values[key]
        override suspend fun upsert(value: RemoteMetadataCacheEntity) { values[value.key] = value }
    }

    private class FakeCreditDao : CreditDao {
        val saved = mutableListOf<List<CreditEntity>>()
        override suspend fun upsertAll(credits: List<CreditEntity>) { saved += credits }
        override fun observeForItem(albumId: String, trackId: String?): Flow<List<CreditEntity>> = flowOf(emptyList())
        override suspend fun deleteAlbumCredits(albumId: String) = Unit
        override suspend fun deleteTrackCredits(trackId: String) = Unit
    }

    private class FakeTrackDao : TrackDao {
        private val track = TrackEntity("track-1", "album-1", "Song", recordingMbid = "recording-1", createdAt = 0L, updatedAt = 0L)
        override fun observeAll(): Flow<List<TrackEntity>> = flowOf(listOf(track))
        override fun observeForAlbum(albumId: String): Flow<List<TrackEntity>> = flowOf(listOf(track))
        override fun observeStandalone(): Flow<List<TrackEntity>> = flowOf(emptyList())
        override suspend fun findById(id: String): TrackEntity? = track.takeIf { it.id == id }
        override suspend fun insert(track: TrackEntity) = Unit
        override suspend fun insertAll(tracks: List<TrackEntity>) = Unit
        override suspend fun insertAllIgnore(tracks: List<TrackEntity>) = Unit
        override suspend fun update(track: TrackEntity) = Unit
        override suspend fun deleteById(id: String) = Unit
        override suspend fun findForAlbum(albumId: String): List<TrackEntity> = listOf(track)
    }
}
