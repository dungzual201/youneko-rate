package com.youneko.rate.data.discogs

import com.youneko.rate.data.SettingsStore
import com.youneko.rate.data.credits.SourceResult
import com.youneko.rate.data.local.dao.RemoteMetadataCacheDao
import com.youneko.rate.data.local.entity.RemoteMetadataCacheEntity
import com.youneko.rate.data.musicbrainz.CreditCandidate
import com.youneko.rate.data.musicbrainz.Resource
import com.youneko.rate.data.musicbrainz.NetworkError
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import retrofit2.HttpException

private const val DISCOGS_PROVIDER = "discogs"
private const val DISCOGS_TTL_MS = 30L * 24L * 60L * 60L * 1_000L

@Serializable
private data class CachedDiscogsCredit(
    val personName: String,
    val personMbid: String? = null,
    val role: String,
    val instrumentOrAttribute: String? = null,
    val sourceUrl: String? = null,
)

@Serializable
private data class CachedDiscogsPayload(
    val credits: List<CachedDiscogsCredit> = emptyList(),
    val trackCredits: Map<String, List<CachedDiscogsCredit>> = emptyMap(),
    val coverUrl: String? = null,
)

data class DiscogsCreditResult(
    val credits: List<CreditCandidate>,
    val coverUrl: String?,
    val trackCredits: Map<String, List<CreditCandidate>> = emptyMap(),
)

interface CoverDiscogsProvider {
    suspend fun loadCover(albumId: String, title: String, artist: String): String?
}

