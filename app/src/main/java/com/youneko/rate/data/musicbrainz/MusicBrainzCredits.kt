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
    val beginDate: String? = null,
    val endDate: String? = null,
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
    "recording", "engineer", "recording engineer", "mix", "assistant mix", "mixing engineer", "mastering", "mastering engineer", "programming", "editor" -> CreditGroup.ENGINEERING
    "vocal", "lead vocals", "background vocals", "instrument", "performer", "conductor", "orchestra" -> CreditGroup.PERFORMANCE
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
    val beginDate: String? = null,
    val endDate: String? = null,
)

object CreditMerger {
    fun merge(albumId: String?, trackId: String?, candidates: List<CreditCandidate>): List<CreditEntity> =
        candidates
            .groupBy { normalize(it.personName) to normalize(it.role) }
            .values
            .mapIndexed { index, samePerson ->
                val first = samePerson.first()
                CreditEntity(
                    id = UUID.nameUUIDFromBytes("${albumId ?: "track"}:${trackId.orEmpty()}:${normalize(first.personName)}:${normalize(first.role)}".toByteArray()).toString(),
                    albumId = albumId,
                    trackId = trackId,
                    personName = first.personName,
                    personMbid = samePerson.firstNotNullOfOrNull { it.personMbid },
                    role = first.role,
                    instrumentOrAttribute = samePerson.mapNotNull { it.instrumentOrAttribute }.distinct().joinToString(", ").ifBlank { null },
                    sourceProvider = samePerson.map { it.sourceProvider }.distinct().joinToString(","),
                    sourceUrl = samePerson.firstNotNullOfOrNull { it.sourceUrl },
                    sortOrder = index,
                    beginDate = samePerson.firstNotNullOfOrNull { it.beginDate },
                    endDate = samePerson.firstNotNullOfOrNull { it.endDate },
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
        if (trackId == null) creditDao.observeForAlbum(albumId) else creditDao.observeForItem(albumId, trackId)

    suspend fun loadAlbumCredits(
        album: AlbumEntity,
        forceRefresh: Boolean = false,
        onProgress: suspend (completed: Int, total: Int) -> Unit = { _, _ -> },
    ): Resource<CreditLoadReport> = withContext(Dispatchers.IO) {
        val releaseMbid = album.mbid ?: return@withContext Resource.Error(NetworkError.NO_RESULTS, "Album chưa có MusicBrainz MBID")
        val cacheKey = "credits:v2:album:$releaseMbid"
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
        val releaseRecordings = mutableMapOf<String, MbRecording>()
        val fallbackRequired = runCatching {
            val release = api.lookupRelease(releaseMbid)
            releaseCandidates += relationCandidates(release.relations, "https://musicbrainz.org/release/$releaseMbid")
            release.media.flatMap { medium -> medium.tracks }.mapNotNull { it.recording }.forEach { recording ->
                if (recording.id.isNotBlank()) releaseRecordings[recording.id] = recording
            }
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
            val recording = releaseRecordings[recordingMbid]
                ?: runCatching { api.lookupRecording(recordingMbid) }.getOrNull()
            if (recording != null) {
                candidates += relationCandidates(recording.relations, "https://musicbrainz.org/recording/$recordingMbid")
                val workMbids = recording.relations.mapNotNull { it.work?.id }.distinct()
                workMbids.firstOrNull()?.let { workMbid ->
                    if (track.workMbid != workMbid) trackDao.update(track.copy(workMbid = workMbid))
                }
                workMbids.forEach { workMbid ->
                    coroutineContext.ensureActive()
                    runCatching { api.lookupWork(workMbid) }.getOrNull()?.let { work ->
                        candidates += relationCandidates(work.relations, "https://musicbrainz.org/work/$workMbid")
                    }
                }
                successfulItems++
            }
            credits += CreditMerger.merge(null, track.id, candidates)
            completed++
            onProgress(completed, total)
        }

        if (credits.isEmpty() && recordingTracks.isNotEmpty() && successfulItems == 0) {
            return@withContext Resource.Error(NetworkError.NO_CONNECTION, "Không tải được credits từ MusicBrainz")
        }
        creditDao.deleteAlbumCredits(album.id)
        creditDao.deleteTrackCreditsForAlbum(album.id)
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
        val cacheKey = "credits:v2:track:$recordingMbid"
        if (!forceRefresh) readCache(cacheKey)?.let { cached ->
            creditDao.deleteTrackCredits(trackId)
            creditDao.upsertAll(cached)
            return@withContext Resource.Success(CreditLoadReport(cached, true, 0, 0))
        }
        onProgress(0, 1)
        val recording = runCatching { api.lookupRecording(recordingMbid) }.getOrElse { return@withContext it.toNetworkError() }
        val candidates = relationCandidates(recording.relations, "https://musicbrainz.org/recording/$recordingMbid").toMutableList()
        val workMbids = recording.relations.mapNotNull { it.work?.id }.distinct()
        workMbids.firstOrNull()?.let { workMbid ->
            if (track.workMbid != workMbid) trackDao.update(track.copy(workMbid = workMbid))
        }
        workMbids.forEach { workMbid ->
            coroutineContext.ensureActive()
            runCatching { api.lookupWork(workMbid) }.getOrNull()?.let { work ->
                candidates += relationCandidates(work.relations, "https://musicbrainz.org/work/$workMbid")
            }
        }
        val credits = CreditMerger.merge(null, trackId, candidates)
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

    private fun relationCandidates(relations: List<MbRelation>, sourceUrl: String): List<CreditCandidate> =
        relations.mapNotNull { relation ->
            val targetType = relation.targetType?.trim()?.lowercase()
            if (targetType == "url" || (targetType != null && targetType !in setOf("artist", "label"))) return@mapNotNull null
            val person = relation.artist
            val label = relation.label
            val name = person?.name?.takeIf { it.isNotBlank() } ?: label?.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val attributes = relation.attributes.map { it.trim().lowercase() }.filter { it.isNotBlank() }.distinct()
            val task = relation.attributeValues.entries.firstOrNull { it.key.equals("task", ignoreCase = true) }?.value.orEmpty().trim().lowercase()
            val type = relation.type.trim().lowercase()
            val role = when (type) {
                "mix" -> if (attributes.any { it == "assistant" }) "Assistant mix" else "Mix"
                "vocal" -> if (attributes.any { it == "background vocals" }) "Background vocals" else "Lead vocals"
                "engineer" -> when (task) {
                    "mix" -> "Mixing engineer"
                    "mastering" -> "Mastering engineer"
                    else -> "Engineer"
                }
                "producer" -> "Producer"
                "programming" -> "Programming"
                "recording" -> "Recording"
                "phonographic copyright" -> "Phonographic copyright"
                "composer", "lyricist", "writer" -> "Writer"
                else -> relation.type.ifBlank { "Unknown" }.replaceFirstChar { it.uppercase() }
            }
            val consumedAttributes = setOf("assistant", "background vocals")
            CreditCandidate(
                personName = name,
                personMbid = person?.id ?: label?.id,
                role = role,
                instrumentOrAttribute = attributes.filterNot { it in consumedAttributes }.joinToString(", ").ifBlank { null },
                sourceProvider = MUSICBRAINZ_CREDITS,
                sourceUrl = person?.id?.let { "https://musicbrainz.org/artist/$it" } ?: label?.id?.let { "https://musicbrainz.org/label/$it" } ?: sourceUrl,
                beginDate = relation.begin,
                endDate = relation.end,
            )
        }

    private fun toCached(value: CreditEntity) = CachedCredit(
        value.id, value.albumId, value.trackId, value.personName, value.personMbid, value.role,
        value.instrumentOrAttribute, value.sourceProvider, value.sourceUrl, value.sortOrder,
        value.beginDate, value.endDate,
    )

    private fun fromCached(value: CachedCredit) = CreditEntity(
        value.id, value.albumId, value.trackId, value.personName, value.personMbid, value.role,
        value.instrumentOrAttribute, value.sourceProvider, value.sourceUrl, value.sortOrder,
        value.beginDate, value.endDate,
    )
}
