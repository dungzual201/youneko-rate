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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.AssistChip
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.youneko.rate.data.discogs.DiscogsCreditsService
import com.youneko.rate.data.local.dao.CreditDao
import com.youneko.rate.data.local.entity.CreditEntity
import com.youneko.rate.data.musicbrainz.CreditGroup
import com.youneko.rate.data.musicbrainz.CreditMerger
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
    val trackTitles: Map<String, String> = emptyMap(),
    val content: CreditsContentState = CreditsContentState.Idle,
    val searchUrl: String? = null,
)

@HiltViewModel
class CreditsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: AlbumRepository,
    private val creditsService: MusicBrainzCreditsService,
    private val discogsService: DiscogsCreditsService,
    private val creditDao: CreditDao,
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
            val trackTitles = library.tracks.associate { it.id to it.title }
            _state.update { it.copy(searchUrl = searchUrl, trackTitles = trackTitles) }
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
                is Resource.Success -> {
                    _state.update { it.copy(content = if (it.credits.isEmpty()) CreditsContentState.Empty else CreditsContentState.Data) }
                    if (trackId == null) loadDiscogsCredits(album, library.artist?.name.orEmpty())
                }
                is Resource.Error -> _state.update { it.copy(content = CreditsContentState.Error(result.kind)) }
                Resource.Loading -> Unit
            }
        }
    }

    private fun loadDiscogsCredits(album: com.youneko.rate.data.local.entity.AlbumEntity, artist: String) {
        viewModelScope.launch {
            when (val result = discogsService.load(album.id, album.title, artist)) {
                is Resource.Success -> if (result.value.credits.isNotEmpty()) {
                    val existing = creditsService.observeCredits(album.id, null).first().filter { it.trackId == null }
                    val existingCandidates = existing.map { credit ->
                        com.youneko.rate.data.musicbrainz.CreditCandidate(
                            personName = credit.personName,
                            personMbid = credit.personMbid,
                            role = credit.role,
                            instrumentOrAttribute = credit.instrumentOrAttribute,
                            sourceProvider = credit.sourceProvider,
                            sourceUrl = credit.sourceUrl,
                            beginDate = credit.beginDate,
                            endDate = credit.endDate,
                        )
                    }
                    creditDao.deleteAlbumCredits(album.id)
                    creditDao.upsertAll(CreditMerger.merge(album.id, null, existingCandidates + result.value.credits))
                }
                else -> Unit
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
    val trackGroups = state.credits
        .groupBy { it.trackId }
        .toList()
        .sortedWith(compareBy<Pair<String?, List<CreditEntity>>> { it.first != null }.thenBy { it.first.orEmpty() })
    val groupKeys = trackGroups.flatMap { (trackId, credits) ->
        credits.groupBy { creditGroupForUi(it.role) }.keys.map { group -> "${trackId ?: "album"}:${group.name}" }
    }
    var expandedGroups by rememberSaveable(groupKeys.joinToString(",")) {
        mutableStateOf(groupKeys.take(3).toSet())
    }
    val openUrl: (String) -> Unit = { url -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    val sourceUrl = releaseUrl ?: state.searchUrl

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
                trackGroups.forEach { (trackId, trackCredits) ->
                    if (trackId != null) {
                        item(key = "track-header-$trackId") {
                            Text(
                                "${state.trackTitles[trackId] ?: trackId}",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                            HorizontalDivider()
                        }
                    }
                    trackCredits.groupBy { creditGroupForUi(it.role) }.forEach { (group, values) ->
                        val groupKey = "${trackId ?: "album"}:${group.name}"
                        val mergedValues = mergeCredits(group, values)
                        val expanded = groupKey in expandedGroups
                        item(key = "header-$groupKey") {
                            TextButton(
                                onClick = {
                                    expandedGroups = if (expanded) {
                                        expandedGroups - groupKey
                                    } else {
                                        (expandedGroups + groupKey).let { names ->
                                            if (names.size > 3) names.drop(1).toSet() else names
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    "${creditGroupLabel(group)} (${mergedValues.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(if (expanded) "▾" else "▸", style = MaterialTheme.typography.titleMedium)
                            }
                            HorizontalDivider()
                        }
                        if (expanded) {
                            items(mergedValues, key = { it.id }) { credit -> CreditRow(credit) }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.credits_source_footer), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { sourceUrl?.let(openUrl) }, enabled = sourceUrl != null) {
                    Text(stringResource(R.string.credits_open_source))
                }
            }
        }
    }
}

data class MergedCredit(
    val id: String,
    val personName: String,
    val roleLabels: List<String>,
    val sources: List<String>,
    val sortOrder: Int,
)

private fun mergeCredits(group: CreditGroup, credits: List<CreditEntity>): List<MergedCredit> = credits
    .groupBy { it.personMbid ?: it.personName.trim().lowercase() }
    .map { (personKey, personCredits) ->
        val first = personCredits.minByOrNull { it.sortOrder } ?: personCredits.first()
        val labels = personCredits
            .flatMap { credit ->
                val date = listOfNotNull(credit.beginDate, credit.endDate).distinct().joinToString("–").ifBlank { null }
                listOfNotNull(credit.role.takeIf(String::isNotBlank), credit.instrumentOrAttribute?.takeIf(String::isNotBlank), date)
            }
            .distinctBy { it.trim().lowercase() }
        MergedCredit("${group.name}-$personKey", first.personName, labels, personCredits.flatMap { it.sourceProvider.split(",") }.map(String::trim).filter { it.isNotBlank() }.distinct(), first.sortOrder)
    }
    .sortedWith(compareBy({ it.sortOrder }, { it.personName.lowercase() }))

@Composable
private fun CreditRow(credit: MergedCredit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(credit.personName, style = MaterialTheme.typography.titleSmall)
            if (credit.roleLabels.isNotEmpty()) {
                Text(credit.roleLabels.map { creditRoleLabel(it) }.joinToString(", "), style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                credit.sources.filterNot { it.equals("musicbrainz", ignoreCase = true) }.forEach { source ->
                    AssistChip(onClick = {}, label = { Text(source.replaceFirstChar { it.uppercase() }) })
                }
            }
        }
    }
}

@Composable
private fun creditRoleLabel(role: String): String = when (role.lowercase()) {
    "mix" -> stringResource(R.string.credit_role_mix)
    "assistant mix" -> stringResource(R.string.credit_role_assistant_mix)
    "lead vocals" -> stringResource(R.string.credit_role_lead_vocals)
    "background vocals" -> stringResource(R.string.credit_role_background_vocals)
    "producer" -> stringResource(R.string.credit_role_producer)
    "programming" -> stringResource(R.string.credit_role_programming)
    "recording" -> stringResource(R.string.credit_role_recording)
    "writer" -> stringResource(R.string.credit_role_writer)
    else -> role
}

private fun creditGroupForUi(role: String): CreditGroup = when (role.lowercase()) {
    "composer", "lyricist", "writer", "arranger", "orchestrator", "librettist" -> CreditGroup.WRITING
    "producer", "executive producer", "co-producer" -> CreditGroup.PRODUCTION
    "recording", "engineer", "recording engineer", "mix", "assistant mix", "mixing engineer", "mastering", "mastering engineer", "programming", "editor" -> CreditGroup.ENGINEERING
    "vocal", "lead vocals", "background vocals", "instrument", "performer", "conductor", "orchestra" -> CreditGroup.PERFORMANCE
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
