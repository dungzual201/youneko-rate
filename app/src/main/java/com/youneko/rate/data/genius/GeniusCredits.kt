package com.youneko.rate.data.genius

import com.youneko.rate.data.SettingsStore
import com.youneko.rate.data.local.dao.RemoteMetadataCacheDao
import com.youneko.rate.data.local.entity.RemoteMetadataCacheEntity
import com.youneko.rate.data.musicbrainz.CreditCandidate
import com.youneko.rate.data.musicbrainz.NetworkError
import com.youneko.rate.data.musicbrainz.Resource
import java.net.UnknownHostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

private const val GENIUS_PROVIDER = "genius"
private const val GENIUS_TTL_MS = 30L * 24L * 60L * 60L * 1_000L

@Serializable
private data class CachedGeniusCredit(
    val personName: String,
    val role: String,
    val instrumentOrAttribute: String? = null,
    val sourceUrl: String? = null,
)

@Serializable
private data class CachedGeniusPayload(val credits: List<CachedGeniusCredit> = emptyList())

data class GeniusCreditResult(val credits: List<CreditCandidate>)

@Singleton
class GeniusCreditsService @Inject constructor(
    private val api: GeniusApi,
    private val cacheDao: RemoteMetadataCacheDao,
    private val settings: SettingsStore,
    private val json: Json,
) {
    private val limiter = Mutex()
    private var lastRequestAt = 0L

    suspend fun load(trackId: String, title: String, artist: String): Resource<GeniusCreditResult> = withContext(Dispatchers.IO) {
        if (settings.offlineOnly.first()) return@withContext Resource.Success(GeniusCreditResult(emptyList()))
        if (!settings.geniusEnabled.first()) return@withContext Resource.Success(GeniusCreditResult(emptyList()))
        val token = settings.geniusToken.first().trim().takeIf { it.isNotBlank() }
            ?: return@withContext Resource.Success(GeniusCreditResult(emptyList()))
        val key = "genius:v1:track:$trackId:${normalize(title)}:${normalize(artist)}"
        val cached = cacheDao.find(key)
        if (cached != null && cached.expiresAt > System.currentTimeMillis()) {
            decode(cached.jsonBody)?.let { return@withContext Resource.Success(it) }
        }
        runCatching {
            val authorization = "Bearer $token"
            val search = request { api.search("$artist $title", authorization) }
            val candidate = search.response.hits
                .map { it.result }
                .firstOrNull { match(title, artist, it.title, it.artistNames) }
                ?: return@runCatching GeniusCreditResult(emptyList())
            val song = request { api.song(candidate.id, authorization = authorization).response.song }
            val sourceUrl = song.url ?: candidate.url ?: "https://genius.com/songs/${candidate.id}"
            val credits = buildList {
                song.producerArtists.forEach { artistValue -> artistValue.name.takeIf(String::isNotBlank)?.let { add(CreditCandidate(it, null, "Producer", null, GENIUS_PROVIDER, sourceUrl)) } }
                song.writerArtists.forEach { artistValue -> artistValue.name.takeIf(String::isNotBlank)?.let { add(CreditCandidate(it, null, "Writer", null, GENIUS_PROVIDER, sourceUrl)) } }
                song.customPerformances.forEach { performance ->
                    performance.artists.forEach { artistValue ->
                        artistValue.name.takeIf(String::isNotBlank)?.let { add(CreditCandidate(it, null, mapRole(performance.label), performance.label, GENIUS_PROVIDER, sourceUrl)) }
                    }
                }
                song.primaryArtist?.name?.takeIf(String::isNotBlank)?.let { add(CreditCandidate(it, null, "Vocal", null, GENIUS_PROVIDER, sourceUrl)) }
                song.featuredArtists.forEach { artistValue -> artistValue.name.takeIf(String::isNotBlank)?.let { add(CreditCandidate(it, null, "Vocal", "Featured", GENIUS_PROVIDER, sourceUrl)) } }
            }
            GeniusCreditResult(credits)
        }.fold(
            onSuccess = { result ->
                val now = System.currentTimeMillis()
                cacheDao.upsert(RemoteMetadataCacheEntity(key = key, provider = GENIUS_PROVIDER, jsonBody = encode(result), fetchedAt = now, expiresAt = now + GENIUS_TTL_MS))
                Resource.Success(result)
            },
            onFailure = { error -> Resource.Error(mapError(error), error.message) },
        )
    }

    private suspend fun <T> request(block: suspend () -> T): T = limiter.withLock {
        val wait = 1_000L - (System.currentTimeMillis() - lastRequestAt)
        if (wait > 0) delay(wait)
        val result = block()
        lastRequestAt = System.currentTimeMillis()
        result
    }

    private fun encode(result: GeniusCreditResult): String = json.encodeToString(
        CachedGeniusPayload(result.credits.map { CachedGeniusCredit(it.personName, it.role, it.instrumentOrAttribute, it.sourceUrl) }),
    )

    private fun decode(raw: String): GeniusCreditResult? = runCatching {
        GeniusCreditResult(json.decodeFromString<CachedGeniusPayload>(raw).credits.map {
            CreditCandidate(it.personName, null, it.role, it.instrumentOrAttribute, GENIUS_PROVIDER, it.sourceUrl)
        })
    }.getOrNull()

    private fun mapRole(raw: String): String = when (raw.lowercase()) {
        in listOf("mixing engineer", "mix engineer", "mixed by") -> "Mixing engineer"
        in listOf("mastering engineer", "mastered by") -> "Mastering engineer"
        "background vocals", "backing vocals" -> "Background vocals"
        "recorded at", "recording engineer", "recorded by" -> "Recording"
        "guitar", "bass", "drums", "keyboard", "piano", "strings" -> "Instrument"
        else -> raw.trim().ifBlank { "Other" }
    }

    private fun match(title: String, artist: String, candidateTitle: String, candidateArtist: String): Boolean =
        similarity(title, candidateTitle) >= 0.85 && similarity(artist, candidateArtist) >= 0.80

    private fun similarity(a: String, b: String): Double {
        val left = normalize(a)
        val right = normalize(b)
        if (left == right) return 1.0
        val max = maxOf(left.length, right.length).coerceAtLeast(1)
        val distance = Array(left.length + 1) { it }
        for (i in 1..left.length) {
            var previous = distance[0]
            distance[0] = i
            for (j in 1..right.length) {
                val current = distance[j]
                distance[j] = minOf(distance[j] + 1, distance[j - 1] + 1, previous + if (left[i - 1] == right[j - 1]) 0 else 1)
                previous = current
            }
        }
        return 1.0 - distance[right.length].toDouble() / max
    }

    private fun normalize(value: String): String = java.text.Normalizer.normalize(value.lowercase(), java.text.Normalizer.Form.NFD)
        .replace("\\p{M}+".toRegex(), "")
        .replace(Regex("\\(.*?\\)|\\[.*?]"), "")
        .replace(Regex("\\b(feat|ft|with|prod|remastered|version|deluxe)\\b.*"), "")
        .replace(Regex("[^a-z0-9]"), "")

    private fun mapError(error: Throwable): NetworkError = when {
        error is UnknownHostException || error is java.io.IOException -> NetworkError.NO_CONNECTION
        error is HttpException && error.code() == 429 -> NetworkError.RATE_LIMITED
        error is HttpException && error.code() >= 500 -> NetworkError.SERVER_ERROR
        error is kotlinx.serialization.SerializationException -> NetworkError.PARSE_ERROR
        else -> NetworkError.UNKNOWN
    }
}
