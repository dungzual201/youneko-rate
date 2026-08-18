package com.youneko.rate.ui.musicbrainz

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.youneko.rate.R
import com.youneko.rate.data.musicbrainz.MusicBrainzPreview
import com.youneko.rate.data.musicbrainz.MusicBrainzSearchItem
import com.youneko.rate.data.musicbrainz.NetworkError
import com.youneko.rate.data.musicbrainz.Resource

@Composable
fun MusicBrainzSearchPanel(viewModel: MusicBrainzSearchViewModel = hiltViewModel()) {
    val results = viewModel.pagedResults.collectAsLazyPagingItems()
    val preview by viewModel.preview.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.online_results), style = MaterialTheme.typography.titleMedium)
        when (val refresh = results.loadState.refresh) {
            LoadState.Loading -> CircularProgressIndicator()
            is LoadState.Error -> Text(stringResource(R.string.network_error), color = MaterialTheme.colorScheme.error)
            is LoadState.NotLoading -> if (results.itemCount == 0) Text(stringResource(R.string.no_results))
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(results.itemCount) { index ->
                results[index]?.let { MusicBrainzResultCard(it, viewModel::openPreview) }
            }
            if (results.loadState.append is LoadState.Loading) {
                item { CircularProgressIndicator() }
            }
        }
        preview?.let { value ->
            when (value) {
                Resource.Loading -> CircularProgressIndicator()
                is Resource.Error -> Text(networkErrorLabel(value.kind), color = MaterialTheme.colorScheme.error)
                is Resource.Success -> MusicBrainzPreviewDialog(value.value, viewModel::closePreview)
            }
        }
    }
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
private fun MusicBrainzPreviewDialog(preview: MusicBrainzPreview, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(preview.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(preview.artist)
                Text(listOfNotNull(preview.year, preview.country, preview.label).joinToString(" · "))
                HorizontalDivider()
                preview.tracks.forEach { track ->
                    Text("${track.discNumber}.${track.trackNumber} ${track.title}")
                }
                Text(stringResource(R.string.online_preview_read_only), style = MaterialTheme.typography.labelSmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun networkErrorLabel(kind: NetworkError): String = when (kind) {
    NetworkError.OFFLINE -> stringResource(R.string.network_offline)
    NetworkError.NO_NETWORK -> stringResource(R.string.network_no_connection)
    NetworkError.TIMEOUT -> stringResource(R.string.network_timeout)
    NetworkError.RATE_LIMITED -> stringResource(R.string.network_rate_limited)
    NetworkError.NO_RESULTS -> stringResource(R.string.no_results)
    else -> stringResource(R.string.network_error)
}
