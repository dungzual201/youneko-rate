package com.youneko.rate.ui.rate

import com.youneko.rate.ui.YounekoEmptyState
import com.youneko.rate.ui.YounekoErrorState
import com.youneko.rate.ui.YounekoLoadingState
import com.youneko.rate.ui.YounekoSpacing
import com.youneko.rate.ui.YnDimens
import com.youneko.rate.ui.YounekoRadius
import com.youneko.rate.ui.ThemeMode
import com.youneko.rate.ui.rememberReducedMotion
import com.youneko.rate.ui.younekoSpring
import com.youneko.rate.ui.stableAlbumKey

import android.app.Activity
import android.content.Intent
import androidx.compose.animation.animateContentSize
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pets
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
import androidx.compose.material3.ListItem
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.youneko.rate.data.SettingsDataStore
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.youneko.rate.R
import com.youneko.rate.data.LibraryAlbum
import com.youneko.rate.data.artwork.ArtworkStore
import com.youneko.rate.data.artwork.CoverPalette
import com.youneko.rate.data.artwork.contrastRatio
import com.youneko.rate.data.artwork.coverDetailGradient
import com.youneko.rate.ui.components.YnPaletteSwatches
import com.youneko.rate.data.local.entity.AlbumEntity
import com.youneko.rate.data.local.entity.TrackEntity
import com.youneko.rate.data.importer.LocalAudioTagReader
import com.youneko.rate.data.lyrics.Lyrics
import com.youneko.rate.data.lyrics.toPlainText
import com.youneko.rate.data.lyrics.LyricLine
import com.youneko.rate.ui.artwork.CoverArtImage
import com.youneko.rate.ui.components.YnAlbumCard
import com.youneko.rate.ui.components.YnSharedTopAppBar
import com.youneko.rate.ui.components.YnTabTitle
import com.youneko.rate.ui.components.YnSkeleton
import com.youneko.rate.domain.usecase.ScoreMode
import com.youneko.rate.domain.usecase.RatingScale
import com.youneko.rate.ui.musicbrainz.MusicBrainzSearchPanel
import com.youneko.rate.ui.musicbrainz.MusicBrainzSearchViewModel
import com.youneko.rate.ui.media.MediaScanRootManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlin.math.abs

private fun androidx.compose.ui.graphics.Color.toHex(): String = "#%08X".format(toArgb())

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
    var refreshing by remember { mutableStateOf(false) }
    val refresh: () -> Unit = {
        if (!refreshing) {
            refreshing = true
            com.youneko.rate.data.scan.enqueueMediaScan(context, forceFull = false)
        }
    }
    LaunchedEffect(refreshing) {
        if (refreshing) {
            delay(900)
            refreshing = false
        }
    }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var onlineMode by rememberSaveable { mutableStateOf(false) }
    var searchActive by rememberSaveable { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize()) {
        YnSharedTopAppBar(
            screenName = "Library",
            windowInsets = WindowInsets(0),
            actions = {
                IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings), modifier = Modifier.size(YnDimens.iconMedium)) }
                IconButton(onClick = { showFilters = true }) { Icon(Icons.Default.FilterList, contentDescription = stringResource(R.string.filters), modifier = Modifier.size(YnDimens.iconMedium)) }
                IconButton(onClick = { viewModel.setGridView(!state.gridView) }) {
                    Icon(if (state.gridView) Icons.Default.List else Icons.Default.GridView, contentDescription = if (state.gridView) stringResource(R.string.library_list) else stringResource(R.string.library_grid), modifier = Modifier.size(YnDimens.iconMedium))
                }
            },
        )
        YnTabTitle(R.string.library, Modifier.padding(horizontal = YnDimens.space4))
        LibraryListHeader(
            state = state,
            onlineMode = onlineMode,
            searchActive = searchActive,
            onSearchActiveChange = { searchActive = it },
            onQueryChange = { query -> viewModel.setQuery(query); onlineViewModel.setQuery(query) },
            onOnlineModeChange = { selectedOnline -> onlineMode = selectedOnline; onlineViewModel.setQuery(state.query) },
            onOpenAdvancedSearch = onOpenAdvancedSearch,
            onOpenCollections = onOpenCollections,
            onRefresh = refresh,
            onSort = viewModel::setSort,
            modifier = Modifier.padding(horizontal = YnDimens.space4),
        )
        when {
            onlineMode -> MusicBrainzSearchPanel(
                viewModel = onlineViewModel,
                onImported = onOpenAlbum,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            state.gridView -> LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = YnDimens.space4, end = YnDimens.space4, top = 0.dp, bottom = YnDimens.navigationSafe),
                horizontalArrangement = Arrangement.spacedBy(YnDimens.space3),
                verticalArrangement = Arrangement.spacedBy(YnDimens.space3),
            ) {
                when {
                    state.error != null -> item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) { YounekoErrorState(state.error ?: stringResource(R.string.error_generic), onRetry = viewModel::clearError, modifier = Modifier.fillMaxWidth()) }
                    state.albums.isEmpty() -> item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) { EmptyLibrary(onAddAlbum, hasQuery = state.query.isNotBlank() || state.unfinishedOnly) }
                    refreshing -> items(6, key = { "skeleton-$it" }) { YnSkeleton(Modifier.padding(YnDimens.space2)) }
                    else -> items(state.albums, key = { stableAlbumKey(it.album.id) }) { item -> AlbumCard(item, onOpenAlbum, onAnalyzeTrack) }
                }
            }
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = YnDimens.space4, end = YnDimens.space4, top = 0.dp, bottom = YnDimens.navigationSafe),
                verticalArrangement = Arrangement.spacedBy(YnDimens.space3),
            ) {
                when {
                    state.error != null -> item { YounekoErrorState(state.error ?: stringResource(R.string.error_generic), onRetry = viewModel::clearError, modifier = Modifier.fillMaxWidth()) }
                    state.albums.isEmpty() -> item { EmptyLibrary(onAddAlbum, hasQuery = state.query.isNotBlank() || state.unfinishedOnly) }
                    else -> items(state.albums, key = { stableAlbumKey(it.album.id) }) { item -> AlbumListRow(item, onOpenAlbum, onAnalyzeTrack) }
                }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryListHeader(
    state: LibraryUiState,
    onlineMode: Boolean,
    searchActive: Boolean,
    onSearchActiveChange: (Boolean) -> Unit,
    onQueryChange: (String) -> Unit,
    onOnlineModeChange: (Boolean) -> Unit,
    onOpenAdvancedSearch: () -> Unit,
    onOpenCollections: () -> Unit,
    onRefresh: () -> Unit,
    onSort: (LibrarySort) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(YnDimens.space2)) {
        SearchBar(
            query = state.query,
            onQueryChange = onQueryChange,
            onSearch = { onSearchActiveChange(false) },
            active = searchActive,
            onActiveChange = onSearchActiveChange,
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search), modifier = Modifier.size(YnDimens.iconMedium)) },
            trailingIcon = if (state.query.isNotBlank()) ({ IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close), modifier = Modifier.size(YnDimens.iconMedium)) } }) else null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.search_library), Modifier.padding(YnDimens.space4)) }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val labels = listOf(stringResource(R.string.search_on_device), stringResource(R.string.search_online))
            labels.forEachIndexed { index, label ->
                SegmentedButton(
                    selected = onlineMode == (index == 1),
                    onClick = { onOnlineModeChange(index == 1) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = labels.size),
                    label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(YnDimens.space2)) {
            TextButton(onClick = onOpenAdvancedSearch) { Text(stringResource(R.string.advanced_search)) }
            TextButton(onClick = onOpenCollections) { Text(stringResource(R.string.collections)) }
            TextButton(onClick = onRefresh) { Text(stringResource(R.string.refresh_music_data)) }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(pluralStringResource(R.plurals.library_count, state.albums.size, state.albums.size), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.weight(1f))
            SortMenu(state.sort, onSort)
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
        Icon(Icons.Default.Pets, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(if (hasQuery) R.string.no_results else R.string.library_empty_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(if (hasQuery) R.string.no_results_hint else R.string.library_empty_body), modifier = Modifier.padding(vertical = 8.dp))
        Button(onClick = onAdd) { Icon(Icons.Default.Add, contentDescription = null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.add_album)) }
    }
}

