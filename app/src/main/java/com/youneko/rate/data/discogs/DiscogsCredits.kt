package com.youneko.rate.data.discogs

import com.youneko.rate.data.SettingsStore
import com.youneko.rate.data.local.dao.RemoteMetadataCacheDao
import com.youneko.rate.data.local.entity.RemoteMetadataCacheEntity
import com.youneko.rate.data.musicbrainz.CreditCandidate
import com.youneko.rate.data.musicbrainz.CreditGroup
import com.youneko.rate.data.musicbrainz.Resource
import com.youneko.rate.data.musicbrainz.NetworkError
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

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
    val coverUrl: String? = null,
)

data class DiscogsCreditResult(
    val credits: List<CreditCandidate>,
    val coverUrl: String?,
)

@Singleton
class DiscogsCreditsService @Inject constructor(
    private val api: DiscogsApi,
    private val cacheDao: RemoteMetadataCacheDao,
    private val settings: SettingsStore,
    private val json: Json,
) {
    suspend fun load(albumId: String, title: String, artist: String): Resource<DiscogsCreditResult> = withContext(Dispatchers.IO) {
        if (settings.offlineOnly.first()) return@withContext Resource.Error(NetworkError.OFFLINE)
        if (!settings.discogsEnabled.first()) return@withContext Resource.Success(DiscogsCreditResult(emptyList(), null))
        val token = settings.discogsToken.first().trim().takeIf { it.isNotBlank() }
            ?: return@withContext Resource.Success(DiscogsCreditResult(emptyList(), null))
        val key = "discogs:release:${albumId}:${normalize(title)}:${normalize(artist)}"
        val cached = cacheDao.find(key)
        val now = System.currentTimeMillis()
        if (cached != null && cached.expiresAt > now) {
            decode(cached.jsonBody)?.let { return@withContext Resource.Success(it) }
        }
        runCatching {
            val auth = "Discogs token=$token"
            val search = api.searchReleases(artist = artist, title = title, authorization = auth)
            val result = search.results.firstOrNull() ?: return@runCatching DiscogsCreditResult(emptyList(), null)
            val release = api.lookupRelease(result.id, authorization = auth)
            val sourceUrl = "https://www.discogs.com/release/${release.id}"
            val candidates = buildList {
                release.labels.forEach { label ->
                    label.name.takeIf(String::isNotBlank)?.let {
                        add(CreditCandidate(it, null, "Label", null, DISCOGS_PROVIDER, sourceUrl))
                    }
                }
                release.extraArtists.forEach { extra ->
                    val name = extra.name.trim().takeIf { it.isNotBlank() } ?: return@forEach
                    val role = mapRole(extra.role)
                    add(CreditCandidate(name, null, role, extra.role.trim().takeIf { it.isNotBlank() }, DISCOGS_PROVIDER, extra.resourceUrl ?: sourceUrl))
                }
            }
            DiscogsCreditResult(candidates, release.images.firstOrNull()?.uri ?: release.images.firstOrNull()?.resourceUrl ?: result.coverImage)
        }.fold(
            onSuccess = { result ->
                cacheDao.upsert(
                    RemoteMetadataCacheEntity(
                        key = key,
                        provider = DISCOGS_PROVIDER,
                        jsonBody = encode(result),
                        fetchedAt = now,
                        expiresAt = now + DISCOGS_TTL_MS,
                    ),
                )
                Resource.Success(result)
            },
            onFailure = { error -> Resource.Error(mapError(error), error.message) },
        )
    }

    private fun encode(value: DiscogsCreditResult): String = json.encodeToString(
        CachedDiscogsPayload(
            credits = value.credits.map { CachedDiscogsCredit(it.personName, it.personMbid, it.role, it.instrumentOrAttribute, it.sourceUrl) },
            coverUrl = value.coverUrl,
        ),
    )

    private fun decode(raw: String): DiscogsCreditResult? = runCatching {
        val payload = json.decodeFromString<CachedDiscogsPayload>(raw)
        DiscogsCreditResult(
            credits = payload.credits.map { CreditCandidate(it.personName, it.personMbid, it.role, it.instrumentOrAttribute, DISCOGS_PROVIDER, it.sourceUrl) },
            coverUrl = payload.coverUrl,
        )
    }.getOrNull()

    private fun mapRole(raw: String): String {
        val role = raw.lowercase()
        return when {
            "producer" in role -> "Producer"
            "mix" in role -> "Mixing engineer"
            "master" in role -> "Mastering engineer"
            "engineer" in role || "record" in role -> "Recording"
            "program" in role -> "Programming"
            "written" in role || "composer" in role || "lyric" in role -> "Writer"
            else -> raw.trim().ifBlank { "Other" }
        }
    }

    private fun normalize(value: String): String = value.trim().lowercase().replace(Regex("\\s+"), " ")

    private fun mapError(error: Throwable): NetworkError = when {
        error is java.net.UnknownHostException || error is java.io.IOException -> NetworkError.NO_CONNECTION
        error is retrofit2.HttpException && error.code() == 429 -> NetworkError.RATE_LIMITED
        error is retrofit2.HttpException && error.code() >= 500 -> NetworkError.SERVER_ERROR
        error is kotlinx.serialization.SerializationException -> NetworkError.PARSE_ERROR
        else -> NetworkError.UNKNOWN
    }
}
