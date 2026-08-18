package com.youneko.rate.data.musicbrainz

import com.youneko.rate.data.local.dao.CreditDao
import com.youneko.rate.data.local.dao.RemoteMetadataCacheDao
import com.youneko.rate.data.local.dao.TrackDao
import com.youneko.rate.data.local.entity.AlbumEntity
import com.youneko.rate.data.local.entity.CreditEntity
import java.text.Normalizer
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

private const val MUSICBRAINZ_CREDITS = "musicbrainz"
private const val CREDITS_TTL_MS = 30L * 24L * 60L * 60L * 1_000L

@Serializable
private data class CachedCredit(
    val id: String,
    val albumId: String? = null,
    val trackId: String? = null,
    val personName: String,
    val personMbid: String? = null,
    val role: String,
    val instrumentOrAttribute: String? = null,
    val sourceProvider: String,
    val sourceUrl: String? = null,
    val sortOrder: Int,
)

@Serializable
private data class CachedCredits(val values: List<CachedCredit>)

data class CreditLoadReport(
    val credits: List<CreditEntity>,
    val fromCache: Boolean,
    val fetchedItems: Int,
    val totalItems: Int,
)

enum class CreditGroup {
    WRITING,
    PRODUCTION,
    ENGINEERING,
    PERFORMANCE,
    RELEASE,
    OTHER,
}

fun creditGroupForRole(role: String): CreditGroup = when (role.trim().lowercase()) {
    "composer", "lyricist", "writer", "arranger", "orchestrator", "librettist" -> CreditGroup.WRITING
    "producer", "executive producer", "co-producer" -> CreditGroup.PRODUCTION
    "recording engineer", "mix", "mastering", "programming", "editor" -> CreditGroup.ENGINEERING
    "vocal", "instrument", "performer", "conductor", "orchestra" -> CreditGroup.PERFORMANCE
    "label", "publisher", "copyright", "phonographic copyright" -> CreditGroup.RELEASE
    else -> CreditGroup.OTHER
}

data class CreditCandidate(
    val personName: String,
    val personMbid: String?,
    val role: String,
    val instrumentOrAttribute: String?,
    val sourceProvider: String,
    val sourceUrl: String?,
)

object CreditMerger {
    fun merge(albumId: String, trackId: String?, candidates: List<CreditCandidate>): List<CreditEntity> =
        candidates
            .groupBy { normalize(it.personName) to normalize(it.role) }
            .values
            .mapIndexed { index, samePerson ->
                val first = samePerson.first()
                CreditEntity(
                    id = UUID.nameUUIDFromBytes("$albumId:${trackId.orEmpty()}:${normalize(first.personName)}:${normalize(first.role)}".toByteArray()).toString(),
                    albumId = albumId,
                    trackId = trackId,
                    personName = first.personName,
                    personMbid = samePerson.firstNotNullOfOrNull { it.personMbid },
                    role = first.role,
                    instrumentOrAttribute = samePerson.mapNotNull { it.instrumentOrAttribute }.distinct().joinToString(", ").ifBlank { null },
                    sourceProvider = samePerson.map { it.sourceProvider }.distinct().joinToString(","),
                    sourceUrl = samePerson.firstNotNullOfOrNull { it.sourceUrl },
                    sortOrder = index,
                )
            }

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace("\\p{M}+".toRegex(), "")
        .lowercase()
        .replace(Regex("\\s+"), " ")
        .trim()
        .replace(Regex("\\s*\\(\\d+\\)$"), "")
        .trim()
}