@Composable
private fun AlbumCard(item: LibraryAlbum, onOpen: (String) -> Unit, onAnalyzeTrack: (String) -> Unit = {}) {
    var showTrackMenu by rememberSaveable(item.album.id) { mutableStateOf(false) }
    YnAlbumCard(item, onClick = { onOpen(item.album.id) }, onLongClick = { showTrackMenu = true }, modifier = Modifier.animateContentSize(animationSpec = younekoSpring(rememberReducedMotion())))
    if (showTrackMenu) AlbumAnalyzeSheet(item, onDismiss = { showTrackMenu = false }, onAnalyzeTrack = onAnalyzeTrack)
}

@Composable
private fun AlbumListRow(item: LibraryAlbum, onOpen: (String) -> Unit, onAnalyzeTrack: (String) -> Unit = {}) {
    var showTrackMenu by rememberSaveable(item.album.id) { mutableStateOf(false) }
    Card(shape = RoundedCornerShape(YounekoRadius.lg), modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { onOpen(item.album.id) }, onLongClick = { showTrackMenu = true })) {
        Row(Modifier.padding(YounekoSpacing.md), verticalAlignment = Alignment.CenterVertically) {
            CoverArtImage(item.album.coverUri, Modifier.size(64.dp), placeholderSeed = item.album.id, placeholderLabel = item.album.title)
            Column(Modifier.weight(1f).padding(start = YounekoSpacing.md)) {
                Text(item.album.title, style = MaterialTheme.typography.titleMedium)
                Text(item.artist?.name.orEmpty(), style = MaterialTheme.typography.bodySmall)
                ScoreLine(item)
            }
        }
    }
    if (showTrackMenu) AlbumAnalyzeSheet(item, onDismiss = { showTrackMenu = false }, onAnalyzeTrack = onAnalyzeTrack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumAnalyzeSheet(item: LibraryAlbum, onDismiss: () -> Unit, onAnalyzeTrack: (String) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = YounekoSpacing.md)) {
            Text(stringResource(R.string.analyze_from_library), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = YounekoSpacing.md, vertical = YounekoSpacing.sm))
            item.tracks.forEach { track ->
                ListItem(
                    headlineContent = { Text(track.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(track.fileName.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.heightIn(min = 56.dp).clickable {
                        track.sourceUri?.let(onAnalyzeTrack)
                        onDismiss()
                    },
                )
            }
            if (item.tracks.isEmpty()) Text(stringResource(R.string.empty_tracks), modifier = Modifier.padding(YounekoSpacing.md))
        }
    }
}

@Composable
private fun ScoreLine(item: LibraryAlbum) {
    val score = item.score
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(YnDimens.space3))
        Text(score?.let { "${it.effectiveScore.format2()}★" } ?: stringResource(R.string.not_rated), style = MaterialTheme.typography.labelLarge)
        if (score != null) Text(" · ${score.ratedCount}/${score.totalCount}", style = MaterialTheme.typography.labelSmall)
    }
}

