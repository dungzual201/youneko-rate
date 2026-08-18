package com.youneko.rate.data.musicbrainz

import com.youneko.rate.data.AlbumDraft
import com.youneko.rate.data.AlbumRepository
import com.youneko.rate.data.TrackDraft
import com.youneko.rate.data.local.entity.AlbumEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

interface AlbumMetadataRefreshService {
    suspend fun refreshMetadata(album: AlbumEntity): Resource<Unit>
}

@Singleton
class MusicBrainzImportService @Inject constructor(
    private val releaseGroupApi: MusicBrainzReleaseGroupApi,
    private val musicBrainzApi: MusicBrainzApi,
    private val coverArtService: CoverArtService,
    private val repository: AlbumRepository,
) : AlbumMetadataRefreshService {
    suspend fun loadReleaseGroup(groupId: String): Resource<MusicBrainzPreview> = try {
        val group = releaseGroupApi.lookupReleaseGroup(groupId)
        val releases = group.releases.sortedWith(compareBy< MbRelease > { it.date.isNullOrBlank() }.thenBy { it.date.orEmpty() })
        val selected = releases.firstOrNull() ?: return Resource.Error(NetworkError.NO_RESULTS)
        val preview = musicBrainzApi.lookupRelease(selected.id).toPreview(
            releaseGroupId = group.id,
            releaseOptions = releases.map { release ->
                MusicBrainzReleaseOption(release.id, release.title, release.date?.take(4), release.country)
            },
        )
        Resource.Success(preview)
    } catch (error: Throwable) {
        error.toNetworkError()
    }

    suspend fun loadRelease(releaseId: String, releaseGroupId: String? = null): Resource<MusicBrainzPreview> = try {
        Resource.Success(musicBrainzApi.lookupRelease(releaseId).toPreview(releaseGroupId))
    } catch (error: Throwable) {
        error.toNetworkError()
    }

    override suspend fun refreshMetadata(album: AlbumEntity): Resource<Unit> = withContext(Dispatchers.IO) {
        val mbid = album.mbid ?: return@withContext Resource.Error(NetworkError.NO_RESULTS, "Album chưa có MusicBrainz MBID")
        when (val loaded = loadRelease(mbid, album.releaseGroupMbid)) {
            is Resource.Success -> {
                val preview = loaded.value
                repository.mergeMusicBrainzMetadata(
                    album.id,
                    AlbumDraft(
                        title = preview.title,
                        artistName = preview.artist,
                        releaseYear = preview.year?.toIntOrNull(),
                        albumType = "ALBUM",
                        genreTags = emptyList(),
                        listenedDate = null,
                        coverUri = when (val cover = coverArtService.downloadForAlbum(album.id, preview.releaseGroupId, preview.releaseId)) {
                            is CoverResult.Success -> cover.localUri
                            else -> null
                        },
                        tracks = emptyList(),
                        mbid = preview.releaseId,
                        releaseGroupMbid = preview.releaseGroupId,
                        label = preview.label,
                        country = preview.country,
                        sourceProvider = "musicbrainz",
                        metadataFetchedAt = System.currentTimeMillis(),
                    ),
                )
                Resource.Success(Unit)
            }
            is Resource.Error -> loaded
            Resource.Loading -> Resource.Loading
        }
    }

    suspend fun import(
        preview: MusicBrainzPreview,
        choice: ImportConflictChoice,
        onProgress: (MusicBrainzImportProgress) -> Unit = {},
    ): Resource<String> = try {
        withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        onProgress(MusicBrainzImportProgress(MusicBrainzImportStage.RELEASE, 1, 1))
        currentCoroutineContext().ensureActive()
        onProgress(MusicBrainzImportProgress(MusicBrainzImportStage.COVER, 0, 1))
        val coverResult = coverArtService.downloadToFile(
            releaseGroupMbid = preview.releaseGroupId,
            releaseMbid = preview.releaseId,
            fileName = "pending-${preview.releaseId}.jpg",
        )
        val coverUri = (coverResult as? CoverResult.Success)?.localUri
        currentCoroutineContext().ensureActive()
        onProgress(MusicBrainzImportProgress(MusicBrainzImportStage.COVER, 1, 1))
        val totalTracks = preview.tracks.size
        onProgress(MusicBrainzImportProgress(MusicBrainzImportStage.SAVING, 0, totalTracks))
        val draft = AlbumDraft(
            title = preview.title,
            artistName = preview.artist,
            releaseYear = preview.year?.toIntOrNull(),
            albumType = "ALBUM",
            genreTags = emptyList(),
            listenedDate = null,
            coverUri = coverUri,
            tracks = preview.tracks.map { track ->
                TrackDraft(
                    title = track.title,
                    discNumber = track.discNumber,
                    durationMs = track.durationMs,
                    recordingMbid = track.recordingMbid,
                )
            },
            mbid = preview.releaseId,
            releaseGroupMbid = preview.releaseGroupId,
            label = preview.label,
            country = preview.country,
            sourceProvider = "musicbrainz",
            metadataFetchedAt = System.currentTimeMillis(),
        )
        val match = repository.findMusicBrainzMatch(draft)
        when {
            match == null || choice == ImportConflictChoice.CREATE_NEW -> {
                val id = repository.saveAlbumBatched(draft)
                val finalCoverUri = coverUri?.let { coverArtService.promoteToAlbumFile(it, id) }
                if (finalCoverUri != null) repository.observeAlbum(id).first()?.album?.let { album ->
                    repository.updateAlbum(album.copy(coverUri = finalCoverUri, coverThumbUri = finalCoverUri))
                }
                onProgress(MusicBrainzImportProgress(MusicBrainzImportStage.SAVING, totalTracks, totalTracks))
                Resource.Success(id)
            }
            choice == ImportConflictChoice.MERGE -> {
                repository.mergeMusicBrainzMetadata(match, draft)
                val finalCoverUri = coverUri?.let { coverArtService.promoteToAlbumFile(it, match) }
                if (finalCoverUri != null) repository.observeAlbum(match).first()?.album?.let { album ->
                    repository.updateAlbum(album.copy(coverUri = finalCoverUri, coverThumbUri = finalCoverUri))
                }
                onProgress(MusicBrainzImportProgress(MusicBrainzImportStage.SAVING, totalTracks, totalTracks))
                Resource.Success(match)
            }
            else -> Resource.Error(NetworkError.NO_RESULTS, "Import đã hủy")
        }
        }
    } catch (error: Throwable) {
        if (error is CancellationException) throw error
        error.toNetworkError()
    }

}

enum class ImportConflictChoice { MERGE, CREATE_NEW, CANCEL }

enum class MusicBrainzImportStage { RELEASE, COVER, SAVING }

data class MusicBrainzImportProgress(
    val stage: MusicBrainzImportStage,
    val current: Int,
    val total: Int,
)