@Singleton
class MusicBrainzCreditsService @Inject constructor(
    private val api: MusicBrainzApi,
    private val creditDao: CreditDao,
    private val cacheDao: RemoteMetadataCacheDao,
    private val trackDao: TrackDao,
    private val json: Json,
) {
    fun observeCredits(albumId: String, trackId: String?): Flow<List<CreditEntity>> =
        creditDao.observeForItem(albumId, trackId)

    suspend fun loadAlbumCredits(
        album: AlbumEntity,
        forceRefresh: Boolean = false,
        onProgress: suspend (completed: Int, total: Int) -> Unit = { _, _ -> },
    ): Resource<CreditLoadReport> = withContext(Dispatchers.IO) {
        val releaseMbid = album.mbid ?: return@withContext Resource.Error(NetworkError.NO_RESULTS, "Album chưa có MusicBrainz MBID")
        val cacheKey = "credits:album:$releaseMbid"
        if (!forceRefresh) readCache(cacheKey)?.let { cached ->
            creditDao.deleteAlbumCredits(album.id)
            creditDao.upsertAll(cached)
            return@withContext Resource.Success(CreditLoadReport(cached, true, 0, 0))
        }

        val tracks = trackDao.findForAlbum(album.id)
        val recordingTracks = tracks.filter { !it.recordingMbid.isNullOrBlank() }
        val total = recordingTracks.size + 1
        var completed = 0
        val releaseCandidates = mutableListOf<CreditCandidate>()
        val fallbackRequired = runCatching {
            val release = api.lookupRelease(releaseMbid)
            releaseCandidates += relationCandidates(release.relations, "https://musicbrainz.org/release/$releaseMbid")
            completed++
            onProgress(completed, total)
            false
        }.getOrElse {
            completed++
            onProgress(completed, total)
            true
        }

        val credits = mutableListOf<CreditEntity>()
        credits += CreditMerger.merge(album.id, null, releaseCandidates)
        var successfulItems = if (fallbackRequired) 0 else 1
        recordingTracks.forEach { track ->
            coroutineContext.ensureActive()
            val recordingMbid = track.recordingMbid ?: return@forEach
            val candidates = mutableListOf<CreditCandidate>()
            val recording = runCatching { api.lookupRecording(recordingMbid) }.getOrNull()
            if (recording != null) {
                candidates += relationCandidates(recording.relations, "https://musicbrainz.org/recording/$recordingMbid")
                recording.relations.mapNotNull { it.work?.id }.distinct().forEach { workMbid ->
                    coroutineContext.ensureActive()
                    runCatching { api.lookupWork(workMbid) }.getOrNull()?.let { work ->
                        candidates += relationCandidates(work.relations, "https://musicbrainz.org/work/$workMbid", work.title)
                    }
                }
                successfulItems++
            }
            credits += CreditMerger.merge(album.id, track.id, candidates)
            completed++
            onProgress(completed, total)
        }

        if (credits.isEmpty() && recordingTracks.isNotEmpty() && successfulItems == 0) {
            return@withContext Resource.Error(NetworkError.NO_CONNECTION, "Không tải được credits từ MusicBrainz")
        }
        creditDao.deleteAlbumCredits(album.id)
        creditDao.upsertAll(credits)
        cacheDao.upsert(
            com.youneko.rate.data.local.entity.RemoteMetadataCacheEntity(
                key = cacheKey,
                provider = MUSICBRAINZ_CREDITS,
                jsonBody = json.encodeToString(CachedCredits(credits.map(::toCached))),
                fetchedAt = System.currentTimeMillis(),
                expiresAt = System.currentTimeMillis() + CREDITS_TTL_MS,
            ),
        )
        Resource.Success(CreditLoadReport(credits, false, successfulItems, total))
    }

    suspend fun loadTrackCredits(
        album: AlbumEntity,
        trackId: String,
        forceRefresh: Boolean = false,
        onProgress: suspend (completed: Int, total: Int) -> Unit = { _, _ -> },
    ): Resource<CreditLoadReport> = withContext(Dispatchers.IO) {
        val track = trackDao.findById(trackId) ?: return@withContext Resource.Error(NetworkError.NO_RESULTS, "Bài hát không tồn tại")
        val recordingMbid = track.recordingMbid ?: return@withContext Resource.Error(NetworkError.NO_RESULTS, "Bài hát chưa có recording MBID")
        val cacheKey = "credits:track:$recordingMbid"
        if (!forceRefresh) readCache(cacheKey)?.let { cached ->
            creditDao.deleteTrackCredits(trackId)
            creditDao.upsertAll(cached)
            return@withContext Resource.Success(CreditLoadReport(cached, true, 0, 0))
        }
        onProgress(0, 1)
        val recording = runCatching { api.lookupRecording(recordingMbid) }.getOrElse { return@withContext it.toNetworkError() }
        val candidates = relationCandidates(recording.relations, "https://musicbrainz.org/recording/$recordingMbid").toMutableList()
        recording.relations.mapNotNull { it.work?.id }.distinct().forEach { workMbid ->
            coroutineContext.ensureActive()
            runCatching { api.lookupWork(workMbid) }.getOrNull()?.let { work ->
                candidates += relationCandidates(work.relations, "https://musicbrainz.org/work/$workMbid", work.title)
            }
        }
        val credits = CreditMerger.merge(album.id, trackId, candidates)
        creditDao.deleteTrackCredits(trackId)
        creditDao.upsertAll(credits)
        cacheDao.upsert(
            com.youneko.rate.data.local.entity.RemoteMetadataCacheEntity(
                key = cacheKey,
                provider = MUSICBRAINZ_CREDITS,
                jsonBody = json.encodeToString(CachedCredits(credits.map(::toCached))),
                fetchedAt = System.currentTimeMillis(),
                expiresAt = System.currentTimeMillis() + CREDITS_TTL_MS,
            ),
        )
        onProgress(1, 1)
        Resource.Success(CreditLoadReport(credits, false, 1, 1))
    }

    suspend fun readCache(key: String): List<CreditEntity>? {
        val cached = cacheDao.find(key) ?: return null
        if (cached.expiresAt <= System.currentTimeMillis()) return null
        return runCatching { json.decodeFromString<CachedCredits>(cached.jsonBody).values.map(::fromCached) }.getOrNull()
    }

    private fun relationCandidates(relations: List<MbRelation>, sourceUrl: String, context: String? = null): List<CreditCandidate> =
        relations.mapNotNull { relation ->
            val person = relation.artist
            val label = relation.label
            val url = relation.url
            val name = person?.name ?: label?.name ?: url?.resource ?: relation.work?.title ?: return@mapNotNull null
            CreditCandidate(
                personName = name,
                personMbid = person?.id ?: label?.id,
                role = relation.type.ifBlank { "unknown" },
                instrumentOrAttribute = (relation.attributes + listOfNotNull(context)).distinct().joinToString(", ").ifBlank { null },
                sourceProvider = MUSICBRAINZ_CREDITS,
                sourceUrl = person?.id?.let { "https://musicbrainz.org/artist/$it" } ?: sourceUrl,
            )
        }

    private fun toCached(value: CreditEntity) = CachedCredit(
        value.id, value.albumId, value.trackId, value.personName, value.personMbid, value.role,
        value.instrumentOrAttribute, value.sourceProvider, value.sourceUrl, value.sortOrder,
    )

    private fun fromCached(value: CachedCredit) = CreditEntity(
        value.id, value.albumId, value.trackId, value.personName, value.personMbid, value.role,
        value.instrumentOrAttribute, value.sourceProvider, value.sourceUrl, value.sortOrder,
    )
}
