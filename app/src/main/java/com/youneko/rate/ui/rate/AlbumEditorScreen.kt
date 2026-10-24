package com.youneko.rate.ui.rate

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.youneko.rate.R
import com.youneko.rate.ui.YnDimens
import com.youneko.rate.ui.adaptive.rememberYnPagePadding
import com.youneko.rate.ui.artwork.CoverArtImage
import com.youneko.rate.ui.components.YnSectionCard
import com.youneko.rate.ui.components.YnTopBar
import kotlin.math.abs

private val albumTypes = listOf(
    "ALBUM" to R.string.album,
    "EP" to R.string.ep,
    "SINGLE" to R.string.single,
    "COMPILATION" to R.string.compilation,
)

@Composable
fun AlbumEditorScreen(
    onSaved: (String) -> Unit,
    onCancel: () -> Unit,
    viewModel: AlbumEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pagePadding = rememberYnPagePadding(maxContentWidth = YnDimens.readingMaxWidth)
    var quickCount by rememberSaveable { mutableStateOf("") }
    var pendingRemoveIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    BackHandler(onBack = onCancel)
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AlbumEditorEvent.OpenAlbum -> onSaved(event.albumId)
            }
        }
    }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        viewModel.setCoverUri(uri?.toString())
    }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        topBar = {
            YnTopBar(
                title = stringResource(if (state.isEditing) R.string.edit_album else R.string.add_album),
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.save() }) { Text(stringResource(R.string.save)) }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).imePadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = pagePadding,
                end = pagePadding,
                top = 8.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                YnSectionCard(title = stringResource(R.string.cover_art)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(
                            Modifier
                                .size(112.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (state.coverUri != null) {
                                CoverArtImage(
                                    model = state.coverUri,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                                IconButton(
                                    onClick = { viewModel.setCoverUri(null) },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.remove_cover), modifier = Modifier.size(16.dp))
                                }
                            } else {
                                Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { coverPicker.launch("image/*") }) {
                                Text(stringResource(if (state.coverUri == null) R.string.choose_cover else R.string.change_cover))
                            }
                            Text(
                                stringResource(R.string.cover_picker_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item {
                YnSectionCard(title = stringResource(R.string.album_info)) {
                    OutlinedTextField(
                        value = state.title,
                        onValueChange = viewModel::setTitle,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.album_title)) },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = state.artist,
                        onValueChange = viewModel::setArtist,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.artist_name)) },
                        singleLine = true,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = state.year,
                            onValueChange = viewModel::setYear,
                            modifier = Modifier.weight(1f),
                            label = { Text(stringResource(R.string.release_year)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = state.listenedDate,
                            onValueChange = viewModel::setListenedDate,
                            modifier = Modifier.weight(1.4f),
                            label = { Text(stringResource(R.string.listened_date)) },
                            singleLine = true,
                        )
                    }
                    Text(stringResource(R.string.album_type), style = MaterialTheme.typography.labelLarge)
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        albumTypes.forEach { (raw, label) ->
                            FilterChip(
                                selected = state.albumType == raw,
                                onClick = { viewModel.setType(raw) },
                                label = { Text(stringResource(label)) },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = state.genres,
                        onValueChange = viewModel::setGenres,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.genres)) },
                        singleLine = true,
                    )
                }
            }
            item {
                YnSectionCard(title = stringResource(R.string.tracklist)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { viewModel.addTrack() }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.add_track), maxLines = 1)
                        }
                        TextButton(onClick = { quickCount = "5" }) {
                            Text(stringResource(R.string.quick_add_tracks), maxLines = 1)
                        }
                    }
                    state.tracks.forEachIndexed { index, value ->
                        EditorTrackRow(
                            index = index,
                            value = value,
                            canRemove = state.tracks.size > 1,
                            onValue = { viewModel.setTrack(index, it) },
                            onMove = { from, to -> viewModel.moveTrack(from, to) },
                            onRemove = { pendingRemoveIndex = index },
                        )
                    }
                    if (state.tracks.isNotEmpty()) {
                        HorizontalDivider(Modifier.padding(top = 4.dp))
                        Text(
                            stringResource(R.string.drag_to_reorder),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            val errorRes = state.errorRes
            if (errorRes != null) {
                item {
                    Text(stringResource(errorRes), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }
            item {
                Button(onClick = { viewModel.save() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }

    if (quickCount.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { quickCount = "" },
            title = { Text(stringResource(R.string.quick_add_tracks)) },
            text = {
                OutlinedTextField(
                    quickCount,
                    { quickCount = it.filter(Char::isDigit).take(2) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.addQuick(quickCount.toIntOrNull() ?: 1); quickCount = "" }) {
                    Text(stringResource(R.string.add_track))
                }
            },
            dismissButton = { TextButton(onClick = { quickCount = "" }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    pendingRemoveIndex?.let { index ->
        AlertDialog(
            onDismissRequest = { pendingRemoveIndex = null },
            title = { Text(stringResource(R.string.remove_track)) },
            text = { Text(stringResource(R.string.remove_track_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeTrack(index)
                    pendingRemoveIndex = null
                }) { Text(stringResource(R.string.remove_track)) }
            },
            dismissButton = { TextButton(onClick = { pendingRemoveIndex = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun EditorTrackRow(
    index: Int,
    value: String,
    canRemove: Boolean,
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
                            val target = index + if (dragDistance > 0) 1 else -1
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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Default.DragHandle,
            contentDescription = stringResource(R.string.drag_to_reorder),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValue,
            modifier = Modifier.weight(1f),
            label = { Text(stringResource(R.string.track_name)) },
            placeholder = { Text("${index + 1}") },
            singleLine = true,
        )
        IconButton(onClick = onRemove, enabled = canRemove) {
            Icon(
                Icons.Default.Delete,
                contentDescription = stringResource(R.string.remove_track),
                tint = if (canRemove) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}
