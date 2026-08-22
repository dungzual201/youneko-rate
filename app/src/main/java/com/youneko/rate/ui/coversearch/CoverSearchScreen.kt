package com.youneko.rate.ui.coversearch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.youneko.rate.R
import com.youneko.rate.data.AlbumRepository
import com.youneko.rate.data.SettingsDataStore
import com.youneko.rate.data.artwork.AppliedCover
import com.youneko.rate.data.artwork.CoverApplyService
import com.youneko.rate.data.artwork.CoverDownloadResult
import com.youneko.rate.data.artwork.CoverDownloadService
import com.youneko.rate.data.coversearch.CoverSearchEvent
import com.youneko.rate.data.coversearch.MusicHoardersApi
import com.youneko.rate.data.coversearch.MusicHoardersCoverLine
import com.youneko.rate.data.coversearch.MusicHoardersInfo
import com.youneko.rate.data.coversearch.MusicHoardersSourceInfo
import com.youneko.rate.ui.YounekoEmptyState
import com.youneko.rate.ui.YounekoErrorState
import com.youneko.rate.ui.rate.CoverArtFullscreenDialog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import javax.inject.Inject

data class CoverSearchUiState(
    val artist: String = "",
    val album: String = "",
    val sources: List<String> = emptyList(),
    val country: String = "us",
    val sourceInfo: MusicHoardersInfo? = null,
    val infoLoading: Boolean = false,
    val infoError: String? = null,
    val results: List<MusicHoardersCoverLine> = emptyList(),
    val sourceStatus: Map<String, String> = emptyMap(),
    val searching: Boolean = false,
    val searched: Boolean = false,
    val error: String? = null,
    val errorFields: Map<String, String> = emptyMap(),
    val downloadProgress: Int? = null,
    val applying: Boolean = false,
    val applyNotice: String? = null,
)

