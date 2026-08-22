package com.youneko.rate

import com.youneko.rate.data.coversearch.CoverSearchEvent
import com.youneko.rate.data.coversearch.MusicHoardersApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class MusicHoardersApiTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun postNdjsonEmitsCoverAndStatusPerLine() = runTest {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/x-ndjson")
                    .setBody(
                        """
                        {"type":"source","source":"applemusic","status":"done"}
                        not-json
                        {"type":"cover","source":"applemusic","releaseInfo":{"title":"Hải Trình Tan Vỡ"},"smallCoverUrl":"https://example/s.jpg","bigCoverUrl":"https://example/b.jpg","info":{"format":"jpg","width":3000,"height":3000,"size":6291456}}
                        {"type":"done"}
                        """.trimIndent() + "\n",
                    ),
            )
            val client = OkHttpClient.Builder().build()
            val api = MusicHoardersApi(client, json, server.url("/api/search").toString())

            val events = api.search("trung i.u", "hải trình tan vỡ", "us", listOf("applemusic")).toList()
            val request = server.takeRequest(2, TimeUnit.SECONDS)

            assertEquals("POST", request?.method)
            assertEquals("/api/search", request?.path)
            assertTrue(request?.getHeader("Content-Type")?.startsWith("application/json") == true)
            assertTrue(request?.getHeader("User-Agent")?.contains("YounekoRate/") == true)
            assertTrue(request?.body?.readUtf8()?.contains("\"country\":\"us\"") == true)
            assertTrue(events.any { it is CoverSearchEvent.SourceStatus && it.source == "applemusic" })
            assertTrue(events.any { it is CoverSearchEvent.Cover && it.cover.bigCoverUrl == "https://example/b.jpg" })
            assertTrue(events.last() is CoverSearchEvent.Done)
        }
    }

    @Test
    fun cancellingFlowCancelsStreamingCall() = runTest {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/x-ndjson")
                    .setBody("{\"type\":\"cover\",\"bigCoverUrl\":\"https://example/1.jpg\"}\n")
                    .setSocketPolicy(SocketPolicy.NO_RESPONSE),
            )
            val client = OkHttpClient.Builder().readTimeout(10, TimeUnit.SECONDS).build()
            val api = MusicHoardersApi(client, json, server.url("/api/search").toString())
            val job = launch(Dispatchers.IO) { api.search("a", "b", "us", listOf("spotify")).collect() }
            val request = withContext(Dispatchers.IO) { server.takeRequest(5, TimeUnit.SECONDS) }
            assertEquals("POST", request?.method)
            job.cancelAndJoin()
            assertTrue(job.isCancelled)
        }
    }
}
