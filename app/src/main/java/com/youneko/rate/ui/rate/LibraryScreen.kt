package com.youneko.rate.ui.rate

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.ManageSearch
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.youneko.rate.R
import com.youneko.rate.data.LibraryAlbum
import com.youneko.rate.data.scan.startScan
import com.youneko.rate.ui.YnDimens
import com.youneko.rate.ui.adaptive.rememberYnGridColumns
import com.youneko.rate.ui.adaptive.rememberYnPagePadding
import com.youneko.rate.ui.components.YnAlbumCard
import com.youneko.rate.ui.components.YnAlbumRow
import com.youneko.rate.ui.components.YnEmptyState
import com.youneko.rate.ui.components.YnErrorState
import com.youneko.rate.ui.components.YnSkeleton
import com.youneko.rate.ui.components.YnTopBar
import com.youneko.rate.ui.musicbrainz.MusicBrainzSearchPanel
import com.youneko.rate.ui.musicbrainz.MusicBrainzSearchViewModel
import com.youneko.rate.ui.stableAlbumKey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenAlbum: (String) -> Unit,
    onAddAlbum: () -> Unit,
    onOpenAdvancedSearch: () -> Unit = {},
    onOpenCollections: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onAnalyzeTrack: (String) -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val onlineViewModel: MusicBrainzSearchViewModel = hiltViewModel()
    val context = LocalContext.current
    var onlineMode by rememberSaveable { mutableStateOf(false) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    val pagePadding = rememberYnPagePadding()
    val columns = rememberYnGridColumns()
    val hasActiveFilter = state.query.isNotBlank() || state.unfinishedOnly

    Column(Modifier.fillMaxSize()) {
        YnTopBar(
            title = stringResource(R.string.library),
            subtitle = if (onlineMode) {
                stringResource(R.string.search_online)
            } else {
                pluralStringResource(R.plurals.library_count, state.albums.size, state.albums.size)
            },
            brandMark = true,
            actions = {
                IconButton(onClick = { showFilters = true }) {
                    Icon(
                        Icons.Default.FilterList,
                        contentDescription = stringResource(R.string.filters),
                        tint = if (state.unfinishedOnly) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.refresh_music_data)) },
                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                startScan(context, forceFull = false, trigger = "library-refresh")
                            },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.advanced_search)) },
                            leadingIcon = { Icon(Icons.Default.ManageSearch, contentDescription = null) },
                            onClick = { menuOpen = false; onOpenAdvancedSearch() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.collections)) },
                            leadingIcon = { Icon(Icons.Default.Collections, contentDescription = null) },
                            onClick = { menuOpen = false; onOpenCollections() },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.settings)) },
                            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                            onClick = { menuOpen = false; onOpenSettings() },
                        )
                    }
                }
            },
        )

        LibraryListHeader(
            state = state,
            onlineMode = onlineMode,
            onToggleOnline = { selectedOnline ->
                onlineMode = selectedOnline
                onlineViewModel.setQuery(state.query)
            },
            onQueryChange = { query ->
                viewModel.setQuery(query)
                onlineViewModel.setQuery(query)
            },
            onClearQuery = {
                viewModel.setQuery("")
                onlineViewModel.setQuery("")
            },
            onOpenFilters = { showFilters = true },
            onSort = viewModel::setSort,
            onToggleView = { viewModel.setGridView(!state.gridView) },
            modifier = Modifier.padding(horizontal = pagePadding),
        )

        when {
            onlineMode -> Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = pagePadding)) {
                MusicBrainzSearchPanel(
                    viewModel = onlineViewModel,
                    onImported = onOpenAlbum,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            state.gridView -> LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = pagePadding, end = pagePadding, top = 8.dp, bottom = YnDimens.listBottomPadding),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when {
                    state.error != null -> item(span = { GridItemSpan(maxLineSpan) }) {
                        YnErrorState(state.error ?: stringResource(R.string.error_generic), onRetry = viewModel::clearError, modifier = Modifier.fillMaxWidth())
                    }
                    state.loading -> items(6, key = { "skeleton-$it" }) {
                        YnSkeleton(Modifier.fillMaxWidth())
                    }
                    state.albums.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyLibraryState(
                            hasFilter = hasActiveFilter,
                            onAddAlbum = onAddAlbum,
                            onClearFilters = {
                                viewModel.setQuery("")
                                viewModel.setUnfinishedOnly(false)
                                onlineViewModel.setQuery("")
                            },
                        )
                    }
                    else -> items(state.albums, key = { stableAlbumKey(it.album.id) }) { item ->
                        AlbumGridItem(item, onOpen = onOpenAlbum, onAnalyzeTrack = onAnalyzeTrack)
                    }
                }
            }
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = pagePadding, end = pagePadding, top = 8.dp, bottom = YnDimens.listBottomPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when {
                    state.error != null -> item {
                        YnErrorState(state.error ?: stringResource(R.string.error_generic), onRetry = viewModel::clearError, modifier = Modifier.fillMaxWidth())
                    }
                    state.loading -> items(6) { YnSkeleton(Modifier.fillMaxWidth(), lines = 1) }
                    state.albums.isEmpty() -> item {
                        EmptyLibraryState(
                            hasFilter = hasActiveFilter,
                            onAddAlbum = onAddAlbum,
                            onClearFilters = {
                                viewModel.setQuery("")
                                viewModel.setUnfinishedOnly(false)
                                onlineViewModel.setQuery("")
                            },
                        )
                    }
                    else -> items(state.albums, key = { stableAlbumKey(it.album.id) }) { item ->
                        AlbumListItem(item, onOpen = onOpenAlbum, onAnalyzeTrack = onAnalyzeTrack)
                    }
                }
            }
        }
    }

    if (showFilters) {
        ModalBottomSheet(
            onDismissRequest = { showFilters = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            FilterSheet(
                unfinishedOnly = state.unfinishedOnly,
                onUnfinished = viewModel::setUnfinishedOnly,
                onClear = { viewModel.setUnfinishedOnly(false); viewModel.setQuery(""); onlineViewModel.setQuery("") },
                onDismiss = { showFilters = false },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryListHeader(
    state: LibraryUiState,
    onlineMode: Boolean,
    onToggleOnline: (Boolean) -> Unit,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onOpenFilters: () -> Unit,
    onSort: (LibrarySort) -> Unit,
    onToggleView: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TextField(
            value = state.query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.search_short), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotBlank()) {
                    IconButton(onClick = onClearQuery) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear_search))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val labels = listOf(stringResource(R.string.search_on_device), stringResource(R.string.search_online))
            labels.forEachIndexed { index, label ->
                SegmentedButton(
                    selected = onlineMode == (index == 1),
                    onClick = { onToggleOnline(index == 1) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = labels.size),
                    label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(
                selected = state.unfinishedOnly,
                onClick = onOpenFilters,
                label = { Text(stringResource(R.string.unfinished_only), maxLines = 1) },
                leadingIcon = if (state.unfinishedOnly) ({ Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }) else null,
            )
            Spacer(Modifier.weight(1f))
            SortMenu(state.sort, onSort)
            IconButton(onClick = onToggleView) {
                Icon(
                    if (state.gridView) Icons.Default.List else Icons.Default.GridView,
                    contentDescription = stringResource(if (state.gridView) R.string.library_list else R.string.library_grid),
                )
            }
        }
    }
}

@Composable
private fun SortMenu(sort: LibrarySort, onSort: (LibrarySort) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Icon(Icons.Default.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text(stringResource(R.string.sort), maxLines = 1)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            LibrarySort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(sortLabel(option)) },
                    onClick = { onSort(option); expanded = false },
                    trailingIcon = if (sort == option) ({ Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }) else null,
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
    onDismiss: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.filters), style = MaterialTheme.typography.headlineSmall)
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.unfinished_only), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.unfinished_only_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = unfinishedOnly, onCheckedChange = onUnfinished)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { onClear(); onDismiss() }) { Text(stringResource(R.string.clear_filters)) }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        }
    }
}

