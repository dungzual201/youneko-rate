package com.youneko.rate.ui.phase12

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.youneko.rate.R
import com.youneko.rate.data.local.YounekoDatabase
import com.youneko.rate.data.local.entity.AlbumEntity
import com.youneko.rate.data.local.entity.CollectionAlbumEntity
import com.youneko.rate.data.local.entity.CollectionEntity
import com.youneko.rate.data.local.entity.TrackEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class CollectionsViewModel @Inject constructor(private val database: YounekoDatabase) : ViewModel() {
    private val _collections = MutableStateFlow<List<CollectionEntity>>(emptyList())
    val collections = _collections.asStateFlow()
    fun load() = viewModelScope.launch(Dispatchers.IO) { _collections.value = database.collectionDao().findAllCollections() }
    fun create(name: String, description: String?) = viewModelScope.launch(Dispatchers.IO) {
        if (name.isBlank()) return@launch
        database.collectionDao().upsertCollection(CollectionEntity(UUID.randomUUID().toString(), name.trim(), description?.trim()?.takeIf(String::isNotBlank), System.currentTimeMillis()))
        load()
    }
    fun addAlbum(collectionId: String, albumId: String) = viewModelScope.launch(Dispatchers.IO) { database.collectionDao().upsertAlbum(CollectionAlbumEntity(collectionId, albumId, database.collectionDao().findAlbums(collectionId).size)); load() }
    fun delete(id: String) = viewModelScope.launch(Dispatchers.IO) { database.collectionDao().deleteCollection(id); load() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionsScreen(onBack: () -> Unit, viewModel: CollectionsViewModel = hiltViewModel()) {
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) { viewModel.load() }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.collections)) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.a11y_back)) } }) }) { innerPadding ->
    Column(Modifier.fillMaxSize().padding(innerPadding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = { showCreate = true }) { Text(stringResource(R.string.collection_create)) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(collections, key = { it.id }) { collection ->
                Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(12.dp)) { Column(Modifier.weight(1f)) { Text(collection.name, style = MaterialTheme.typography.titleMedium); collection.description?.let { Text(it) } }; TextButton(onClick = { viewModel.delete(collection.id) }) { Text(stringResource(R.string.delete_album)) } } }
            }
        }
    }
    }
    if (showCreate) AlertDialog(
        onDismissRequest = { showCreate = false },
        title = { Text(stringResource(R.string.collection_create)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.collection_name)) }); OutlinedTextField(description, { description = it }, label = { Text(stringResource(R.string.collection_description)) }) } },
        confirmButton = { TextButton(onClick = { viewModel.create(name, description); name = ""; description = ""; showCreate = false }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = { showCreate = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

@HiltViewModel
class ArtistPageViewModel @Inject constructor(private val database: YounekoDatabase) : ViewModel() {
    private val _state = MutableStateFlow<ArtistPageState?>(null)
    val state = _state.asStateFlow()
    fun load(artistId: String) = viewModelScope.launch(Dispatchers.IO) {
        val artist = database.artistDao().findById(artistId) ?: return@launch
        val albums = database.albumDao().findAll().filter { it.artistId == artistId }
        val tracks = database.trackDao().findAll().groupBy { it.albumId }
        _state.value = ArtistPageState(artist.name, albums.map { album -> ArtistAlbum(album, album.manualScoreOverride ?: tracks[album.id].orEmpty().mapNotNull(TrackEntity::stars).average().takeIf { !it.isNaN() }) })
    }
}
data class ArtistPageState(val name: String, val albums: List<ArtistAlbum>)
data class ArtistAlbum(val album: AlbumEntity, val score: Double?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistPageScreen(artistId: String, onBack: () -> Unit, viewModel: ArtistPageViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(artistId) { viewModel.load(artistId) }
    Scaffold(topBar = { TopAppBar(title = { Text(state?.name.orEmpty()) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.a11y_back)) } }) }) { innerPadding ->
    Column(Modifier.fillMaxSize().padding(innerPadding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        state?.albums?.let { albums -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(albums, key = { it.album.id }) { item -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { Text(item.album.title, style = MaterialTheme.typography.titleMedium); Text(item.score?.let { "%.1f/5".format(it) } ?: stringResource(R.string.not_rated)) } } } } }
    }
    }
}

