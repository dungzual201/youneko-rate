package com.youneko.rate.ui.credits

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.youneko.rate.R
import com.youneko.rate.data.AlbumRepository
import com.youneko.rate.data.local.entity.CreditEntity
import com.youneko.rate.data.musicbrainz.CreditGroup
import com.youneko.rate.data.musicbrainz.MusicBrainzCreditsService
import com.youneko.rate.data.musicbrainz.NetworkError
import com.youneko.rate.data.musicbrainz.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface CreditsContentState {
    data object Idle : CreditsContentState
    data class Loading(val completed: Int, val total: Int) : CreditsContentState
    data class Error(val kind: NetworkError) : CreditsContentState
    data class NoMbid(val searchUrl: String) : CreditsContentState
    data object Empty : CreditsContentState
    data object Data : CreditsContentState
}

data class CreditsUiState(
    val credits: List<CreditEntity> = emptyList(),
    val content: CreditsContentState = CreditsContentState.Idle,
    val searchUrl: String? = null,
)

@HiltViewModel
class CreditsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: AlbumRepository,
    private val creditsService: MusicBrainzCreditsService,
) : ViewModel() {
    private val albumId: String = checkNotNull(savedStateHandle["albumId"])
    private val trackId: String? = savedStateHandle["trackId"]
    private val _state = MutableStateFlow(CreditsUiState())
    val state = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            creditsService.observeCredits(albumId, trackId)
                .catch { _state.update { value -> value.copy(content = CreditsContentState.Error(NetworkError.UNKNOWN)) } }
                .collect { credits -> _state.update { value -> value.copy(credits = credits) } }
        }
    }

    fun load(forceRefresh: Boolean = false) {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            val library = repository.observeAlbum(albumId).first()
            val album = library?.album
            if (album == null) {
                _state.update { it.copy(content = CreditsContentState.Error(NetworkError.NO_RESULTS)) }
                return@launch
            }
            val searchUrl = musicBrainzSearchUrl(library.album.title, library.artist?.name.orEmpty())
            _state.update { it.copy(searchUrl = searchUrl) }
            if (album.mbid.isNullOrBlank()) {
                _state.update { it.copy(content = CreditsContentState.NoMbid(searchUrl)) }
                return@launch
            }
            if (trackId != null && library.tracks.none { it.id == trackId }) {
                _state.update { it.copy(content = CreditsContentState.Error(NetworkError.NO_RESULTS)) }
                return@launch
            }
            if (trackId != null && library.tracks.firstOrNull { it.id == trackId }?.recordingMbid.isNullOrBlank()) {
                _state.update { it.copy(content = CreditsContentState.NoMbid(searchUrl)) }
                return@launch
            }
            _state.update { it.copy(content = CreditsContentState.Loading(0, 0)) }
            val result = if (trackId == null) {
                creditsService.loadAlbumCredits(album, forceRefresh) { done, total ->
                    _state.update { it.copy(content = CreditsContentState.Loading(done, total)) }
                }
            } else {
                creditsService.loadTrackCredits(album, trackId, forceRefresh) { done, total ->
                    _state.update { it.copy(content = CreditsContentState.Loading(done, total)) }
                }
            }
            when (result) {
                is Resource.Success -> _state.update {
                    it.copy(content = if (it.credits.isEmpty()) CreditsContentState.Empty else CreditsContentState.Data)
                }
                is Resource.Error -> _state.update { it.copy(content = CreditsContentState.Error(result.kind)) }
                Resource.Loading -> Unit
            }
        }
    }

    fun cancel() {
        loadJob?.cancel()
        loadJob = null
        _state.update { it.copy(content = if (it.credits.isEmpty()) CreditsContentState.Idle else CreditsContentState.Data) }
    }

    private fun musicBrainzSearchUrl(title: String, artist: String): String {
        val query = URLEncoder.encode("$title $artist".trim(), StandardCharsets.UTF_8.name())
        return "https://musicbrainz.org/search?type=release&method=indexed&query=$query"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditsScreen(
    onBack: () -> Unit,
    releaseUrl: String?,
    viewModel: CreditsViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.load() }
    val grouped = state.credits.groupBy { creditGroupForUi(it.role) }
    val openUrl: (String) -> Unit = { url -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }

    Scaffold(
        topBar = {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.cancel)) }
                Text(stringResource(R.string.credits_title), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { viewModel.load(forceRefresh = true) }) { Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.credits_reload)) }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            when (val content = state.content) {
                CreditsContentState.Idle -> Unit
                is CreditsContentState.Loading -> {
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator()
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.credits_progress, content.completed, content.total.coerceAtLeast(1)))
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = viewModel::cancel) { Text(stringResource(R.string.cancel)) }
                    }
                }
                is CreditsContentState.Error -> {
                    Text(creditsErrorLabel(content.kind), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp))
                    TextButton(onClick = { viewModel.load(forceRefresh = true) }) { Text(stringResource(R.string.retry)) }
                }
                is CreditsContentState.NoMbid -> {
                    Text(stringResource(R.string.credits_no_mbid), modifier = Modifier.padding(top = 24.dp))
                    Button(onClick = { openUrl(content.searchUrl) }) { Text(stringResource(R.string.credits_link_musicbrainz)) }
                }
                CreditsContentState.Empty -> {
                    Text(stringResource(R.string.credits_empty), modifier = Modifier.padding(top = 24.dp))
                    releaseUrl?.let { url ->
                        Button(onClick = { openUrl(url) }) { Text(stringResource(R.string.credits_contribute)) }
                    }
                    state.searchUrl?.let { url ->
                        TextButton(onClick = { openUrl(url) }) { Text(stringResource(R.string.credits_try_other_release)) }
                    }
                }
                CreditsContentState.Data -> Unit
            }
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                grouped.forEach { (group, values) ->
                    item(key = group.name) {
                        Text(creditGroupLabel(group), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                        HorizontalDivider()
                    }
                    items(values, key = { it.id }) { credit ->
                        CreditRow(credit) { url -> openUrl(url) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreditRow(credit: CreditEntity, onOpen: (String) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(credit.personName, style = MaterialTheme.typography.titleSmall)
                Text(listOfNotNull(credit.role, credit.instrumentOrAttribute).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            }
            AssistChip(onClick = { credit.sourceUrl?.let(onOpen) }, label = { Text("MB") })
        }
    }
}

private fun creditGroupForUi(role: String): CreditGroup = when (role.lowercase()) {
    "composer", "lyricist", "writer", "arranger", "orchestrator", "librettist" -> CreditGroup.WRITING
    "producer", "executive producer", "co-producer" -> CreditGroup.PRODUCTION
    "recording engineer", "mix", "mixing engineer", "mastering", "mastering engineer", "programming", "editor" -> CreditGroup.ENGINEERING
    "vocal", "instrument", "performer", "conductor", "orchestra" -> CreditGroup.PERFORMANCE
    "label", "publisher", "copyright", "phonographic copyright" -> CreditGroup.RELEASE
    else -> CreditGroup.OTHER
}

@Composable
private fun creditGroupLabel(group: CreditGroup): String = when (group) {
    CreditGroup.WRITING -> stringResource(R.string.credits_group_writing)
    CreditGroup.PRODUCTION -> stringResource(R.string.credits_group_production)
    CreditGroup.ENGINEERING -> stringResource(R.string.credits_group_engineering)
    CreditGroup.PERFORMANCE -> stringResource(R.string.credits_group_performance)
    CreditGroup.RELEASE -> stringResource(R.string.credits_group_release)
    CreditGroup.OTHER -> stringResource(R.string.credits_group_other)
}

@Composable
private fun creditsErrorLabel(error: NetworkError): String = when (error) {
    NetworkError.OFFLINE -> stringResource(R.string.network_offline)
    NetworkError.NO_CONNECTION -> stringResource(R.string.network_no_connection)
    NetworkError.TIMEOUT -> stringResource(R.string.network_timeout)
    NetworkError.RATE_LIMITED -> stringResource(R.string.network_rate_limited)
    NetworkError.SERVER_ERROR -> stringResource(R.string.network_server_error)
    NetworkError.BAD_REQUEST -> stringResource(R.string.network_bad_request)
    NetworkError.PARSE_ERROR -> stringResource(R.string.network_parse_error)
    NetworkError.NO_RESULTS -> stringResource(R.string.credits_empty)
    NetworkError.UNKNOWN -> stringResource(R.string.network_error)
}
