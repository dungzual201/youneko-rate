package com.youneko.rate.ui.rate

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.youneko.rate.data.AlbumDraft
import com.youneko.rate.data.AlbumRepository
import com.youneko.rate.data.artwork.ArtworkStore
import com.youneko.rate.data.artwork.CoverPalette
import com.youneko.rate.data.artwork.CoverPaletteStore
import com.youneko.rate.data.SettingsStore
import com.youneko.rate.data.MediaScanStore
import com.youneko.rate.data.scan.UNIQUE_ON_RESUME
import com.youneko.rate.data.scan.startScan
import com.youneko.rate.data.musicbrainz.AlbumMetadataRefreshService
import com.youneko.rate.data.credits.CreditSourceId
import com.youneko.rate.data.discogs.DiscogsCreditsService
import com.youneko.rate.data.genius.GeniusCreditsService
import com.youneko.rate.data.musicbrainz.CoverArtService
import com.youneko.rate.data.musicbrainz.Resource
import com.youneko.rate.data.TrackDraft
import com.youneko.rate.data.local.dao.RemoteMetadataCacheDao
import com.youneko.rate.data.local.dao.AlbumDao
import com.youneko.rate.data.local.dao.ReviewRevisionDao
import com.youneko.rate.data.local.dao.AlbumTagDao
import com.youneko.rate.data.local.dao.ListeningLogDao
import com.youneko.rate.data.local.entity.ReviewRevisionEntity
import com.youneko.rate.data.local.entity.AlbumTagEntity
import com.youneko.rate.data.local.entity.ListeningLogEntity
import com.youneko.rate.data.local.entity.AlbumEntity
import com.youneko.rate.data.local.entity.TrackEntity
import com.youneko.rate.domain.usecase.ScoreMode
import com.youneko.rate.domain.usecase.RatingScale
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.launch

enum class LibrarySort { NEWEST, SCORE_HIGH, SCORE_LOW, TITLE, YEAR, LISTENED_DATE }

sealed interface AlbumDetailUiState {
    data object Loading : AlbumDetailUiState
    data object AlbumDeleted : AlbumDetailUiState
    data class Content(val album: com.youneko.rate.data.LibraryAlbum) : AlbumDetailUiState
}

sealed interface AlbumDetailEvent {
    data object ExitAlbum : AlbumDetailEvent
}

sealed interface AlbumEditorEvent {
    data class OpenAlbum(val albumId: String) : AlbumEditorEvent
}