@Singleton
class DiscogsCreditsService @Inject constructor(
    private val api: DiscogsApi,
    private val cacheDao: RemoteMetadataCacheDao,
    private val settings: SettingsStore,
    private val json: Json,
) : CoverDiscogsProvider {
    override suspend fun loadCover(albumId: String, title: String, artist: String): String? = when (val result = load(albumId, title, artist)) {
        is Resource.Success -> result.value.coverUrl
        else -> null
    }

    suspend fun testToken(token: String): Int = runCatching {
        api.searchReleases(artist = "Music", title = "Music", perPage = 1, authorization = "Discogs token=${token.trim()}")
        200
    }.getOrElse { error -> (error as? HttpException)?.code() ?: 0 }

    suspend fun loadSource(
        albumId: String,
        title: String,
        artist: String,
        trackTitles: Map<String, String> = emptyMap(),
        enabledSourcesHash: String = "default",
        forceRefresh: Boolean = false,
        manualReleaseId: Long? = null,
    ): SourceResult {
        if (settings.offlineOnly.first()) return SourceResult.Offline
        if (!settings.discogsEnabled.first()) return SourceResult.Empty()
        if (settings.discogsToken.first().trim().isBlank()) return SourceResult.NeedsToken
        return when (val result = load(albumId, title, artist, trackTitles, enabledSourcesHash, forceRefresh, manualReleaseId)) {
            is Resource.Success -> if (result.value.credits.isEmpty() && result.value.trackCredits.isEmpty()) SourceResult.Empty() else SourceResult.Success(result.value.credits, result.value.trackCredits)
            is Resource.Error -> if (result.kind == NetworkError.RATE_LIMITED) SourceResult.RateLimited(60) else SourceResult.Error(result.message ?: result.kind.name)
            is Resource.Loading -> SourceResult.Error("Discogs đang tải")
        }
    }

    suspend fun load(
        albumId: String,
        title: String,
        artist: String,
        trackTitles: Map<String, String> = emptyMap(),
        enabledSourcesHash: String = "default",
        forceRefresh: Boolean = false,
        manualReleaseId: Long? = null,
    ): Resource<DiscogsCreditResult> = withContext(Dispatchers.IO) {
        if (settings.offlineOnly.first()) return@withContext Resource.Success(DiscogsCreditResult(emptyList(), null))
        if (!settings.discogsEnabled.first()) return@withContext Resource.Success(DiscogsCreditResult(emptyList(), null))
        val token = settings.discogsToken.first().trim().takeIf { it.isNotBlank() }
            ?: return@withContext Resource.Error(NetworkError.NO_RESULTS, "NeedsToken")
        val key = "credits:v3:release:${albumId}:${normalize(title)}:${normalize(artist)}:discogs:$enabledSourcesHash:provider-v3"
        val cached = cacheDao.find(key)
        val now = System.currentTimeMillis()
        if (!forceRefresh && cached != null && cached.expiresAt > now) decode(cached.jsonBody)?.let { return@withContext Resource.Success(it) }
        runCatching {
            val authorization = "Discogs token=$token"
            val result = manualReleaseId?.let { DiscogsSearchResult(id = it) }
                ?: api.searchReleases(artist = artist, title = title, authorization = authorization).results.firstOrNull()
                ?: return@runCatching DiscogsCreditResult(emptyList(), null)
            val release = api.lookupRelease(result.id, authorization = authorization)
            val sourceUrl = "https://www.discogs.com/release/${release.id}"
            val albumCandidates = mutableListOf<CreditCandidate>()
            val trackCandidates = linkedMapOf<String, MutableList<CreditCandidate>>()
            release.labels.forEach { label ->
                label.name.trim().takeIf { it.isNotBlank() }?.let { albumCandidates += CreditCandidate(it, null, "Label", null, DISCOGS_PROVIDER, sourceUrl) }
            }
            release.extraArtists.forEach { extra ->
                val candidate = extra.toCandidate(sourceUrl)
                val positions = parseTrackSelector(extra.tracks)
                if (positions.isEmpty()) albumCandidates += candidate
                else positions.forEach { position ->
                    trackIdsForPosition(position, release.tracklist, trackTitles).forEach { id -> trackCandidates.getOrPut(id) { mutableListOf() } += candidate }
                }
            }
            release.tracklist.forEachIndexed { index, track ->
                val trackId = matchTrackId(track.title, trackTitles)
                if (trackId != null) track.extraArtists.forEach { extra -> trackCandidates.getOrPut(trackId) { mutableListOf() } += extra.toCandidate(sourceUrl) }
                else if (track.extraArtists.isNotEmpty()) track.extraArtists.forEach { extra -> albumCandidates += extra.toCandidate(sourceUrl) }
            }
            DiscogsCreditResult(
                credits = albumCandidates,
                coverUrl = release.images.firstOrNull()?.uri ?: release.images.firstOrNull()?.resourceUrl ?: result.coverImage,
                trackCredits = trackCandidates,
            )
        }.fold(
            onSuccess = { result ->
                cacheDao.upsert(RemoteMetadataCacheEntity(key = key, provider = DISCOGS_PROVIDER, jsonBody = encode(result), fetchedAt = now, expiresAt = now + DISCOGS_TTL_MS))
                Resource.Success(result)
            },
            onFailure = { error -> Resource.Error(mapError(error), error.message) },
        )
    }

    private fun DiscogsExtraArtist.toCandidate(sourceUrl: String) = CreditCandidate(
        personName = name.trim(),
        personMbid = null,
        role = mapRole(role),
        instrumentOrAttribute = role.trim().takeIf { it.isNotBlank() },
        sourceProvider = DISCOGS_PROVIDER,
        sourceUrl = resourceUrl ?: sourceUrl,
    )

    private fun encode(value: DiscogsCreditResult): String = json.encodeToString(
        CachedDiscogsPayload(
            credits = value.credits.map { CachedDiscogsCredit(it.personName, it.personMbid, it.role, it.instrumentOrAttribute, it.sourceUrl) },
            trackCredits = value.trackCredits.mapValues { (_, values) -> values.map { CachedDiscogsCredit(it.personName, it.personMbid, it.role, it.instrumentOrAttribute, it.sourceUrl) } },
            coverUrl = value.coverUrl,
        ),
    )

    private fun decode(raw: String): DiscogsCreditResult? = runCatching {
        val payload = json.decodeFromString<CachedDiscogsPayload>(raw)
        fun convert(value: CachedDiscogsCredit) = CreditCandidate(value.personName, value.personMbid, value.role, value.instrumentOrAttribute, DISCOGS_PROVIDER, value.sourceUrl)
        DiscogsCreditResult(payload.credits.map(::convert), payload.coverUrl, payload.trackCredits.mapValues { (_, values) -> values.map(::convert) })
    }.getOrNull()

    private fun matchTrackId(title: String, trackTitles: Map<String, String>): String? = trackTitles.entries.maxByOrNull { similarity(title, it.value) }
        ?.takeIf { similarity(title, it.value) >= 0.85 }?.key

    private fun trackIdsForPosition(position: Int, tracks: List<DiscogsTrack>, trackTitles: Map<String, String>): List<String> {
        val indexed = tracks.getOrNull(position - 1)?.let { matchTrackId(it.title, trackTitles) }
        return indexed?.let(::listOf) ?: emptyList()
    }

    private fun parseTrackSelector(raw: String?): List<Int> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.lowercase()
            .replace(Regex("\\([^)]*\\)"), "")
            .split(',')
            .flatMap { token ->
                val normalized = token.trim().replace(Regex("^[a-z]+"), "")
                val range = Regex("(\\d+)\\s+to\\s+(\\d+)").find(normalized)
                if (range != null) (range.groupValues[1].toInt()..range.groupValues[2].toInt()).toList()
                else normalized.toIntOrNull()?.let(::listOf).orEmpty()
            }
            .distinct()
    }

    private fun mapRole(raw: String): String {
        val role = raw.substringBefore('(').trim().lowercase()
        return when {
            "producer" in role -> "Producer"
            "mix" in role -> "Mixing engineer"
            "master" in role -> "Mastering engineer"
            "engineer" in role || "record" in role -> "Recording"
            "program" in role -> "Programming"
            "written" in role || "composer" in role || "lyric" in role -> "Writer"
            "vocal" in role -> if ("back" in role || "background" in role) "Background vocals" else "Lead vocals"
            else -> raw.trim().ifBlank { "Other" }
        }
    }

    private fun normalize(value: String): String = java.text.Normalizer.normalize(value.trim().lowercase(), java.text.Normalizer.Form.NFD)
        .replace("\\p{M}+".toRegex(), "")
        .replace(Regex("[^a-z0-9]"), "")

    private fun similarity(a: String, b: String): Double {
        val left = normalize(a); val right = normalize(b)
        if (left == right) return 1.0
        val max = maxOf(left.length, right.length).coerceAtLeast(1)
        val row = IntArray(right.length + 1) { it }
        for (i in left.indices) {
            var diagonal = row[0]; row[0] = i + 1
            for (j in right.indices) {
                val old = row[j + 1]
                row[j + 1] = minOf(row[j + 1] + 1, row[j] + 1, diagonal + if (left[i] == right[j]) 0 else 1)
                diagonal = old
            }
        }
        return 1.0 - row[right.length].toDouble() / max
    }

    private fun mapError(error: Throwable): NetworkError = when {
        error is java.net.UnknownHostException || error is java.io.IOException -> NetworkError.NO_CONNECTION
        error is retrofit2.HttpException && error.code() == 429 -> NetworkError.RATE_LIMITED
        error is retrofit2.HttpException && error.code() >= 500 -> NetworkError.SERVER_ERROR
        error is kotlinx.serialization.SerializationException -> NetworkError.PARSE_ERROR
        else -> NetworkError.UNKNOWN
    }
}
