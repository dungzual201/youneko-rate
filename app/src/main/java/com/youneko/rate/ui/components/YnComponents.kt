package com.youneko.rate.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.youneko.rate.R
import com.youneko.rate.data.LibraryAlbum
import com.youneko.rate.data.local.entity.AlbumEntity
import com.youneko.rate.data.local.entity.ArtistEntity
import com.youneko.rate.data.local.entity.TrackEntity
import com.youneko.rate.ui.YounekoRateTheme
import com.youneko.rate.ui.YnDimens
import com.youneko.rate.ui.artwork.CoverArtImage

@Composable
fun YnRatingBadge(score: Double?, modifier: Modifier = Modifier, large: Boolean = false) {
    val label = score?.let { "%.1f".format(java.util.Locale.getDefault(), it) } ?: stringResource(R.string.not_rated)
    val color = when {
        score == null -> MaterialTheme.colorScheme.surfaceVariant
        score < 2.5 -> MaterialTheme.colorScheme.errorContainer
        score < 3.5 -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    Surface(
        modifier = modifier.semantics { contentDescription = label },
        shape = if (large) RoundedCornerShape(YnDimens.radiusSm) else CircleShape,
        color = color,
        tonalElevation = YnDimens.cardElevation,
    ) {
        Text(label, modifier = Modifier.padding(horizontal = if (large) YnDimens.space3 else YnDimens.space2, vertical = YnDimens.space1), style = if (large) MaterialTheme.typography.titleLarge else MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun YnAlbumCard(item: LibraryAlbum, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(YnDimens.radiusMd), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(YnDimens.space3), verticalArrangement = Arrangement.spacedBy(YnDimens.space2)) {
            Box {
                CoverArtImage(item.album.coverUri, Modifier.fillMaxWidth(), placeholderSeed = item.album.id, placeholderLabel = item.album.title)
                YnRatingBadge(item.score?.effectiveScore, Modifier.align(Alignment.BottomEnd).padding(YnDimens.space2))
            }
            Text(item.album.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(item.artist?.name.orEmpty(), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun YnTrackRow(track: TrackEntity, score: Double? = track.stars, onClick: () -> Unit = {}, modifier: Modifier = Modifier) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(YnDimens.radiusSm), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.padding(YnDimens.space3), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(YnDimens.space2)) {
            Text(track.trackNumber?.toString() ?: "•", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(YnDimens.space6))
            Column(Modifier.weight(1f)) {
                Text(track.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(track.durationMs?.let(::formatDuration) ?: stringResource(R.string.audio_analysis_not_applicable), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (track.isMissing) Icon(Icons.Default.WarningAmber, contentDescription = stringResource(R.string.track_missing), tint = MaterialTheme.colorScheme.error)
            YnRatingBadge(score, large = false)
        }
    }
}

@Composable
fun YnStatCard(value: String, label: String, icon: @Composable () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(YnDimens.radiusMd), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Row(Modifier.fillMaxWidth().padding(YnDimens.space4), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(YnDimens.space3)) {
            Box(Modifier.size(YnDimens.space7), contentAlignment = Alignment.Center) { icon() }
            Column { Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
fun YnEmptyState(title: String, body: String, actionLabel: String? = null, onAction: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(YnDimens.space7), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(YnDimens.space3)) {
        Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(YnDimens.coverSmall))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (actionLabel != null && onAction != null) Button(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
fun YnErrorState(reason: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(YnDimens.space7), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(YnDimens.space3)) {
        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(YnDimens.coverSmall))
        Text(reason, style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

@Composable
fun YnSkeleton(modifier: Modifier = Modifier, lines: Int = 2) {
    val base = MaterialTheme.colorScheme.surfaceVariant
    Column(modifier, verticalArrangement = Arrangement.spacedBy(YnDimens.space2)) {
        Box(Modifier.fillMaxWidth().height(YnDimens.coverSmall).clip(RoundedCornerShape(YnDimens.radiusSm)).background(base))
        repeat(lines) { index -> Box(Modifier.fillMaxWidth(if (index == lines - 1) 0.62f else 0.9f).height(YnDimens.space2).clip(RoundedCornerShape(YnDimens.radiusXs)).background(base)) }
    }
}

@Composable
fun YnSectionHeader(title: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (actionLabel != null && onAction != null) TextButton(onClick = onAction) { Text(actionLabel) }
    }
}

enum class YnButtonState { Idle, Loading, Success }

@Composable
fun YnPrimaryButton(label: String, state: YnButtonState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(state) { if (state == YnButtonState.Success) haptic.performHapticFeedback(HapticFeedbackType.Confirm) }
    Button(onClick = onClick, enabled = state != YnButtonState.Loading, modifier = modifier.semantics { contentDescription = label }) {
        when (state) {
            YnButtonState.Idle -> Text(label)
            YnButtonState.Loading -> { CircularProgressIndicator(Modifier.size(YnDimens.space3), strokeWidth = 2.dp); Spacer(Modifier.width(YnDimens.space2)); Text(stringResource(R.string.loading)) }
            YnButtonState.Success -> { Icon(Icons.Default.Check, contentDescription = null); Spacer(Modifier.width(YnDimens.space2)); Text(stringResource(R.string.saved)) }
        }
    }
}

@Composable
fun YnFilterChipRow(options: List<Pair<String, Boolean>>, onToggle: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(YnDimens.space2)) {
        options.forEachIndexed { index, (label, selected) -> FilterChip(selected = selected, onClick = { onToggle(index) }, label = { Text(label) }) }
    }
}

@Composable
fun YnConfirmDialog(title: String, body: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(body) }, confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.confirm)) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable
fun YnMetricRow(label: String, value: String, onHelp: () -> Unit, warning: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = YnDimens.space1), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        IconButton(onClick = onHelp, modifier = Modifier.size(YnDimens.minTouchTarget)) { Icon(Icons.Default.HelpOutline, contentDescription = stringResource(R.string.audio_analysis_help)) }
        Text(value, color = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface, fontWeight = if (warning) FontWeight.Bold else FontWeight.Normal)
    }
}

private fun formatDuration(durationMs: Long): String = "%d:%02d".format(java.util.Locale.getDefault(), durationMs / 60_000L, (durationMs / 1_000L) % 60L)

@Preview(name = "English light", locale = "en")
@Composable
private fun YnComponentsLightPreview() { YounekoRateTheme { PreviewComponents() } }

@Preview(name = "Vietnamese dark", locale = "vi", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun YnComponentsDarkPreview() { YounekoRateTheme(darkTheme = true) { PreviewComponents() } }

@Composable
private fun PreviewComponents() {
    Column(Modifier.padding(YnDimens.space4), verticalArrangement = Arrangement.spacedBy(YnDimens.space3)) {
        YnRatingBadge(4.5, large = true)
        YnStatCard("229", stringResource(R.string.library), { Icon(Icons.Default.Star, contentDescription = null) })
        YnSkeleton()
        YnMetricRow(stringResource(R.string.audio_analysis_cutoff), "24.0 kHz", {}, warning = false)
    }
}
