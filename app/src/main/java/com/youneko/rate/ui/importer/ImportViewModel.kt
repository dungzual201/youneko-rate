package com.youneko.rate.ui.importer

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.youneko.rate.data.importer.ImportGroup
import com.youneko.rate.data.importer.ImportSelection
import com.youneko.rate.data.importer.ImportWorker
import com.youneko.rate.data.importer.ImportDedupe
import com.youneko.rate.data.importer.LocalAudioTagReader
import com.youneko.rate.data.musicbrainz.CoverArtService
import com.youneko.rate.data.musicbrainz.CoverCandidate
import com.youneko.rate.data.musicbrainz.CoverResult
import com.youneko.rate.data.importer.stableKey
import com.youneko.rate.data.local.dao.ImportSessionDao
import com.youneko.rate.data.local.entity.ImportSessionEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

sealed interface ImportEvent {
    data object Success : ImportEvent
    data object Cancelled : ImportEvent
}

data class ImportUiState(
    val isReading: Boolean = false,
    val groups: List<ImportGroup> = emptyList(),
    val selectedUris: Set<String> = emptySet(),
    val selections: Map<String, ImportSelection> = emptyMap(),
    val failures: List<String> = emptyList(),
    val progressCurrent: Int = 0,
    val progressTotal: Int = 0,
    val workId: UUID? = null,
    val workState: WorkInfo.State? = null,
    val importedCount: Int = 0,
    val error: String? = null,
    val sourceUris: List<String> = emptyList(),
    val sourceIsTree: Boolean = false,
    val coverCandidates: Map<String, List<CoverCandidate>> = emptyMap(),
    val coverLoadingKey: String? = null,
    val dialogVisible: Boolean = false,
)

