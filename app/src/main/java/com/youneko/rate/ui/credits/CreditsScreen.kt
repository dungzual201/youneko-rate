package com.youneko.rate.ui.credits

import android.content.Intent
import android.util.Log
import android.net.Uri
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.youneko.rate.data.SettingsStore
import com.youneko.rate.data.credits.CreditFetchRequest
import com.youneko.rate.data.credits.ManualCreditLinkParser
import com.youneko.rate.data.credits.CreditSource
import com.youneko.rate.data.credits.CreditSourceId
import com.youneko.rate.data.credits.SourceResult
import com.youneko.rate.data.local.entity.AlbumEntity
import com.youneko.rate.data.local.entity.CreditEntity
import com.youneko.rate.data.musicbrainz.CreditCandidate
import com.youneko.rate.data.musicbrainz.CreditGroup
import com.youneko.rate.data.musicbrainz.CreditMerger
import com.youneko.rate.data.local.dao.CreditDao
import com.youneko.rate.data.local.dao.ExternalLinkDao
import com.youneko.rate.data.local.entity.ExternalLinkEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

sealed interface CreditsContentState {
    data object Idle : CreditsContentState
    data object Loading : CreditsContentState
    data object Error : CreditsContentState
    data object Empty : CreditsContentState
    data object Data : CreditsContentState
}

data class CreditsUiState(
    val perSource: Map<CreditSourceId, SourceResult> = emptyMap(),
    val perSourceCredits: Map<CreditSourceId, List<CreditEntity>> = emptyMap(),
    val activeSources: Set<CreditSourceId> = emptySet(),
    val mergeMode: Boolean = false,
    val trackTitles: Map<String, String> = emptyMap(),
    val content: CreditsContentState = CreditsContentState.Idle,
    val searchUrl: String? = null,
    val loadingSources: Set<CreditSourceId> = emptySet(),
) {
    fun rowsFor(sourceId: CreditSourceId): List<CreditEntity> = perSourceCredits[sourceId].orEmpty()
    val hasRenderableRows: Boolean get() = activeSources.any { rowsFor(it).isNotEmpty() }
    val visibleCredits: List<CreditEntity>
        get() = if (mergeMode) perSourceCredits.filterKeys(activeSources::contains).values.flatten().let { values ->
            val byScope = values.groupBy { it.albumId to it.trackId }
            byScope.values.flatMap { scope ->
                val first = scope.firstOrNull() ?: return@flatMap emptyList()
                CreditMerger.merge(first.albumId, first.trackId, scope.map { it.toCandidate() })
            }
        } else activeSources.flatMap { perSourceCredits[it].orEmpty() }
}

