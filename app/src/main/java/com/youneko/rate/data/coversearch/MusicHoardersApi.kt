package com.youneko.rate.data.coversearch

import com.youneko.rate.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class MusicHoardersSearchRequest(
    val artist: String,
    val album: String,
    val country: String,
    val sources: List<String>,
)

@Serializable
data class MusicHoardersReleaseInfo(
    val title: String? = null,
    val artist: String? = null,
    val id: String? = null,
)

@Serializable
data class MusicHoardersCoverInfo(
    val format: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val size: Long? = null,
)

@Serializable
data class MusicHoardersCoverLine(
    val type: String? = null,
    val source: String? = null,
    val releaseInfo: MusicHoardersReleaseInfo? = null,
    val smallCoverUrl: String? = null,
    val bigCoverUrl: String? = null,
    val info: MusicHoardersCoverInfo? = null,
    val status: String? = null,
    val message: String? = null,
)

sealed interface CoverSearchEvent {
    data class Cover(val cover: MusicHoardersCoverLine) : CoverSearchEvent
    data class SourceStatus(val source: String, val status: String, val count: Int? = null) : CoverSearchEvent
    data object Done : CoverSearchEvent
    data class Error(val code: Int? = null, val message: String? = null) : CoverSearchEvent
}

object MusicHoardersSession {
    @Volatile private var session: String? = null

    fun value(): String = session ?: synchronized(this) {
        session ?: UUID.randomUUID().toString().replace("-", "").also { session = it }
    }
}

class MusicHoardersApi(
    private val client: OkHttpClient,
    private val json: Json,
    private val endpoint: String = ENDPOINT,
) {
    private data class CacheEntry(val expiresAtMillis: Long, val events: List<CoverSearchEvent>)

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    fun search(
        artist: String,
        album: String,
        country: String,
        sources: List<String>,
    ): Flow<CoverSearchEvent> = callbackFlow {
        val key = listOf(artist.trim(), album.trim(), country.lowercase(), sources.sorted().joinToString(",")).joinToString("|")
        val now = System.currentTimeMillis()
        cache[key]?.takeIf { it.expiresAtMillis > now }?.let { cached ->
            cached.events.forEach { trySend(it) }
            close()
            return@callbackFlow
        }
        cache.remove(key)

        val requestJson = json.encodeToString(
            MusicHoardersSearchRequest(
                artist = artist,
                album = album,
                country = country,
                sources = sources,
            ),
        )
        val request = Request.Builder()
            .url(endpoint)
            .header("Content-Type", "application/json")
            .header("X-Session", MusicHoardersSession.value())
            .header("X-Page-Referrer", "")
            .header("X-Page-Query", "")
            .header("Accept", "application/x-ndjson")
            .header("Referer", "https://covers.musichoarders.xyz/")
            .header("Origin", "https://covers.musichoarders.xyz")
            .header("User-Agent", "YounekoRate/${BuildConfig.VERSION_NAME} (Android; personal library use)")
            .post(requestJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val call = client.newCall(request)
        val events = mutableListOf<CoverSearchEvent>()
        val worker: Job = launch(Dispatchers.IO) {
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        val error = CoverSearchEvent.Error(response.code, response.message)
                        events += error
                        trySend(error)
                        close()
                        return@use
                    }
                    val source = response.body?.source()
                    if (source == null) {
                        val error = CoverSearchEvent.Error(message = "empty response")
                        events += error
                        trySend(error)
                    } else {
                        while (!source.exhausted()) {
                            val line = source.readUtf8Line() ?: break
                            if (line.isBlank()) continue
                            val parsed = runCatching { json.decodeFromString<MusicHoardersCoverLine>(line) }.getOrNull() ?: continue
                            val event = when (parsed.type) {
                                "cover" -> if (!parsed.bigCoverUrl.isNullOrBlank()) CoverSearchEvent.Cover(parsed) else null
                                "source" -> parsed.source?.let { CoverSearchEvent.SourceStatus(it, parsed.status ?: "unknown") }
                                "done" -> CoverSearchEvent.Done
                                "error" -> CoverSearchEvent.Error(message = parsed.message)
                                else -> null
                            }
                            if (event != null) {
                                events += event
                                trySend(event)
                            }
                        }
                    }
                }
                if (events.none { it is CoverSearchEvent.Error }) {
                    cache[key] = CacheEntry(System.currentTimeMillis() + CACHE_TTL_MILLIS, events.toList())
                }
                close()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val event = CoverSearchEvent.Error(message = error.message)
                events += event
                trySend(event)
                close()
            }
        }
        awaitClose {
            call.cancel()
            worker.cancel()
        }
    }

    companion object {
        const val ENDPOINT = "https://covers.musichoarders.xyz/api/search"
        private const val CACHE_TTL_MILLIS = 10 * 60 * 1000L
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