data class LibraryUiState(
    val albums: List<com.youneko.rate.data.LibraryAlbum> = emptyList(),
    val query: String = "",
    val gridView: Boolean = true,
    val sort: LibrarySort = LibrarySort.NEWEST,
    val unfinishedOnly: Boolean = false,
    val error: String? = null,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: AlbumRepository,
    private val settings: SettingsStore,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val error = MutableStateFlow<String?>(null)
    private val scoreMode = settings.scoreMode.map { if (it == "WEIGHTED_BY_DURATION") ScoreMode.WEIGHTED_BY_DURATION else ScoreMode.SIMPLE }
    private val rawAlbums = scoreMode.flatMapLatest { repository.observeAlbums(it) }.distinctUntilChanged()
    private val searchIds = query
        .debounce(400)
        .distinctUntilChanged()
        .flatMapLatest { value -> flow { emit(repository.searchEntityIds(value)) }.catch { emit(emptySet()) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())
    private val prefs = combine(
        settings.gridView,
        settings.sortOrder,
        settings.unfinishedOnly,
    ) { grid, sort, unfinished ->
        QuadPrefs(grid, runCatching { LibrarySort.valueOf(sort) }.getOrDefault(LibrarySort.NEWEST), unfinished)
    }

    val uiState: StateFlow<LibraryUiState> = combine(rawAlbums, query, searchIds, prefs) { albums, text, ids, pref ->
        val filtered = albums.asSequence()
            .filter { text.isBlank() || it.album.id in ids || it.tracks.any { track -> track.id in ids } }
            .filter { !pref.unfinished || (it.tracks.isNotEmpty() && it.score?.ratedCount != it.tracks.size) }
            .sortedWith(sortComparator(pref.sort))
            .toList()
        LibraryUiState(filtered, text, pref.grid, pref.sort, pref.unfinished, error.value)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    private data class QuadPrefs(val grid: Boolean, val sort: LibrarySort, val unfinished: Boolean)

    private fun sortComparator(sort: LibrarySort): Comparator<com.youneko.rate.data.LibraryAlbum> = when (sort) {
        LibrarySort.NEWEST -> compareByDescending { it.album.createdAt }
        LibrarySort.SCORE_HIGH -> compareByDescending<com.youneko.rate.data.LibraryAlbum> { it.score?.effectiveScore ?: -1.0 }
        LibrarySort.SCORE_LOW -> compareBy { it.score?.effectiveScore ?: 99.0 }
        LibrarySort.TITLE -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.album.title }
        LibrarySort.YEAR -> compareByDescending<com.youneko.rate.data.LibraryAlbum> { it.album.releaseYear ?: 0 }
        LibrarySort.LISTENED_DATE -> compareByDescending { it.album.listenedDate.orEmpty() }
    }

    fun setQuery(value: String) { query.value = value }
    fun setGridView(value: Boolean) = viewModelScope.launch(Dispatchers.IO) { settings.setGridView(value) }
    fun setSort(value: LibrarySort) = viewModelScope.launch(Dispatchers.IO) { settings.setSortOrder(value.name) }
    fun setUnfinishedOnly(value: Boolean) = viewModelScope.launch(Dispatchers.IO) { settings.setUnfinishedOnly(value) }
    fun clearError() { error.value = null }
}

@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: AlbumRepository,
    private val settings: SettingsStore,
    private val musicBrainzImportService: AlbumMetadataRefreshService,
    private val coverArtService: CoverArtService? = null,
    private val coverPaletteStore: CoverPaletteStore,
    private val reviewRevisionDao: ReviewRevisionDao,
    private val albumTagDao: AlbumTagDao,
    private val listeningLogDao: ListeningLogDao,
) : ViewModel() {
    private val albumId: String = checkNotNull(savedStateHandle["albumId"])
    private val eventsChannel = Channel<AlbumDetailEvent>(Channel.BUFFERED)
    val events = eventsChannel.receiveAsFlow()
    private var hasObservedContent = false
    private var exitEventSent = false
    private val scoreMode = settings.scoreMode.map { if (it == "WEIGHTED_BY_DURATION") ScoreMode.WEIGHTED_BY_DURATION else ScoreMode.SIMPLE }
    val ratingScale: StateFlow<RatingScale> = settings.ratingScale.map(RatingScale::parse).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RatingScale.FIVE_STARS)
    private val albumData = scoreMode.flatMapLatest { repository.observeAlbum(albumId, it) }
    val tags = albumTagDao.observeForAlbum(albumId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val listeningLogs = listeningLogDao.observeForAlbum(albumId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val reviewRevisions = reviewRevisionDao.observeRecent(albumId, null).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val palette: StateFlow<CoverPalette?> = albumData
        .flatMapLatest { value ->
            flow { emit(value?.let { coverPaletteStore.getOrCreate(it.album) }) }
        }
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val releaseUrl: StateFlow<String?> = albumData
        .map { value -> value?.album?.mbid?.let { "https://musicbrainz.org/release/$it" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val state: StateFlow<AlbumDetailUiState> = albumData.map { value ->
        if (value != null) {
            hasObservedContent = true
            exitEventSent = false
            AlbumDetailUiState.Content(value)
        } else if (hasObservedContent) {
            if (!exitEventSent) {
                exitEventSent = true
                eventsChannel.trySend(AlbumDetailEvent.ExitAlbum)
            }
            AlbumDetailUiState.AlbumDeleted
        } else {
            AlbumDetailUiState.Loading
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlbumDetailUiState.Loading)

    fun currentAlbum(): AlbumEntity? = (state.value as? AlbumDetailUiState.Content)?.album?.album
    fun updateTrack(track: TrackEntity) = viewModelScope.launch(Dispatchers.IO) { repository.updateTrack(track) }
    fun updateAlbum(album: AlbumEntity) = viewModelScope.launch(Dispatchers.IO) { repository.updateAlbum(album) }
    fun saveReviewRevision(body: String) = viewModelScope.launch(Dispatchers.IO) {
        if (body.isBlank()) return@launch
        reviewRevisionDao.insert(ReviewRevisionEntity(UUID.randomUUID().toString(), albumId, null, body, System.currentTimeMillis()))
    }
    fun addTag(raw: String) = viewModelScope.launch(Dispatchers.IO) {
        val tag = raw.trim().take(40)
        if (tag.isBlank() || tags.value.size >= 10 || tags.value.any { it.name.equals(tag, ignoreCase = true) }) return@launch
        albumTagDao.insert(AlbumTagEntity(UUID.randomUUID().toString(), albumId, tag, System.currentTimeMillis()))
    }
    fun removeTag(id: String) = viewModelScope.launch(Dispatchers.IO) { albumTagDao.delete(id) }
    fun logListening(note: String? = null, trackId: String? = null) = viewModelScope.launch(Dispatchers.IO) {
        listeningLogDao.insert(ListeningLogEntity(UUID.randomUUID().toString(), albumId, trackId, LocalDate.now().toString(), note?.trim()?.takeIf { it.isNotBlank() }))
    }
    private val _refreshResult = MutableStateFlow<Resource<Unit>?>(null)
    val refreshResult: StateFlow<Resource<Unit>?> = _refreshResult.asStateFlow()
    fun refreshMetadata() {
        val album = currentAlbum() ?: return
        viewModelScope.launch(Dispatchers.IO) { _refreshResult.value = musicBrainzImportService.refreshMetadata(album) }
    }

    fun reloadCover() {
        val album = currentAlbum() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            when (val result = coverArtService?.downloadForAlbum(album.id, album.releaseGroupMbid, album.mbid)) {
                is com.youneko.rate.data.musicbrainz.CoverResult.Success -> repository.updateAlbum(album.copy(coverUri = result.localUri, coverThumbUri = result.localUri, coverSource = result.sourceProvider, coverUpdatedAt = System.currentTimeMillis()))
                else -> Unit
            }
        }
    }

    fun setManualCover(uri: String) {
        currentAlbum()?.let { album ->
            updateAlbum(album.copy(coverUri = uri, coverThumbUri = uri, coverSource = "manual", coverUpdatedAt = System.currentTimeMillis()))
        }
    }
    fun deleteAlbum() = viewModelScope.launch(Dispatchers.IO) { repository.deleteAlbum(albumId) }
}

data class EditorState(
    val title: String = "",
    val artist: String = "",
    val year: String = "",
    val albumType: String = "ALBUM",
    val genres: String = "",
    val listenedDate: String = "",
    val coverUri: String? = null,
    val tracks: List<String> = listOf(""),
    val error: String? = null,
)

@HiltViewModel
class AlbumEditorViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: AlbumRepository,
) : ViewModel() {
    private val eventsChannel = Channel<AlbumEditorEvent>(Channel.BUFFERED)
    val events = eventsChannel.receiveAsFlow()
    private val title = savedStateHandle.getStateFlow("title", "")
    private val artist = savedStateHandle.getStateFlow("artist", "")
    private val year = savedStateHandle.getStateFlow("year", "")
    private val albumType = savedStateHandle.getStateFlow("albumType", "ALBUM")
    private val genres = savedStateHandle.getStateFlow("genres", "")
    private val listenedDate = savedStateHandle.getStateFlow("listenedDate", "")
    private val coverUri = savedStateHandle.getStateFlow<String?>("coverUri", null)
    private val trackTitles = savedStateHandle.getStateFlow("trackTitles", "")
    private val error = savedStateHandle.getStateFlow<String?>("error", null)

    private val firstFields = combine(title, artist, year, albumType, genres) { t, a, y, type, g ->
        arrayOf(t, a, y, type, g)
    }
    val state: StateFlow<EditorState> = combine(firstFields, listenedDate, coverUri, trackTitles, error) { values, date, cover, tracks, message ->
        EditorState(
            title = values[0], artist = values[1], year = values[2], albumType = values[3], genres = values[4],
            listenedDate = date, coverUri = cover, tracks = tracks.split("\u001f").ifEmpty { listOf("") }, error = message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EditorState())

    fun setTitle(value: String) { savedStateHandle["title"] = value }
    fun setArtist(value: String) { savedStateHandle["artist"] = value }
    fun setYear(value: String) { savedStateHandle["year"] = value.filter(Char::isDigit).take(4) }
    fun setType(value: String) { savedStateHandle["albumType"] = value }
    fun setGenres(value: String) { savedStateHandle["genres"] = value }
    fun setListenedDate(value: String) { savedStateHandle["listenedDate"] = value }
    fun setCoverUri(value: String?) { savedStateHandle["coverUri"] = value }
    fun setTrack(index: Int, value: String) {
        val values = currentTracks().toMutableList()
        while (values.size <= index) values += ""
        values[index] = value
        savedStateHandle["trackTitles"] = values.joinToString("\u001f")
    }
    fun addTrack() { savedStateHandle["trackTitles"] = (currentTracks() + "").joinToString("\u001f") }
    fun removeTrack(index: Int) { savedStateHandle["trackTitles"] = currentTracks().filterIndexed { i, _ -> i != index }.ifEmpty { listOf("") }.joinToString("\u001f") }
    fun moveTrack(from: Int, to: Int) {
        val values = currentTracks().toMutableList()
        if (from !in values.indices || to !in values.indices || from == to) return
        val moved = values.removeAt(from)
        values.add(to, moved)
        savedStateHandle["trackTitles"] = values.joinToString("\u001f")
    }
    fun addQuick(count: Int) { savedStateHandle["trackTitles"] = (currentTracks() + List(count.coerceIn(1, 50)) { "" }).joinToString("\u001f") }

    fun save() = viewModelScope.launch(Dispatchers.IO) {
        val current = state.value
        val yearValue = current.year.toIntOrNull()
        if (current.title.isBlank() || current.artist.isBlank()) {
            savedStateHandle["error"] = "Tên album và nghệ sĩ không được để trống"
            return@launch
        }
        if (yearValue != null && yearValue !in 1800..2100) {
            savedStateHandle["error"] = "Năm phát hành phải trong khoảng 1800–2100"
            return@launch
        }
        val validTracks = current.tracks
        if (validTracks.isEmpty() || validTracks.any { it.isBlank() }) {
            savedStateHandle["error"] = "Tên bài không được để trống"
            return@launch
        }
        runCatching {
            repository.saveAlbum(
                AlbumDraft(
                    title = current.title,
                    artistName = current.artist,
                    releaseYear = yearValue,
                    albumType = current.albumType,
                    genreTags = current.genres.split(',').map(String::trim),
                    listenedDate = current.listenedDate.ifBlank { null },
                    coverUri = current.coverUri,
                    coverSource = current.coverUri?.let { "Manual" },
                    coverUpdatedAt = current.coverUri?.let { System.currentTimeMillis() },
                    tracks = validTracks.map { track -> TrackDraft(title = track, discNumber = 1) },
                ),
            )
        }.onSuccess { albumId -> eventsChannel.trySend(AlbumEditorEvent.OpenAlbum(albumId)) }
            .onFailure { savedStateHandle["error"] = it.message ?: "Không thể lưu album" }
    }

    private fun currentTracks(): List<String> = savedStateHandle.get<String>("trackTitles")?.split("\u001f")?.ifEmpty { listOf("") } ?: listOf("")
}

@HiltViewModel
class ScoreSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsStore,
    private val mediaScanStore: MediaScanStore,
    private val artworkStore: ArtworkStore,
    private val albumDao: AlbumDao,
    private val remoteMetadataCacheDao: RemoteMetadataCacheDao,
    private val discogsService: DiscogsCreditsService,
    private val geniusService: GeniusCreditsService,
) : ViewModel() {
    val offlineOnly = settings.offlineOnly.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val dynamicColor: StateFlow<Boolean> = settings.dynamicColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val reducedMotion: StateFlow<Boolean> = settings.reducedMotion
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val ratingScale: StateFlow<RatingScale> = settings.ratingScale.map(RatingScale::parse).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RatingScale.FIVE_STARS)
    val scoreMode: StateFlow<ScoreMode> = settings.scoreMode
        .map { if (it == "WEIGHTED_BY_DURATION") ScoreMode.WEIGHTED_BY_DURATION else ScoreMode.SIMPLE }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScoreMode.SIMPLE)
    val discogsEnabled = settings.discogsEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val discogsToken = settings.discogsToken.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
    val lastFmEnabled = settings.lastFmEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val lastFmApiKey = settings.lastFmApiKey.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
    val geniusEnabled = settings.geniusEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val geniusToken = settings.geniusToken.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
    val showCreditSources = settings.showCreditSources.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val creditSourceOrder = settings.creditSourceOrder.map(CreditSourceId::parse).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CreditSourceId.defaultOrder)
    val activeCreditSources = settings.activeCreditSources.map { CreditSourceId.parse(it).toSet() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), setOf(CreditSourceId.FILE_TAG, CreditSourceId.MUSICBRAINZ))
    val creditsMergeMode = settings.creditsMergeMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    private val _tokenTestResult = MutableStateFlow<Map<String, Int>>(emptyMap())
    val tokenTestResult: StateFlow<Map<String, Int>> = _tokenTestResult.asStateFlow()
    private val scanWorkManager = WorkManager.getInstance(context)
    val scanWorkInfos = scanWorkManager.getWorkInfosForUniqueWorkFlow(UNIQUE_ON_RESUME)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setRatingScale(scale: RatingScale) = viewModelScope.launch(Dispatchers.IO) { settings.setRatingScale(scale.name) }
    fun setScoreMode(mode: ScoreMode) = viewModelScope.launch(Dispatchers.IO) {
        settings.setScoreMode(mode.name)
    }

    fun setOfflineOnly(enabled: Boolean) = viewModelScope.launch(Dispatchers.IO) { settings.setOfflineOnly(enabled) }

    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        settings.setDynamicColor(enabled)
    }

    fun setReducedMotion(enabled: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        settings.setReducedMotion(enabled)
    }

    fun setDiscogsEnabled(enabled: Boolean) = viewModelScope.launch(Dispatchers.IO) { settings.setDiscogsEnabled(enabled) }
    fun setDiscogsToken(token: String) = viewModelScope.launch(Dispatchers.IO) { settings.setDiscogsToken(token) }
    fun setLastFmEnabled(enabled: Boolean) = viewModelScope.launch(Dispatchers.IO) { settings.setLastFmEnabled(enabled) }
    fun setLastFmApiKey(key: String) = viewModelScope.launch(Dispatchers.IO) { settings.setLastFmApiKey(key) }
    fun setGeniusEnabled(enabled: Boolean) = viewModelScope.launch(Dispatchers.IO) { settings.setGeniusEnabled(enabled) }
    fun setGeniusToken(token: String) = viewModelScope.launch(Dispatchers.IO) { settings.setGeniusToken(token) }
    fun setShowCreditSources(enabled: Boolean) = viewModelScope.launch(Dispatchers.IO) { settings.setShowCreditSources(enabled) }
    fun setCreditSourceEnabled(id: CreditSourceId, enabled: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        val next = activeCreditSources.value.toMutableSet().apply { if (enabled) add(id) else remove(id) }
        if (id == CreditSourceId.FILE_TAG) next.add(id)
        settings.setActiveCreditSources(CreditSourceId.encode(next))
        if (id == CreditSourceId.DISCOGS) settings.setDiscogsEnabled(enabled)
        if (id == CreditSourceId.GENIUS) settings.setGeniusEnabled(enabled)
    }
    fun moveCreditSource(id: CreditSourceId, direction: Int) = viewModelScope.launch(Dispatchers.IO) {
        val values = creditSourceOrder.value.toMutableList()
        val index = values.indexOf(id); val target = (index + direction).coerceIn(0, values.lastIndex)
        if (index >= 0 && index != target) { val item = values.removeAt(index); values.add(target, item); settings.setCreditSourceOrder(CreditSourceId.encode(values)) }
    }
    fun setCreditsMergeMode(enabled: Boolean) = viewModelScope.launch(Dispatchers.IO) { settings.setCreditsMergeMode(enabled) }
    fun testDiscogsToken(token: String) = viewModelScope.launch(Dispatchers.IO) { _tokenTestResult.value = _tokenTestResult.value + ("discogs" to discogsService.testToken(token)) }
    fun testGeniusToken(token: String) = viewModelScope.launch(Dispatchers.IO) { _tokenTestResult.value = _tokenTestResult.value + ("genius" to geniusService.testToken(token)) }
    fun clearMetadataCache() = viewModelScope.launch(Dispatchers.IO) { remoteMetadataCacheDao.deleteAll() }
    fun reextractArtwork() = viewModelScope.launch(Dispatchers.IO) {
        startScan(context, trigger = "settings-reextract-artwork", artworkOnly = true)
    }

    fun rescanAllMusic() = viewModelScope.launch(Dispatchers.IO) {
        mediaScanStore.reset()
        startScan(context, forceFull = true, trigger = "settings-rescan-all")
    }

    fun refreshMusicData() = viewModelScope.launch(Dispatchers.IO) {
        mediaScanStore.reset()
        startScan(context, forceFull = true, trigger = "settings-refresh")
    }
    fun reloadAllCovers() = viewModelScope.launch(Dispatchers.IO) {
        artworkStore.clearCachedCovers()
        albumDao.clearAutomaticCovers()
        startScan(context, forceFull = true, trigger = "settings-reload-covers")
    }
}