private enum class RateFilter { ALL, UNRATED, RATED, TOP }

@Composable
fun RateScreen(onAddAlbum: () -> Unit, onImportTags: () -> Unit, onOpenAlbum: (String) -> Unit, viewModel: LibraryViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showStandalone by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf(RateFilter.ALL) }
    Column(Modifier.fillMaxSize().padding(horizontal = YnDimens.space4)) {
        YnTabTitle(R.string.rate)
        Column(verticalArrangement = Arrangement.spacedBy(YnDimens.space2), modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(YnDimens.space2)) {
            Button(onClick = onAddAlbum, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_album), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.add_album), maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(onClick = { showStandalone = true }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.add_standalone), maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        OutlinedButton(onClick = onImportTags, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.FolderOpen, contentDescription = stringResource(R.string.import_music), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.import_music), maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.albums_in_progress), style = MaterialTheme.typography.headlineSmall)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = filter == RateFilter.ALL, onClick = { filter = RateFilter.ALL }, label = { Text(stringResource(R.string.rate_filter_all)) })
            FilterChip(selected = filter == RateFilter.UNRATED, onClick = { filter = RateFilter.UNRATED }, label = { Text(stringResource(R.string.rate_filter_unrated)) })
            FilterChip(selected = filter == RateFilter.RATED, onClick = { filter = RateFilter.RATED }, label = { Text(stringResource(R.string.rate_filter_rated)) })
            FilterChip(selected = filter == RateFilter.TOP, onClick = { filter = RateFilter.TOP }, label = { Text(stringResource(R.string.rate_filter_top)) })
        }
        Spacer(Modifier.height(8.dp))
        val rateItems = when (filter) {
            RateFilter.ALL -> state.albums
            RateFilter.UNRATED -> state.albums.filter { it.score?.ratedCount ?: 0 < it.tracks.size }
            RateFilter.RATED -> state.albums.filter { it.tracks.isNotEmpty() && it.score?.ratedCount == it.tracks.size }
            RateFilter.TOP -> state.albums.sortedByDescending { it.score?.effectiveScore ?: -1.0 }
        }
        if (rateItems.isEmpty()) {
            EmptyLibrary(onAddAlbum, hasQuery = false)
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = YnDimens.navigationSafe),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(rateItems, key = { stableAlbumKey(it.album.id) }) { item -> AlbumListRow(item, onOpenAlbum) }
            }
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
    val titleRequired = stringResource(R.string.validation_title)
    val artistRequired = stringResource(R.string.validation_artist)
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
                if (title.isBlank()) error = titleRequired else if (artist.isBlank()) error = artistRequired else {
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
        state.coverUri?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
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
fun AlbumDetailScreen(
    onBack: () -> Unit,
    onViewCredits: (albumId: String, trackId: String?, releaseMbid: String?) -> Unit = { _, _, _ -> },
    onOpenArtist: (String) -> Unit = {},
    onAnalyzeTrack: (String) -> Unit = {},
    onSearchCover: (String) -> Unit = {},
    viewModel: AlbumDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val manualCoverPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        viewModel.setManualCover(uri.toString())
    }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                AlbumDetailEvent.ExitAlbum -> onBack()
            }
        }
    }
    var showDelete by rememberSaveable { mutableStateOf(false) }
    var showManualScore by rememberSaveable { mutableStateOf(false) }
    var fileInfoTrackId by rememberSaveable { mutableStateOf<String?>(null) }
    var showFullCover by rememberSaveable { mutableStateOf(false) }
    var manualScoreText by rememberSaveable { mutableStateOf("") }
    var tagDraft by rememberSaveable { mutableStateOf("") }
    var listeningNote by rememberSaveable { mutableStateOf("") }
    var menuExpanded by rememberSaveable { mutableStateOf(false) }
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val paletteCopiedPattern = stringResource(R.string.palette_copied)
    val refreshResult by viewModel.refreshResult.collectAsStateWithLifecycle()
    val ratingScale by viewModel.ratingScale.collectAsStateWithLifecycle()
    val albumPalette by viewModel.palette.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val listeningLogs by viewModel.listeningLogs.collectAsStateWithLifecycle()
    val reviewRevisions by viewModel.reviewRevisions.collectAsStateWithLifecycle()
    val contentValue = (state as? AlbumDetailUiState.Content)?.album
    val palette = albumPalette
    val darkTheme = isSystemInDarkTheme()
    val dominantColor = palette?.dominant ?: Color(0xFF403A46)
    val view = LocalView.current
    val statusBarPx = ViewCompat.getRootWindowInsets(view)?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: 0
    val statusBarDp = with(LocalDensity.current) { statusBarPx.toDp() }
    val scrimHeight = statusBarDp + 120.dp
    val detailGradient = remember(contentValue?.album?.id, palette, darkTheme) {
        coverDetailGradient(palette, contentValue?.album?.id ?: "album-detail", darkTheme)
    }
    contentValue?.let {
        val topBarContrast = contrastRatio(dominantColor, Color.White)
        android.util.Log.d("CONTRAST", "album=${it.album.id} bg=${dominantColor.toHex()} text=#FFFFFF ratio=$topBarContrast")
    }
    SideEffect {
        (context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(statusBarDp + 420.dp)
                .background(detailGradient),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(scrimHeight)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent))),
        )
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0),
            snackbarHost = { SnackbarHost(snackbarHost) },
            topBar = {
                TopAppBar(
                    windowInsets = WindowInsets(0),
                    title = { Text(stringResource(R.string.album_detail), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.a11y_back), modifier = Modifier.size(YnDimens.iconMedium)) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent, titleContentColor = Color.White, navigationIconContentColor = Color.White, actionIconContentColor = Color.White),
                    actions = {
                        Box {
                            IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.edit_album), modifier = Modifier.size(YnDimens.iconMedium)) }
                            DropdownMenu(menuExpanded, { menuExpanded = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.refresh_metadata), maxLines = 3, overflow = TextOverflow.Ellipsis) }, onClick = { menuExpanded = false; viewModel.refreshMetadata() })
                                DropdownMenuItem(text = { Text(stringResource(R.string.reload_cover), maxLines = 3, overflow = TextOverflow.Ellipsis) }, onClick = { menuExpanded = false; viewModel.reloadCover() })
                                DropdownMenuItem(text = { Text(stringResource(R.string.choose_manual_cover), maxLines = 3, overflow = TextOverflow.Ellipsis) }, onClick = { menuExpanded = false; manualCoverPicker.launch(arrayOf("image/*")) })
                                DropdownMenuItem(text = { Text(stringResource(R.string.menu_search_cover_online), maxLines = 3, overflow = TextOverflow.Ellipsis) }, onClick = { menuExpanded = false; contentValue?.let { onSearchCover(it.album.id) } })
                                DropdownMenuItem(text = { Text(stringResource(R.string.view_credits), maxLines = 3, overflow = TextOverflow.Ellipsis) }, onClick = { menuExpanded = false; contentValue?.let { onViewCredits(it.album.id, null, it.album.mbid) } })
                                DropdownMenuItem(text = { Text(stringResource(R.string.delete_album), maxLines = 3, overflow = TextOverflow.Ellipsis) }, onClick = { menuExpanded = false; showDelete = true }, leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) })
                            }
                        }
                    },
                )
            },
        ) { padding ->
        when (state) {
            AlbumDetailUiState.Loading -> YounekoLoadingState(Modifier.fillMaxSize().padding(padding).padding(YounekoSpacing.md), lines = 4)
            AlbumDetailUiState.AlbumDeleted -> YounekoEmptyState(stringResource(R.string.confirm_delete_body), modifier = Modifier.fillMaxSize().padding(padding))
            is AlbumDetailUiState.Content -> {
            val value = (state as AlbumDetailUiState.Content).album
            android.util.Log.d("INSET", "screen=album_detail topPadding=${padding.calculateTopPadding().value}dp")
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).padding(horizontal = 16.dp)) {
                Box(Modifier.fillMaxWidth().padding(top = YnDimens.space4), contentAlignment = Alignment.Center) {
                    CoverArtImage(
                        value.album.coverUri,
                        Modifier.fillMaxWidth(0.72f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .shadow(12.dp, RoundedCornerShape(16.dp))
                            .clickable(enabled = value.album.coverUri != null) { showFullCover = true },
                        contentScale = ContentScale.Fit,
                        placeholderSeed = value.album.id,
                        placeholderLabel = value.album.title,
                    )
                }
                Text(value.album.title, style = MaterialTheme.typography.displaySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = YnDimens.space5))
                TextButton(onClick = { value.artist?.id?.let(onOpenArtist) }, enabled = value.artist != null) { Text(value.artist?.name.orEmpty(), style = MaterialTheme.typography.titleMedium) }
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
                if (refreshResult is com.youneko.rate.data.musicbrainz.Resource.Error) {
                    Text(stringResource(R.string.refresh_metadata_error), color = MaterialTheme.colorScheme.error)
                }
                ReviewEditor(
                    label = stringResource(R.string.album_review), initial = value.album.reviewText.orEmpty(), max = 4000,
                    onChanged = { viewModel.updateAlbum(value.album.copy(reviewText = it)); viewModel.saveReviewRevision(it) },
                )
                if (reviewRevisions.isNotEmpty()) Text(stringResource(R.string.review_history, reviewRevisions.size), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.custom_tags), style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(tagDraft, { tagDraft = it.take(40) }, Modifier.weight(1f), label = { Text(stringResource(R.string.add_tag)) }, singleLine = true)
                    TextButton(onClick = { viewModel.addTag(tagDraft); tagDraft = "" }, enabled = tagDraft.isNotBlank() && tags.size < 10) { Text(stringResource(R.string.add)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { tags.forEach { tag -> AssistChip(onClick = { viewModel.removeTag(tag.id) }, label = { Text(tag.name) }) } }
                Text(stringResource(R.string.listening_log), style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(listeningNote, { listeningNote = it.take(160) }, Modifier.weight(1f), label = { Text(stringResource(R.string.listening_note)) }, singleLine = true)
                    TextButton(onClick = { viewModel.logListening(listeningNote); listeningNote = "" }) { Text(stringResource(R.string.log_listening)) }
                }
                if (listeningLogs.isNotEmpty()) Text(stringResource(R.string.listening_count, listeningLogs.size), style = MaterialTheme.typography.bodySmall)
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Text(stringResource(R.string.tracklist), style = MaterialTheme.typography.titleLarge)
                if (value.tracks.isEmpty()) Text(stringResource(R.string.empty_tracks), modifier = Modifier.padding(vertical = 24.dp))
                else value.tracks.forEach { track ->
                    TrackRow(
                        track = track,
                        onChanged = viewModel::updateTrack,
                        ratingScale = ratingScale,
                        onViewCredits = { onViewCredits(value.album.id, track.id, value.album.mbid) },
                        onAnalyzeTrack = { onAnalyzeTrack(track.id) },
                    )
                }
                Spacer(Modifier.height(32.dp))
            }
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
    if (showFullCover) {
        (state as? AlbumDetailUiState.Content)?.album?.album?.coverUri?.let { coverUri ->
            CoverArtFullscreenDialog(
                model = coverUri,
                palette = albumPalette,
                onDismiss = { showFullCover = false },
                onCopied = { hex -> scope.launch { snackbarHost.showSnackbar(paletteCopiedPattern.replace("%1\$s", hex)) } },
            )
        }
    }
    fileInfoTrackId?.let { trackId ->
        val track = (state as? AlbumDetailUiState.Content)?.album?.tracks?.firstOrNull { it.id == trackId }
        AlertDialog(
            onDismissRequest = { fileInfoTrackId = null },
            title = { Text(stringResource(R.string.track_file_info)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(track?.fileName.orEmpty())
                    Text(track?.sourceUri.orEmpty(), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { fileInfoTrackId = null }) { Text(stringResource(R.string.close)) } },
        )
    }
}

@Composable
internal fun CoverArtFullscreenDialog(
    model: Any,
    palette: CoverPalette?,
    onDismiss: () -> Unit,
    onSave: () -> Unit = {},
    onCopied: (String) -> Unit = {},
    onUse: (() -> Unit)? = null,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var swipeDistance by remember { mutableFloatStateOf(0f) }
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.cover_share)
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        if (scale > 1f) offset += panChange
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(color = Color.Black.copy(alpha = 0.95f), modifier = Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { _, amount -> swipeDistance += amount },
                        onDragEnd = { if (swipeDistance > 120f) onDismiss(); swipeDistance = 0f },
                        onDragCancel = { swipeDistance = 0f },
                    )
                },
            ) {
                CoverArtImage(
                    model = model,
                    modifier = Modifier.fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 72.dp)
                        .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y }
                        .transformable(transformState),
                    contentScale = ContentScale.Fit,
                )
                IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopStart).padding(12.dp)) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close), tint = Color.White)
                }
                palette?.let { currentPalette ->
                    YnPaletteSwatches(
                        colors = currentPalette.swatches,
                        onReleased = {},
                        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp),
                        onCopied = onCopied,
                    )
                }
                onUse?.let { useCover ->
                    Button(onClick = useCover, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 88.dp)) {
                        Text(stringResource(R.string.cover_use_this))
                    }
                }
                Row(Modifier.align(Alignment.TopEnd).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onSave) { Icon(Icons.Default.SaveAlt, contentDescription = stringResource(R.string.cover_save), tint = Color.White) }
                    IconButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, model.toString()) }
                        context.startActivity(Intent.createChooser(send, shareTitle))
                    }) { Icon(Icons.Default.Share, contentDescription = stringResource(R.string.cover_share), tint = Color.White) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackRow(
    track: TrackEntity,
    onChanged: (TrackEntity) -> Unit,
    ratingScale: RatingScale = RatingScale.FIVE_STARS,
    onViewCredits: () -> Unit = {},
    onAnalyzeTrack: () -> Unit = {},
) {
    val context = LocalContext.current
    var review by rememberSaveable(track.id) { mutableStateOf(track.reviewText.orEmpty()) }
    var reviewExpanded by rememberSaveable(track.id) { mutableStateOf(false) }
    var actionsOpen by rememberSaveable(track.id) { mutableStateOf(false) }
    var lyricsExpanded by rememberSaveable(track.id) { mutableStateOf(false) }
    var lyricsShowAll by rememberSaveable(track.id) { mutableStateOf(false) }
    var lyricsTimestamps by rememberSaveable(track.id) { mutableStateOf(false) }
    var lyricsFullscreen by rememberSaveable(track.id) { mutableStateOf(false) }
    val lyricsResult by produceState<Result<Lyrics?>?>(initialValue = null, key1 = track.sourceUri, key2 = lyricsExpanded) {
        if (!lyricsExpanded) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                track.sourceUri?.let { source ->
                    LocalAudioTagReader(context, ArtworkStore(context)).readAll(listOf(Uri.parse(source))).tags.firstOrNull()?.lyrics
                }
            }
        }
    }
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    fun closeSheetThen(action: () -> Unit) {
        scope.launch {
            sheetState.hide()
            actionsOpen = false
            action()
        }
    }
    LaunchedEffect(review) {
        delay(800)
        if (review != track.reviewText.orEmpty()) onChanged(track.copy(reviewText = review))
    }
    Card(Modifier.fillMaxWidth().padding(vertical = 5.dp).heightIn(min = 56.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${track.trackNumber ?: "•"}. ${track.title}", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (ratingScale == RatingScale.FIVE_STARS) {
                StarRatingBar(track.stars, { onChanged(track.copy(stars = it)) }, { onChanged(track.copy(stars = null)) }, step = 0.5)
            } else {
                OutlinedTextField(
                    value = ratingScale.fromStars(track.stars)?.format2().orEmpty(),
                    onValueChange = { value -> onChanged(track.copy(stars = ratingScale.toStars(value.toDoubleOrNull()?.coerceIn(0.0, ratingScale.max.toDouble())))) },
                    label = { Text(ratingScale.label) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.width(112.dp),
                )
            }
            IconButton(onClick = { actionsOpen = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.track_actions), modifier = Modifier.size(24.dp))
            }
        }
        if (track.isMissing) {
            Text(stringResource(R.string.track_missing), modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 3.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        if (reviewExpanded) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
                OutlinedTextField(review, { review = it.take(2000) }, Modifier.fillMaxWidth(), minLines = 2, maxLines = 6, label = { Text(stringResource(R.string.track_review)) })
                Text(stringResource(R.string.characters, review.length), style = MaterialTheme.typography.labelSmall)
            }
        }
        LyricsSection(
            lyrics = lyricsResult?.getOrNull(),
            expanded = lyricsExpanded,
            showAll = lyricsShowAll,
            timestamps = lyricsTimestamps,
            onToggleExpanded = { lyricsExpanded = !lyricsExpanded },
            onToggleShowAll = { lyricsShowAll = !lyricsShowAll },
            onToggleTimestamps = { lyricsTimestamps = !lyricsTimestamps },
            onFullscreen = { lyricsFullscreen = true },
        )
    }
    if (lyricsFullscreen) {
        LyricsFullscreenDialog(
            lyrics = lyricsResult?.getOrNull(),
            timestamps = lyricsTimestamps,
            onDismiss = { lyricsFullscreen = false },
        )
    }
    if (actionsOpen) {
        ModalBottomSheet(
            onDismissRequest = { actionsOpen = false },
            sheetState = sheetState,
        ) {
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Text(track.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp), maxLines = 3, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = { closeSheetThen(onViewCredits) }) { Text(stringResource(R.string.track_credits)) }
                TextButton(onClick = { closeSheetThen { reviewExpanded = true } }) { Text(stringResource(R.string.track_review)) }
                TextButton(onClick = { closeSheetThen { /* inline stars remain the score control */ } }) { Text(stringResource(R.string.track_score)) }
                TextButton(onClick = { closeSheetThen(onAnalyzeTrack) }) { Text(stringResource(R.string.track_analyze_quality)) }
                TextButton(
                    enabled = !track.recordingMbid.isNullOrBlank(),
                    onClick = { closeSheetThen {
                        track.recordingMbid?.let { mbid ->
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://musicbrainz.org/recording/$mbid"))
                            context.startActivity(intent)
                        }
                    } },
                ) { Text(stringResource(R.string.track_musicbrainz)) }
            }
        }
    }
}

