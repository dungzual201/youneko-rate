package com.youneko.rate.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.youneko.rate.R
import com.youneko.rate.data.LibraryAlbum
import com.youneko.rate.data.local.entity.TrackEntity
import com.youneko.rate.ui.YounekoRateTheme
import com.youneko.rate.ui.YnDimens
import com.youneko.rate.ui.YnMotion
import com.youneko.rate.ui.artwork.CoverArtImage
import com.youneko.rate.ui.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YnTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    brandMark: Boolean = false,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.surface,
        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
    ),
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (brandMark) {
                    Icon(
                        Icons.Default.Pets,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    subtitle?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        navigationIcon = { navigationIcon?.invoke() },
        actions = actions,
        colors = colors,
        windowInsets = windowInsets,
        modifier = modifier,
    )
}

@Composable
fun YnPaletteSwatches(
    colors: List<Color>,
    onReleased: (Color) -> Unit,
    modifier: Modifier = Modifier,
    onCopied: (String) -> Unit = {},
) {
    val clipboard = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        colors.distinctBy { it.toArgb() }.take(6).forEach { color ->
            var pressed by remember { mutableStateOf(false) }
            val hex = remember(color) { "#%06X".format(0xFFFFFF and color.toArgb()) }
            val a11y = stringResource(R.string.a11y_palette_swatch, hex)
            Box(
                Modifier.size(YnDimens.minTouchTarget).semantics { contentDescription = a11y },
                contentAlignment = Alignment.Center,
            ) {
                if (pressed) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.85f),
                        modifier = Modifier.align(Alignment.TopCenter).offset(y = (-56).dp),
                    ) {
                        Text(
                            hex,
                            style = MaterialTheme.typography.labelLarge,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
                Box(
                    Modifier.size(44.dp)
                        .shadow(4.dp, CircleShape)
                        .clip(CircleShape)
                        .background(color)
                        .border(1.5.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                        .pointerInput(color) {
                            awaitPointerEventScope {
                                while (true) {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    down.consume()
                                    pressed = true
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val copyJob = scope.launch {
                                        delay(800L)
                                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(hex))
                                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                        onCopied(hex)
                                    }
                                    waitForUpOrCancellation()
                                    copyJob.cancel()
                                    pressed = false
                                    onReleased(color)
                                }
                            }
                        },
                )
            }
        }
    }
}