@HiltViewModel
class ImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val importSessionDao: ImportSessionDao,
    private val coverArtService: CoverArtService,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val restoredWorkId = savedStateHandle.get<String>(KEY_WORK_ID)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    private val _state = MutableStateFlow(ImportUiState(workId = restoredWorkId, dialogVisible = savedStateHandle[KEY_DIALOG_VISIBLE] ?: false))
    val state: StateFlow<ImportUiState> = _state.asStateFlow()
    private val eventsChannel = Channel<ImportEvent>(Channel.BUFFERED)
    val events = eventsChannel.receiveAsFlow()
    private var terminalEventSent = false
    private var suppressTerminalEvents = false
    private var lastEnqueueAt = 0L
    private val workManager = WorkManager.getInstance(context)
    private val json = Json { ignoreUnknownKeys = true }

    init {
        restoredWorkId?.let { observeWork(it) }
    }

    fun readSelection(uri: Uri, isTree: Boolean) = readSelections(listOf(uri), isTree)

    fun readSelections(uris: List<Uri>, isTree: Boolean) {
        uris.forEach { uri ->
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(isReading = true, error = null)
            val result = withContext(Dispatchers.IO) {
                val reader = LocalAudioTagReader(context)
                val audioUris = uris.flatMap { reader.collectAudioUris(it, isTree) }.distinct()
                reader.readAll(audioUris)
            }
            val groups = com.youneko.rate.data.importer.ImportGrouping.group(result.tags)
            val selectedUris = groups.flatMap { it.tracks }.mapTo(mutableSetOf()) { it.uri }
            val selections = groups.associate { group ->
                group.stableKey() to ImportSelection(
                    groupKey = group.stableKey(),
                    title = group.displayTitle,
                    artist = group.artist,
                    selectedUris = group.tracks.map { it.uri },
                    mergeIfExisting = group.album != null,
                )
            }
            _state.value = _state.value.copy(
                isReading = false,
                groups = groups,
                selectedUris = selectedUris,
                selections = selections,
                failures = result.failures.map { "${it.fileName}: ${it.reason}" },
                sourceUris = uris.map(Uri::toString),
                sourceIsTree = isTree,
            )
        }
    }

    fun toggleTrack(uri: String) {
        val selected = _state.value.selectedUris.toMutableSet()
        if (!selected.add(uri)) selected.remove(uri)
        _state.value = _state.value.copy(selectedUris = selected)
    }

    fun updateGroup(group: ImportGroup, title: String = group.displayTitle, artist: String = group.artist) {
        val key = group.stableKey()
        val old = _state.value.selections[key] ?: return
        _state.value = _state.value.copy(selections = _state.value.selections + (key to old.copy(title = title, artist = artist)))
    }

    fun setMerge(group: ImportGroup, merge: Boolean) {
        val key = group.stableKey()
        val old = _state.value.selections[key] ?: return
        _state.value = _state.value.copy(selections = _state.value.selections + (key to old.copy(mergeIfExisting = merge)))
    }

    fun setCover(group: ImportGroup, uri: String, source: String = "Manual") {
        val key = group.stableKey()
        val old = _state.value.selections[key] ?: return
        _state.value = _state.value.copy(selections = _state.value.selections + (key to old.copy(coverUri = uri, coverSource = source)))
    }

    fun loadCoverCandidates(group: ImportGroup) {
        val key = group.stableKey()
        _state.value = _state.value.copy(coverLoadingKey = key)
        viewModelScope.launch {
            val candidates = withContext(Dispatchers.IO) {
                coverArtService.searchCandidates(group.displayTitle, group.artist)
            }
            _state.value = _state.value.copy(
                coverCandidates = _state.value.coverCandidates + (key to candidates),
                coverLoadingKey = null,
            )
        }
    }

    fun chooseCoverCandidate(group: ImportGroup, candidate: CoverCandidate) {
        val key = group.stableKey()
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                coverArtService.cacheCandidate(candidate, "picker-${UUID.randomUUID()}.jpg")
            }
            if (result is CoverResult.Success) setCover(group, result.localUri, candidate.sourceProvider)
            _state.value = _state.value.copy(coverCandidates = _state.value.coverCandidates - key)
        }
    }

    fun closeCoverPicker(group: ImportGroup) {
        _state.value = _state.value.copy(coverCandidates = _state.value.coverCandidates - group.stableKey())
    }

    fun enqueueImport() {
        val now = System.currentTimeMillis()
        val current = _state.value
        if (now - lastEnqueueAt < 500L || current.dialogVisible || current.workState == WorkInfo.State.RUNNING || current.workState == WorkInfo.State.ENQUEUED || current.selectedUris.isEmpty() || current.selections.isEmpty() || current.sourceUris.isEmpty()) return
        lastEnqueueAt = now
        terminalEventSent = false
        suppressTerminalEvents = false
        viewModelScope.launch {
            val sessionId = UUID.randomUUID().toString()
            importSessionDao.upsert(
                ImportSessionEntity(
                    id = sessionId,
                    sourceUrisJson = json.encodeToString(current.sourceUris),
                    sourceIsTree = current.sourceIsTree,
                    selectedUrisJson = json.encodeToString(current.selectedUris.toList()),
                    selectionsJson = json.encodeToString(current.selections.values.toList()),
                    createdAt = System.currentTimeMillis(),
                ),
            )
            val request = OneTimeWorkRequestBuilder<ImportWorker>()
                .setInputData(workDataOf(ImportWorker.KEY_SESSION_ID to sessionId))
                .build()
            workManager.enqueue(request)
            savedStateHandle[KEY_WORK_ID] = request.id.toString()
            savedStateHandle[KEY_DIALOG_VISIBLE] = true
            _state.value = _state.value.copy(workId = request.id, workState = WorkInfo.State.ENQUEUED, dialogVisible = true)
            observeWork(request.id)
        }
    }

    fun cancelImport() {
        _state.value.workId?.let(workManager::cancelWorkById)
    }

    fun dismissDialog() {
        if (_state.value.workId != null) suppressTerminalEvents = true
        savedStateHandle.remove<String>(KEY_WORK_ID)
        savedStateHandle[KEY_DIALOG_VISIBLE] = false
        _state.value = _state.value.copy(dialogVisible = false, isReading = false, workId = null, workState = null, progressCurrent = 0, progressTotal = 0)
    }

    fun resetImportState() {
        savedStateHandle.remove<String>(KEY_WORK_ID)
        savedStateHandle.remove<Boolean>(KEY_DIALOG_VISIBLE)
        _state.value = ImportUiState()
        terminalEventSent = false
        suppressTerminalEvents = false
    }

    private fun observeWork(id: UUID) {
        viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(id).collectLatest { info ->
                if (info == null) return@collectLatest
                val progress = info.progress
                if (!terminalEventSent && info.state.isFinished) {
                    terminalEventSent = true
                    if (!suppressTerminalEvents) {
                        if (info.state == WorkInfo.State.SUCCEEDED) eventsChannel.trySend(ImportEvent.Success) else if (info.state == WorkInfo.State.CANCELLED) eventsChannel.trySend(ImportEvent.Cancelled)
                    }
                }
                _state.value = _state.value.copy(
                    workState = info.state,
                    progressCurrent = progress.getInt(ImportWorker.KEY_CURRENT, 0),
                    progressTotal = progress.getInt(ImportWorker.KEY_TOTAL, 0),
                    importedCount = info.outputData.getInt(ImportWorker.KEY_IMPORTED, 0),
                    failures = info.outputData.getString(ImportWorker.KEY_FAILURES)?.split('\n').orEmpty().filter(String::isNotBlank),
                    error = null,
                )
            }
        }
    }

    private companion object {
        const val KEY_WORK_ID = "import_work_id"
        const val KEY_DIALOG_VISIBLE = "import_dialog_visible"
    }
}
