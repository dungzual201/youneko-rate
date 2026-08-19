package com.youneko.rate.ui.importer

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.youneko.rate.R
import com.youneko.rate.data.importer.ImportGroup
import com.youneko.rate.data.importer.stableKey
import com.youneko.rate.ui.importer.ImportEvent
import androidx.compose.runtime.saveable.rememberSaveable

@Composable
fun ImportScreen(
    onDone: () -> Unit,
    viewModel: ImportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                ImportEvent.Success -> { viewModel.dismissDialog(); onDone() }
                ImportEvent.Cancelled -> viewModel.dismissDialog()
            }
        }
    }
    DisposableEffect(Unit) { onDispose { viewModel.resetImportState() } }
    BackHandler(enabled = state.dialogVisible || state.isReading) { viewModel.dismissDialog() }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.readSelections(uris, false)
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.readSelection(uri, true)
    }
    var manualCoverGroup by remember { mutableStateOf<ImportGroup?>(null) }
    var manualUrlGroup by remember { mutableStateOf<ImportGroup?>(null) }
    var manualUrl by rememberSaveable { mutableStateOf("") }
    val manualCoverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) manualCoverGroup?.let { viewModel.setCover(it, uri.toString(), "Manual") }
        manualCoverGroup = null
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.import_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.import_body), style = MaterialTheme.typography.bodyMedium)
        if (state.groups.isNotEmpty()) Text(stringResource(R.string.import_preview_note), style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { filePicker.launch(arrayOf("audio/*")) }) { Text(stringResource(R.string.import_file)) }
            OutlinedButton(onClick = { folderPicker.launch(null) }) { Text(stringResource(R.string.import_folder)) }
        }
        if (state.groups.isNotEmpty()) {
            Text(stringResource(R.string.import_preview), style = MaterialTheme.typography.titleLarge)
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.groups, key = { it.stableKey() }) { group ->
                    ImportGroupCard(group, state, viewModel, onChooseCover = { viewModel.loadCoverCandidates(group) })
                }
            }
            state.workId?.let {
                if (state.workState == androidx.work.WorkInfo.State.RUNNING || state.workState == androidx.work.WorkInfo.State.ENQUEUED) {
                    // Progress is rendered in a modal dialog below, not at the bottom of this list.
                } else if (state.workState == androidx.work.WorkInfo.State.SUCCEEDED) {
                    Text(stringResource(R.string.import_done, state.importedCount), color = MaterialTheme.colorScheme.primary)
                    Button(onClick = onDone) { Text(stringResource(R.string.import_open_library)) }
                }
            } ?: Button(onClick = viewModel::enqueueImport) { Text(stringResource(R.string.import_save)) }
        } else if (state.groups.isEmpty() && !state.isReading) {
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.import_no_files))
            Spacer(Modifier.weight(1f))
        }
        if (state.failures.isNotEmpty()) {
            Text(stringResource(R.string.import_failures), style = MaterialTheme.typography.titleMedium)
            state.failures.take(8).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
    val pickerEntry = state.coverCandidates.entries.firstOrNull()
    val pickerGroup = pickerEntry?.key?.let { key -> state.groups.firstOrNull { it.stableKey() == key } }
    if (pickerEntry != null && pickerGroup != null) {
        AlertDialog(
            onDismissRequest = { viewModel.closeCoverPicker(pickerGroup) },
            title = { Text("Chọn ảnh bìa khác") },
            text = {
                if (pickerEntry.value.isEmpty()) {
                    Text("Không tìm thấy ảnh bìa từ iTunes, Deezer hoặc Cover Art Archive.")
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(pickerEntry.value) { candidate ->
                            Column(
                                Modifier.padding(4.dp).clickable { viewModel.chooseCoverCandidate(pickerGroup, candidate) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                AsyncImage(
                                    model = candidate.url,
                                    contentDescription = candidate.sourceProvider,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Text(candidate.sourceProvider, style = MaterialTheme.typography.labelSmall, maxLines = 2)
                                Text("${candidate.widthHint ?: "—"} px · ${candidate.matchScore?.let { "%.2f".format(it) } ?: "—"}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { manualCoverGroup = pickerGroup; manualCoverPicker.launch("image/*") }) { Text("Chọn ảnh từ thiết bị") }
                    TextButton(onClick = { manualUrlGroup = pickerGroup; manualUrl = "" }) { Text("Nhập URL") }
                    TextButton(onClick = { viewModel.closeCoverPicker(pickerGroup) }) { Text(stringResource(R.string.cancel)) }
                }
            },
        )
    }
    if (state.isReading) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDialog,
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false),
            title = { Text(stringResource(R.string.import_title)) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.import_reading))
                }
            },
            confirmButton = {},
        )
    }
    val workRunning = state.workState == androidx.work.WorkInfo.State.RUNNING || state.workState == androidx.work.WorkInfo.State.ENQUEUED
    if (workRunning) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDialog,
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false),
            title = { Text(stringResource(R.string.import_save)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.import_stage_saving, state.progressCurrent, state.progressTotal))
                    if (state.progressTotal > 0) {
                        LinearProgressIndicator(
                            progress = { (state.progressCurrent.toFloat() / state.progressTotal).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(stringResource(R.string.import_processing, state.progressCurrent, state.progressTotal))
                    }
                }
            },
            confirmButton = { TextButton(onClick = viewModel::cancelImport) { Text(stringResource(R.string.cancel)) } },
        )
        }
    manualUrlGroup?.let { group ->
        AlertDialog(
            onDismissRequest = { manualUrlGroup = null },
            title = { Text("Nhập URL ảnh bìa") },
            text = { OutlinedTextField(manualUrl, { manualUrl = it }, label = { Text("URL") }, modifier = Modifier.fillMaxWidth(), maxLines = 2) },
            confirmButton = { TextButton(onClick = { if (manualUrl.isNotBlank()) viewModel.setCover(group, manualUrl.trim(), "Manual"); manualUrlGroup = null }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { manualUrlGroup = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun ImportGroupCard(
    group: ImportGroup,
    state: ImportUiState,
    viewModel: ImportViewModel,
    onChooseCover: () -> Unit,
) {
    val selection = state.selections[group.stableKey()] ?: return
    var title by remember(group.stableKey(), selection.title) { mutableStateOf(selection.title) }
    var artist by remember(group.stableKey(), selection.artist) { mutableStateOf(selection.artist) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(title, { title = it; viewModel.updateGroup(group, it, artist) }, label = { Text(stringResource(R.string.import_album_title)) }, singleLine = true)
            OutlinedTextField(artist, { artist = it; viewModel.updateGroup(group, title, it) }, label = { Text(stringResource(R.string.artist_name)) }, singleLine = true)
            Row {
                Text(stringResource(R.string.import_merge_existing), Modifier.weight(1f))
                Switch(checked = selection.mergeIfExisting, onCheckedChange = { viewModel.setMerge(group, it) })
            }
            OutlinedButton(onClick = onChooseCover) { Text(stringResource(R.string.import_choose_cover)) }
            group.tracks.forEach { track ->
                Row {
                    Checkbox(checked = track.uri in state.selectedUris, onCheckedChange = { viewModel.toggleTrack(track.uri) })
                    Text("${track.discNumber ?: 1}.${track.trackNumber ?: "?"} ${track.title}")
                    Spacer(Modifier.width(4.dp))
                    Text(track.durationMs?.let { "${it / 1000}s" }.orEmpty(), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
