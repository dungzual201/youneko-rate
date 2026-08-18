package com.youneko.rate

import androidx.test.core.app.ApplicationProvider
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.runner.RunWith
import com.youneko.rate.data.musicbrainz.CoverArtApi
import com.youneko.rate.data.musicbrainz.CoverResult
import com.youneko.rate.data.musicbrainz.CoverArtService
import com.youneko.rate.data.musicbrainz.CoverArtUrls
import com.youneko.rate.data.musicbrainz.CoverMatch
import java.io.File
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit

@RunWith(RobolectricTestRunner::class)
@Config(application = YounekoRateApplication::class, sdk = [35])
class CoverArtTest {
    @Test
    fun urlBuilderUsesReleaseGroupThenReleaseQualityFallback() {
        assertEquals(
            listOf(
                "https://coverartarchive.org/release-group/group/front-1200",
                "https://coverartarchive.org/release-group/group/front-500",
                "https://coverartarchive.org/release-group/group/front-250",
                "https://coverartarchive.org/release/release/front-1200",
                "https://coverartarchive.org/release/release/front-500",
                "https://coverartarchive.org/release/release/front-250",
            ),
            CoverArtUrls.listCandidates("group", "release"),
        )
        assertEquals(
            "https://coverartarchive.org/release-group/group/front-1200",
            CoverArtUrls.releaseGroupFront1200("group"),
        )
        assertEquals(
            "https://coverartarchive.org/release-group/group/front-500",
            CoverArtUrls.releaseGroupFront500("group"),
        )
        assertEquals(
            "https://coverartarchive.org/release/release/front-500",
            CoverArtUrls.releaseFront500("release"),
        )
    }

    @Test
    fun coverMatchRejectsWrongArtistAndFiveTrackMismatch() {
        assertTrue(!CoverMatch.passes("AMORTAGE", "JISOO", "AMORTAGE", "OTHER", 4, 4, 2025, 2025))
        assertTrue(!CoverMatch.passes("AMORTAGE", "JISOO", "AMORTAGE", "JISOO", 4, 9, 2025, 2020))
        assertTrue(CoverMatch.passes("Bài hát (feat. X)", "Phùng Khánh Linh", "Bai hat", "Phung Khanh Linh", null, null, null, null))
    }

    @Test
    fun all404ReturnsNotFoundInsteadOfNetworkError() {
        runBlocking {
        MockWebServer().use { server ->
            repeat(6) { server.enqueue(MockResponse().setResponseCode(404)) }
            val api = Retrofit.Builder()
                .baseUrl(server.url("/"))
                .client(OkHttpClient.Builder().followRedirects(true).followSslRedirects(true).build())
                .build()
                .create(CoverArtApi::class.java)
            val context = ApplicationProvider.getApplicationContext<YounekoRateApplication>()
            val service = CoverArtService(api, context)
            val result = service.downloadToFile("group", "release", "cover-art-test.jpg")
            assertTrue(result is CoverResult.NotFound)
            assertEquals(6, server.requestCount)
            File(context.filesDir, "covers/cover-art-test.jpg").delete()
        }
    }
}
}