@HiltViewModel
class CreditsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: AlbumRepository,
    private val settings: SettingsStore,
    private val creditDao: CreditDao,
    private val externalLinkDao: ExternalLinkDao,
    private val sources: Set<@JvmSuppressWildcards CreditSource>,
) : ViewModel() {
    private val albumId: String = checkNotNull(savedStateHandle["albumId"])
    private val trackId: String? = savedStateHandle["trackId"]
    private val _state = MutableStateFlow(CreditsUiState())
    val state = _state.asStateFlow()
    private var request: CreditFetchRequest? = null
    private var loadJob: Job? = null
    private val sourceById by lazy { sources.associateBy { it.id } }

    init {
        viewModelScope.launch {
            settings.activeCreditSources.collect { raw ->
                val active = CreditSourceId.parse(raw).toSet().let { it + CreditSourceId.FILE_TAG }
                _state.update { it.copy(activeSources = active) }
            }
        }
        viewModelScope.launch {
            settings.creditsMergeMode.collect { enabled -> _state.update { it.copy(mergeMode = enabled) } }
        }
    }

    fun load(forceRefresh: Boolean = false) {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            val library = repository.observeAlbum(albumId).first()
            val album = library?.album ?: run { _state.update { it.copy(content = CreditsContentState.Error) }; return@launch }
            val titles = library.tracks.associate { it.id to it.title }
            _state.update { it.copy(trackTitles = titles, searchUrl = musicBrainzSearchUrl(album.title, library.artist?.name.orEmpty()), content = CreditsContentState.Loading) }
            val manualLinks = CreditSourceId.entries.mapNotNull { id -> externalLinkDao.find(album.id, trackId, id.name.lowercase())?.let { id to it.externalId } }.toMap()
            request = CreditFetchRequest(
                albumId = album.id,
                albumTitle = album.title,
                artistName = library.artist?.name.orEmpty(),
                releaseMbid = album.mbid,
                tracks = if (trackId == null) library.tracks else library.tracks.filter { it.id == trackId },
                selectedTrackId = trackId,
                force = forceRefresh,
                enabledSourcesHash = activeHash(_state.value.activeSources),
                manualLinks = manualLinks,
            )
            val active = _state.value.activeSources
            if (active.isEmpty()) { _state.update { it.copy(content = CreditsContentState.Empty) }; return@launch }
            supervisorScope {
                active.map { sourceId -> async { fetchSourceInternal(sourceId, forceRefresh) } }.awaitAll()
            }
            val hasData = _state.value.perSourceCredits.values.any { it.isNotEmpty() }
            _state.update { it.copy(content = if (hasData) CreditsContentState.Data else CreditsContentState.Empty) }
        }
    }

    fun toggleSource(id: CreditSourceId, onNeedsToken: () -> Unit = {}) {
        if (id == CreditSourceId.FILE_TAG) return
        viewModelScope.launch(Dispatchers.IO) {
            val active = _state.value.activeSources.toMutableSet()
            if (id in active) active.remove(id) else active.add(id)
            settings.setActiveCreditSources(CreditSourceId.encode(active))
            if (id !in active) return@launch
            if (id.needsToken && (if (id == CreditSourceId.DISCOGS) settings.discogsToken.first() else settings.geniusToken.first()).isBlank()) {
                onNeedsToken()
            } else fetchSourceInternal(id, force = false)
        }
    }

    fun reloadSource(id: CreditSourceId) = viewModelScope.launch { fetchSourceInternal(id, force = true) }
    fun saveManualLink(sourceId: CreditSourceId, raw: String): Boolean {
        val link = ManualCreditLinkParser.parse(sourceId, raw) ?: return false
        viewModelScope.launch(Dispatchers.IO) {
            externalLinkDao.upsert(ExternalLinkEntity(java.util.UUID.randomUUID().toString(), albumId.takeIf { trackId == null }, trackId, sourceId.name.lowercase(), link.externalId, link.url, System.currentTimeMillis()))
            load(forceRefresh = true)
        }
        return true
    }
    fun reloadAll() = load(forceRefresh = true)
    fun setMergeMode(value: Boolean) = viewModelScope.launch(Dispatchers.IO) { settings.setCreditsMergeMode(value) }
    fun cancel() { loadJob?.cancel(); loadJob = null; _state.update { it.copy(content = if (it.visibleCredits.isEmpty()) CreditsContentState.Idle else CreditsContentState.Data) } }

    private suspend fun fetchSourceInternal(id: CreditSourceId, force: Boolean) {
        val base = request ?: return
        val current = base.copy(force = force, enabledSourcesHash = activeHash(_state.value.activeSources))
        _state.update { it.copy(loadingSources = it.loadingSources + id) }
        val result = runCatching { sourceById[id]?.fetch(current) ?: SourceResult.Error("Provider chưa đăng ký") }
            .getOrElse { SourceResult.Error(it.message ?: "Nguồn lỗi") }
        val effectiveResult = includeAlbumRowsForTrack(result, current, id)
        val rows = effectiveResult.toEntities(id, current)
        if (effectiveResult is SourceResult.Success) persist(effectiveResult, current)
        _state.update { it.copy(perSource = it.perSource + (id to effectiveResult), perSourceCredits = it.perSourceCredits + (id to rows), loadingSources = it.loadingSources - id, content = if (rows.isNotEmpty()) CreditsContentState.Data else it.content) }
    }

    private suspend fun includeAlbumRowsForTrack(result: SourceResult, request: CreditFetchRequest, sourceId: CreditSourceId): SourceResult {
        if (request.selectedTrackId == null || result !is SourceResult.Success) return result
        val albumRows = creditDao.findForAlbumWithTracks(request.albumId)
            .filter { it.trackId == null && it.sourceProvider.split(',').mapNotNull(CreditSourceId::fromStored).contains(sourceId) }
            .map { it.toCandidate() }
        val trackRows = result.trackCredits[request.selectedTrackId].orEmpty().ifEmpty { result.credits }
        return result.copy(credits = albumRows, trackCredits = mapOf(request.selectedTrackId to trackRows))
    }

    private suspend fun persist(result: SourceResult.Success, request: CreditFetchRequest) {
        val albumCandidates = if (request.selectedTrackId == null) result.credits else emptyList()
        if (albumCandidates.isNotEmpty()) mergePersist(request.albumId, null, albumCandidates)
        result.trackCredits.forEach { (id, candidates) -> if (candidates.isNotEmpty()) mergePersist(null, id, candidates) }
    }

    private suspend fun mergePersist(albumId: String?, trackId: String?, candidates: List<CreditCandidate>) {
        val existing = if (trackId == null) creditDao.findAlbumCredits(albumId!!) else creditDao.findTrackCredits(trackId)
        creditDao.upsertAll(CreditMerger.merge(albumId, trackId, existing.map { it.toCandidate() } + candidates))
    }

    private fun activeHash(active: Set<CreditSourceId>): String = active.sortedBy { it.name }.joinToString("+") { it.name } + ":v4"

    private fun musicBrainzSearchUrl(title: String, artist: String): String = "https://musicbrainz.org/search?type=release&method=indexed&query=" + URLEncoder.encode("$title $artist".trim(), StandardCharsets.UTF_8.name())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditsScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit = {},
    releaseUrl: String?,
    viewModel: CreditsViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var tokenPrompt by rememberSaveable { mutableStateOf<CreditSourceId?>(null) }
    var manualSource by rememberSaveable { mutableStateOf(CreditSourceId.DISCOGS) }
    var manualLink by rememberSaveable { mutableStateOf("") }
    var manualLinkError by rememberSaveable { mutableStateOf(false) }
    var manualExpanded by rememberSaveable { mutableStateOf(false) }
    var showEmptySources by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.load() }
    val openUrl: (String) -> Unit = { url -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    Scaffold(
        topBar = {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.cancel)) }
                Text(stringResource(R.string.credits_title), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = viewModel::reloadAll) { Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.credits_reload)) }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            SourcePicker(
                state = state,
                onToggle = { id -> viewModel.toggleSource(id) { tokenPrompt = id } },
                onSettings = onOpenSettings,
                onMergeMode = viewModel::setMergeMode,
            )
            TextButton(onClick = { manualExpanded = !manualExpanded }) { Text(stringResource(if (manualExpanded) R.string.credits_manual_link_close else R.string.credits_manual_link_open)) }
            if (manualExpanded) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(CreditSourceId.DISCOGS, CreditSourceId.GENIUS, CreditSourceId.MUSICBRAINZ).forEach { id -> FilterChip(selected = manualSource == id, onClick = { manualSource = id }, label = { Text(id.displayName) }) }
                }
                OutlinedTextField(value = manualLink, onValueChange = { manualLink = it; manualLinkError = false }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.credits_manual_link_label, manualSource.displayName)) }, maxLines = 2)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { manualLinkError = !viewModel.saveManualLink(manualSource, manualLink); if (!manualLinkError) manualLink = "" }, enabled = manualLink.isNotBlank()) { Text(stringResource(R.string.credits_manual_link_save)) }
                    if (manualLinkError) Text(stringResource(R.string.credits_manual_link_invalid), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 8.dp))
                }
            }
            when (state.content) {
                CreditsContentState.Loading -> Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.credits_progress_loading)) }
                CreditsContentState.Empty -> Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.credits_empty_truthful))
                    TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.credits_enable_sources)) }
                    releaseUrl?.let { Button(onClick = { openUrl(it) }) { Text(stringResource(R.string.credits_open_musicbrainz)) } }
                }
                CreditsContentState.Error -> Text(stringResource(R.string.network_error), color = MaterialTheme.colorScheme.error)
                else -> Unit
            }
            if (state.mergeMode) {
                CreditSections(title = stringResource(R.string.credits_merged_title), credits = state.visibleCredits, trackTitles = state.trackTitles, showSources = true)
            } else {
                val dataSources = state.activeSources.filter { state.rowsFor(it).isNotEmpty() }
                val emptySources = state.activeSources.filterNot { it in dataSources }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    dataSources.forEach { sourceId ->
                        val result = state.perSource[sourceId]
                        val rows = state.rowsFor(sourceId)
                        item(key = "source-$sourceId") { SourceStatusHeader(sourceId, result, rows, state.loadingSources.contains(sourceId)) }
                        item(key = "source-content-$sourceId") { CreditSections(title = sourceId.displayName, credits = rows, trackTitles = state.trackTitles, showSources = true, sourceId = sourceId) }
                    }
                    if (emptySources.isNotEmpty()) {
                        item(key = "empty-sources-toggle") { TextButton(onClick = { showEmptySources = !showEmptySources }) { Text(stringResource(R.string.credits_empty_sources, emptySources.size)) } }
                        if (showEmptySources) emptySources.forEach { sourceId -> item(key = "empty-source-$sourceId") { SourceStatusHeader(sourceId, state.perSource[sourceId], emptyList(), state.loadingSources.contains(sourceId)) } }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                val rows = state.visibleCredits
                val count = rows.size
                Text(stringResource(R.string.credits_source_summary, state.activeSources.joinToString(" · ") { it.displayName }), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                Text(stringResource(R.string.credits_count, count), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    tokenPrompt?.let { id ->
        TextButton(onClick = { tokenPrompt = null; onOpenSettings() }) { Text("${id.displayName}: ${stringResource(R.string.credits_enable_sources)}") }
    }
}

@Composable
private fun SourcePicker(state: CreditsUiState, onToggle: (CreditSourceId) -> Unit, onSettings: () -> Unit, onMergeMode: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            items(CreditSourceId.entries.toList() + null) { id ->
                if (id == null) AssistChip(onClick = onSettings, label = { Text(stringResource(R.string.credits_more_sources)) })
                else {
                    val active = id in state.activeSources
                    val result = state.perSource[id]
                    val rows = state.rowsFor(id)
                    val count = rows.size
                    val needsToken = result is SourceResult.NeedsToken
                    FilterChip(selected = active, onClick = { onToggle(id) }, label = { Text(buildString { append(if (active) "✓ " else ""); append(id.displayName); if (count > 0) append(" $count"); if (needsToken) append(" ⚠") }) })
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = !state.mergeMode, onClick = { onMergeMode(false) }, label = { Text(stringResource(R.string.credits_view_separate)) })
            FilterChip(selected = state.mergeMode, onClick = { onMergeMode(true) }, label = { Text(stringResource(R.string.credits_view_merged)) })
        }
    }
}

@Composable
private fun SourceStatusHeader(id: CreditSourceId, result: SourceResult?, rows: List<CreditEntity>, loading: Boolean) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(id.displayName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        when {
            loading -> CircularProgressIndicator(strokeWidth = 2.dp)
            result is SourceResult.Success -> Text(stringResource(R.string.credits_count, rows.size), style = MaterialTheme.typography.labelSmall)
            result is SourceResult.NeedsToken -> Text(stringResource(R.string.credits_needs_token), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            result is SourceResult.Empty -> Text(stringResource(R.string.credits_source_empty), style = MaterialTheme.typography.labelSmall)
            result is SourceResult.Offline -> Text(stringResource(R.string.network_offline), style = MaterialTheme.typography.labelSmall)
            result is SourceResult.NoMatch -> Text(stringResource(R.string.credits_no_match), style = MaterialTheme.typography.labelSmall)
            result is SourceResult.Error -> Text(stringResource(R.string.network_error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun renderCreditRows(sourceId: CreditSourceId?, rawRows: List<CreditEntity>): List<CreditEntity> {
    val renderedRows = rawRows
    if (rawRows.isNotEmpty() && renderedRows.isEmpty()) Log.w("CreditsRenderMismatch", "sourceId=$sourceId rawSize=${rawRows.size} renderedSize=0")
    return renderedRows
}

@Composable
private fun CreditSections(title: String, credits: List<CreditEntity>, trackTitles: Map<String, String>, showSources: Boolean, sourceId: CreditSourceId? = null) {
    val rows = renderCreditRows(sourceId, credits)
    if (rows.isEmpty()) {
        Text("$title · ${stringResource(R.string.credits_source_empty)}", style = MaterialTheme.typography.bodySmall)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.groupBy { it.trackId }.toList().sortedBy { it.first.orEmpty() }.forEach { (trackId, values) ->
            Text(if (trackId == null) stringResource(R.string.credits_release_album_scope) else trackTitles[trackId] ?: trackId, style = MaterialTheme.typography.titleSmall)
            values.groupBy { creditGroupForUi(it.role) }.forEach { (group, groupValues) ->
                Text("${creditGroupLabel(group)} (${groupValues.size})", style = MaterialTheme.typography.labelLarge)
                groupValues.groupBy { it.personMbid ?: normalizeCreditPerson(it.personName) }.values.forEach { personCredits ->
                    val first = personCredits.first()
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp)) {
                            Text(first.personName, style = MaterialTheme.typography.titleSmall)
                            Text(personCredits.flatMap { listOf(it.role, it.instrumentOrAttribute).filterNotNull() }.distinct().joinToString(", "), style = MaterialTheme.typography.bodySmall)
                            if (showSources) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { personCredits.flatMap { it.sourceProvider.split(",") }.mapNotNull(CreditSourceId::fromStored).distinct().forEach { source -> AssistChip(onClick = {}, label = { Text(source.displayName) }) } }
                        }
                    }
                }
            }
        }
    }
}

private fun SourceResult.toEntities(source: CreditSourceId, request: CreditFetchRequest): List<CreditEntity> = when (this) {
    is SourceResult.Success -> buildList {
        if (request.selectedTrackId != null) {
            addAll(credits.map { it.toEntity(source, request.albumId, null) })
            addAll(trackCredits[request.selectedTrackId].orEmpty().map { it.toEntity(source, null, request.selectedTrackId) })
        } else {
            addAll(credits.map { it.toEntity(source, request.albumId, null) })
            trackCredits.forEach { (trackId, candidates) -> addAll(candidates.map { it.toEntity(source, null, trackId) }) }
        }
    }
    else -> emptyList()
}

private fun CreditCandidate.toEntity(source: CreditSourceId, albumId: String?, trackId: String?) = CreditEntity(
    id = UUID.nameUUIDFromBytes("${source.name}:${albumId.orEmpty()}:${trackId.orEmpty()}:${personName}:${role}".toByteArray()).toString(),
    albumId = albumId,
    trackId = trackId,
    personName = personName,
    personMbid = personMbid,
    role = role,
    instrumentOrAttribute = instrumentOrAttribute,
    sourceProvider = source.name.lowercase(),
    sourceUrl = sourceUrl,
    sortOrder = 0,
    beginDate = beginDate,
    endDate = endDate,
)

private fun CreditEntity.toCandidate() = CreditCandidate(personName, personMbid, role, instrumentOrAttribute, sourceProvider, sourceUrl, beginDate, endDate)
private fun normalizeCreditPerson(value: String): String = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD).replace("\\p{M}+".toRegex(), "").lowercase().replace(Regex("\\s+"), " ").trim()
private fun creditGroupForUi(role: String): CreditGroup = com.youneko.rate.data.musicbrainz.creditGroupForRole(role)
private fun creditGroupLabel(group: CreditGroup): String = when (group) {
    CreditGroup.WRITING -> "Sáng tác"
    CreditGroup.PRODUCTION -> "Sản xuất"
    CreditGroup.ENGINEERING -> "Kỹ thuật"
    CreditGroup.PERFORMANCE -> "Trình diễn"
    CreditGroup.RELEASE -> "Phát hành"
    CreditGroup.OTHER -> "Khác"
}
