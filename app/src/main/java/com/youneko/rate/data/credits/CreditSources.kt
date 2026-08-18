package com.youneko.rate.data.credits

import com.youneko.rate.data.SettingsStore
import com.youneko.rate.data.discogs.DiscogsCreditsService
import com.youneko.rate.data.genius.GeniusCreditsService
import com.youneko.rate.data.local.dao.CreditDao
import com.youneko.rate.data.local.entity.CreditEntity
import com.youneko.rate.data.local.entity.TrackEntity
import com.youneko.rate.data.musicbrainz.CreditCandidate
import com.youneko.rate.data.musicbrainz.MusicBrainzCreditsService
import com.youneko.rate.data.musicbrainz.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Stable ids used by settings, cache keys and the source picker UI. */
enum class CreditSourceId(
    val displayName: String,
    val needsToken: Boolean,
    val worksOffline: Boolean,
) {
    FILE_TAG("Tag trong file", needsToken = false, worksOffline = true),
    MUSICBRAINZ("MusicBrainz", needsToken = false, worksOffline = false),
    DISCOGS("Discogs", needsToken = true, worksOffline = false),
    GENIUS("Genius", needsToken = true, worksOffline = false),
    DEEZER("Deezer", needsToken = false, worksOffline = false),
    ITUNES("Apple Music", needsToken = false, worksOffline = false),
    ;

    companion object {
        val defaultOrder = listOf(FILE_TAG, MUSICBRAINZ, DISCOGS, GENIUS, DEEZER, ITUNES)
        fun parse(value: String): List<CreditSourceId> = value.split(',')
            .mapNotNull { raw -> entries.firstOrNull { it.name == raw.trim() } }
            .distinct()
            .let { parsed -> if (parsed.isEmpty()) defaultOrder else parsed + defaultOrder.filterNot(parsed::contains) }
        fun encode(value: Iterable<CreditSourceId>): String = value.distinct().joinToString(",") { it.name }
    }
}

sealed interface SourceResult {
    data class Success(
        val credits: List<CreditCandidate>,
        val trackCredits: Map<String, List<CreditCandidate>> = emptyMap(),
        val fetchedAt: Long = System.currentTimeMillis(),
    ) : SourceResult
    data object Empty : SourceResult
    data object NeedsToken : SourceResult
    data object NoMatch : SourceResult
    data object Offline : SourceResult
    data class RateLimited(val retryAfterSec: Int) : SourceResult
    data class Error(val message: String) : SourceResult
}

data class CreditFetchRequest(
    val albumId: String,
    val albumTitle: String,
    val artistName: String,
    val releaseMbid: String?,
    val tracks: List<TrackEntity>,
    val selectedTrackId: String? = null,
    val force: Boolean = false,
    val enabledSourcesHash: String = "default",
    val manualLinks: Map<CreditSourceId, String> = emptyMap(),
)

interface CreditSource {
    val id: CreditSourceId
    suspend fun fetch(request: CreditFetchRequest): SourceResult
}

@Singleton
class TagCreditSource @Inject constructor(private val creditDao: CreditDao) : CreditSource {
    override val id = CreditSourceId.FILE_TAG

    override suspend fun fetch(request: CreditFetchRequest): SourceResult = withContext(Dispatchers.IO) {
        val rows = if (request.selectedTrackId == null) {
            creditDao.findForAlbumWithTracks(request.albumId)
        } else {
            creditDao.findTrackCredits(request.selectedTrackId)
        }.filter { credit ->
            credit.sourceProvider.split(',').any { it.trim().equals("file_tags", ignoreCase = true) || it.trim().equals("file tag", ignoreCase = true) }
        }
        if (rows.isEmpty()) SourceResult.Empty else rows.toSourceResult()
    }
}

@Singleton
class MusicBrainzCreditSource @Inject constructor(private val service: MusicBrainzCreditsService) : CreditSource {
    override val id = CreditSourceId.MUSICBRAINZ

    override suspend fun fetch(request: CreditFetchRequest): SourceResult {
        if (request.releaseMbid.isNullOrBlank() && request.selectedTrackId == null) return SourceResult.NoMatch
        val result = if (request.selectedTrackId == null) {
            service.loadAlbumCredits(request.toAlbum(), request.force, enabledSourcesHash = request.enabledSourcesHash)
        } else {
            service.loadTrackCredits(request.toAlbum(), request.selectedTrackId, request.force, enabledSourcesHash = request.enabledSourcesHash)
        }
        return when (result) {
            is Resource.Success -> result.value.credits.toSourceResult()
            is Resource.Error -> result.message?.let(SourceResult::Error) ?: SourceResult.Error(result.kind.name)
            is Resource.Loading -> SourceResult.Error("MusicBrainz đang tải")
        }
    }

