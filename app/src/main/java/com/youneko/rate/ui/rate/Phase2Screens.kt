package com.youneko.rate.ui.rate

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.youneko.rate.R
import com.youneko.rate.data.LibraryAlbum
import com.youneko.rate.data.local.entity.AlbumEntity
import com.youneko.rate.data.local.entity.TrackEntity
import com.youneko.rate.domain.usecase.ScoreMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenAlbum: (String) -> Unit,
    onAddAlbum: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showFilters by rememberSaveable { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.weight(1f),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text(stringResource(R.string.search_library)) },
            )
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = { showFilters = true }) {
                Icon(Icons.Default.FilterList, contentDescription = stringResource(R.string.filters))
            }
            IconButton(onClick = { viewModel.setGridView(!state.gridView) }) {
                Icon(
                    if (state.gridView) Icons.Default.List else Icons.Default.GridView,
                    contentDescription = if (state.gridView) stringResource(R.string.library_list) else stringResource(R.string.library_grid),
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.library_count, state.albums.size), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.weight(1f))
            SortMenu(state.sort, viewModel::setSort)
        }
        Spacer(Modifier.height(8.dp))
        if (state.albums.isEmpty()) {
            EmptyLibrary(onAddAlbum, hasQuery = state.query.isNotBlank() || state.unfinishedOnly)
        } else if (state.gridView) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.albums, key = { it.album.id }) { item -> AlbumCard(item, onOpenAlbum) }
            }
        } else {
            LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.albums, key = { it.album.id }) { item -> AlbumListRow(item, onOpenAlbum) }
            }
        }
    }
    if (showFilters) {
        ModalBottomSheet(onDismissRequest = { showFilters = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            FilterSheet(
                unfinishedOnly = state.unfinishedOnly,
                onUnfinished = viewModel::setUnfinishedOnly,
                onClear = { viewModel.setUnfinishedOnly(false) },
            )
        }
    }
}

@Composable
private fun SortMenu(sort: LibrarySort, onSort: (LibrarySort) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) { Text(stringResource(R.string.sort)) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            LibrarySort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(sortLabel(option)) },
                    onClick = { onSort(option); expanded = false },
                    trailingIcon = if (sort == option) ({ Icon(Icons.Default.Star, contentDescription = null) }) else null,
                )
            }
        }
    }
}

@Composable
private fun sortLabel(sort: LibrarySort): String = stringResource(
    when (sort) {
        LibrarySort.NEWEST -> R.string.sort_newest
        LibrarySort.SCORE_HIGH -> R.string.sort_score_high
        LibrarySort.SCORE_LOW -> R.string.sort_score_low
        LibrarySort.TITLE -> R.string.sort_title
        LibrarySort.YEAR -> R.string.sort_year
        LibrarySort.LISTENED_DATE -> R.string.sort_listened
    },
)

@Composable
private fun FilterSheet(
    unfinishedOnly: Boolean,
    onUnfinished: (Boolean) -> Unit,
    onClear: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.filters), style = MaterialTheme.typography.headlineSmall)
        FilterChip(selected = unfinishedOnly, onClick = { onUnfinished(!unfinishedOnly) }, label = { Text(stringResource(R.string.unfinished_only)) })
        TextButton(onClick = onClear) { Text(stringResource(R.string.clear_filters)) }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun EmptyLibrary(onAdd: () -> Unit, hasQuery: Boolean) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("ฅ^•ﻌ•^ฅ", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
        Text(stringResource(if (hasQuery) R.string.no_results else R.string.library_empty_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(if (hasQuery) R.string.no_results_hint else R.string.library_empty_body), modifier = Modifier.padding(vertical = 8.dp))
        Button(onClick = onAdd) { Icon(Icons.Default.Add, contentDescription = null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.add_album)) }
    }
}

@Composable
private fun AlbumCard(item: LibraryAlbum, onOpen: (String) -> Unit) {
    Card(onClick = { onOpen(item.album.id) }) {
        Column(Modifier.padding(12.dp)) {
            CoverPlaceholder(Modifier.fillMaxWidth().height(120.dp))
            Spacer(Modifier.height(8.dp))
            Text(item.album.title, maxLines = 2, style = MaterialTheme.typography.titleMedium)
            Text(item.artist?.name.orEmpty(), maxLines = 1, style = MaterialTheme.typography.bodySmall)
            ScoreLine(item)
        }
    }
}

