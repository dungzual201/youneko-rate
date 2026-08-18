package com.youneko.rate.ui.musicbrainz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.youneko.rate.data.musicbrainz.MusicBrainzPreview
import com.youneko.rate.data.musicbrainz.MusicBrainzRepository
import com.youneko.rate.data.musicbrainz.MusicBrainzImportService
import com.youneko.rate.data.musicbrainz.ImportConflictChoice
import com.youneko.rate.data.musicbrainz.MusicBrainzSearchItem
import com.youneko.rate.data.musicbrainz.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class MusicBrainzSearchViewModel @Inject constructor(
    private val repository: MusicBrainzRepository,
    private val importService: MusicBrainzImportService,
) : ViewModel() {
    private val query = MutableStateFlow("")
    val queryText: StateFlow<String> = query.asStateFlow()
    private val _preview = MutableStateFlow<Resource<MusicBrainzPreview>?>(null)
    val preview: StateFlow<Resource<MusicBrainzPreview>?> = _preview.asStateFlow()
    private val _pendingImport = MutableStateFlow<MusicBrainzPreview?>(null)
    val pendingImport: StateFlow<MusicBrainzPreview?> = _pendingImport.asStateFlow()
    private val _importResult = MutableStateFlow<Resource<String>?>(null)
    val importResult: StateFlow<Resource<String>?> = _importResult.asStateFlow()
    val pagedResults: Flow<PagingData<MusicBrainzSearchItem>> = query
        .debounce(400)
        .distinctUntilChanged()
        .flatMapLatest { value ->
            if (value.isBlank()) kotlinx.coroutines.flow.flowOf(PagingData.empty())
            else repository.searchPager("release-group", value.trim())
        }
        .cachedIn(viewModelScope)

    fun setQuery(value: String) { query.value = value }

    fun openPreview(item: MusicBrainzSearchItem) {
        viewModelScope.launch {
            _preview.value = Resource.Loading
            _preview.value = if (item.entityType == "release-group") {
                importService.loadReleaseGroup(item.id)
            } else {
                importService.loadRelease(item.id)
            }
        }
    }

    fun closePreview() { _preview.value = null }

    fun selectRelease(releaseId: String) {
        val current = (_preview.value as? Resource.Success)?.value ?: return
        viewModelScope.launch {
            _preview.value = Resource.Loading
            _preview.value = importService.loadRelease(releaseId, current.releaseGroupId)
        }
    }

    fun requestImport(preview: MusicBrainzPreview) { _pendingImport.value = preview }

    fun resolveImport(choice: ImportConflictChoice) {
        val value = _pendingImport.value ?: return
        _pendingImport.value = null
        _importResult.value = Resource.Loading
        viewModelScope.launch {
            _importResult.value = importService.import(value, choice)
        }
    }
}
