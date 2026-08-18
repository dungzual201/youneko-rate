package com.youneko.rate.ui.musicbrainz

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.youneko.rate.R
import com.youneko.rate.data.musicbrainz.ImportConflictChoice
import com.youneko.rate.data.musicbrainz.MusicBrainzImportProgress
import com.youneko.rate.data.musicbrainz.MusicBrainzImportStage
import com.youneko.rate.data.musicbrainz.MusicBrainzPreview
import com.youneko.rate.data.musicbrainz.MusicBrainzSearchItem
import com.youneko.rate.data.musicbrainz.NetworkError
import com.youneko.rate.data.musicbrainz.Resource

@Composable
fun MusicBrainzSearchPanel(
    viewModel: MusicBrainzSearchViewModel = hiltViewModel(),
    onImported: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val results = viewModel.pagedResults.collectAsLazyPagingItems()
    val preview by viewModel.preview.collectAsStateWithLifecycle()
    val query by viewModel.queryText.collectAsStateWithLifecycle()
    val pendingImport by viewModel.pendingImport.collectAsStateWithLifecycle()
    val importResult by viewModel.importResult.collectAsStateWithLifecycle()
    val importProgress by viewModel.importProgress.collectAsStateWithLifecycle()
    LaunchedEffect(importResult) {
        val result = importResult
        if (result is Resource.Success) onImported(result.value)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier.fillMaxWidth()) {
        Text(stringResource(R.string.online_results), style = MaterialTheme.typography.titleMedium)
        if (query.isBlank()) {
            Box(
                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Pets, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.online_enter_query))
                }
            }
        } else {
            when (val refresh = results.loadState.refresh) {
                LoadState.Loading -> Box(
                    modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
                is LoadState.Error -> Box(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.network_error), color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = results::retry) { Text(stringResource(R.string.retry)) }
                    }
                }
                is LoadState.NotLoading -> if (results.itemCount == 0) {
                    Text(stringResource(R.string.no_results_for_query, query))
                } else {
                    Text(pluralStringResource(R.plurals.online_result_count, results.itemCount, results.itemCount))
                }
            }
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(results.itemCount) { index ->
                    results[index]?.let { MusicBrainzResultCard(it, viewModel::openPreview) }
                }
                if (results.loadState.append is LoadState.Loading) {
                    item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                }
                if (results.loadState.append is LoadState.Error) {
                    item { TextButton(onClick = results::retry) { Text(stringResource(R.string.retry)) } }
                }
            }
        }
        preview?.let { value ->
            when (value) {
                Resource.Loading -> CircularProgressIndicator()
                is Resource.Error -> Text(networkErrorLabel(value.kind), color = MaterialTheme.colorScheme.error)
                is Resource.Success -> MusicBrainzPreviewDialog(
                    value.value,
                    onDismiss = viewModel::closePreview,
                    onImport = viewModel::requestImport,
                    onSelectRelease = viewModel::selectRelease,
                )
            }
        }
        importResult?.let { result ->
            if (result is Resource.Error) Text(result.message ?: stringResource(R.string.network_error), color = MaterialTheme.colorScheme.error)
        }
    }
    pendingImport?.let { previewValue ->
        ImportConflictDialog(previewValue, viewModel::resolveImport)
    }
    importProgress?.let { progress ->
        ImportProgressDialog(progress, viewModel::cancelImport)
    }
}

@Composable
private fun ImportProgressDialog(progress: MusicBrainzImportProgress, onCancel: () -> Unit) {
    val label = when (progress.stage) {
        MusicBrainzImportStage.RELEASE -> stringResource(R.string.import_stage_release)
        MusicBrainzImportStage.COVER -> stringResource(R.string.import_stage_cover)
        MusicBrainzImportStage.SAVING -> stringResource(R.string.import_stage_saving, progress.current, progress.total)
    }
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.import_save)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Text(label)
                if (progress.total > 0) {
                    LinearProgressIndicator(
                        progress = { (progress.current.toFloat() / progress.total).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(stringResource(R.string.import_progress_items, progress.current, progress.total))
                }
            }
        },
        confirmButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun MusicBrainzResultCard(item: MusicBrainzSearchItem, onClick: (MusicBrainzSearchItem) -> Unit) {
    androidx.compose.material3.Card(onClick = { onClick(item) }, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium)
                Text(item.artist, style = MaterialTheme.typography.bodyMedium)
                item.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                item.year?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
            }
            AssistChip(onClick = { onClick(item) }, label = { Text("MB") })
        }
    }
}

@Composable
private fun MusicBrainzPreviewDialog(
    preview: MusicBrainzPreview,
    onDismiss: () -> Unit,
    onImport: (MusicBrainzPreview) -> Unit,
    onSelectRelease: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(preview.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(preview.artist)
                Text(listOfNotNull(preview.year, preview.country, preview.label).joinToString(" · "))
                if (preview.releaseOptions.size > 1) {
                    Text(stringResource(R.string.online_choose_release), style = MaterialTheme.typography.titleSmall)
                    preview.releaseOptions.forEach { option ->
                        TextButton(onClick = { onSelectRelease(option.id) }) {
                            Text(listOfNotNull(option.title, option.year, option.country).joinToString(" · "))
                        }
                    }
                }
                HorizontalDivider()
                preview.tracks.forEach { track -> Text("${track.discNumber}.${track.trackNumber} ${track.title}") }
                Text(stringResource(R.string.online_preview_read_only), style = MaterialTheme.typography.labelSmall)
            }
        },
        confirmButton = { Button(onClick = { onImport(preview) }) { Text(stringResource(R.string.import_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun ImportConflictDialog(preview: MusicBrainzPreview, onChoice: (ImportConflictChoice) -> Unit) {
    AlertDialog(
        onDismissRequest = { onChoice(ImportConflictChoice.CANCEL) },
        title = { Text(stringResource(R.string.online_import_conflict_title)) },
        text = {
            Column {
                Text(stringResource(R.string.online_import_conflict_body, preview.title))
                TextButton(onClick = { onChoice(ImportConflictChoice.MERGE) }) { Text(stringResource(R.string.merge)) }
                TextButton(onClick = { onChoice(ImportConflictChoice.CREATE_NEW) }) { Text(stringResource(R.string.create_new)) }
            }
        },
        confirmButton = { TextButton(onClick = { onChoice(ImportConflictChoice.CANCEL) }) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun networkErrorLabel(kind: NetworkError): String = when (kind) {
    NetworkError.OFFLINE -> stringResource(R.string.network_offline)
    NetworkError.NO_CONNECTION -> stringResource(R.string.network_no_connection)
    NetworkError.TIMEOUT -> stringResource(R.string.network_timeout)
    NetworkError.RATE_LIMITED -> stringResource(R.string.network_rate_limited)
    NetworkError.SERVER_ERROR -> stringResource(R.string.network_server_error)
    NetworkError.BAD_REQUEST -> stringResource(R.string.network_bad_request)
    NetworkError.PARSE_ERROR -> stringResource(R.string.network_parse_error)
    NetworkError.NO_RESULTS -> stringResource(R.string.no_results)
    NetworkError.UNKNOWN -> stringResource(R.string.network_error)
}
