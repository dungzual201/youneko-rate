package com.youneko.rate.ui.rate

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.youneko.rate.R
import com.youneko.rate.data.artwork.ArtworkStore
import com.youneko.rate.data.artwork.CoverPalette
import com.youneko.rate.data.importer.LocalAudioTagReader
import com.youneko.rate.data.local.entity.ListeningLogEntity
import com.youneko.rate.data.local.entity.TrackEntity
import com.youneko.rate.data.lyrics.Lyrics
import com.youneko.rate.data.lyrics.LyricLine
import com.youneko.rate.data.lyrics.toPlainText
import com.youneko.rate.data.musicbrainz.Resource
import com.youneko.rate.domain.usecase.RatingScale
import com.youneko.rate.ui.YnDimens
import com.youneko.rate.ui.adaptive.rememberYnPagePadding
import com.youneko.rate.ui.adaptive.rememberYnWindowInfo
import com.youneko.rate.ui.artwork.CoverArtImage
import com.youneko.rate.ui.components.YnPaletteSwatches
import com.youneko.rate.ui.components.YnSectionCard
import com.youneko.rate.ui.components.YnTopBar
import com.youneko.rate.ui.rememberReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AlbumDetailScreen(
    onBack: () -> Unit,
    onOpenArtist: (String) -> Unit = {},
    onAnalyzeTrack: (String) -> Unit = {},
    onSearchCover: (String) -> Unit = {},
    onEditAlbum: (String) -> Unit = {},
    viewModel: AlbumDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val window = rememberYnWindowInfo()
    val pagePadding = rememberYnPagePadding()
    val panePadding = if (window.isTwoPane) 16.dp else pagePadding
    val ratingScale by viewModel.ratingScale.collectAsStateWithLifecycle()
    val palette by viewModel.palette.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val listeningLogs by viewModel.listeningLogs.collectAsStateWithLifecycle()
    val reviewRevisions by viewModel.reviewRevisions.collectAsStateWithLifecycle()
    val refreshResult by viewModel.refreshResult.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val refreshMetadataError = stringResource(R.string.refresh_metadata_error)
    var showDelete by rememberSaveable { mutableStateOf(false) }
    var showManualScore by rememberSaveable { mutableStateOf(false) }
    var fileInfoTrack by rememberSaveable { mutableStateOf<String?>(null) }
    var showFullCover by rememberSaveable { mutableStateOf(false) }
    var menuExpanded by rememberSaveable { mutableStateOf(false) }
    var showListeningDialog by rememberSaveable { mutableStateOf(false) }
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
    val content = state as? AlbumDetailUiState.Content
    val album = content?.album

    BackHandler(onBack = onBack)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            YnTopBar(
                title = album?.album?.title ?: stringResource(R.string.album_detail),
                subtitle = album?.artist?.name,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.a11y_back))
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.edit_album))
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            album?.let { value ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.edit_album)) },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                    onClick = { menuExpanded = false; onEditAlbum(value.album.id) },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.refresh_metadata)) },
                                    onClick = { menuExpanded = false; viewModel.refreshMetadata() },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.reload_cover)) },
                                    onClick = { menuExpanded = false; viewModel.reloadCover() },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.choose_manual_cover)) },
                                    onClick = { menuExpanded = false; manualCoverPicker.launch(arrayOf("image/*")) },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_search_cover_online)) },
                                    onClick = { menuExpanded = false; onSearchCover(value.album.id) },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.search_on_google)) },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        context.openGoogleSearch(listOfNotNull(value.artist?.name, value.album.title).joinToString(" "))
                                    },
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.delete_album)) },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                    onClick = { menuExpanded = false; showDelete = true },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        when (val current = state) {
            AlbumDetailUiState.Loading -> Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                LinearProgressIndicator(Modifier.fillMaxWidth(0.4f))
            }
            AlbumDetailUiState.AlbumDeleted -> Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.confirm_delete_body))
            }
            is AlbumDetailUiState.Content -> {
                val value = current.album
                if (window.isTwoPane) {
                    Row(Modifier.fillMaxSize().padding(innerPadding)) {
                        LazyColumn(
                            modifier = Modifier.weight(0.44f).fillMaxHeight(),
                            contentPadding = PaddingValues(start = panePadding, end = 12.dp, top = 8.dp, bottom = 32.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            item { AlbumHero(value, palette, onOpenArtist, onOpenFullCover = { showFullCover = true }) }
                            item {
                                AlbumFacts(
                                    value = value,
                                    onSetManualScore = { showManualScore = true },
                                    onClearOverride = { viewModel.updateAlbum(value.album.copy(manualScoreOverride = null)) },
                                )
                            }
                            item { AlbumReviewSection(value, viewModel, reviewRevisions.size) }
                            item { AlbumTagsSection(tags, viewModel) }
                            item {
                                AlbumListeningSection(
                                    logs = listeningLogs,
                                    onAdd = { showListeningDialog = true },
                                )
                            }
                        }
                        LazyColumn(
                            modifier = Modifier.weight(0.56f).fillMaxHeight(),
                            contentPadding = PaddingValues(start = 12.dp, end = panePadding, top = 8.dp, bottom = 32.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            trackListSection(
                                value = value,
                                ratingScale = ratingScale,
                                onChanged = viewModel::updateTrack,
                                onAnalyzeTrack = onAnalyzeTrack,
                                onFileInfo = { fileInfoTrack = it },
                                onGoogle = { track ->
                                    context.openGoogleSearch(listOfNotNull(value.artist?.name, track.title).joinToString(" "))
                                },
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(innerPadding),
                        contentPadding = PaddingValues(start = pagePadding, end = pagePadding, top = 8.dp, bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item { AlbumHero(value, palette, onOpenArtist, onOpenFullCover = { showFullCover = true }) }
                        item {
                            AlbumFacts(
                                value = value,
                                onSetManualScore = { showManualScore = true },
                                onClearOverride = { viewModel.updateAlbum(value.album.copy(manualScoreOverride = null)) },
                            )
                        }
                        item { AlbumReviewSection(value, viewModel, reviewRevisions.size) }
                        item { AlbumTagsSection(tags, viewModel) }
                        item {
                            AlbumListeningSection(
                                logs = listeningLogs,
                                onAdd = { showListeningDialog = true },
                            )
                        }
                        trackListSection(
                            value = value,
                            ratingScale = ratingScale,
                            onChanged = viewModel::updateTrack,
                            onAnalyzeTrack = onAnalyzeTrack,
                            onFileInfo = { fileInfoTrack = it },
                            onGoogle = { track ->
                                context.openGoogleSearch(listOfNotNull(value.artist?.name, track.title).joinToString(" "))
                            },
                        )
                    }
                }
                if (refreshResult is Resource.Error) {
                    LaunchedEffect(refreshResult) {
                        snackbarHost.showSnackbar(refreshMetadataError)
                    }
                }
            }
        }
    }

    if (showManualScore && album != null) {
        ManualScoreDialog(
            current = album.album.manualScoreOverride,
            onDismiss = { showManualScore = false },
            onSave = { value -> viewModel.updateAlbum(album.album.copy(manualScoreOverride = value)); showManualScore = false },
            onClear = { viewModel.updateAlbum(album.album.copy(manualScoreOverride = null)); showManualScore = false },
        )
    }
    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text(stringResource(R.string.confirm_delete_title)) },
            text = { Text(stringResource(R.string.confirm_delete_body)) },
            confirmButton = {
                TextButton(onClick = { showDelete = false; viewModel.deleteAlbum() }) {
                    Text(stringResource(R.string.delete_album), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (showFullCover && album?.album?.coverUri != null) {
        CoverArtFullscreenDialog(
            model = album.album.coverUri,
            cacheVersion = album.album.coverUpdatedAt,
            palette = palette,
            onDismiss = { showFullCover = false },
        )
    }
    fileInfoTrack?.let { trackId ->
        val track = album?.tracks?.firstOrNull { it.id == trackId }
        AlertDialog(
            onDismissRequest = { fileInfoTrack = null },
            title = { Text(stringResource(R.string.track_file_info)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(track?.fileName.orEmpty())
                    Text(track?.sourceUri.orEmpty(), style = MaterialTheme.typography.bodySmall)
                    Text(
                        track?.durationMs?.let { formatDuration(it) } ?: stringResource(R.string.audio_analysis_not_applicable),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (track?.isMissing == true) {
                        Text(stringResource(R.string.track_missing), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { fileInfoTrack = null }) { Text(stringResource(R.string.close)) } },
        )
    }
    if (showListeningDialog) {
        ListeningDialog(
            onDismiss = { showListeningDialog = false },
            onSave = { note -> viewModel.logListening(note); showListeningDialog = false },
        )
    }
}

private fun LazyListScope.trackListSection(
    value: com.youneko.rate.data.LibraryAlbum,
    ratingScale: RatingScale,
    onChanged: (TrackEntity) -> Unit,
    onAnalyzeTrack: (String) -> Unit,
    onFileInfo: (String) -> Unit,
    onGoogle: (TrackEntity) -> Unit,
) {
    item {
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.tracklist), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Text(
                "${value.tracks.size}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (value.tracks.isEmpty()) {
        item { Text(stringResource(R.string.empty_tracks), modifier = Modifier.padding(vertical = 16.dp)) }
    } else {
        items(value.tracks, key = { it.id }) { track ->
            TrackDetailRow(
                track = track,
                ratingScale = ratingScale,
                onChanged = onChanged,
                onAnalyze = { track.sourceUri?.let(onAnalyzeTrack) },
                onFileInfo = { onFileInfo(track.id) },
                onGoogle = { onGoogle(track) },
            )
        }
    }
    item { Spacer(Modifier.height(16.dp)) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlbumHero(
    value: com.youneko.rate.data.LibraryAlbum,
    palette: CoverPalette?,
    onOpenArtist: (String) -> Unit,
    onOpenFullCover: () -> Unit,
) {
    val window = rememberYnWindowInfo()
    val surface = MaterialTheme.colorScheme.surface
    val dominant = palette?.dominant ?: MaterialTheme.colorScheme.primaryContainer
    val gradient = remember(dominant, surface) {
        Brush.verticalGradient(
            listOf(
                dominant.copy(alpha = if (surface.luminance() < 0.5f) 0.45f else 0.30f),
                surface.copy(alpha = 0.0f),
            ),
        )
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(gradient)
            .padding(top = 16.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val coverSize = if (window.isTwoPane) YnDimens.coverHeroWide else YnDimens.coverHero
        Box(
            Modifier
                .size(coverSize)
                .shadow(14.dp, RoundedCornerShape(26.dp))
                .clip(RoundedCornerShape(26.dp))
                .clickable(enabled = value.album.coverUri != null, onClick = onOpenFullCover),
        ) {
            CoverArtImage(
                value.album.coverUri,
                Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                placeholderSeed = value.album.id,
                placeholderLabel = value.album.title,
                cacheVersion = value.album.coverUpdatedAt,
            )
        }
        Text(
            value.album.title,
            style = MaterialTheme.typography.headlineMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(top = 20.dp, start = 8.dp, end = 8.dp),
        )
        TextButton(onClick = { value.artist?.id?.let(onOpenArtist) }, enabled = value.artist != null) {
            Text(value.artist?.name.orEmpty(), style = MaterialTheme.typography.titleMedium)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                value.score?.let { formatScore(it.effectiveScore) } ?: stringResource(R.string.not_rated),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFE7A93C), modifier = Modifier.size(26.dp))
        }
        Text(
            stringResource(R.string.rated_progress, value.score?.ratedCount ?: 0, value.tracks.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val total = value.tracks.size
        if (total > 0) {
            com.youneko.rate.ui.components.YnProgressBar(
                progress = (value.score?.ratedCount ?: 0).toFloat() / total.toFloat(),
                modifier = Modifier.padding(top = 8.dp).fillMaxWidth(0.56f),
            )
        }
    }
}

@Composable
private fun AlbumFacts(
    value: com.youneko.rate.data.LibraryAlbum,
    onSetManualScore: () -> Unit,
    onClearOverride: () -> Unit,
) {
    YnSectionCard {
        Text(stringResource(R.string.album_info), style = MaterialTheme.typography.titleMedium)
        Text(
            listOfNotNull(
                value.album.releaseYear?.toString(),
                value.album.albumType,
                value.album.genreTags.takeIf { it.isNotEmpty() }?.joinToString(" · "),
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.rating_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        value.score?.manualOverride?.let { override ->
            Text(
                "${stringResource(R.string.manual_score)}: ${formatScore(override)}★ (avg ${formatScore(value.score.average)})",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (value.score?.usedEqualWeightsFallback == true) {
            Text(stringResource(R.string.weighted_fallback), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onSetManualScore) { Text(stringResource(R.string.set_manual_score)) }
            if (value.album.manualScoreOverride != null) {
                TextButton(onClick = onClearOverride) { Text(stringResource(R.string.clear_override)) }
            }
        }
    }
}

@Composable
private fun AlbumReviewSection(
    value: com.youneko.rate.data.LibraryAlbum,
    viewModel: AlbumDetailViewModel,
    revisionCount: Int,
) {
    YnSectionCard(title = stringResource(R.string.album_review)) {
        ReviewEditor(
            initial = value.album.reviewText.orEmpty(),
            onCommit = { text ->
                viewModel.updateAlbum(value.album.copy(reviewText = text))
                viewModel.saveReviewRevision(text)
            },
        )
        if (revisionCount > 0) {
            Text(
                stringResource(R.string.review_history, revisionCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlbumTagsSection(
    tags: List<com.youneko.rate.data.local.entity.AlbumTagEntity>,
    viewModel: AlbumDetailViewModel,
) {
    var draft by rememberSaveable { mutableStateOf("") }
    YnSectionCard(title = stringResource(R.string.custom_tags)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(40) },
                modifier = Modifier.weight(1f),
                label = { Text(stringResource(R.string.add_tag)) },
                singleLine = true,
            )
            TextButton(
                onClick = { viewModel.addTag(draft); draft = "" },
                enabled = draft.isNotBlank() && tags.size < 10,
            ) { Text(stringResource(R.string.add)) }
        }
        if (tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                tags.forEach { tag ->
                    AssistChip(
                        onClick = { viewModel.removeTag(tag.id) },
                        label = { Text(tag.name) },
                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumListeningSection(logs: List<ListeningLogEntity>, onAdd: () -> Unit) {
    YnSectionCard(
        title = stringResource(R.string.listening_log),
        action = { TextButton(onClick = onAdd) { Text(stringResource(R.string.log_listening)) } },
    ) {
        if (logs.isEmpty()) {
            Text(
                stringResource(R.string.listening_count, 0),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(stringResource(R.string.listening_count, logs.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            logs.take(3).forEach { entry ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Text("•", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(entry.listenedAt, style = MaterialTheme.typography.labelMedium)
                        entry.note?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewEditor(initial: String, onCommit: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    val latestCommit by rememberUpdatedState(onCommit)
    val latestText by rememberUpdatedState(text)
    val latestInitial by rememberUpdatedState(initial)
    LaunchedEffect(text) {
        if (text != initial) {
            delay(1_200)
            latestCommit(text)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            if (latestText != latestInitial) latestCommit(latestText)
        }
    }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it.take(4_000) },
        modifier = Modifier.fillMaxWidth(),
        minLines = 3,
        maxLines = 8,
        placeholder = { Text(stringResource(R.string.album_review)) },
    )
    Text(
        stringResource(R.string.characters, text.length),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ListeningDialog(onDismiss: () -> Unit, onSave: (String?) -> Unit) {
    var note by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.listening_log)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(LocalDate.now().toString(), style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(160) },
                    label = { Text(stringResource(R.string.listening_note)) },
                    singleLine = false,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(note.trim().ifBlank { null }) }) { Text(stringResource(R.string.log_listening)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ManualScoreDialog(
    current: Double?,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit,
    onClear: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(current?.toString().orEmpty()) }
    var showError by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.set_manual_score)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; showError = false },
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    singleLine = true,
                    isError = showError,
                    supportingText = if (showError) ({ Text(stringResource(R.string.manual_score_invalid)) }) else null,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsed = text.replace(',', '.').toDoubleOrNull()
                if (parsed == null || parsed !in 0.5..5.0) showError = true else onSave(parsed)
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (current != null) TextButton(onClick = onClear) { Text(stringResource(R.string.clear_override)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrackDetailRow(
    track: TrackEntity,
    ratingScale: RatingScale,
    onChanged: (TrackEntity) -> Unit,
    onAnalyze: () -> Unit,
    onFileInfo: () -> Unit,
    onGoogle: () -> Unit,
) {
    val context = LocalContext.current
    var expanded by rememberSaveable(track.id) { mutableStateOf(false) }
    var review by rememberSaveable(track.id) { mutableStateOf(track.reviewText.orEmpty()) }
    var lyricsOpen by rememberSaveable(track.id) { mutableStateOf(false) }
    var lyricsShowAll by rememberSaveable(track.id) { mutableStateOf(false) }
    var lyricsTimestamps by rememberSaveable(track.id) { mutableStateOf(false) }
    var lyricsFullscreen by rememberSaveable(track.id) { mutableStateOf(false) }
    val latestTrack by rememberUpdatedState(track)
    val lyricsResult by produceState<Result<Lyrics?>?>(initialValue = null, key1 = track.sourceUri, key2 = lyricsOpen) {
        if (!lyricsOpen) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                track.sourceUri?.let { source ->
                    LocalAudioTagReader(context, ArtworkStore(context))
                        .readAll(listOf(Uri.parse(source))).tags.firstOrNull()?.lyrics
                }
            }
        }
    }
    LaunchedEffect(review) {
        if (review != latestTrack.reviewText.orEmpty()) {
            delay(800)
            onChanged(latestTrack.copy(reviewText = review))
        }
    }
    DisposableEffect(track.id) {
        onDispose {
            val current = latestTrack
            if (review != current.reviewText.orEmpty()) onChanged(current.copy(reviewText = review))
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(YnDimens.radiusMd),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    track.trackNumber?.toString() ?: "•",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(22.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(track.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(
                            track.durationMs?.let(::formatDuration),
                            track.isMissing.takeIf { it }?.let { stringResource(R.string.track_missing) },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (track.isMissing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                    )
                }
            }
            if (ratingScale == RatingScale.FIVE_STARS) {
                StarRatingBar(
                    value = track.stars,
                    onValueChange = { onChanged(track.copy(stars = it)) },
                    onClear = { onChanged(track.copy(stars = null)) },
                    step = 0.5,
                    modifier = Modifier.padding(start = 22.dp),
                    starSize = YnDimens.ratingStarCompact,
                    showValue = true,
                )
            } else {
                OutlinedTextField(
                    value = ratingScale.fromStars(track.stars)?.format2().orEmpty(),
                    onValueChange = { value ->
                        onChanged(track.copy(stars = ratingScale.toStars(value.replace(',', '.').toDoubleOrNull()?.coerceIn(0.0, ratingScale.max.toDouble()))))
                    },
                    label = { Text(ratingScale.displayLabel()) },
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.padding(start = 22.dp).width(132.dp),
                )
            }
            if (expanded) {
                Column(Modifier.padding(start = 22.dp, top = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = review,
                        onValueChange = { review = it.take(2_000) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 6,
                        label = { Text(stringResource(R.string.track_review)) },
                    )
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AssistChip(onClick = onAnalyze, label = { Text(stringResource(R.string.track_analyze_quality)) })
                        AssistChip(onClick = onFileInfo, label = { Text(stringResource(R.string.track_file_info)) })
                        AssistChip(onClick = onGoogle, label = { Text(stringResource(R.string.search_on_google)) })
                        if (!track.recordingMbid.isNullOrBlank()) {
                            AssistChip(
                                onClick = {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://musicbrainz.org/recording/${track.recordingMbid}")))
                                },
                                label = { Text(stringResource(R.string.track_musicbrainz)) },
                            )
                        }
                        AssistChip(
                            onClick = { lyricsOpen = !lyricsOpen },
                            label = { Text(stringResource(if (lyricsOpen) R.string.close else R.string.lyrics_show)) },
                        )
                    }
                    if (lyricsOpen) {
                        LyricsPanel(
                            lyrics = lyricsResult?.getOrNull(),
                            loading = lyricsResult == null,
                            timestamps = lyricsTimestamps,
                            showAll = lyricsShowAll,
                            onToggleTimestamps = { lyricsTimestamps = !lyricsTimestamps },
                            onToggleShowAll = { lyricsShowAll = !lyricsShowAll },
                            onFullscreen = { lyricsFullscreen = true },
                        )
                    }
                }
            }
        }
    }
    if (lyricsFullscreen) {
        LyricsFullscreenDialog(
            lyrics = lyricsResult?.getOrNull(),
            timestamps = lyricsTimestamps,
            onDismiss = { lyricsFullscreen = false },
        )
    }
}

@Composable
private fun LyricsPanel(
    lyrics: Lyrics?,
    loading: Boolean,
    timestamps: Boolean,
    showAll: Boolean,
    onToggleTimestamps: () -> Unit,
    onToggleShowAll: () -> Unit,
    onFullscreen: () -> Unit,
) {
    val context = LocalContext.current
    val label = stringResource(R.string.lyrics_title)
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.lyrics_title), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                if (lyrics != null) {
                    TextButton(onClick = onFullscreen) { Text(stringResource(R.string.lyrics_fullscreen)) }
                    TextButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText(label, lyrics.toCopyText()))
                    }) { Text(stringResource(R.string.lyrics_copy)) }
                }
            }
            when {
                loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                lyrics == null -> Text(stringResource(R.string.lyrics_empty), style = MaterialTheme.typography.bodySmall)
                else -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.lyrics_timestamps), style = MaterialTheme.typography.labelSmall)
                        Switch(checked = timestamps, onCheckedChange = { onToggleTimestamps() })
                    }
                    LyricsLines(lyrics, timestamps, showAll)
                    if (lyrics.lineCount() > 4 && !showAll) {
                        TextButton(onClick = onToggleShowAll) { Text(stringResource(R.string.lyrics_show_all)) }
                    }
                }
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
        Column(Modifier.fillMaxWidth().heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
            visible.forEach { line -> LyricLineText(line, timestamps, agents) }
        }
    }
}

@Composable
private fun LyricLineText(line: LyricLine, timestamps: Boolean, agents: List<String>) {
    val rendered = buildString {
        if (timestamps && line.startMs > 0) {
            append("[").append(line.startMs / 60_000).append(":")
                .append(((line.startMs % 60_000) / 1_000).toString().padStart(2, '0')).append("] ")
        }
        if (agents.size >= 2 && !line.agent.isNullOrBlank()) append("[").append(line.agent).append("] ")
        if (line.isBackground) append("(").append(line.text).append(")") else append(line.text)
        line.translation?.takeIf { it.isNotBlank() }?.let { append("\n").append(it) }
    }
    Text(
        rendered,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        color = if (line.isBackground) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun LyricsFullscreenDialog(lyrics: Lyrics?, timestamps: Boolean, onDismiss: () -> Unit) {
    if (lyrics == null) return
    val lines = lyrics.toLyricLines()
    val agents = lines.mapNotNull { it.agent }.distinct()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Scaffold(
                topBar = {
                    YnTopBar(
                        title = stringResource(R.string.lyrics_title),
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                            }
                        },
                    )
                },
            ) { innerPadding ->
                SelectionContainer {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(innerPadding).navigationBarsPadding(),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    ) {
                        items(lines.size, key = { index -> "${lines[index].startMs}-$index" }) { index ->
                            LyricLineText(lines[index], timestamps, agents)
                        }
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
internal fun CoverArtFullscreenDialog(
    model: Any,
    cacheVersion: Long? = null,
    palette: CoverPalette?,
    onDismiss: () -> Unit,
    onCopied: (String) -> Unit = {},
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var swipeDistance by remember { mutableFloatStateOf(0f) }
    val transformState = androidx.compose.foundation.gestures.rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        if (scale > 1f) offset += panChange
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(color = Color.Black.copy(alpha = 0.96f), modifier = Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(scale) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { _, amount ->
                                if (scale <= 1f) swipeDistance += amount
                            },
                            onDragEnd = {
                                if (scale <= 1f && swipeDistance > 120f) onDismiss()
                                swipeDistance = 0f
                            },
                            onDragCancel = { swipeDistance = 0f },
                        )
                    },
            ) {
                CoverArtImage(
                    model = model,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 72.dp)
                        .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y }
                        .transformable(transformState),
                    contentScale = ContentScale.Fit,
                    cacheVersion = cacheVersion,
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
            }
        }
    }
}

private fun formatScore(value: Double): String = String.format(Locale.getDefault(), "%.2f", value)

private fun formatDuration(durationMs: Long): String =
    "%d:%02d".format(Locale.getDefault(), durationMs / 60_000L, (durationMs / 1_000L) % 60L)

private fun Double.format2(): String = String.format(Locale.getDefault(), "%.2f", this)

private fun Context.openGoogleSearch(query: String) {
    val cleanQuery = query.trim().replace(Regex("\\s+"), " ")
    if (cleanQuery.isBlank()) return
    val encoded = URLEncoder.encode(cleanQuery, StandardCharsets.UTF_8.name())
    val uri = Uri.parse("https://www.google.com/search?q=$encoded")
    val openedInCustomTab = runCatching { CustomTabsIntent.Builder().build().launchUrl(this, uri) }.isSuccess
    if (!openedInCustomTab) {
        val intent = Intent(Intent.ACTION_VIEW, uri)
        if (this !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }
}