@Composable
fun YnRatingBadge(score: Double?, modifier: Modifier = Modifier, large: Boolean = false) {
    val label = score?.let { formatScore(it) } ?: stringResource(R.string.not_rated)
    val container = when {
        score == null -> MaterialTheme.colorScheme.surfaceContainerHighest
        score < 2.5 -> MaterialTheme.colorScheme.errorContainer
        score < 3.5 -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val content = when {
        score == null -> MaterialTheme.colorScheme.onSurfaceVariant
        score < 2.5 -> MaterialTheme.colorScheme.onErrorContainer
        score < 3.5 -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }
    Surface(
        modifier = modifier.semantics { contentDescription = label },
        shape = if (large) RoundedCornerShape(YnDimens.radiusSm) else CircleShape,
        color = container,
        contentColor = content,
    ) {
        Text(
            label,
            modifier = Modifier.padding(
                horizontal = if (large) YnDimens.space3 else YnDimens.space2,
                vertical = YnDimens.space1,
            ),
            style = if (large) MaterialTheme.typography.titleLarge else MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun ScorePill(score: Double, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.62f),
        contentColor = Color.White,
    ) {
        Row(
            Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD36E), modifier = Modifier.size(14.dp))
            Text(formatScore(score), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun YnAlbumCard(
    item: LibraryAlbum,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .semantics { role = Role.Button }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(YnDimens.cardCorner),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, top = 8.dp, end = 8.dp)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(14.dp)),
            ) {
                CoverArtImage(
                    item.album.coverUri,
                    Modifier.fillMaxSize(),
                    placeholderSeed = item.album.id,
                    placeholderLabel = item.album.title,
                    cacheVersion = item.album.coverUpdatedAt,
                )
                item.score?.effectiveScore?.let { score ->
                    ScorePill(score, Modifier.align(Alignment.BottomEnd).padding(8.dp))
                }
            }
            Column(
                Modifier.padding(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    item.album.title,
                    style = MaterialTheme.typography.titleSmall,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    item.artist?.name.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val total = item.tracks.size
                if (total > 0) {
                    val rated = item.score?.ratedCount ?: 0
                    YnProgressBar(
                        progress = rated.toFloat() / total.toFloat(),
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        height = 3.dp,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun YnAlbumRow(
    item: LibraryAlbum,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics { role = Role.Button }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(YnDimens.radiusMd),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CoverArtImage(
                item.album.coverUri,
                Modifier.size(YnDimens.albumRowCover).clip(RoundedCornerShape(12.dp)),
                placeholderSeed = item.album.id,
                placeholderLabel = item.album.title,
                cacheVersion = item.album.coverUpdatedAt,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    item.album.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    item.artist?.name.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val score = item.score
                    Text(
                        score?.let { "${formatScore(it.effectiveScore)} ★" } ?: stringResource(R.string.not_rated),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (score == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                    )
                    if (score != null) {
                        Text(
                            "· ${score.ratedCount}/${item.tracks.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (trailing != null) {
                trailing()
            } else {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
fun YnProgressBar(progress: Float, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 4.dp) {
    Box(
        modifier
            .height(height)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
fun YnTrackRow(track: TrackEntity, score: Double? = track.stars, onClick: () -> Unit = {}, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(YnDimens.radiusSm),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            Modifier.padding(horizontal = YnDimens.space3, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(YnDimens.space2),
        ) {
            Text(
                track.trackNumber?.toString() ?: "•",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(22.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(track.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    track.durationMs?.let(::formatDuration) ?: stringResource(R.string.audio_analysis_not_applicable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (track.isMissing) {
                Icon(
                    Icons.Default.WarningAmber,
                    contentDescription = stringResource(R.string.track_missing),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
            }
            YnRatingBadge(score)
        }
    }
}

@Composable
fun YnSectionCard(
    title: String? = null,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(YnDimens.cardCorner),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (title != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleMedium)
                        subtitle?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    action?.invoke()
                }
            }
            content()
        }
    }
}

@Composable
fun YnStatTile(
    value: String,
    label: String,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(YnDimens.cardCorner),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onPrimaryContainer,
                ) { icon() }
            }
            Text(value, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun YnStatCard(value: String, label: String, icon: @Composable () -> Unit, modifier: Modifier = Modifier) {
    YnStatTile(value, label, icon, modifier)
}

@Composable
fun YnSectionHeader(title: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (actionLabel != null && onAction != null) TextButton(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
fun YnEmptyState(
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(104.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_cat_chibi_sit),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(68.dp),
            )
        }
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Button(onClick = onAction, modifier = Modifier.padding(top = 4.dp)) { Text(actionLabel) }
        }
    }
}

@Composable
fun YnErrorState(reason: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
        Text(reason, style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

@Composable
fun YnSkeleton(modifier: Modifier = Modifier, lines: Int = 2) {
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    Column(modifier, verticalArrangement = Arrangement.spacedBy(YnDimens.space2)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(YnDimens.cardCorner)).background(base))
        repeat(lines) { index ->
            Box(
                Modifier
                    .fillMaxWidth(if (index == lines - 1) 0.62f else 0.9f)
                    .height(YnDimens.space2)
                    .clip(RoundedCornerShape(YnDimens.radiusXs))
                    .background(base),
            )
        }
    }
}

enum class YnButtonState { Idle, Loading, Success }

@Composable
fun YnPrimaryButton(label: String, state: YnButtonState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val reducedMotion = rememberReducedMotion()
    val pressScale by animateFloatAsState(if (pressed) 0.96f else 1f, YnMotion.springOrSnap(reducedMotion), label = "primaryButtonScale")
    LaunchedEffect(state) {
        if (state == YnButtonState.Success) haptic.performHapticFeedback(HapticFeedbackType.Confirm)
    }
    Button(
        onClick = onClick,
        enabled = state != YnButtonState.Loading,
        interactionSource = interactionSource,
        modifier = modifier.graphicsLayer { scaleX = pressScale; scaleY = pressScale }.semantics { contentDescription = label },
    ) {
        when (state) {
            YnButtonState.Idle -> Text(label)
            YnButtonState.Loading -> {
                CircularProgressIndicator(Modifier.size(YnDimens.space3), strokeWidth = 2.dp)
                Spacer(Modifier.width(YnDimens.space2))
                Text(stringResource(R.string.loading))
            }
            YnButtonState.Success -> {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(YnDimens.space2))
                Text(stringResource(R.string.saved))
            }
        }
    }
}

@Composable
fun YnFilterChipRow(options: List<Pair<String, Boolean>>, onToggle: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(YnDimens.space2)) {
        options.forEachIndexed { index, (label, selected) ->
            FilterChip(selected = selected, onClick = { onToggle(index) }, label = { Text(label) })
        }
    }
}

@Composable
fun YnConfirmDialog(title: String, body: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun YnMetricRow(label: String, value: String, onHelp: () -> Unit, warning: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = YnDimens.space1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        IconButton(onClick = onHelp, modifier = Modifier.size(YnDimens.minTouchTarget)) {
            Icon(
                Icons.Default.HelpOutline,
                contentDescription = stringResource(R.string.audio_analysis_help),
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            color = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun formatScore(value: Double): String = String.format(Locale.getDefault(), "%.1f", value)

private fun formatDuration(durationMs: Long): String =
    "%d:%02d".format(Locale.getDefault(), durationMs / 60_000L, (durationMs / 1_000L) % 60L)

@Preview(name = "English light", locale = "en")
@Composable
private fun YnComponentsLightPreview() {
    YounekoRateTheme { PreviewComponents() }
}

@Preview(name = "Vietnamese dark", locale = "vi", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun YnComponentsDarkPreview() {
    YounekoRateTheme(darkTheme = true) { PreviewComponents() }
}

@Composable
private fun PreviewComponents() {
    Column(Modifier.padding(YnDimens.space4), verticalArrangement = Arrangement.spacedBy(YnDimens.space3)) {
        YnRatingBadge(4.5, large = true)
        YnStatTile("229", stringResource(R.string.library), { Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(20.dp)) })
        YnSkeleton(lines = 2)
        YnMetricRow(stringResource(R.string.audio_analysis_cutoff), "24.0 kHz", {}, warning = false)
        HorizontalDivider()
        Box(Modifier.fillMaxWidth().height(80.dp))
    }
}
