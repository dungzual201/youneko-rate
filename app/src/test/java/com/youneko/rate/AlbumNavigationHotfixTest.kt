package com.youneko.rate

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import com.youneko.rate.data.AlbumDraft
import com.youneko.rate.data.AlbumRepository
import com.youneko.rate.data.LibraryAlbum
import com.youneko.rate.data.importer.ImportGroup
import com.youneko.rate.data.importer.ImportedTrack
import com.youneko.rate.data.SettingsStore
import com.youneko.rate.data.musicbrainz.AlbumMetadataRefreshService
import com.youneko.rate.data.musicbrainz.Resource
import com.youneko.rate.data.local.entity.AlbumEntity
import com.youneko.rate.data.local.entity.ArtistEntity
import com.youneko.rate.data.local.entity.TrackEntity
import com.youneko.rate.data.local.dao.ReviewRevisionDao
import com.youneko.rate.data.local.dao.AlbumTagDao
import com.youneko.rate.data.local.dao.ListeningLogDao
import com.youneko.rate.data.local.entity.ReviewRevisionEntity
import com.youneko.rate.data.local.entity.AlbumTagEntity
import com.youneko.rate.data.local.entity.ListeningLogEntity
import com.youneko.rate.domain.usecase.ScoreMode
import com.youneko.rate.ui.rate.AlbumDetailEvent
import com.youneko.rate.ui.rate.AlbumDetailUiState
import com.youneko.rate.ui.rate.AlbumDetailViewModel
import com.youneko.rate.ui.rate.AlbumEditorEvent
import com.youneko.rate.ui.rate.AlbumEditorViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AlbumNavigationHotfixTest {
    @Test
    fun add_emits_openAlbum_event_after_repository_returns_id() = runTest {
        val repository = FakeAlbumRepository(saveResult = "created-id")
        val viewModel = AlbumEditorViewModel(
            SavedStateHandle(),
            repository,
        )
        val stateReady = async {
            viewModel.state.first { it.title == "Album" && it.artist == "Artist" && it.tracks == listOf("Track") }
        }
        viewModel.setTitle("Album")
        viewModel.setArtist("Artist")
        viewModel.setTrack(0, "Track")
        stateReady.await()
        val event = async { viewModel.events.first() }

        viewModel.save()

        assertEquals(AlbumEditorEvent.OpenAlbum("created-id"), event.await())
        assertTrue(repository.saveCalled)
    }

    @Test
    fun delete_emits_exit_event_when_observed_album_flow_becomes_null() = runTest {
        val albumId = "album-id"
        val repository = FakeAlbumRepository()
        repository.albumFlow.value = sampleAlbum(albumId)
        val viewModel = AlbumDetailViewModel(
            SavedStateHandle(mapOf("albumId" to albumId)),
            repository,
            FakeSettingsStore(),
            object : AlbumMetadataRefreshService {
                override suspend fun refreshMetadata(album: AlbumEntity): Resource<Unit> = Resource.Success(Unit)
            },
            null,
            FakeReviewRevisionDao(),
            FakeAlbumTagDao(),
            FakeListeningLogDao(),
        )
        viewModel.state.first { it is AlbumDetailUiState.Content }
        val event = async { viewModel.events.first() }

        repository.albumFlow.value = null

        assertEquals(AlbumDetailEvent.ExitAlbum, event.await())
        assertTrue(viewModel.state.first { it is AlbumDetailUiState.AlbumDeleted } is AlbumDetailUiState.AlbumDeleted)
    }

}

private class FakeReviewRevisionDao : ReviewRevisionDao {
    override suspend fun insert(value: ReviewRevisionEntity) = Unit
    override suspend fun findAll(): List<ReviewRevisionEntity> = emptyList()
    override fun observeRecent(albumId: String, trackId: String?): Flow<List<ReviewRevisionEntity>> = emptyFlow()
}

private class FakeAlbumTagDao : AlbumTagDao {
    override suspend fun insert(value: AlbumTagEntity) = Unit
    override suspend fun findAll(): List<AlbumTagEntity> = emptyList()
    override fun observeForAlbum(albumId: String): Flow<List<AlbumTagEntity>> = emptyFlow()
    override suspend fun delete(id: String) = Unit
}

private class FakeListeningLogDao : ListeningLogDao {
    override suspend fun insert(value: ListeningLogEntity) = Unit
    override suspend fun findAll(): List<ListeningLogEntity> = emptyList()
    override fun observeForAlbum(albumId: String): Flow<List<ListeningLogEntity>> = emptyFlow()
    override suspend fun countForAlbum(albumId: String): Int = 0
}

