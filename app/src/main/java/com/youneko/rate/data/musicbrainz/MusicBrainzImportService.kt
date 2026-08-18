package com.youneko.rate.data.musicbrainz

import android.content.Context
import com.youneko.rate.data.AlbumDraft
import com.youneko.rate.data.AlbumRepository
import com.youneko.rate.data.TrackDraft
import com.youneko.rate.data.local.entity.AlbumEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

interface AlbumMetadataRefreshService {
    suspend fun refreshMetadata(album: AlbumEntity): Resource<Unit>
}

@Singleton
class MusicBrainzImportService @Inject constructor(
    private val releaseGroupApi: MusicBrainzReleaseGroupApi,
    private val musicBrainzApi: MusicBrainzApi,
    private val coverArtApi: CoverArtApi,
    private val repository: AlbumRepository,
    @ApplicationContext private val context: Context,
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
                        coverUri = downloadCover(preview.releaseId),
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
        val coverUri = downloadCover(preview.releaseId)
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
                onProgress(MusicBrainzImportProgress(MusicBrainzImportStage.SAVING, totalTracks, totalTracks))
                Resource.Success(id)
            }
            choice == ImportConflictChoice.MERGE -> {
                repository.mergeMusicBrainzMetadata(match, draft)
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

    private suspend fun downloadCover(releaseId: String): String? {
        val requests = listOf<suspend () -> Response<ResponseBody>>(
            { coverArtApi.front500(releaseId) },
            { coverArtApi.front250(releaseId) },
        )
        var response: Response<ResponseBody>? = null
        for (request in requests) {
            val candidate = runCatching { request() }.getOrNull()
            if (candidate?.isSuccessful == true && candidate.body() != null) {
                response = candidate
                break
            }
        }
        val successfulResponse = response ?: return "android.resource://${context.packageName}/drawable/ic_cat_cover"
        val directory = File(context.filesDir, "covers").apply { mkdirs() }
        val file = File(directory, "musicbrainz-$releaseId.jpg")
        successfulResponse.body()!!.byteStream().use { input -> file.outputStream().use { output -> input.copyTo(output) } }
        return file.toURI().toString()
    }
}

enum class ImportConflictChoice { MERGE, CREATE_NEW, CANCEL }

enum class MusicBrainzImportStage { RELEASE, COVER, SAVING }

data class MusicBrainzImportProgress(
    val stage: MusicBrainzImportStage,
    val current: Int,
    val total: Int,
)
