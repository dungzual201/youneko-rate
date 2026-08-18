package com.youneko.rate.ui.rate

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.youneko.rate.data.AlbumDraft
import com.youneko.rate.data.AlbumRepository
import com.youneko.rate.data.SettingsStore
import com.youneko.rate.data.musicbrainz.AlbumMetadataRefreshService
import com.youneko.rate.data.musicbrainz.Resource
import com.youneko.rate.data.TrackDraft
import com.youneko.rate.data.local.entity.AlbumEntity
import com.youneko.rate.data.local.entity.TrackEntity
import com.youneko.rate.domain.usecase.ScoreMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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
    private val rawAlbums = scoreMode.flatMapLatest { repository.observeAlbums(it) }
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
) : ViewModel() {
    private val albumId: String = checkNotNull(savedStateHandle["albumId"])
    private val eventsChannel = Channel<AlbumDetailEvent>(Channel.BUFFERED)
    val events = eventsChannel.receiveAsFlow()
    private var hasObservedContent = false
    private var exitEventSent = false
    private val scoreMode = settings.scoreMode.map { if (it == "WEIGHTED_BY_DURATION") ScoreMode.WEIGHTED_BY_DURATION else ScoreMode.SIMPLE }
    private val albumData = scoreMode.flatMapLatest { repository.observeAlbum(albumId, it) }
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
    private val _refreshResult = MutableStateFlow<Resource<Unit>?>(null)
    val refreshResult: StateFlow<Resource<Unit>?> = _refreshResult.asStateFlow()
    fun refreshMetadata() {
        val album = currentAlbum() ?: return
        viewModelScope.launch(Dispatchers.IO) { _refreshResult.value = musicBrainzImportService.refreshMetadata(album) }
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
    private val settings: SettingsStore,
) : ViewModel() {
    val dynamicColor: StateFlow<Boolean> = settings.dynamicColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val scoreMode: StateFlow<ScoreMode> = settings.scoreMode
        .map { if (it == "WEIGHTED_BY_DURATION") ScoreMode.WEIGHTED_BY_DURATION else ScoreMode.SIMPLE }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScoreMode.SIMPLE)

    fun setScoreMode(mode: ScoreMode) = viewModelScope.launch(Dispatchers.IO) {
        settings.setScoreMode(mode.name)
    }

    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        settings.setDynamicColor(enabled)
    }
}