@Composable
private fun AlbumListRow(item: LibraryAlbum, onOpen: (String) -> Unit) {
    Card(onClick = { onOpen(item.album.id) }, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CoverPlaceholder(Modifier.size(64.dp))
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(item.album.title, style = MaterialTheme.typography.titleMedium)
                Text(item.artist?.name.orEmpty(), style = MaterialTheme.typography.bodySmall)
                ScoreLine(item)
            }
        }
    }
}

@Composable
private fun ScoreLine(item: LibraryAlbum) {
    val score = item.score
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFB84D), modifier = Modifier.size(18.dp))
        Text(score?.let { "${it.effectiveScore.format2()}★" } ?: stringResource(R.string.not_rated), style = MaterialTheme.typography.labelLarge)
        if (score != null) Text(" · ${score.ratedCount}/${score.totalCount}", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun CoverPlaceholder(modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.Album, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
fun RateScreen(onAddAlbum: () -> Unit, onOpenAlbum: (String) -> Unit, viewModel: LibraryViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showStandalone by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onAddAlbum) { Icon(Icons.Default.Add, contentDescription = null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.add_album)) }
            OutlinedButton(onClick = { showStandalone = true }) { Text(stringResource(R.string.add_standalone)) }
        }
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.albums_in_progress), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        val inProgress = state.albums.filter { it.tracks.isNotEmpty() && it.score?.ratedCount != it.tracks.size }
        if (inProgress.isEmpty()) {
            EmptyLibrary(onAddAlbum, hasQuery = false)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(inProgress, key = { it.album.id }) { item -> AlbumListRow(item, onOpenAlbum) }
            }
        }
    }
    if (showStandalone) StandaloneDialog(onDismiss = { showStandalone = false })
}

