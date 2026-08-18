package com.youneko.rate

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.youneko.rate.data.musicbrainz.MbRelease
import com.youneko.rate.data.musicbrainz.MbSearchResponse
import com.youneko.rate.data.musicbrainz.MusicBrainzApi
import com.youneko.rate.data.musicbrainz.encodeLuceneQuery
import com.youneko.rate.data.musicbrainz.escapeLuceneQuery
import com.youneko.rate.data.musicbrainz.toSearchItems
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit

class MusicBrainzSearchHotfixTest {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        isLenient = true
    }

    @Test
    fun realReleaseGroupFixtureParsesAndMapsToVisibleItems() {
        val response = json.decodeFromString<MbSearchResponse>(fixture("musicbrainz/release_group_random_access_memories.json"))
        assertEquals(7188, response.count)
        assertTrue(response.releaseGroups.isNotEmpty())
        val daft = response.releaseGroups.firstOrNull { it.artistCredit.any { credit -> credit.artist?.name == "Daft Punk" } }
        assertNotNull(daft)
        assertEquals("Random Access Memories", daft?.title)
        assertTrue(response.toSearchItems("release-group").any { it.artist == "Daft Punk" })
    }

    @Test
    fun realReleaseFixtureParsesHyphenatedFieldsAndLabelInfo() {
        val response = json.decodeFromString<MbSearchResponse>(fixture("musicbrainz/release_random_access_memories.json"))
        val daft = response.releases.first { it.artistCredit.any { credit -> credit.artist?.name == "Daft Punk" } }
        assertEquals("Random Access Memories", daft.title)
        assertEquals("2013-05-17", daft.date)
        assertEquals("AU", daft.country)
        assertEquals("Columbia", daft.labelInfo.single().label?.name)
        assertEquals("88883716862", daft.labelInfo.single().catalogNumber)
        assertEquals(13, daft.trackCount)
        assertEquals("AU", daft.releaseEvents.single().area?.iso31661Codes?.single().orEmpty())
    }

    @Test
    fun responseMissingOptionalFieldsStillParses() {
        val release = json.decodeFromString<MbRelease>("{\"id\":\"release-1\",\"title\":\"Minimal\"}")
        assertEquals("Minimal", release.title)
        assertTrue(release.artistCredit.isEmpty())
        assertTrue(release.labelInfo.isEmpty())
        assertTrue(release.media.isEmpty())
        assertTrue(release.releaseEvents.isEmpty())
    }

    @Test
    fun luceneSpecialCharactersAreEscapedWithoutChangingUnicode() {
        assertEquals("AC\\/DC", escapeLuceneQuery("AC/DC"))
        assertEquals("Sơn Tùng M\\-TP", escapeLuceneQuery("Sơn Tùng M-TP"))
        assertEquals("幾田りら", escapeLuceneQuery("幾田りら"))
        assertEquals("a\\:b", escapeLuceneQuery("a:b"))
    }

    @Test
    fun retrofitRequestContainsJsonLimitOffsetAndEscapedQuery() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody("{\"created\":\"now\",\"count\":0,\"offset\":25,\"release-groups\":[]}"))
            val api = Retrofit.Builder()
                .baseUrl(server.url("ws/2/"))
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(MusicBrainzApi::class.java)
            runBlocking { api.search("release-group", encodeLuceneQuery("AC/DC"), limit = 25, offset = 25) }
            val request = server.takeRequest()
            assertEquals("release-group", request.requestUrl?.pathSegments?.last())
            assertTrue(request.requestUrl.toString(), request.requestUrl.toString().contains("AC%5C%2FDC"))
            assertEquals("AC\\/DC", request.requestUrl?.queryParameter("query"))
            assertEquals("json", request.requestUrl?.queryParameter("fmt"))
            assertEquals("25", request.requestUrl?.queryParameter("limit"))
            assertEquals("25", request.requestUrl?.queryParameter("offset"))
        }
    }

    private fun fixture(path: String): String = requireNotNull(javaClass.classLoader?.getResource(path)).readText()
}