@Composable
private fun LyricsSection(
    lyrics: Lyrics?,
    expanded: Boolean,
    showAll: Boolean,
    timestamps: Boolean,
    onToggleExpanded: () -> Unit,
    onToggleShowAll: () -> Unit,
    onToggleTimestamps: () -> Unit,
    onFullscreen: () -> Unit,
) {
    val context = LocalContext.current
    val lyricsLabel = stringResource(R.string.lyrics_title)
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.lyrics_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onToggleExpanded) { Text(stringResource(if (expanded) R.string.close else R.string.lyrics_show)) }
        }
        if (expanded) {
            if (lyrics == null) {
                Text(stringResource(R.string.lyrics_empty), style = MaterialTheme.typography.bodySmall)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.lyrics_timestamps), style = MaterialTheme.typography.bodySmall)
                    Switch(checked = timestamps, onCheckedChange = { onToggleTimestamps() })
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onFullscreen) { Text(stringResource(R.string.lyrics_fullscreen)) }
                    TextButton(onClick = {
                        val text = lyrics.toCopyText()
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText(lyricsLabel, text))
                    }) { Text(stringResource(R.string.lyrics_copy)) }
                }
                LyricsLines(lyrics, timestamps, showAll)
                if (lyrics.lineCount() > 4 && !showAll) TextButton(onClick = onToggleShowAll) { Text(stringResource(R.string.lyrics_show_all)) }
            }
        }
    }
}