@Composable
private fun StandaloneDialog(onDismiss: () -> Unit) {
    val repository: com.youneko.rate.data.RateRepository = hiltViewModel<StandaloneViewModel>().repository
    val scope = rememberCoroutineScope()
    var title by rememberSaveable { mutableStateOf("") }
    var artist by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.standalone_dialog)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.standalone_title)) }, singleLine = true)
                OutlinedTextField(artist, { artist = it }, label = { Text(stringResource(R.string.standalone_artist)) }, singleLine = true)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.isBlank()) error = "Tên bài không được để trống" else if (artist.isBlank()) error = "Tên nghệ sĩ không được để trống" else {
                    scope.launch {
                        repository.saveStandalone(title, artist, null)
                        onDismiss()
                    }
                }
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun AlbumEditorScreen(onSaved: (String) -> Unit, onCancel: () -> Unit, viewModel: AlbumEditorViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AlbumEditorEvent.OpenAlbum -> onSaved(event.albumId)
            }
        }
    }
    var quickCount by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(state.title, viewModel::setTitle, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.album_title)) }, singleLine = true)
        OutlinedTextField(state.artist, viewModel::setArtist, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.artist_name)) }, singleLine = true)
        OutlinedTextField(state.year, viewModel::setYear, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.release_year)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
        AlbumTypeMenu(state.albumType, viewModel::setType)
        OutlinedTextField(state.genres, viewModel::setGenres, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.genres)) }, singleLine = true)
        OutlinedTextField(state.listenedDate, viewModel::setListenedDate, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.listened_date)) }, singleLine = true)
        val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> viewModel.setCoverUri(uri?.toString()) }
        OutlinedButton(onClick = { coverPicker.launch("image/*") }) { Text(stringResource(R.string.choose_cover)) }
        state.coverUri?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1) }
        HorizontalDivider()
        Text(stringResource(R.string.tracklist), style = MaterialTheme.typography.titleLarge)
        state.tracks.forEachIndexed { index, value ->
            ReorderableEditorTrackRow(
                index = index,
                value = value,
                onValue = { viewModel.setTrack(index, it) },
                onMove = { from, to -> viewModel.moveTrack(from, to) },
                onRemove = { viewModel.removeTrack(index) },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { viewModel.addTrack() }) { Text(stringResource(R.string.add_track)) }
            OutlinedButton(onClick = { quickCount = "5" }) { Text(stringResource(R.string.quick_add_tracks)) }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
            Button(onClick = { viewModel.save() }) { Text(stringResource(R.string.save)) }
        }
    }
    if (quickCount.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { quickCount = "" },
            title = { Text(stringResource(R.string.quick_add_tracks)) },
            text = { OutlinedTextField(quickCount, { quickCount = it.filter(Char::isDigit).take(2) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true) },
            confirmButton = { TextButton(onClick = { viewModel.addQuick(quickCount.toIntOrNull() ?: 1); quickCount = "" }) { Text(stringResource(R.string.add_track)) } },
            dismissButton = { TextButton(onClick = { quickCount = "" }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun ReorderableEditorTrackRow(
    index: Int,
    value: String,
    onValue: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: () -> Unit,
) {
    var dragDistance by remember { mutableStateOf(0f) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(index) {
                detectDragGesturesAfterLongPress(
                    onDrag = { change, amount ->
                        change.consume()
                        dragDistance += amount.y
                        if (abs(dragDistance) > 42f) {
                            val target = (index + if (dragDistance > 0) 1 else -1).coerceIn(0, 50)
                            if (target != index) {
                                onMove(index, target)
                                dragDistance = 0f
                            }
                        }
                    },
                    onDragEnd = { dragDistance = 0f },
                    onDragCancel = { dragDistance = 0f },
                )
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(value, onValue, Modifier.weight(1f), label = { Text("${index + 1}. ${stringResource(R.string.track_name)}") }, singleLine = true)
        IconButton(onClick = onRemove) { Icon(Icons.Default.RemoveCircleOutline, contentDescription = stringResource(R.string.remove_track)) }
    }
}

@Composable
private fun AlbumTypeMenu(value: String, onValue: (String) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text(value) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf("ALBUM" to R.string.album, "EP" to R.string.ep, "SINGLE" to R.string.single, "COMPILATION" to R.string.compilation).forEach { (raw, label) ->
                DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = { onValue(raw); expanded = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(onBack: () -> Unit, viewModel: AlbumDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                AlbumDetailEvent.ExitAlbum -> onBack()
            }
        }
    }
    var showDelete by rememberSaveable { mutableStateOf(false) }
    var showManualScore by rememberSaveable { mutableStateOf(false) }
    var manualScoreText by rememberSaveable { mutableStateOf("") }
    var menuExpanded by rememberSaveable { mutableStateOf(false) }
    val snackbarHost = remember { SnackbarHostState() }
    Scaffold(snackbarHost = { SnackbarHost(snackbarHost) }) { padding ->
        when (state) {
            AlbumDetailUiState.Loading -> Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Text(stringResource(R.string.error_generic)) }
            AlbumDetailUiState.AlbumDeleted -> Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Text(stringResource(R.string.confirm_delete_body)) }
            is AlbumDetailUiState.Content -> {
            val value = (state as AlbumDetailUiState.Content).album
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).padding(horizontal = 16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.cancel)) }
                    Text(stringResource(R.string.album_detail), style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.weight(1f))
                    Box {
                        IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.edit_album)) }
                        DropdownMenu(menuExpanded, { menuExpanded = false }) { DropdownMenuItem(text = { Text(stringResource(R.string.delete_album)) }, onClick = { menuExpanded = false; showDelete = true }, leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }) }
                    }
                }
                CoverPlaceholder(Modifier.fillMaxWidth().height(220.dp))
                Text(value.album.title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 12.dp))
                Text(value.artist?.name.orEmpty(), style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(value.score?.let { "${it.effectiveScore.format2()}★" } ?: stringResource(R.string.not_rated), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.rated_progress, value.score?.ratedCount ?: 0, value.tracks.size))
                }
                value.score?.manualOverride?.let { override ->
                    Text("${stringResource(R.string.manual_score)}: ${override.format2()}★ (avg ${value.score.average.format2()})", style = MaterialTheme.typography.bodyMedium)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { manualScoreText = value.album.manualScoreOverride?.toString().orEmpty(); showManualScore = true }) { Text(stringResource(R.string.set_manual_score)) }
                    if (value.album.manualScoreOverride != null) TextButton(onClick = { viewModel.updateAlbum(value.album.copy(manualScoreOverride = null)) }) { Text(stringResource(R.string.clear_override)) }
                }
                if (value.score?.usedEqualWeightsFallback == true) Text(stringResource(R.string.weighted_fallback), style = MaterialTheme.typography.bodySmall)
                ReviewEditor(
                    label = stringResource(R.string.album_review), initial = value.album.reviewText.orEmpty(), max = 4000,
                    onChanged = { viewModel.updateAlbum(value.album.copy(reviewText = it)) },
                )
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Text(stringResource(R.string.tracklist), style = MaterialTheme.typography.titleLarge)
                if (value.tracks.isEmpty()) Text(stringResource(R.string.empty_tracks), modifier = Modifier.padding(vertical = 24.dp))
                else value.tracks.forEach { track -> TrackRow(track, viewModel::updateTrack) }
                Spacer(Modifier.height(32.dp))
            }
            }
        }
    }
    if (showManualScore) {
        AlertDialog(
            onDismissRequest = { showManualScore = false },
            title = { Text(stringResource(R.string.set_manual_score)) },
            text = { OutlinedTextField(manualScoreText, { manualScoreText = it }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true) },
            confirmButton = { TextButton(onClick = {
                manualScoreText.toDoubleOrNull()?.takeIf { it in 0.5..5.0 }?.let { valueNow -> viewModel.currentAlbum()?.let { album -> viewModel.updateAlbum(album.copy(manualScoreOverride = valueNow)) } }
                showManualScore = false
            }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { showManualScore = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text(stringResource(R.string.confirm_delete_title)) },
            text = { Text(stringResource(R.string.confirm_delete_body)) },
            confirmButton = { TextButton(onClick = { showDelete = false; viewModel.deleteAlbum() }) { Text(stringResource(R.string.delete_album)) } },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun TrackRow(track: TrackEntity, onChanged: (TrackEntity) -> Unit) {
    var review by rememberSaveable(track.id) { mutableStateOf(track.reviewText.orEmpty()) }
    var expanded by rememberSaveable(track.id) { mutableStateOf(false) }
    LaunchedEffect(review) {
        delay(800)
        if (review != track.reviewText.orEmpty()) onChanged(track.copy(reviewText = review))
    }
    Card(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${track.trackNumber ?: "•"}. ${track.title}", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                StarRatingBar(track.stars, { onChanged(track.copy(stars = it)) }, { onChanged(track.copy(stars = null)) }, step = 0.5)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val highlightLabel = "${stringResource(R.string.highlight)} ${track.title}"
                val skipLabel = "${stringResource(R.string.skip)} ${track.title}"
                IconToggleButton(checked = track.isHighlight, onCheckedChange = { onChanged(track.copy(isHighlight = it)) }, modifier = Modifier.semantics { contentDescription = highlightLabel }) { Icon(Icons.Default.Highlight, contentDescription = null, tint = if (track.isHighlight) Color(0xFFFFB84D) else Color.Gray) }
                IconToggleButton(checked = track.isSkip, onCheckedChange = { onChanged(track.copy(isSkip = it)) }, modifier = Modifier.semantics { contentDescription = skipLabel }) { Icon(Icons.Default.SkipNext, contentDescription = null, tint = if (track.isSkip) MaterialTheme.colorScheme.error else Color.Gray) }
                TextButton(onClick = { expanded = !expanded }) { Text(stringResource(R.string.track_review)) }
            }
            if (expanded) {
                OutlinedTextField(review, { review = it.take(2000) }, Modifier.fillMaxWidth(), minLines = 2, maxLines = 6, label = { Text(stringResource(R.string.track_review)) })
                Text(stringResource(R.string.characters, review.length), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ReviewEditor(label: String, initial: String, max: Int, onChanged: (String) -> Unit) {
    var expanded by rememberSaveable(label) { mutableStateOf(initial.isNotBlank()) }
    var text by rememberSaveable(label) { mutableStateOf(initial) }
    LaunchedEffect(text) {
        delay(800)
        if (text != initial) onChanged(text)
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "−" else "+") }
        }
        if (expanded) {
            OutlinedTextField(text, { text = it.take(max) }, Modifier.fillMaxWidth(), minLines = 3, maxLines = 8, label = { Text(label) })
            Text(stringResource(R.string.characters, text.length), style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun Double.format2(): String = "%.2f".format(java.util.Locale.US, this)

@dagger.hilt.android.lifecycle.HiltViewModel
class StandaloneViewModel @javax.inject.Inject constructor(
    val repository: com.youneko.rate.data.RateRepository,
) : androidx.lifecycle.ViewModel()

@Composable
fun SettingsScreen(viewModel: ScoreSettingsViewModel = hiltViewModel()) {
    val mode by viewModel.scoreMode.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.score_mode), style = MaterialTheme.typography.titleLarge)
        FilterChip(
            selected = mode == ScoreMode.SIMPLE,
            onClick = { viewModel.setScoreMode(ScoreMode.SIMPLE) },
            label = { Text(stringResource(R.string.simple_average)) },
        )
        FilterChip(
            selected = mode == ScoreMode.WEIGHTED_BY_DURATION,
            onClick = { viewModel.setScoreMode(ScoreMode.WEIGHTED_BY_DURATION) },
            label = { Text(stringResource(R.string.weighted_average)) },
        )
        Text(stringResource(R.string.settings_body), style = MaterialTheme.typography.bodyMedium)
    }
}