    private fun CreditFetchRequest.toAlbum() = com.youneko.rate.data.local.entity.AlbumEntity(
        id = albumId,
        title = albumTitle,
        artistId = "source-picker",
        mbid = releaseMbid,
        createdAt = 0L,
        updatedAt = 0L,
    )
}

@Singleton
class DiscogsCreditSource @Inject constructor(private val service: DiscogsCreditsService) : CreditSource {
    override val id = CreditSourceId.DISCOGS

    override suspend fun fetch(request: CreditFetchRequest): SourceResult {
        if (request.selectedTrackId != null) {
            val result = service.loadSource(request.albumId, request.albumTitle, request.artistName, request.tracks.associate { it.id to it.title }, request.enabledSourcesHash, request.force, request.manualLinks[CreditSourceId.DISCOGS]?.toLongOrNull())
            return when (result) {
                is SourceResult.Success -> SourceResult.Success(result.trackCredits[request.selectedTrackId].orEmpty(), fetchedAt = result.fetchedAt)
                else -> result
            }
        }
        return service.loadSource(request.albumId, request.albumTitle, request.artistName, request.tracks.associate { it.id to it.title }, request.enabledSourcesHash, request.force, request.manualLinks[CreditSourceId.DISCOGS]?.toLongOrNull())
    }
}

@Singleton
class GeniusCreditSource @Inject constructor(private val service: GeniusCreditsService) : CreditSource {
    override val id = CreditSourceId.GENIUS

    override suspend fun fetch(request: CreditFetchRequest): SourceResult = supervisorScope {
        val selected = request.tracks.filter { request.selectedTrackId == null || it.id == request.selectedTrackId }
        if (selected.isEmpty()) return@supervisorScope SourceResult.Empty
        val results = selected.map { track -> async { service.loadSource(track.id, track.title, request.artistName, request.enabledSourcesHash, request.force, request.manualLinks[CreditSourceId.GENIUS]?.toLongOrNull()) } }.map { deferred -> runCatching { deferred.await() }.getOrElse { SourceResult.Error(it.message ?: "Genius error") } }
        val firstFailure = results.firstOrNull { it !is SourceResult.Success && it !is SourceResult.Empty }
        if (firstFailure != null && results.none { it is SourceResult.Success }) return@supervisorScope firstFailure
        val successful = results.filterIsInstance<SourceResult.Success>()
        if (successful.isEmpty()) SourceResult.Empty else SourceResult.Success(
            credits = if (request.selectedTrackId != null) successful.flatMap { it.credits } else emptyList(),
            trackCredits = successful.flatMapIndexed { index, result ->
                listOfNotNull(selected.getOrNull(index)?.id?.let { it to result.credits })
            }.toMap(),
            fetchedAt = successful.maxOf { it.fetchedAt },
        )
    }
}

@Singleton
class DeezerCreditSource @Inject constructor(private val settings: SettingsStore) : CreditSource {
    override val id = CreditSourceId.DEEZER
    override suspend fun fetch(request: CreditFetchRequest): SourceResult = if (settings.offlineOnly.firstValue()) SourceResult.Offline else SourceResult.Empty
}

@Singleton
class ItunesCreditSource @Inject constructor(private val settings: SettingsStore) : CreditSource {
    override val id = CreditSourceId.ITUNES
    override suspend fun fetch(request: CreditFetchRequest): SourceResult = if (settings.offlineOnly.firstValue()) SourceResult.Offline else SourceResult.Empty
}

private suspend fun kotlinx.coroutines.flow.Flow<Boolean>.firstValue(): Boolean = first()

private fun List<CreditEntity>.toSourceResult(): SourceResult {
    if (isEmpty()) return SourceResult.Empty
    val candidates = map { it.toCandidate() }
    val trackMap = filter { it.trackId != null }.groupBy { it.trackId!! }.mapValues { (_, values) -> values.map { it.toCandidate() } }
    return SourceResult.Success(
        credits = filter { it.trackId == null }.map { it.toCandidate() },
        trackCredits = trackMap,
    )
}

private fun CreditEntity.toCandidate() = CreditCandidate(
    personName = personName,
    personMbid = personMbid,
    role = role,
    instrumentOrAttribute = instrumentOrAttribute,
    sourceProvider = sourceProvider,
    sourceUrl = sourceUrl,
    beginDate = beginDate,
    endDate = endDate,
)