@HiltViewModel
class CoverSearchViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: AlbumRepository,
    private val api: MusicHoardersApi,
    private val downloader: CoverDownloadService,
    private val applier: CoverApplyService,
    @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context,
) : ViewModel() {
    private val settings = SettingsDataStore(context)
    private val albumId: String = checkNotNull(savedStateHandle["albumId"])
    private val _state = MutableStateFlow(CoverSearchUiState())
    val state: StateFlow<CoverSearchUiState> = _state.asStateFlow()
    private var originalArtist = ""
    private var originalAlbum = ""
    private var lastApplied: AppliedCover? = null

    init {
        viewModelScope.launch {
            val album = repository.observeAlbum(albumId).first()
            originalArtist = album?.artist?.name.orEmpty()
            originalAlbum = album?.album?.title.orEmpty()
            val savedSources = settings.coverSearchSources.first().split(',').filter { it.isNotBlank() }
            _state.value = _state.value.copy(
                artist = originalArtist,
                album = originalAlbum,
                sources = savedSources,
                country = settings.coverSearchCountry.first().ifBlank { "us" },
            )
        }
        loadInfo()
    }

    fun loadInfo() {
        viewModelScope.launch {
            _state.value = _state.value.copy(infoLoading = true, infoError = null)
            api.info().onSuccess { info ->
                val available = info.sources.map { it.id }.toSet()
                val selected = _state.value.sources.filter { it in available }
                _state.value = _state.value.copy(
                    sourceInfo = info,
                    sources = selected.ifEmpty { info.sources.map { it.id }.take(4) },
                    infoLoading = false,
                )
            }.onFailure { error ->
                _state.value = _state.value.copy(infoLoading = false, infoError = error.message)
            }
        }
    }

    fun setArtist(value: String) { _state.value = _state.value.copy(artist = value) }
    fun setAlbum(value: String) { _state.value = _state.value.copy(album = value) }
    fun setCountry(value: String) {
        val catalog = _state.value.sourceInfo?.sources.orEmpty()
        val selected = _state.value.sources.filter { sourceId -> catalog.firstOrNull { it.id == sourceId }?.let { isMusicHoardersSourceActive(it, value) } ?: true }
        _state.value = _state.value.copy(country = value, sources = selected)
    }
    fun isSourceActive(sourceId: String): Boolean = _state.value.sourceInfo?.sources?.firstOrNull { it.id == sourceId }?.let { isMusicHoardersSourceActive(it, _state.value.country) } ?: false

    fun toggleSource(source: String) {
        if (!isSourceActive(source)) return
        val selected = _state.value.sources.toMutableList()
        if (source in selected && selected.size == 1) return
        if (source in selected) selected.remove(source) else selected.add(source)
        _state.value = _state.value.copy(sources = selected)
    }
    fun reset() { _state.value = _state.value.copy(artist = originalArtist, album = originalAlbum, error = null) }

    fun applyCover(result: MusicHoardersCoverLine) {
        val url = result.bigCoverUrl ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(applying = true, downloadProgress = 0, error = null, applyNotice = null)
            when (val downloaded = downloader.download(albumId, url, result.source.orEmpty()) { progress ->
                _state.value = _state.value.copy(downloadProgress = progress)
            }) {
                is CoverDownloadResult.Failure -> _state.value = _state.value.copy(applying = false, downloadProgress = null, error = downloaded.reason.name)
                is CoverDownloadResult.Success -> {
                    applier.apply(albumId, downloaded.cover).onSuccess { applied ->
                        lastApplied = applied
                        _state.value = _state.value.copy(applying = false, downloadProgress = null, applyNotice = "applied")
                    }.onFailure { error ->
                        _state.value = _state.value.copy(applying = false, downloadProgress = null, error = error.message)
                    }
                }
            }
        }
    }

    fun undo() {
        val applied = lastApplied ?: return
        viewModelScope.launch {
            if (applier.undo(applied)) {
                lastApplied = null
                _state.value = _state.value.copy(applyNotice = "undone")
            }
        }
    }

    fun clearApplyNotice() { _state.value = _state.value.copy(applyNotice = null) }

    fun search() {
        val current = _state.value
        if (current.artist.isBlank() || current.album.isBlank() || current.sources.isEmpty()) return
        viewModelScope.launch {
            settings.setCoverSearchSources(current.sources.joinToString(","))
            settings.setCoverSearchCountry(current.country)
            _state.value = current.copy(results = emptyList(), sourceStatus = current.sources.associateWith { "searching" }, searching = true, searched = true, error = null, errorFields = emptyMap())
            runCatching {
                api.search(current.artist, current.album, current.country, current.sources).collect { event ->
                    when (event) {
                        is CoverSearchEvent.Cover -> _state.value = _state.value.copy(results = sortResults(_state.value.results + event.cover))
                        is CoverSearchEvent.SourceStatus -> _state.value = _state.value.copy(sourceStatus = _state.value.sourceStatus + (event.source to event.status))
                        is CoverSearchEvent.Count -> event.source?.let { source -> _state.value = _state.value.copy(sourceStatus = _state.value.sourceStatus + (source to "${event.releaseCount ?: 0}/${event.releaseTotal ?: 0}")) }
                        CoverSearchEvent.Done -> _state.value = _state.value.copy(searching = false)
                        is CoverSearchEvent.Error -> _state.value = _state.value.copy(searching = false, error = event.message ?: event.code?.toString(), errorFields = event.fieldErrors)
                    }
                }
            }.onFailure { error -> _state.value = _state.value.copy(searching = false, error = error.message, errorFields = emptyMap()) }
            _state.value = _state.value.copy(searching = false)
        }
    }

    private fun sortResults(values: List<MusicHoardersCoverLine>): List<MusicHoardersCoverLine> = values.distinctBy { it.bigCoverUrl }.sortedWith(
        compareByDescending<MusicHoardersCoverLine> { it.info?.width == it.info?.height }
            .thenByDescending { (it.info?.width ?: 0) * (it.info?.height ?: 0) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoverSearchScreen(
    onBack: () -> Unit,
    onUseCover: (MusicHoardersCoverLine) -> Unit = {},
    viewModel: CoverSearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var countryExpanded by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<MusicHoardersCoverLine?>(null) }
    val snackbarHost = remember { SnackbarHostState() }
    val appliedText = stringResource(R.string.cover_applied)
    val undoText = stringResource(R.string.cover_undo)
    LaunchedEffect(state.applyNotice) {
        when (state.applyNotice) {
            "applied" -> {
                val result = snackbarHost.showSnackbar(appliedText, actionLabel = undoText)
                if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) viewModel.undo()
                viewModel.clearApplyNotice()
            }
            "undone" -> viewModel.clearApplyNotice()
        }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cover_search_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.a11y_back)) } },
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(120.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(state.artist, viewModel::setArtist, label = { Text(stringResource(R.string.cover_search_artist)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(state.album, viewModel::setAlbum, label = { Text(stringResource(R.string.cover_search_album)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Text(stringResource(R.string.cover_search_sources), style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        state.sourceInfo?.sources.orEmpty().forEach { source -> FilterChip(enabled = viewModel.isSourceActive(source.id), selected = source.id in state.sources, onClick = { viewModel.toggleSource(source.id) }, label = { Text(sourceLabel(source.id, state.sourceInfo?.sources.orEmpty())) }) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        state.sources.forEach { source ->
                            val count = state.results.count { it.source == source }
                            val status = state.sourceStatus[source]
                            FilterChip(
                                selected = false,
                                onClick = {},
                                enabled = false,
                                label = { Text(statusLabel(status, count)) },
                            )
                        }
                    }
                    if (state.infoLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    state.infoError?.let { message -> YounekoErrorState(message, onRetry = viewModel::loadInfo, modifier = Modifier.fillMaxWidth()) }
                    Box {
                        OutlinedTextField(state.country, {}, readOnly = true, label = { Text(stringResource(R.string.cover_search_country)) }, modifier = Modifier.fillMaxWidth())
                        Box(Modifier.matchParentSize().clickable { countryExpanded = true })
                        DropdownMenu(expanded = countryExpanded, onDismissRequest = { countryExpanded = false }) {
                            (state.sourceInfo?.countries?.ifEmpty { listOf("us") } ?: listOf("us")).forEach { value -> DropdownMenuItem(text = { Text(value.uppercase()) }, onClick = { viewModel.setCountry(value); countryExpanded = false }) }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = viewModel::search, enabled = !state.searching && state.artist.isNotBlank() && state.album.isNotBlank()) { Text(stringResource(R.string.search)) }
                        Button(onClick = viewModel::reset, enabled = !state.searching) { Icon(Icons.Default.Refresh, contentDescription = null); Text(stringResource(R.string.cover_search_reset)) }
                    }
                    Text(stringResource(R.string.cover_attribution), style = MaterialTheme.typography.bodySmall)
                    if (state.searching) LinearProgressIndicator(Modifier.fillMaxWidth())
                    state.downloadProgress?.let { progress -> Text(stringResource(R.string.cover_downloading, progress), style = MaterialTheme.typography.bodySmall) }
                    if (state.error != null) {
                        Column(Modifier.fillMaxWidth()) {
                            YounekoErrorState(state.error ?: stringResource(R.string.error_generic), onRetry = viewModel::search, modifier = Modifier.fillMaxWidth())
                            state.errorFields.forEach { (field, message) -> Text("$field: $message", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                    if (!state.searched) YounekoEmptyState(stringResource(R.string.cover_search_empty), modifier = Modifier.fillMaxWidth())
                    if (state.searched && !state.searching && state.error == null && state.results.isEmpty()) YounekoEmptyState(stringResource(R.string.cover_search_none), modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(4.dp))
                }
            }
            state.sources.forEach { source ->
                val sourceResults = state.results.filter { it.source == source }
                if (sourceResults.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text("${sourceLabel(source, state.sourceInfo?.sources.orEmpty())} — ${sourceResults.size}", style = MaterialTheme.typography.titleSmall)
                    }
                    items(sourceResults, key = { it.bigCoverUrl.orEmpty() }) { result ->
                        CoverResultCard(result, onClick = { preview = result })
                    }
                }
            }
        }
    }
    preview?.let { selected ->
        CoverArtFullscreenDialog(
            model = selected.bigCoverUrl.orEmpty(),
            palette = null,
            onDismiss = { preview = null },
            onUse = { preview = null; viewModel.applyCover(selected); onUseCover(selected) },
        )
    }
}

@Composable
private fun CoverResultCard(result: MusicHoardersCoverLine, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors()) {
        Column {
            Box {
                AsyncImage(model = result.smallCoverUrl ?: result.bigCoverUrl, contentDescription = result.releaseInfo?.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)))
                Text(result.source.orEmpty(), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.TopStart).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)).padding(4.dp))
            }
            Text(formatInfo(result), style = MaterialTheme.typography.labelSmall, maxLines = 1, modifier = Modifier.padding(8.dp))
        }
    }
}

fun isMusicHoardersSourceActive(source: MusicHoardersSourceInfo, country: String): Boolean =
    (source.countries.any { it.equals(country, ignoreCase = true) } || source.countries.any { it.equals("xw", ignoreCase = true) }) && source.queries.any { it.equals("search", ignoreCase = true) }

@Composable
private fun sourceLabel(sourceId: String, catalog: List<MusicHoardersSourceInfo>): String = catalog.firstOrNull { it.id == sourceId }?.name?.takeIf { it.isNotBlank() } ?: sourceId

@Composable
private fun statusLabel(status: String?, count: Int): String = when {
    status == "searching" -> stringResource(R.string.cover_status_searching)
    status == "error" -> stringResource(R.string.cover_status_error)
    count > 0 -> stringResource(R.string.cover_status_count, count)
    status != null -> stringResource(R.string.cover_status_none)
    else -> stringResource(R.string.cover_status_none)
}

private fun formatInfo(result: MusicHoardersCoverLine): String {
    val info = result.info
    val dimensions = if (info?.width != null && info.height != null) "${info.width}×${info.height}" else "—"
    val megabytes = info?.size?.let { String.format(java.util.Locale.getDefault(), "%.1f MB", it / 1_000_000.0) } ?: "—"
    return "$dimensions · $megabytes · ${(info?.format ?: "JPG").uppercase()}"
}
