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
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        isLenient = true
    }

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
    fun realReleaseFixtureParsesSkinnyRelationsAndRequiredCredits() = runBlocking {
        val fixture = javaClass.classLoader?.getResourceAsStream("fixtures/release_credits.json")
            ?.bufferedReader()?.use { it.readText() }
            ?: throw FileNotFoundException("release_credits.json")
        val release = json.decodeFromString<MbRelease>(fixture)
        val skinny = release.media.flatMap { it.tracks }.first { it.title == "SKINNY" }
        val recording = requireNotNull(skinny.recording)
        val relations = recording.relations
        assertTrue("SKINNY should have at least 15 recording relations", relations.size >= 15)
        assertTrue(relations.all { relation ->
            relation.targetType !in setOf("artist", "label") ||
                ((relation.artist?.name ?: relation.label?.name).orEmpty().isNotBlank() && relation.type.isNotBlank())
        })
        val brad = relations.first { it.artist?.name == "Brad Lauchert" }
        assertEquals("engineer", brad.type)
        assertEquals("mix", brad.attributeValues["task"])
        assertTrue(brad.attributes.contains("task"))
        val finneasAttributes = relations.filter { it.artist?.name == "FINNEAS" }.flatMap { it.attributes }.toSet()
        assertTrue(setOf("bass", "drums (drum set)", "guitar", "glockenspiel", "keyboard", "percussion", "synthesizer").all { it in finneasAttributes })
        assertTrue(relations.any { it.artist?.name == "Andrew Yee" && "cello" in it.attributes })
        assertTrue(relations.any { it.artist?.name == "Amy Schroeder" && "violin" in it.attributes })
        assertTrue(relations.none { it.targetType in setOf("artist", "label") && (it.artist?.name ?: it.label?.name).isNullOrBlank() })

        val api = FakeApi().apply { releaseResponse = release }
        val creditDao = FakeCreditDao()
        val trackDao = FakeTrackDao(TrackEntity("skinny-track", "album-1", "SKINNY", recordingMbid = recording.id, createdAt = 0L, updatedAt = 0L))
        val service = MusicBrainzCreditsService(api, creditDao, FakeCacheDao(), trackDao, json)
        val result = service.loadAlbumCredits(album(), forceRefresh = true)
        assertTrue(result is com.youneko.rate.data.musicbrainz.Resource.Success)
        val credits = creditDao.saved.last()
        assertTrue(credits.size >= 15)
        assertTrue(credits.any { it.personName == "Brad Lauchert" && it.role == "Mixing engineer" })
        assertTrue(credits.any { it.personName == "FINNEAS" && it.instrumentOrAttribute?.contains("bass") == true })
        assertTrue(credits.any { it.personName == "Andrew Yee" && it.instrumentOrAttribute == "cello" })
        assertTrue(credits.any { it.personName == "Amy Schroeder" && it.instrumentOrAttribute == "violin" })
        assertTrue(credits.none { it.personName.isBlank() || it.role.isBlank() })
    }

    @Test
    fun amortageRecordingRelationsLabelsAndWorkWritersAreAllMerged() = runBlocking {
        val releaseFixture = javaClass.classLoader?.getResourceAsStream("fixtures/release_amortage.json")
            ?.bufferedReader()?.use { it.readText() } ?: throw FileNotFoundException("release_amortage.json")
        val workFixture = javaClass.classLoader?.getResourceAsStream("fixtures/work_earthquake.json")
            ?.bufferedReader()?.use { it.readText() } ?: throw FileNotFoundException("work_earthquake.json")
        val release = json.decodeFromString<MbRelease>(releaseFixture)
        val work = json.decodeFromString<MbWork>(workFixture)
        val recording = release.media.flatMap { it.tracks }.first { it.title == "earthquake" }.recording
        requireNotNull(recording)
        assertTrue(recording.relations.size >= 13)

        val api = FakeApi().apply {
            releaseResponse = release
            workResponses[work.id] = work
        }
        val creditDao = FakeCreditDao()
        val trackDao = FakeTrackDao(TrackEntity("earthquake-track", "album-1", "earthquake", recordingMbid = recording.id, createdAt = 0L, updatedAt = 0L))
        val service = MusicBrainzCreditsService(api, creditDao, FakeCacheDao(), trackDao, json)
        val result = service.loadAlbumCredits(amortageAlbum(), forceRefresh = true)
        assertTrue(result is com.youneko.rate.data.musicbrainz.Resource.Success)

        val allCredits = creditDao.saved.last()
        val trackCredits = allCredits.filter { it.trackId == "earthquake-track" }
        val albumCredits = allCredits.filter { it.trackId == null }
        assertTrue("recording relations must be preserved", trackCredits.size >= 13)
        assertTrue(trackCredits.all { it.albumId == null })
        assertTrue(albumCredits.any { it.personName == "BLISSOO LIMITED" && it.albumId == "album-1" })
        assertTrue(albumCredits.none { it.role == "Mix" || it.role == "Background vocals" })
        assertTrue(trackCredits.any { it.personName == "Manny Marroquin" && it.role == "Mix" })
        assertEquals(3, trackCredits.count { it.role == "Assistant mix" })
        assertTrue(trackCredits.any { it.personName == "The Wavys" && it.role.contains("Producer") && it.role.contains("Programming") })
        assertTrue(trackCredits.any { it.personName == "Sarah Troy" && it.role == "Background vocals" })
        assertTrue(trackCredits.any { it.personName == "BLISSOO LIMITED" && it.role == "Phonographic copyright" && it.beginDate == "2025" && it.endDate == "2025" })
        assertEquals(5, trackCredits.count { it.role == "Writer" })
        assertTrue("performance relation must trigger work lookup", api.workCalls.contains(work.id))
    }

    @Test
    fun manualTrackCreditSurvivesMusicBrainzRefresh() = runBlocking {
        val creditDao = FakeCreditDao()
        creditDao.upsertAll(listOf(CreditEntity("manual-1", null, "track-1", "Local Producer", role = "Producer", sourceProvider = "manual")))
        val result = MusicBrainzCreditsService(FakeApi(), creditDao, FakeCacheDao(), FakeTrackDao(), json)
            .loadTrackCredits(album(), "track-1", forceRefresh = true)
        assertTrue(result is com.youneko.rate.data.musicbrainz.Resource.Success)
        assertTrue(creditDao.saved.last().any { it.personName == "Local Producer" && it.sourceProvider.contains("manual") })
    }

    @Test
    fun emptyRecordingRelationsProduceEmptySuccessNotError() = runBlocking {
        val api = FakeApi().apply { recordingResponse = MbRecording("recording-empty") }
        val service = MusicBrainzCreditsService(api, FakeCreditDao(), FakeCacheDao(), FakeTrackDao(), json)
        val result = service.loadTrackCredits(album(), "track-1", forceRefresh = true)
        assertTrue(result is com.youneko.rate.data.musicbrainz.Resource.Success)
        assertTrue((result as com.youneko.rate.data.musicbrainz.Resource.Success).value.credits.isEmpty())
    }

    @Test
    fun creditsUseLookupEndpointsAndNeverSearch() = runBlocking {
        val api = FakeApi()
        val service = MusicBrainzCreditsService(api, FakeCreditDao(), FakeCacheDao(), FakeTrackDao(), json)
        service.loadTrackCredits(album(), "track-1")
        assertEquals(0, api.searchCalls)
        assertEquals(1, api.recordingCalls)
    }

    @Test
    fun albumWithoutMbidReturnsBeforeAnyNetworkRequest() = runBlocking {
        val api = FakeApi()
        val service = MusicBrainzCreditsService(api, FakeCreditDao(), FakeCacheDao(), FakeTrackDao(), json)
        val result = service.loadAlbumCredits(album().copy(mbid = null))
        assertTrue(result is com.youneko.rate.data.musicbrainz.Resource.Error)
        assertEquals(0, api.releaseCalls)
        assertEquals(0, api.searchCalls)
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
    fun mergerCombinesSourcesAndRolesWithinRoleGroup() {
        val merged = CreditMerger.merge(
            albumId = null,
            trackId = "track-1",
            candidates = listOf(
                CreditCandidate("Phung Khanh Linh", null, "Producer", null, "discogs", null),
                CreditCandidate("Phùng Khánh Linh", null, "Programming", null, "file_tags", null),
                CreditCandidate("Phùng Khánh Linh", null, "Producer", null, "genius", null),
            ),
        )
        assertEquals(1, merged.size)
        assertEquals("Phùng Khánh Linh", merged.single().personName)
        assertTrue(merged.single().role.contains("Producer"))
        assertTrue(merged.single().role.contains("Programming"))
        assertTrue(merged.single().sourceProvider.contains("discogs"))
        assertTrue(merged.single().sourceProvider.contains("file_tags"))
        assertTrue(merged.single().sourceProvider.contains("genius"))
    }

    @Test
    fun cacheWithinThirtyDaysReturnsWithoutRecordingRequest() = runBlocking {
        val now = System.currentTimeMillis()
        val cache = FakeCacheDao()
        cache.values["credits:v3:track:recording-1:musicbrainz:default:provider-v3"] = RemoteMetadataCacheEntity(
            key = "credits:v3:track:recording-1:musicbrainz:default:provider-v3",
            provider = "musicbrainz",
            jsonBody = "{\"values\":[{\"id\":\"credit-1\",\"albumId\":null,\"trackId\":\"track-1\",\"personName\":\"Cached Producer\",\"personMbid\":\"person-1\",\"role\":\"producer\",\"instrumentOrAttribute\":null,\"sourceProvider\":\"musicbrainz\",\"sourceUrl\":\"https://musicbrainz.org/artist/person-1\",\"sortOrder\":0}]}",
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
        cache.values["credits:v3:track:recording-1:musicbrainz:default:provider-v3"] = RemoteMetadataCacheEntity(
            key = "credits:v3:track:recording-1:musicbrainz:default:provider-v3", provider = "musicbrainz", jsonBody = "{\"values\":[]}",
            fetchedAt = 0L, expiresAt = 1L,
        )
        val service = MusicBrainzCreditsService(FakeApi(), FakeCreditDao(), cache, FakeTrackDao(), json)
        assertNull(service.readCache("credits:v3:track:recording-1:musicbrainz:default:provider-v3"))
    }

    private fun album() = AlbumEntity("album-1", "Album", "artist-1", mbid = "release-1", createdAt = 0L, updatedAt = 0L)

    private fun amortageAlbum() = AlbumEntity("album-1", "AMORTAGE", "artist-1", mbid = "42911e58-a29f-451b-91a4-38938ac19608", createdAt = 0L, updatedAt = 0L)

    private class FakeApi : MusicBrainzApi {
        var searchCalls = 0
        var releaseCalls = 0
        var recordingCalls = 0
        val workCalls = mutableListOf<String>()
        var releaseResponse: MbRelease? = null
        var recordingResponse: MbRecording? = null
        val workResponses = mutableMapOf<String, MbWork>()
        override suspend fun search(entity: String, query: String, format: String, limit: Int, offset: Int): MbSearchResponse {
            searchCalls++
            return MbSearchResponse()
        }
        override suspend fun lookupRelease(mbid: String, includes: String, format: String): MbRelease {
            releaseCalls++
            return releaseResponse ?: MbRelease(mbid)
        }
        override suspend fun lookupRecording(mbid: String, includes: String, format: String): MbRecording {
            recordingCalls++
            return recordingResponse ?: MbRecording(mbid)
        }
        override suspend fun lookupWork(mbid: String, includes: String, format: String): MbWork {
            workCalls += mbid
            return workResponses[mbid] ?: MbWork(mbid)
        }
    }

    private class FakeCacheDao : RemoteMetadataCacheDao {
        val values = mutableMapOf<String, RemoteMetadataCacheEntity>()
        override suspend fun find(key: String) = values[key]
        override suspend fun upsert(value: RemoteMetadataCacheEntity) { values[value.key] = value }
        override suspend fun deleteAll() { values.clear() }
        override suspend fun delete(key: String) { values.remove(key) }
    }

    private class FakeCreditDao : CreditDao {
        val saved = mutableListOf<List<CreditEntity>>()
        private val rows = mutableListOf<CreditEntity>()
        override suspend fun upsertAll(credits: List<CreditEntity>) {
            saved += credits
            credits.forEach { credit -> rows.removeAll { it.id == credit.id }; rows += credit }
        }
        override fun observeForItem(albumId: String, trackId: String?): Flow<List<CreditEntity>> = flowOf(emptyList())
        override fun observeForAlbum(albumId: String): Flow<List<CreditEntity>> = flowOf(emptyList())
        override suspend fun findAlbumCredits(albumId: String): List<CreditEntity> = rows.filter { it.albumId == albumId && it.trackId == null }
        override suspend fun findAll(): List<CreditEntity> = rows.toList()
        override fun observeForAlbumWithTracks(albumId: String): Flow<List<CreditEntity>> = flowOf(rows.filter { it.albumId == albumId || it.trackId == "track-1" })
        override suspend fun findForAlbumWithTracks(albumId: String): List<CreditEntity> = rows.filter { it.albumId == albumId || it.trackId == "track-1" }
        override suspend fun findTrackCredits(trackId: String): List<CreditEntity> = rows.filter { it.trackId == trackId }
        override suspend fun deleteAlbumCredits(albumId: String) { rows.removeAll { it.albumId == albumId && it.trackId == null } }
        override suspend fun deleteTrackCredits(trackId: String) { rows.removeAll { it.trackId == trackId } }
        override suspend fun deleteTrackCreditsForAlbum(albumId: String) { rows.removeAll { it.albumId == albumId || it.trackId == "track-1" } }
    }

    private class FakeTrackDao(
        private val track: TrackEntity = TrackEntity("track-1", "album-1", "Song", recordingMbid = "recording-1", createdAt = 0L, updatedAt = 0L),
    ) : TrackDao {
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
        override suspend fun findAll(): List<TrackEntity> = listOf(track)
    }
}
