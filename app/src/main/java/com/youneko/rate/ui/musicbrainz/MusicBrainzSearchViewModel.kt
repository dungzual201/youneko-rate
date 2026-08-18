package com.youneko.rate.ui.musicbrainz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.youneko.rate.data.musicbrainz.MusicBrainzPreview
import com.youneko.rate.data.musicbrainz.MusicBrainzRepository
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
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val _preview = MutableStateFlow<Resource<MusicBrainzPreview>?>(null)
    val preview: StateFlow<Resource<MusicBrainzPreview>?> = _preview.asStateFlow()
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
            _preview.value = repository.lookupRelease(item.id)
        }
    }

    fun closePreview() { _preview.value = null }
}
