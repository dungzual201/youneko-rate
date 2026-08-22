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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class MusicHoardersSourceInfo(
    val id: String,
    val name: String? = null,
    val countries: List<String> = emptyList(),
    val queries: List<String> = emptyList(),
)

@Serializable
data class MusicHoardersInfo(
    val activeSourceLimit: Int = 9,
    val maximumPage: Int = 5,
    val countries: List<String> = emptyList(),
    val sources: List<MusicHoardersSourceInfo> = emptyList(),
    val serverPatterns: List<String> = emptyList(),
)

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
    val text: String? = null,
    val severity: String? = null,
    val releaseCount: Int? = null,
    val releaseTotal: Int? = null,
    val accuracy: String? = null,
    val next: String? = null,
    val success: Boolean? = null,
)

sealed interface CoverSearchEvent {
    data class Cover(val cover: MusicHoardersCoverLine) : CoverSearchEvent
    data class SourceStatus(val source: String, val status: String, val count: Int? = null) : CoverSearchEvent
    data class Count(val source: String?, val releaseCount: Int?, val releaseTotal: Int?, val accuracy: String?, val next: String?) : CoverSearchEvent
    data object Done : CoverSearchEvent
    data class Error(val code: Int? = null, val message: String? = null, val fieldErrors: Map<String, String> = emptyMap()) : CoverSearchEvent
}

internal fun parseBadRequestFields(json: Json, body: String): Map<String, String> = runCatching {
    val objectBody: JsonObject = json.parseToJsonElement(body).jsonObject
    objectBody.entries
        .filter { it.key != "query" }
        .associate { (key, value) -> key to (value.jsonPrimitive.contentOrNull ?: value.toString()) }
}.getOrDefault(emptyMap())

internal fun parseMusicHoardersLine(json: Json, line: String): CoverSearchEvent? {
    val parsed = runCatching { json.decodeFromString<MusicHoardersCoverLine>(line) }.getOrNull() ?: return null
    return when (parsed.type) {
        "cover" -> if (!parsed.bigCoverUrl.isNullOrBlank()) CoverSearchEvent.Cover(parsed) else null
        "source" -> parsed.source?.let { CoverSearchEvent.SourceStatus(it, parsed.status ?: "unknown") }
        "count" -> CoverSearchEvent.Count(parsed.source, parsed.releaseCount, parsed.releaseTotal, parsed.accuracy, parsed.next)
        "done" -> CoverSearchEvent.Done
        "error" -> CoverSearchEvent.Error(message = parsed.text ?: parsed.message)
        else -> null
    }
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
    private val infoEndpoint: String = if (endpoint == ENDPOINT) INFO_ENDPOINT else endpoint.substringBeforeLast("/api/search") + "/api/info"
    private data class CacheEntry(val expiresAtMillis: Long, val events: List<CoverSearchEvent>)

    private val cache = ConcurrentHashMap<String, CacheEntry>()
    @Volatile private var infoCache: Pair<Long, MusicHoardersInfo>? = null

    suspend fun info(): Result<MusicHoardersInfo> {
        val cached = infoCache
        if (cached != null && cached.first > System.currentTimeMillis()) return Result.success(cached.second)
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(infoEndpoint)
                .header("X-Session", MusicHoardersSession.value())
                .header("X-Page-Referrer", "")
                .header("X-Page-Query", "")
                .header("Accept", "application/json")
                .header("Referer", "https://covers.musichoarders.xyz/")
                .header("Origin", "https://covers.musichoarders.xyz")
                .header("User-Agent", "YounekoRate/${BuildConfig.VERSION_NAME} (Android; personal library use)")
                .get()
                .build()
            runCatching {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        runCatching { android.util.Log.e("CoverSearch", "HTTP info ${response.code} ${response.message}; body=$body") }
                        error("MusicHoarders info HTTP ${response.code}: ${body.ifBlank { response.message }}")
                    }
                    json.decodeFromString<MusicHoardersInfo>(body).also { infoCache = System.currentTimeMillis() + INFO_CACHE_TTL_MILLIS to it }
                }
            }
        }
    }

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
                        val body = response.body?.string().orEmpty()
                        runCatching { android.util.Log.e("CoverSearch", "HTTP ${response.code} ${response.message}; body=$body") }
                        val fieldErrors = if (response.code == 400) parseBadRequestFields(json, body) else emptyMap()
                        val message = when {
                            response.code == 429 -> "Slow down"
                            response.code == 500 && body.isNotBlank() -> body
                            body.isNotBlank() -> body
                            else -> response.message
                        }
                        val error = CoverSearchEvent.Error(response.code, message, fieldErrors)
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
                            val event = parseMusicHoardersLine(json, line) ?: continue
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
        const val INFO_ENDPOINT = "https://covers.musichoarders.xyz/api/info"
        private const val CACHE_TTL_MILLIS = 10 * 60 * 1000L
        private const val INFO_CACHE_TTL_MILLIS = 24 * 60 * 60 * 1000L
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
