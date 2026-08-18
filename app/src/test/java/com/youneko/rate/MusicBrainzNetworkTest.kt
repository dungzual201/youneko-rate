package com.youneko.rate

import com.youneko.rate.data.SettingsStore
import com.youneko.rate.data.local.dao.RemoteMetadataCacheDao
import com.youneko.rate.data.local.dao.SearchHistoryDao
import com.youneko.rate.data.local.entity.RemoteMetadataCacheEntity
import com.youneko.rate.data.local.entity.SearchHistoryEntity
import com.youneko.rate.data.musicbrainz.MbSearchResponse
import com.youneko.rate.data.musicbrainz.MusicBrainzApi
import com.youneko.rate.data.musicbrainz.MusicBrainzRepository
import com.youneko.rate.data.musicbrainz.NetworkError
import com.youneko.rate.data.musicbrainz.Resource
import com.youneko.rate.data.musicbrainz.TokenBucket
import androidx.paging.PagingSource
import com.youneko.rate.data.musicbrainz.toPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.ResponseBody.Companion.toResponseBody

class MusicBrainzNetworkTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun mockWebServerParsesSearchFixtureWithMultiDiscAlbum() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(fixture("musicbrainz/search_release_group.json")))
            val api = retrofit(server).create(MusicBrainzApi::class.java)
            val response = kotlinx.coroutines.runBlocking { api.search("release-group", "album") }
            assertEquals(1, response.releaseGroups.size)
            assertEquals("Album nhiều đĩa", response.releaseGroups.single().title)
            assertEquals("Artist Test", response.releaseGroups.single().artistCredit.single().name)
            assertEquals("release-group", server.takeRequest().path?.substringBefore('?')?.substringAfterLast('/'))
        }
    }

    @Test
    fun creditsLookupUsesReleaseMbidEndpointAndRelationIncludes() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody("{\"id\":\"release-1\",\"title\":\"Album\"}"))
            val api = retrofit(server).create(MusicBrainzApi::class.java)
            kotlinx.coroutines.runBlocking { api.lookupRelease("release-1") }
            val request = server.takeRequest()
            assertEquals("release", request.requestUrl?.pathSegments?.let { it[it.lastIndex - 1] })
            assertEquals("release-1", request.requestUrl?.pathSegments?.last())
            val includes = request.requestUrl?.queryParameter("inc").orEmpty()
            assertTrue(includes.contains("recording-level-rels"))
            assertTrue(includes.contains("work-rels"))
            assertTrue(includes.contains("work-level-rels"))
            assertTrue(includes.contains("artist-credits"))
            assertTrue(includes.contains("labels"))
        }
    }

    @Test
    fun mockWebServerParsesLookupFixtureWithMissingOptionalFields() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(fixture("musicbrainz/lookup_release.json")))
            val api = retrofit(server).create(MusicBrainzApi::class.java)
            val response = kotlinx.coroutines.runBlocking { api.lookupRelease("22222222-2222-4222-8222-222222222222") }
            val preview = response.toPreview()
            assertEquals(2, preview.tracks.size)
            assertEquals(2, preview.tracks[1].discNumber)
            assertEquals(null, preview.tracks[1].durationMs)
        }
    }

    @Test
    fun tokenBucketCapacityFiveThenRefillsWithVirtualTime() {
        var now = 0L
        val sleeps = mutableListOf<Long>()
        val bucket = TokenBucket(nowMillis = { now }, sleeper = { delay -> sleeps += delay; now += delay })
        repeat(6) { bucket.acquire() }
        assertEquals(1, sleeps.size)
        assertTrue(sleeps.single() in 999L..1000L)
    }

    @Test
    fun repositoryUsesFreshTtlCacheBeforeCallingApiAgain() = runTest {
        val api = FakeApi()
        val cache = FakeCacheDao()
        val repository = repository(api, cache)
        assertTrue(repository.search("release-group", "album") is Resource.Success)
        assertTrue(repository.search("release-group", "album") is Resource.Success)
        assertEquals(1, api.searchCalls)
        assertEquals("musicbrainz", cache.entries.values.toList()[0].provider)
    }

    @Test
    fun repositoryDoesNotCacheZeroResultResponse() = runTest {
        val api = FakeApi().apply { response = MbSearchResponse(count = 0) }
        val cache = FakeCacheDao()
        val result = repository(api, cache).search("release-group", "zzzzqqqq")
        assertTrue(result is Resource.Error)
        assertTrue(cache.entries.isEmpty())
    }

    @Test
    fun repositoryMapsHttp429ToRateLimitedError() = runTest {
        val api = FakeApi().apply { error = HttpException(Response.error<MbSearchResponse>(429, "".toResponseBody("application/json".toMediaType()))) }
        val result = repository(api, FakeCacheDao()).search("release-group", "album")
        assertEquals(NetworkError.RATE_LIMITED, (result as Resource.Error).kind)
    }

    @Test
    fun repositoryMapsNoConnectionWithoutThrowing() = runTest {
        assertEquals(NetworkError.NO_CONNECTION, mappedError(UnknownHostException("dns")))
    }

    @Test
    fun repositoryMapsTimeoutWithoutThrowing() = runTest {
        assertEquals(NetworkError.TIMEOUT, mappedError(SocketTimeoutException("timeout")))
    }

    @Test
    fun repositoryMaps503ToRateLimitedWithoutThrowing() = runTest {
        assertEquals(NetworkError.RATE_LIMITED, mappedError(httpError(503)))
    }

    @Test
    fun repositoryMapsOtherServerErrorsWithoutThrowing() = runTest {
        assertEquals(NetworkError.SERVER_ERROR, mappedError(httpError(500)))
    }

    @Test
    fun repositoryMapsClientErrorsWithoutThrowing() = runTest {
        assertEquals(NetworkError.BAD_REQUEST, mappedError(httpError(400)))
    }

    @Test
    fun repositoryMapsSerializationErrorsWithoutThrowing() = runTest {
        assertEquals(NetworkError.PARSE_ERROR, mappedError(SerializationException("bad json")))
    }

    @Test
    fun repositoryMapsUnknownErrorsWithoutThrowing() = runTest {
        assertEquals(NetworkError.UNKNOWN, mappedError(IllegalStateException("unexpected")))
    }

    @Test
    fun pagingSourceReturnsLoadErrorWhenNetworkFails() = runTest {
        val api = FakeApi().apply { error = UnknownHostException("dns") }
        val source = repository(api, FakeCacheDao()).createSearchPagingSource("release-group", "album")
        val result = source.load(PagingSource.LoadParams.Refresh(null, 25, false))
        assertTrue(result is PagingSource.LoadResult.Error)
    }

    private fun mappedError(error: Throwable): NetworkError = kotlinx.coroutines.runBlocking {
        val api = FakeApi().apply { this.error = error }
        (repository(api, FakeCacheDao()).search("release-group", "album") as Resource.Error).kind
    }

    private fun httpError(code: Int): HttpException =
        HttpException(Response.error<MbSearchResponse>(code, "".toResponseBody("application/json".toMediaType())))

    private fun retrofit(server: MockWebServer): Retrofit = Retrofit.Builder()
        .baseUrl(server.url("ws/2/"))
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    private fun repository(api: MusicBrainzApi, cache: FakeCacheDao) = MusicBrainzRepository(
        api = api,
        json = json,
        cacheDao = cache,
        historyDao = FakeHistoryDao(),
        settings = FakeSettingsStore(),
    )

    private fun fixture(path: String): String = requireNotNull(javaClass.classLoader?.getResource(path)).readText()

    private class FakeApi : MusicBrainzApi {
        var searchCalls = 0
        var error: Throwable? = null
        var response = MbSearchResponse(count = 1, releaseGroups = listOf(com.youneko.rate.data.musicbrainz.MbReleaseGroup("id", "Album")))
        override suspend fun search(entity: String, query: String, format: String, limit: Int, offset: Int): MbSearchResponse {
            searchCalls++
            error?.let { throw it }
            return response
        }
        override suspend fun lookupRelease(mbid: String, includes: String, format: String) =
            com.youneko.rate.data.musicbrainz.MbRelease(mbid, "Release")
        override suspend fun lookupRecording(mbid: String, includes: String, format: String) =
            com.youneko.rate.data.musicbrainz.MbRecording(mbid, "Recording")
        override suspend fun lookupWork(mbid: String, includes: String, format: String) =
            com.youneko.rate.data.musicbrainz.MbWork(mbid, "Work")
    }

    private class FakeCacheDao : RemoteMetadataCacheDao {
        val entries = linkedMapOf<String, RemoteMetadataCacheEntity>()
        override suspend fun find(key: String) = entries[key]
        override suspend fun upsert(value: RemoteMetadataCacheEntity) { entries[value.key] = value }
        override suspend fun deleteAll() { entries.clear() }
        override suspend fun delete(key: String) { entries.remove(key) }
    }

    private class FakeHistoryDao : SearchHistoryDao {
        override fun observeRecent(): Flow<List<SearchHistoryEntity>> = flowOf(emptyList())
        override suspend fun insert(value: SearchHistoryEntity) = Unit
        override suspend fun delete(id: String) = Unit
    }

    private class FakeSettingsStore : SettingsStore {
        override val offlineOnly = flowOf(false)
        override val ratingStep = flowOf(0.5)
        override val scoreMode = flowOf("SIMPLE")
        override val gridView = flowOf(true)
        override val dynamicColor = flowOf(false)
        override val sortOrder = flowOf("NEWEST")
        override val unfinishedOnly = flowOf(false)
        override val discogsEnabled = flowOf(false)
        override val discogsToken = flowOf("")
        override val lastFmEnabled = flowOf(false)
        override val lastFmApiKey = flowOf("")
        override val geniusEnabled = flowOf(false)
        override val geniusToken = flowOf("")
        override val showCreditSources = flowOf(false)
        override suspend fun setOfflineOnly(value: Boolean) = Unit
        override suspend fun setRatingStep(value: Double) = Unit
        override suspend fun setScoreMode(value: String) = Unit
        override suspend fun setGridView(value: Boolean) = Unit
        override suspend fun setDynamicColor(value: Boolean) = Unit
        override suspend fun setSortOrder(value: String) = Unit
        override suspend fun setUnfinishedOnly(value: Boolean) = Unit
        override suspend fun setDiscogsEnabled(value: Boolean) = Unit
        override suspend fun setDiscogsToken(value: String) = Unit
        override suspend fun setLastFmEnabled(value: Boolean) = Unit
        override suspend fun setLastFmApiKey(value: String) = Unit
        override suspend fun setGeniusEnabled(value: Boolean) = Unit
        override suspend fun setGeniusToken(value: String) = Unit
        override suspend fun setShowCreditSources(value: Boolean) = Unit
    }
}