@HiltViewModel
class AdvancedSearchViewModel @Inject constructor(private val database: YounekoDatabase) : ViewModel() {
    private val _results = MutableStateFlow<List<AdvancedSearchResult>>(emptyList())
    val results = _results.asStateFlow()
    fun search(query: String, minScore: Double?, maxScore: Double?, tag: String, creditPerson: String, verdict: String) = viewModelScope.launch(Dispatchers.IO) {
        val artists = database.artistDao().findAll().associateBy { it.id }
        val albums = database.albumDao().findAll()
        val tracks = database.trackDao().findAll().groupBy { it.albumId }
        val credits = database.creditDao().findAll().groupBy { it.albumId }
        val tags = database.albumTagDao().findAll().groupBy { it.albumId }
        val analyses = database.audioAnalysisDao().findAll().groupBy { it.albumId ?: it.trackId }
        _results.value = albums.mapNotNull { album ->
            val artist = artists[album.artistId]?.name.orEmpty()
            val albumTracks = tracks[album.id].orEmpty()
            val score = album.manualScoreOverride ?: albumTracks.mapNotNull(TrackEntity::stars).average().takeIf { !it.isNaN() }
            val textMatches = query.isBlank() || listOf(album.title, artist, album.reviewText.orEmpty()).any { it.contains(query, ignoreCase = true) } || albumTracks.any { it.title.contains(query, true) }
            val scoreMatches = (minScore == null || (score ?: -1.0) >= minScore) && (maxScore == null || (score ?: 99.0) <= maxScore)
            val tagMatches = tag.isBlank() || album.genreTags.any { it.contains(tag, true) } || tags[album.id].orEmpty().any { it.name.contains(tag, true) }
            val creditMatches = creditPerson.isBlank() || credits[album.id].orEmpty().any { it.personName.contains(creditPerson, true) }
            val verdictMatches = verdict.isBlank() || analyses.values.flatten().filter { it.albumId == album.id || it.trackId in albumTracks.map(TrackEntity::id) }.any { it.verdict.contains(verdict, true) }
            if (textMatches && scoreMatches && tagMatches && creditMatches && verdictMatches) AdvancedSearchResult(album, artist, score) else null
        }
    }
}
data class AdvancedSearchResult(val album: AlbumEntity, val artist: String, val score: Double?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedSearchScreen(onBack: () -> Unit, viewModel: AdvancedSearchViewModel = hiltViewModel()) {
    val results by viewModel.results.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var min by rememberSaveable { mutableStateOf("") }
    var max by rememberSaveable { mutableStateOf("") }
    var tag by rememberSaveable { mutableStateOf("") }
    var credit by rememberSaveable { mutableStateOf("") }
    var verdict by rememberSaveable { mutableStateOf("") }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.advanced_search)) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.a11y_back)) } }) }) { innerPadding ->
    Column(Modifier.fillMaxSize().padding(innerPadding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.search_hint)) })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(min, { min = it }, modifier = Modifier.weight(1f), label = { Text(stringResource(R.string.advanced_min_score)) }); OutlinedTextField(max, { max = it }, modifier = Modifier.weight(1f), label = { Text(stringResource(R.string.advanced_max_score)) }) }
        OutlinedTextField(tag, { tag = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.advanced_tag)) })
        OutlinedTextField(credit, { credit = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.advanced_credit_person)) })
        OutlinedTextField(verdict, { verdict = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.advanced_audio_verdict)) })
        Button(onClick = { viewModel.search(query, min.toDoubleOrNull(), max.toDoubleOrNull(), tag, credit, verdict) }) { Text(stringResource(R.string.search)) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(results, key = { it.album.id }) { item -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { Text(item.album.title, style = MaterialTheme.typography.titleMedium); Text(item.artist); Text(item.score?.let { "%.1f/5".format(it) } ?: stringResource(R.string.not_rated)) } } } }
    }
    }
}
