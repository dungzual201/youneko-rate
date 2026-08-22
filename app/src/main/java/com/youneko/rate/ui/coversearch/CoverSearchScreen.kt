package com.youneko.rate.ui.coversearch

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.youneko.rate.R
import com.youneko.rate.data.AlbumRepository
import com.youneko.rate.data.musicbrainz.CoverCandidate
import com.youneko.rate.data.musicbrainz.ItunesCoverApi
import com.youneko.rate.data.musicbrainz.PublicCoverProviders
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import javax.inject.Inject

enum class CoverSearchPhase { IDLE, LOADING, RESULTS, EMPTY, ERROR }

data class NativeCoverUiState(
    val artist: String = "",
    val album: String = "",
    val phase: CoverSearchPhase = CoverSearchPhase.IDLE,
    val candidates: List<CoverCandidate> = emptyList(),
    val sourceErrors: List<String> = emptyList(),
    val selectedUrl: String? = null,
)

@HiltViewModel
class CoverSearchViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: AlbumRepository,
    private val itunesApi: ItunesCoverApi,
    private val publicCoverProviders: PublicCoverProviders,
) : ViewModel() {
    private val albumId: String = checkNotNull(savedStateHandle["albumId"])
    private val _state = MutableStateFlow(NativeCoverUiState())
    val state: StateFlow<NativeCoverUiState> = _state.asStateFlow()
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            repository.observeAlbum(albumId).collect { album ->
                if (album == null) return@collect
                val artist = album.artist?.name.orEmpty()
                val title = album.album.title
                if (_state.value.artist != artist || _state.value.album != title) {
                    _state.value = _state.value.copy(artist = artist, album = title)
                    if (artist.isNotBlank() && title.isNotBlank()) search()
                }
                return@collect
            }
        }
    }

    fun setArtist(value: String) { _state.value = _state.value.copy(artist = value) }
    fun setAlbum(value: String) { _state.value = _state.value.copy(album = value) }

    fun search() {
        searchJob?.cancel()
        val query = _state.value
        if (query.artist.isBlank() || query.album.isBlank()) {
            _state.value = query.copy(phase = CoverSearchPhase.EMPTY, candidates = emptyList(), sourceErrors = emptyList())
            return
        }
        _state.value = query.copy(phase = CoverSearchPhase.LOADING, candidates = emptyList(), sourceErrors = emptyList(), selectedUrl = null)
        searchJob = viewModelScope.launch {
            val itunes = async { runCatching { searchItunes(query.artist, query.album) } }
            val archive = async { runCatching { searchArchive(query.artist, query.album) } }
            val results = listOf(itunes.await(), archive.await())
            val candidates = results.flatMap { it.getOrDefault(emptyList()) }
                .distinctBy { "${it.sourceProvider}|${it.url}" }
                .sortedByDescending { it.matchScore ?: 0.0 }
            val errors = results.mapNotNull { it.exceptionOrNull()?.displayMessage() }
            _state.value = _state.value.copy(
                phase = when {
                    candidates.isNotEmpty() -> CoverSearchPhase.RESULTS
                    errors.isNotEmpty() -> CoverSearchPhase.ERROR
                    else -> CoverSearchPhase.EMPTY
                },
                candidates = candidates,
                sourceErrors = errors,
            )
        }
    }

    fun cancelSearch() {
        searchJob?.cancel()
        _state.value = _state.value.copy(phase = CoverSearchPhase.IDLE, candidates = emptyList())
    }

    fun select(candidate: CoverCandidate) {
        _state.value = _state.value.copy(selectedUrl = candidate.url)
    }

    private suspend fun searchItunes(artist: String, album: String): List<CoverCandidate> = try {
        val response = itunesApi.searchAlbums("$artist $album", limit = 25, country = "us")
        val candidates = response.results.mapNotNull { result ->
            val raw = result.artworkUrl100 ?: return@mapNotNull null
            val url = raw.replace("100x100bb", "100000x100000bb")
            CoverCandidate(
                url = url,
                sourceProvider = "itunes",
                title = result.collectionName,
                artistName = result.artistName,
                trackCount = result.trackCount,
                releaseDate = result.releaseDate,
                matchScore = score(artist, album, result.artistName, result.collectionName),
                verified = true,
                widthHint = 100000,
            )
        }
        Log.d("COVER", "source=itunes httpCode=200 count=${candidates.size} err=null")
        candidates
    } catch (error: Throwable) {
        val code = (error as? HttpException)?.code() ?: -1
        Log.d("COVER", "source=itunes httpCode=$code count=0 err=${error.displayMessage()}")
        throw error
    }

    private suspend fun searchArchive(artist: String, album: String): List<CoverCandidate> = try {
        val candidates = publicCoverProviders.searchCoverArtArchive(artist, album).map {
            CoverCandidate(
                url = it.url,
                sourceProvider = "cover_art_archive",
                title = it.title,
                artistName = it.artist,
                matchScore = 1.0,
                verified = true,
                widthHint = it.widthHint,
            )
        }
        Log.d("COVER", "source=cover_art_archive httpCode=200 count=${candidates.size} err=null")
        candidates
    } catch (error: Throwable) {
        val code = (error as? HttpException)?.code() ?: -1
        Log.d("COVER", "source=cover_art_archive httpCode=$code count=0 err=${error.displayMessage()}")
        throw error
    }

    private fun score(expectedArtist: String, expectedAlbum: String, candidateArtist: String?, candidateAlbum: String?): Double? {
        if (candidateArtist.isNullOrBlank() || candidateAlbum.isNullOrBlank()) return null
        val artistMatch = normalizedSimilarity(expectedArtist, candidateArtist)
        val albumMatch = normalizedSimilarity(expectedAlbum, candidateAlbum)
        return if (artistMatch >= 0.65 && albumMatch >= 0.65) (artistMatch + albumMatch) / 2.0 else null
    }

    private fun normalizedSimilarity(left: String, right: String): Double {
        val a = left.lowercase().filter(Char::isLetterOrDigit)
        val b = right.lowercase().filter(Char::isLetterOrDigit)
        if (a == b) return 1.0
        if (a.isBlank() || b.isBlank()) return 0.0
        val common = a.zip(b).count { it.first == it.second }.toDouble()
        return common / maxOf(a.length, b.length)
    }

    private fun Throwable.displayMessage(): String = when (this) {
        is HttpException -> "HTTP ${code()}: ${message()}"
        else -> message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoverSearchScreen(onBack: () -> Unit, viewModel: CoverSearchViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cover_search_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.a11y_back)) } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = state.artist, onValueChange = viewModel::setArtist, label = { Text(stringResource(R.string.cover_search_artist)) }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(value = state.album, onValueChange = viewModel::setAlbum, label = { Text(stringResource(R.string.cover_search_album)) }, modifier = Modifier.weight(1f), singleLine = true)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = viewModel::search, enabled = state.phase != CoverSearchPhase.LOADING) { Icon(Icons.Default.Refresh, contentDescription = null); Spacer(Modifier.height(1.dp)); Text(stringResource(R.string.cover_search_retry)) }
                if (state.phase == CoverSearchPhase.LOADING) TextButton(onClick = viewModel::cancelSearch) { Text(stringResource(R.string.cover_search_cancel)) }
            }
            when (state.phase) {
                CoverSearchPhase.LOADING -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(stringResource(R.string.cover_search_loading))
                }
                CoverSearchPhase.EMPTY -> CoverEmptyState(state, onRetry = viewModel::search)
                CoverSearchPhase.ERROR -> CoverErrorState(state, onRetry = viewModel::search)
                CoverSearchPhase.RESULTS -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.candidates, key = { "${it.sourceProvider}:${it.url}" }) { candidate ->
                        CoverResultCard(candidate, selected = candidate.url == state.selectedUrl, onClick = { viewModel.select(candidate) })
                    }
                }
                CoverSearchPhase.IDLE -> Text(stringResource(R.string.cover_search_ready))
            }
        }
    }
}

@Composable
private fun CoverResultCard(candidate: CoverCandidate, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AsyncImage(model = candidate.url, contentDescription = candidate.title, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.small))
            Text(candidate.sourceProvider, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            Text(candidate.widthHint?.let { "${it}px" } ?: stringResource(R.string.cover_search_resolution_unknown), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CoverEmptyState(state: NativeCoverUiState, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.cover_search_no_results, state.artist, state.album), style = MaterialTheme.typography.titleMedium)
        Button(onClick = onRetry) { Text(stringResource(R.string.cover_search_retry)) }
    }
}

@Composable
private fun CoverErrorState(state: NativeCoverUiState, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.cover_search_error), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
        state.sourceErrors.forEach { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Button(onClick = onRetry) { Text(stringResource(R.string.cover_search_retry)) }
    }
}