@Composable
private fun LyricsLines(lyrics: Lyrics, timestamps: Boolean, showAll: Boolean) {
    val lines = lyrics.toLyricLines()
    val agents = lines.mapNotNull { it.agent }.distinct()
    val visible = if (showAll) lines else lines.take(4)
    SelectionContainer {
        Column(Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
            visible.forEach { line -> LyricLineText(line, timestamps, agents) }
        }
    }
}

@Composable
private fun LyricLineText(line: LyricLine, timestamps: Boolean, agents: List<String>) {
    val rendered = buildString {
        if (timestamps && line.startMs > 0) append("[").append(line.startMs / 60_000).append(":")
            .append(((line.startMs % 60_000) / 1_000).toString().padStart(2, '0')).append("] ")
        if (agents.size >= 2 && !line.agent.isNullOrBlank()) append("[").append(line.agent).append("] ")
        if (line.isBackground) append("(").append(line.text).append(")") else append(line.text)
        line.translation?.takeIf { it.isNotBlank() }?.let { append("\n").append(it) }
    }
    Text(
        rendered,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        color = if (line.isBackground) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LyricsFullscreenDialog(lyrics: Lyrics?, timestamps: Boolean, onDismiss: () -> Unit) {
    if (lyrics == null) return
    val lines = lyrics.toLyricLines()
    val agents = lines.mapNotNull { it.agent }.distinct()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(R.string.lyrics_title)) },
                        actions = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
                    )
                },
            ) { innerPadding ->
                SelectionContainer {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(innerPadding).navigationBarsPadding(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.Top,
                    ) {
                        items(lines.size, key = { index -> "${lines[index].startMs}-$index" }) { index -> LyricLineText(lines[index], timestamps, agents) }
                    }
                }
            }
        }
    }
}