private class FakeSettingsStore : SettingsStore {
    override val offlineOnly = MutableStateFlow(false)
    override val ratingStep = MutableStateFlow(0.5)
    override val ratingScale = MutableStateFlow("FIVE_STARS")
    override val scoreMode = MutableStateFlow("SIMPLE")
    override val gridView = MutableStateFlow(true)
    override val dynamicColor = MutableStateFlow(false)
    override val reducedMotion = MutableStateFlow(false)
    override val sortOrder = MutableStateFlow("NEWEST")
    override val unfinishedOnly = MutableStateFlow(false)
    override val discogsEnabled = MutableStateFlow(false)
    override val discogsToken = MutableStateFlow("")
    override val lastFmEnabled = MutableStateFlow(false)
    override val lastFmApiKey = MutableStateFlow("")
    override val geniusEnabled = MutableStateFlow(false)
    override val geniusToken = MutableStateFlow("")
    override val showCreditSources = MutableStateFlow(false)
    override val creditSourceOrder = MutableStateFlow("FILE_TAG,MUSICBRAINZ,DISCOGS,GENIUS,DEEZER,ITUNES")
    override val activeCreditSources = MutableStateFlow("FILE_TAG,MUSICBRAINZ")
    override val creditsMergeMode = MutableStateFlow(false)
    override suspend fun setOfflineOnly(value: Boolean) { offlineOnly.value = value }
    override suspend fun setRatingStep(value: Double) { ratingStep.value = value }
    override suspend fun setRatingScale(value: String) { ratingScale.value = value }
    override suspend fun setScoreMode(value: String) { scoreMode.value = value }
    override suspend fun setGridView(value: Boolean) { gridView.value = value }
    override suspend fun setDynamicColor(value: Boolean) { dynamicColor.value = value }
    override suspend fun setReducedMotion(value: Boolean) { reducedMotion.value = value }
    override suspend fun setSortOrder(value: String) { sortOrder.value = value }
    override suspend fun setUnfinishedOnly(value: Boolean) { unfinishedOnly.value = value }
    override suspend fun setDiscogsEnabled(value: Boolean) { discogsEnabled.value = value }
    override suspend fun setDiscogsToken(value: String) { discogsToken.value = value }
    override suspend fun setLastFmEnabled(value: Boolean) { lastFmEnabled.value = value }
    override suspend fun setLastFmApiKey(value: String) { lastFmApiKey.value = value }
    override suspend fun setGeniusEnabled(value: Boolean) { geniusEnabled.value = value }
    override suspend fun setGeniusToken(value: String) { geniusToken.value = value }
    override suspend fun setShowCreditSources(value: Boolean) { showCreditSources.value = value }
    override suspend fun setCreditSourceOrder(value: String) { creditSourceOrder.value = value }
    override suspend fun setActiveCreditSources(value: String) { activeCreditSources.value = value }
    override suspend fun setCreditsMergeMode(value: Boolean) { creditsMergeMode.value = value }
}

private class FakeAlbumRepository(private val saveResult: String = "id") : AlbumRepository {
    val albumFlow = MutableStateFlow<LibraryAlbum?>(null)
    var saveCalled = false
    override fun observeAlbums(scoreMode: ScoreMode): Flow<List<LibraryAlbum>> = emptyFlow()
    override fun observeAlbum(id: String, scoreMode: ScoreMode): Flow<LibraryAlbum?> = albumFlow
    override suspend fun searchEntityIds(query: String): Set<String> = emptySet()
    override suspend fun saveAlbum(draft: AlbumDraft): String { saveCalled = true; return saveResult }
    override suspend fun saveAlbumBatched(draft: AlbumDraft, batchSize: Int): String { saveCalled = true; return saveResult }
    override suspend fun saveStandalone(title: String, artistName: String, listenedDate: String?, sourceUri: String?, fileName: String?, embeddedCredits: List<com.youneko.rate.data.musicbrainz.CreditCandidate>): String = "track-id"
    override suspend fun updateTrack(track: TrackEntity) = Unit
    override suspend fun updateAlbum(album: AlbumEntity) = Unit
    override suspend fun deleteAlbum(id: String) { albumFlow.value = null }
    override suspend fun findMatchingAlbum(group: ImportGroup): String? = null
    override suspend fun appendImportedTracks(albumId: String, tracks: List<ImportedTrack>): Int = tracks.size
    override suspend fun findMusicBrainzMatch(draft: AlbumDraft): String? = null
    override suspend fun mergeMusicBrainzMetadata(albumId: String, draft: AlbumDraft) = Unit
}

private fun sampleAlbum(id: String): LibraryAlbum {
    val now = 1L
    val artist = ArtistEntity(id = "artist-id", name = "Artist", createdAt = now, updatedAt = now)
    val album = AlbumEntity(id = id, title = "Album", artistId = artist.id, createdAt = now, updatedAt = now)
    return LibraryAlbum(album, artist, emptyList(), null)
}