@Composable
private fun EmptyLibraryState(
    hasFilter: Boolean,
    onAddAlbum: () -> Unit,
    onClearFilters: () -> Unit,
) {
    YnEmptyState(
        title = stringResource(if (hasFilter) R.string.no_results else R.string.library_empty_title),
        body = stringResource(if (hasFilter) R.string.no_results_hint else R.string.library_empty_body),
        actionLabel = stringResource(if (hasFilter) R.string.clear_filters else R.string.add_album),
        onAction = if (hasFilter) onClearFilters else onAddAlbum,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AlbumGridItem(item: LibraryAlbum, onOpen: (String) -> Unit, onAnalyzeTrack: (String) -> Unit) {
    var showTrackMenu by rememberSaveable(item.album.id) { mutableStateOf(false) }
    YnAlbumCard(
        item = item,
        onClick = { onOpen(item.album.id) },
        onLongClick = { showTrackMenu = true },
    )
    if (showTrackMenu) {
        AlbumAnalyzeSheet(item, onDismiss = { showTrackMenu = false }, onAnalyzeTrack = onAnalyzeTrack)
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun AlbumListItem(item: LibraryAlbum, onOpen: (String) -> Unit, onAnalyzeTrack: (String) -> Unit) {
    var showTrackMenu by rememberSaveable(item.album.id) { mutableStateOf(false) }
    YnAlbumRow(
        item = item,
        onClick = { onOpen(item.album.id) },
        onLongClick = { showTrackMenu = true },
    )
    if (showTrackMenu) {
        AlbumAnalyzeSheet(item, onDismiss = { showTrackMenu = false }, onAnalyzeTrack = onAnalyzeTrack)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumAnalyzeSheet(item: LibraryAlbum, onDismiss: () -> Unit, onAnalyzeTrack: (String) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                stringResource(R.string.analyze_from_library),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            Text(
                item.album.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            LazyColumn(Modifier.heightIn(max = 480.dp)) {
                items(item.tracks, key = { it.id }) { track ->
                    androidx.compose.material3.ListItem(
                        headlineContent = { Text(track.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(track.fileName.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier
                            .heightIn(min = 56.dp)
                            .clickable(enabled = track.sourceUri != null) {
                                track.sourceUri?.let(onAnalyzeTrack)
                                onDismiss()
                            },
                    )
                }
            }
            if (item.tracks.isEmpty()) {
                Text(stringResource(R.string.empty_tracks), modifier = Modifier.padding(24.dp))
            }
        }
    }
}
