package com.youneko.rate.ui.rate

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.youneko.rate.R
import com.youneko.rate.data.LibraryAlbum
import com.youneko.rate.ui.YnDimens
import com.youneko.rate.ui.adaptive.rememberYnPagePadding
import com.youneko.rate.ui.components.YnAlbumRow
import com.youneko.rate.ui.components.YnEmptyState
import com.youneko.rate.ui.components.YnSkeleton
import com.youneko.rate.ui.components.YnTopBar
import kotlinx.coroutines.launch

private enum class RateFilter { ALL, UNRATED, RATED, TOP }

@Composable
fun RateScreen(
    onAddAlbum: () -> Unit,
    onImportTags: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenSettings: () -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showStandalone by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf(RateFilter.ALL) }
    val pagePadding = rememberYnPagePadding()

    val rateItems = remember(state.albums, filter) {
        when (filter) {
            RateFilter.ALL -> state.albums
            RateFilter.UNRATED -> state.albums.filter { album ->
                album.tracks.isNotEmpty() && (album.score?.ratedCount ?: 0) < album.tracks.size
            }
            RateFilter.RATED -> state.albums.filter { album ->
                album.tracks.isNotEmpty() && album.score?.ratedCount == album.tracks.size
            }
            RateFilter.TOP -> state.albums.sortedByDescending { it.score?.effectiveScore ?: -1.0 }
        }
    }

    Column(Modifier.fillMaxSize()) {
        YnTopBar(
            title = stringResource(R.string.rate),
            subtitle = pluralStringResource(R.plurals.library_count, rateItems.size, rateItems.size),
            actions = {
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                }
            },
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = pagePadding, end = pagePadding, top = 8.dp, bottom = YnDimens.listBottomPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickActionTile(
                        title = stringResource(R.string.add_album),
                        icon = Icons.Default.Add,
                        container = MaterialTheme.colorScheme.primaryContainer,
                        content = MaterialTheme.colorScheme.onPrimaryContainer,
                        onClick = onAddAlbum,
                        modifier = Modifier.weight(1f),
                    )
                    QuickActionTile(
                        title = stringResource(R.string.add_standalone_short),
                        icon = Icons.Default.MusicNote,
                        container = MaterialTheme.colorScheme.surfaceContainerHigh,
                        content = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = { showStandalone = true },
                        modifier = Modifier.weight(1f),
                    )
                    QuickActionTile(
                        title = stringResource(R.string.import_music),
                        icon = Icons.Default.FolderOpen,
                        container = MaterialTheme.colorScheme.surfaceContainerHigh,
                        content = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = onImportTags,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(selected = filter == RateFilter.ALL, onClick = { filter = RateFilter.ALL }, label = { Text(stringResource(R.string.rate_filter_all)) })
                        FilterChip(selected = filter == RateFilter.UNRATED, onClick = { filter = RateFilter.UNRATED }, label = { Text(stringResource(R.string.rate_filter_unrated)) })
                        FilterChip(selected = filter == RateFilter.RATED, onClick = { filter = RateFilter.RATED }, label = { Text(stringResource(R.string.rate_filter_rated)) })
                        FilterChip(selected = filter == RateFilter.TOP, onClick = { filter = RateFilter.TOP }, label = { Text(stringResource(R.string.rate_filter_top)) })
                    }
                    Text(
                        stringResource(R.string.rate_helper),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            when {
                state.loading -> items(6) {
                    YnSkeleton(Modifier.fillMaxWidth(), lines = 1)
                }
                rateItems.isEmpty() -> item {
                    YnEmptyState(
                        title = stringResource(if (state.albums.isEmpty()) R.string.library_empty_title else R.string.no_results),
                        body = stringResource(if (state.albums.isEmpty()) R.string.rate_empty_body else R.string.no_results_hint),
                        actionLabel = stringResource(if (state.albums.isEmpty()) R.string.add_album else R.string.clear_filters),
                        onAction = if (state.albums.isEmpty()) onAddAlbum else {
                            { filter = RateFilter.ALL }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                else -> items(rateItems, key = { "rate-${it.album.id}" }) { album ->
                    RateAlbumRow(album, onOpenAlbum)
                }
            }
        }
    }

    if (showStandalone) {
        StandaloneDialog(onDismiss = { showStandalone = false })
    }
}

@Composable
private fun RateAlbumRow(album: LibraryAlbum, onOpenAlbum: (String) -> Unit) {
    YnAlbumRow(
        item = album,
        onClick = { onOpenAlbum(album.album.id) },
    )
}

@Composable
private fun QuickActionTile(
    title: String,
    icon: ImageVector,
    container: Color,
    content: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.heightIn(min = 96.dp),
        shape = RoundedCornerShape(YnDimens.cardCorner),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
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
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.standalone_title)) }, singleLine = true)
                OutlinedTextField(artist, { artist = it }, label = { Text(stringResource(R.string.standalone_artist)) }, singleLine = true)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    title.isBlank() -> error = titleRequired
                    artist.isBlank() -> error = artistRequired
                    else -> scope.launch {
                        repository.saveStandalone(title, artist, null)
                        onDismiss()
                    }
                }
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@dagger.hilt.android.lifecycle.HiltViewModel
class StandaloneViewModel @javax.inject.Inject constructor(
    val repository: com.youneko.rate.data.RateRepository,
) : androidx.lifecycle.ViewModel()
