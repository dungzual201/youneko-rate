package com.youneko.rate.data.genius

import android.util.Log
import com.youneko.rate.data.SettingsStore
import com.youneko.rate.data.credits.SourceResult
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

    suspend fun testToken(token: String): Int = runCatching {
        request { api.search("music", "Bearer ${token.trim()}") }
        200
    }.getOrElse { error -> (error as? HttpException)?.code() ?: 0 }

    suspend fun loadSource(trackId: String, title: String, artist: String, enabledSourcesHash: String = "default", forceRefresh: Boolean = false, manualSongId: Long? = null): SourceResult {
        if (settings.offlineOnly.first()) return SourceResult.Offline
        if (!settings.geniusEnabled.first()) return SourceResult.Empty()
        if (settings.geniusToken.first().trim().isBlank()) return SourceResult.NeedsToken
        return when (val result = load(trackId, title, artist, enabledSourcesHash, forceRefresh, manualSongId)) {
            is Resource.Success -> if (result.value.credits.isEmpty()) SourceResult.Empty() else SourceResult.Success(result.value.credits)
            is Resource.Error -> when (result.kind) {
                NetworkError.RATE_LIMITED -> SourceResult.RateLimited(60)
                NetworkError.NO_RESULTS -> SourceResult.NoMatch
                else -> SourceResult.Error(result.message ?: result.kind.name)
            }
            is Resource.Loading -> SourceResult.Error("Genius đang tải")
        }
    }

    suspend fun load(trackId: String, title: String, artist: String, enabledSourcesHash: String = "default", forceRefresh: Boolean = false, manualSongId: Long? = null): Resource<GeniusCreditResult> = withContext(Dispatchers.IO) {
        if (settings.offlineOnly.first()) return@withContext Resource.Success(GeniusCreditResult(emptyList()))
        if (!settings.geniusEnabled.first()) return@withContext Resource.Success(GeniusCreditResult(emptyList()))
        val token = settings.geniusToken.first().trim().takeIf { it.isNotBlank() }
            ?: return@withContext Resource.Success(GeniusCreditResult(emptyList()))
        val key = "credits:v3:track:$trackId:${normalize(title)}:${normalize(artist)}:genius:$enabledSourcesHash:provider-v3"
        val cached = cacheDao.find(key)
        if (!forceRefresh && cached != null && cached.expiresAt > System.currentTimeMillis()) {
            decode(cached.jsonBody)?.let { return@withContext Resource.Success(it) }
        }
        runCatching {
            val authorization = "Bearer $token"
            val queries = listOf(
                "$artist $title",
                "$artist ${title.lowercase()}",
                "$artist ${removeDiacritics(title)}",
                "${removeDiacritics(artist)} ${removeDiacritics(title)}",
                title,
            ).distinct()
            val searchedResults = mutableListOf<GeniusSearchResult>()
            for (query in queries) {
                searchedResults += request { api.search(query, authorization) }.response.hits.map { it.result }
            }
            val candidate = manualSongId?.let { GeniusSearchResult(id = it, title = title, artistNames = artist) }
                ?: searchedResults.asSequence()
                    .distinctBy { it.id }
                    .firstOrNull { match(title, artist, it.title, it.artistNames) }
                ?: run {
                    Log.d("GeniusCredits", "NoMatch title=$title artist=$artist queries=${queries.joinToString(" | ")}")
                    return@runCatching GeniusCreditResult(emptyList())
                }
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
        jaroWinkler(normalize(title), normalize(candidateTitle)) >= 0.85 && jaroWinkler(normalize(artist), normalize(candidateArtist)) >= 0.75

    private fun jaroWinkler(left: String, right: String): Double {
        if (left == right) return 1.0
        if (left.isEmpty() || right.isEmpty()) return 0.0
        val distance = (maxOf(left.length, right.length) / 2 - 1).coerceAtLeast(0)
        val leftMatches = BooleanArray(left.length)
        val rightMatches = BooleanArray(right.length)
        var matches = 0
        for (i in left.indices) {
            for (j in (i - distance).coerceAtLeast(0)..(i + distance).coerceAtMost(right.lastIndex)) {
                if (!rightMatches[j] && left[i] == right[j]) {
                    leftMatches[i] = true
                    rightMatches[j] = true
                    matches++
                    break
                }
            }
        }
        if (matches == 0) return 0.0
        val leftSequence = left.indices.filter { leftMatches[it] }.map { left[it] }
        val rightSequence = right.indices.filter { rightMatches[it] }.map { right[it] }
        val transpositions = leftSequence.zip(rightSequence).count { it.first != it.second } / 2.0
        val jaro = (matches.toDouble() / left.length + matches.toDouble() / right.length + (matches - transpositions) / matches) / 3.0
        val prefix = left.zip(right).take(4).takeWhile { it.first == it.second }.count()
        return jaro + prefix * 0.1 * (1.0 - jaro)
    }

    private fun normalize(value: String): String = java.text.Normalizer.normalize(removeDiacritics(value).lowercase(), java.text.Normalizer.Form.NFD)
        .replace("\\p{M}+".toRegex(), "")
        .replace(Regex("\\(.*?\\)|\\[.*?]"), "")
        .replace(Regex("\\b(feat|ft|with|prod|remastered|version|deluxe|official|audio|mv|lyrics|bản mở rộng)\\b.*"), "")
        .replace(Regex("[^a-z0-9]"), "")

    private fun removeDiacritics(value: String): String = value
        .replace('đ', 'd')
        .replace('Đ', 'D')

    private fun mapError(error: Throwable): NetworkError = when {
        error is UnknownHostException || error is java.io.IOException -> NetworkError.NO_CONNECTION
        error is HttpException && error.code() == 429 -> NetworkError.RATE_LIMITED
        error is HttpException && error.code() >= 500 -> NetworkError.SERVER_ERROR
        error is kotlinx.serialization.SerializationException -> NetworkError.PARSE_ERROR
        else -> NetworkError.UNKNOWN
    }
}

internal fun geniusMatchForTest(title: String, artist: String, candidateTitle: String, candidateArtist: String): Boolean {
    fun normalizeForTest(value: String): String = java.text.Normalizer.normalize(value.replace('đ', 'd').replace('Đ', 'D').lowercase(), java.text.Normalizer.Form.NFD)
        .replace("\\p{M}+".toRegex(), "")
        .replace(Regex("\\(.*?\\)|\\[.*?]"), "")
        .replace(Regex("[^a-z0-9]"), "")
    fun jaroWinkler(left: String, right: String): Double {
        if (left == right) return 1.0
        if (left.isEmpty() || right.isEmpty()) return 0.0
        val distance = (maxOf(left.length, right.length) / 2 - 1).coerceAtLeast(0)
        val lm = BooleanArray(left.length); val rm = BooleanArray(right.length); var matches = 0
        for (i in left.indices) for (j in (i - distance).coerceAtLeast(0)..(i + distance).coerceAtMost(right.lastIndex)) {
            if (!rm[j] && left[i] == right[j]) { lm[i] = true; rm[j] = true; matches++; break }
        }
        if (matches == 0) return 0.0
        val ls = left.indices.filter { lm[it] }.map { left[it] }
        val rs = right.indices.filter { rm[it] }.map { right[it] }
        val transpositions = ls.zip(rs).count { it.first != it.second } / 2.0
        val jaro = (matches.toDouble() / left.length + matches.toDouble() / right.length + (matches - transpositions) / matches) / 3.0
        val prefix = left.zip(right).take(4).takeWhile { it.first == it.second }.count()
        return jaro + prefix * 0.1 * (1.0 - jaro)
    }
    return jaroWinkler(normalizeForTest(title), normalizeForTest(candidateTitle)) >= 0.85 && jaroWinkler(normalizeForTest(artist), normalizeForTest(candidateArtist)) >= 0.75
}