private fun Lyrics.toLyricLines(): List<LyricLine> = when (this) {
    is Lyrics.Plain -> listOf(LyricLine(0L, text = text))
    is Lyrics.Timed -> lines
}

private fun Lyrics.lineCount(): Int = toLyricLines().size

private fun Lyrics.toCopyText(): String = toPlainText()

@Composable
private fun ReviewEditor(label: String, initial: String, max: Int, onChanged: (String) -> Unit) {
    var expanded by rememberSaveable(label) { mutableStateOf(initial.isNotBlank()) }
    var text by rememberSaveable(label) { mutableStateOf(initial) }
    LaunchedEffect(text) {
        delay(3_000)
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

private fun Double.format2(): String = "%.2f".format(java.util.Locale.getDefault(), this)

@dagger.hilt.android.lifecycle.HiltViewModel
class StandaloneViewModel @javax.inject.Inject constructor(
    val repository: com.youneko.rate.data.RateRepository,
) : androidx.lifecycle.ViewModel()

@Composable
private fun SettingsSectionHeader(title: String, icon: ImageVector) {
    Row(Modifier.fillMaxWidth().padding(top = YnDimens.space3), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(YnDimens.space2)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(YnDimens.minTouchTarget).padding(YnDimens.space2))
        Text(title, style = MaterialTheme.typography.titleLarge, )
    }
}

@Composable
fun SettingsScreen(onOpenExport: () -> Unit = {}, viewModel: ScoreSettingsViewModel = hiltViewModel()) {
    val mode by viewModel.scoreMode.collectAsStateWithLifecycle()
    val ratingScale by viewModel.ratingScale.collectAsStateWithLifecycle()
    val offlineOnly by viewModel.offlineOnly.collectAsStateWithLifecycle()
    val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()
    val reducedMotion by viewModel.reducedMotion.collectAsStateWithLifecycle()
    val discogsEnabled by viewModel.discogsEnabled.collectAsStateWithLifecycle()
    val discogsToken by viewModel.discogsToken.collectAsStateWithLifecycle()
    val lastFmEnabled by viewModel.lastFmEnabled.collectAsStateWithLifecycle()
    val lastFmApiKey by viewModel.lastFmApiKey.collectAsStateWithLifecycle()
    val geniusEnabled by viewModel.geniusEnabled.collectAsStateWithLifecycle()
    val geniusToken by viewModel.geniusToken.collectAsStateWithLifecycle()
    val showCreditSources by viewModel.showCreditSources.collectAsStateWithLifecycle()
    val creditSourceOrder by viewModel.creditSourceOrder.collectAsStateWithLifecycle()
    val activeCreditSources by viewModel.activeCreditSources.collectAsStateWithLifecycle()
    val creditsMergeMode by viewModel.creditsMergeMode.collectAsStateWithLifecycle()
    val tokenTestResult by viewModel.tokenTestResult.collectAsStateWithLifecycle()
    val applicationLanguage = AppCompatDelegate.getApplicationLocales().toLanguageTags()
    val appContext = LocalContext.current.applicationContext
    val settingsStore = remember(appContext) { SettingsDataStore(appContext) }
    val themeModeName by settingsStore.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM.name)
    val settingsScope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(YounekoSpacing.md), verticalArrangement = Arrangement.spacedBy(YounekoSpacing.sm)) {
        SettingsSectionHeader(stringResource(R.string.language), Icons.Default.Language)
        FilterChip(
            selected = applicationLanguage.isBlank(),
            onClick = { AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList()) },
            label = { Text(stringResource(R.string.language_system), maxLines = 3, overflow = TextOverflow.Ellipsis) },
        )
        FilterChip(
            selected = applicationLanguage == "en",
            onClick = { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en")) },
            label = { Text(stringResource(R.string.language_english), maxLines = 3, overflow = TextOverflow.Ellipsis) },
        )
        FilterChip(
            selected = applicationLanguage == "vi",
            onClick = { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("vi")) },
            label = { Text(stringResource(R.string.language_vietnamese), maxLines = 3, overflow = TextOverflow.Ellipsis) },
        )
        SettingsSectionHeader(stringResource(R.string.appearance), Icons.Default.Palette)
        Text(stringResource(R.string.theme_mode), style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(YounekoSpacing.xs)) {
            ThemeMode.entries.forEach { option ->
                FilterChip(
                    selected = themeModeName == option.name,
                    onClick = { settingsScope.launch { settingsStore.setThemeMode(option.name) } },
                    label = { Text(stringResource(when (option) { ThemeMode.SYSTEM -> R.string.theme_system; ThemeMode.LIGHT -> R.string.theme_light; ThemeMode.DARK -> R.string.theme_dark })) },
                )
            }
        }
        SettingsSectionHeader(stringResource(R.string.score_mode), Icons.Default.Star)
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
        Text(stringResource(R.string.rating_scale), style = MaterialTheme.typography.titleMedium)
        com.youneko.rate.domain.usecase.RatingScale.entries.forEach { scale ->
            FilterChip(selected = ratingScale == scale, onClick = { viewModel.setRatingScale(scale) }, label = { Text(scale.label) })
        }
        FilterChip(
            selected = dynamicColor,
            onClick = { viewModel.setDynamicColor(!dynamicColor) },
            label = { Text(stringResource(R.string.dynamic_color)) },
        )
        Text(stringResource(R.string.dynamic_color_body), style = MaterialTheme.typography.bodySmall)
        FilterChip(selected = reducedMotion, onClick = { viewModel.setReducedMotion(!reducedMotion) }, label = { Text(stringResource(R.string.reduce_motion)) })
        Text(stringResource(R.string.reduce_motion_body), style = MaterialTheme.typography.bodySmall)
        SettingsSectionHeader(stringResource(R.string.data_sources), Icons.Default.Settings)
        MediaScanRootManager()
        SettingsSectionHeader(stringResource(R.string.batch_cover_search_title), Icons.Default.Settings)
        Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.batch_cover_search_not_implemented)) }
        Text(stringResource(R.string.batch_cover_search_reason), style = MaterialTheme.typography.bodySmall)
        FilterChip(
            selected = offlineOnly,
            onClick = { viewModel.setOfflineOnly(!offlineOnly) },
            label = { Text(stringResource(R.string.offline_mode)) },
        )
        Text(stringResource(R.string.provider_discogs), style = MaterialTheme.typography.titleMedium)
        FilterChip(
            selected = discogsEnabled,
            onClick = { viewModel.setDiscogsEnabled(!discogsEnabled) },
            label = { Text(if (discogsEnabled) stringResource(R.string.provider_enabled) else stringResource(R.string.provider_need_key)) },
        )
        OutlinedTextField(
            value = discogsToken,
            onValueChange = viewModel::setDiscogsToken,
            label = { Text(stringResource(R.string.provider_api_key)) },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.credits_token_help_discogs), style = MaterialTheme.typography.bodySmall)
        Button(onClick = { viewModel.testDiscogsToken(discogsToken) }, enabled = discogsToken.isNotBlank()) { Text(stringResource(R.string.credits_test_token)) }
        tokenTestResult["discogs"]?.let { code -> Text(if (code == 200) stringResource(R.string.credits_token_valid) else stringResource(R.string.credits_token_invalid, code), color = if (code == 200) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
        Text(stringResource(R.string.provider_lastfm), style = MaterialTheme.typography.titleMedium)
        FilterChip(
            selected = lastFmEnabled,
            onClick = { viewModel.setLastFmEnabled(!lastFmEnabled) },
            label = { Text(if (lastFmEnabled) stringResource(R.string.provider_enabled) else stringResource(R.string.provider_need_key)) },
        )
        OutlinedTextField(
            value = lastFmApiKey,
            onValueChange = viewModel::setLastFmApiKey,
            label = { Text(stringResource(R.string.provider_api_key)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.provider_genius), style = MaterialTheme.typography.titleMedium)
        FilterChip(
            selected = geniusEnabled,
            onClick = { viewModel.setGeniusEnabled(!geniusEnabled) },
            label = { Text(if (geniusEnabled) stringResource(R.string.provider_enabled) else stringResource(R.string.provider_need_key)) },
        )
        OutlinedTextField(
            value = geniusToken,
            onValueChange = viewModel::setGeniusToken,
            label = { Text(stringResource(R.string.provider_api_key)) },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.credits_token_help_genius), style = MaterialTheme.typography.bodySmall)
        Button(onClick = { viewModel.testGeniusToken(geniusToken) }, enabled = geniusToken.isNotBlank()) { Text(stringResource(R.string.credits_test_token)) }
        tokenTestResult["genius"]?.let { code -> Text(if (code == 200) stringResource(R.string.credits_token_valid) else stringResource(R.string.credits_token_invalid, code), color = if (code == 200) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
        FilterChip(
            selected = showCreditSources,
            onClick = { viewModel.setShowCreditSources(!showCreditSources) },
            label = { Text(stringResource(R.string.credits_show_sources)) },
        )
        HorizontalDivider()
        SettingsSectionHeader(stringResource(R.string.credits_sources_settings), Icons.Default.Settings)
        Text(stringResource(R.string.credits_source_order), style = MaterialTheme.typography.bodySmall)
        creditSourceOrder.forEach { id ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = id in activeCreditSources || id == com.youneko.rate.data.credits.CreditSourceId.FILE_TAG,
                    onClick = { viewModel.setCreditSourceEnabled(id, id !in activeCreditSources) },
                    label = { Text(id.displayName, maxLines = 3, overflow = TextOverflow.Ellipsis) },
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { viewModel.moveCreditSource(id, -1) }) { Text(stringResource(R.string.credits_move_up)) }
                TextButton(onClick = { viewModel.moveCreditSource(id, 1) }) { Text(stringResource(R.string.credits_move_down)) }
            }
        }
        FilterChip(
            selected = creditsMergeMode,
            onClick = { viewModel.setCreditsMergeMode(!creditsMergeMode) },
            label = { Text(stringResource(R.string.credits_view_merged)) },
        )
        Card(shape = RoundedCornerShape(YounekoRadius.lg), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(YounekoSpacing.md), verticalArrangement = Arrangement.spacedBy(YounekoSpacing.xs)) {
                Text(stringResource(R.string.backup_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.backup_description), style = MaterialTheme.typography.bodySmall)
                Button(onClick = onOpenExport, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.backup_export)) }
                OutlinedButton(onClick = onOpenExport, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.backup_import)) }
                OutlinedButton(onClick = onOpenExport, modifier = Modifier.fillMaxWidth()) { Text("${stringResource(R.string.backup_export_csv)} & ${stringResource(R.string.backup_export_json)}") }
                OutlinedButton(onClick = onOpenExport, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.backup_auto)) }
            }
        }
        TextButton(onClick = viewModel::refreshMusicData) { Text(stringResource(R.string.refresh_music_data)) }
        TextButton(onClick = viewModel::reloadAllCovers) { Text(stringResource(R.string.reload_all_covers)) }
        TextButton(onClick = viewModel::clearMetadataCache) { Text(stringResource(R.string.metadata_cache_clear)) }
        Text(stringResource(R.string.settings_body), style = MaterialTheme.typography.bodyMedium)
    }
}
