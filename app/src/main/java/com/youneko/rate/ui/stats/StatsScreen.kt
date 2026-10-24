package com.youneko.rate.ui.stats

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.youneko.rate.BuildConfig
import com.youneko.rate.R
import com.youneko.rate.data.local.dao.StatsCountRow
import com.youneko.rate.data.local.dao.StatsDao
import com.youneko.rate.data.local.dao.StatsValueRow
import com.youneko.rate.ui.YnDimens
import com.youneko.rate.ui.YnMotion
import com.youneko.rate.ui.YounekoLightColorScheme
import com.youneko.rate.ui.YounekoTypography
import com.youneko.rate.ui.adaptive.rememberYnPagePadding
import com.youneko.rate.ui.adaptive.rememberYnWindowInfo
import com.youneko.rate.ui.components.YnEmptyState
import com.youneko.rate.ui.components.YnSectionCard
import com.youneko.rate.ui.components.YnStatTile
import com.youneko.rate.ui.components.YnTopBar
import com.youneko.rate.ui.rememberReducedMotion
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.text.NumberFormat
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class ShareStatsLabels(
    val title: String,
    val subtitle: String,
    val appName: String,
    val ratedAlbums: String,
    val tracksRated: String,
    val averageScore: String,
    val tracksAnalyzed: String,
    val topAlbum: String,
)

internal data class ShareStatsImage(val file: File, val bitmap: Bitmap)

data class StatsUiState(
    val loading: Boolean = true,
    val ratedAlbums: Int = 0,
    val tracksRated: Int = 0,
    val tracksAnalyzed: Int = 0,
    val averageScore: Double? = null,
    val topRatedAlbum: String? = null,
    val histogram: List<StatsCountRow> = emptyList(),
    val topArtists: List<StatsCountRow> = emptyList(),
    val topLabels: List<StatsCountRow> = emptyList(),
    val topCredits: List<StatsCountRow> = emptyList(),
    val quality: List<StatsCountRow> = emptyList(),
    val averageByYear: List<StatsValueRow> = emptyList(),
    val averageByMonth: List<StatsValueRow> = emptyList(),
)

@HiltViewModel
class StatsViewModel @Inject constructor(private val statsDao: StatsDao) : ViewModel() {
    private val _state = MutableStateFlow(StatsUiState())
    val state: StateFlow<StatsUiState> = _state.asStateFlow()

    fun reload() = viewModelScope.launch {
        _state.value = StatsUiState(loading = true)
        _state.value = StatsUiState(
            loading = false,
            ratedAlbums = statsDao.ratedAlbumCount(),
            tracksRated = statsDao.ratedTrackCount(),
            tracksAnalyzed = statsDao.analyzedTrackCount(),
            averageScore = statsDao.averageTrackScore().value,
            topRatedAlbum = statsDao.topRatedAlbum(),
            histogram = statsDao.scoreHistogram(),
            topArtists = statsDao.topArtists(),
            topLabels = statsDao.topLabels(),
            topCredits = statsDao.topProducersAndMixers(),
            quality = statsDao.qualityDistribution(),
            averageByYear = statsDao.averageByYear(),
            averageByMonth = statsDao.averageByMonth(),
        )
    }
}

@Composable
fun StatsScreen(onOpenSettings: () -> Unit = {}, viewModel: StatsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val window = rememberYnWindowInfo()
    val pagePadding = rememberYnPagePadding()
    val shareFailureFallback = stringResource(R.string.stats_share_failed)
    val saveFailure = stringResource(R.string.stats_share_save_failed)
    val labels = ShareStatsLabels(
        title = stringResource(R.string.stats_share_title),
        subtitle = stringResource(R.string.app_name),
        appName = stringResource(R.string.app_name),
        ratedAlbums = stringResource(R.string.stats_rated_albums),
        tracksRated = stringResource(R.string.share_tracks_rated),
        averageScore = stringResource(R.string.share_average_score),
        tracksAnalyzed = stringResource(R.string.share_tracks_analyzed),
        topAlbum = stringResource(R.string.stats_top_album),
    )
    val parentCompositionContext = rememberCompositionContext()
    val lifecycleOwner = LocalLifecycleOwner.current
    val renderHost = LocalView.current.rootView as? ViewGroup
    val savedStateOwner = lifecycleOwner as SavedStateRegistryOwner
    var shareLoading by remember { mutableStateOf(false) }
    var shareImage by remember { mutableStateOf<ShareStatsImage?>(null) }
    var shareError by remember { mutableStateOf<String?>(null) }

    LifecycleResumeEffect(Unit) {
        viewModel.reload()
        onPauseOrDispose { }
    }

    Column(Modifier.fillMaxSize()) {
        YnTopBar(
            title = stringResource(R.string.stats),
            actions = {
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                }
            },
        )
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.ratedAlbums == 0 && state.tracksRated == 0 && state.quality.isEmpty() -> {
                YnEmptyState(
                    title = stringResource(R.string.stats_empty_title),
                    body = stringResource(R.string.stats_empty_body),
                    modifier = Modifier.fillMaxSize(),
                )
            }
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = pagePadding, end = pagePadding, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Overview(state, window.isTwoPane) }
                state.topRatedAlbum?.let { top ->
                    item { TopAlbumCard(top) }
                }
                item {
                    Button(
                        onClick = {
                            shareError = null
                            shareLoading = true
                            shareImage = null
                            scope.launch(Dispatchers.Default) {
                                runCatching {
                                    renderStatsImage(
                                        context = context,
                                        state = state,
                                        labels = labels,
                                        colors = YounekoLightColorScheme,
                                        parentCompositionContext = parentCompositionContext,
                                        lifecycleOwner = lifecycleOwner,
                                        savedStateOwner = savedStateOwner,
                                        renderHost = renderHost,
                                    )
                                }.onSuccess { shareImage = it }
                                    .onFailure { shareError = it.message ?: shareFailureFallback }
                                shareLoading = false
                            }
                        },
                        enabled = !shareLoading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (shareLoading) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.BarChart, contentDescription = null)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.stats_share_image))
                    }
                }
                item { HistogramCard(state.histogram) }
                if (window.isTwoPane) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ValueCard(stringResource(R.string.stats_average_by_year), state.averageByYear, Modifier.weight(1f))
                            ValueCard(stringResource(R.string.stats_average_by_month), state.averageByMonth, Modifier.weight(1f))
                        }
                    }
                } else {
                    item { ValueCard(stringResource(R.string.stats_average_by_year), state.averageByYear) }
                    item { ValueCard(stringResource(R.string.stats_average_by_month), state.averageByMonth) }
                }
                item { QualityCard(state.quality) }
                if (window.isTwoPane) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            RankingCard(stringResource(R.string.stats_top_artists), state.topArtists, Modifier.weight(1f))
                            RankingCard(stringResource(R.string.stats_top_labels), state.topLabels, Modifier.weight(1f))
                        }
                    }
                } else {
                    item { RankingCard(stringResource(R.string.stats_top_artists), state.topArtists) }
                    item { RankingCard(stringResource(R.string.stats_top_labels), state.topLabels) }
                }
                item { RankingCard(stringResource(R.string.stats_top_credits), state.topCredits) }
                item {
                    Text(
                        stringResource(R.string.stats_local_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }
        }
    }

    shareError?.let { message ->
        AlertDialog(
            onDismissRequest = { shareError = null },
            title = { Text(stringResource(R.string.stats_share_failed)) },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { shareError = null }) { Text(stringResource(R.string.close)) } },
        )
    }
    if (shareLoading || shareImage != null) {
        SharePreviewDialog(
            file = shareImage?.file,
            bitmap = shareImage?.bitmap,
            loading = shareLoading,
            onDismiss = {
                shareImage = null
                shareLoading = false
            },
            onShare = {
                shareImage?.file?.let { shareStatsFile(context, it) }
                shareImage = null
            },
            onSave = {
                val file = shareImage?.file
                shareImage = null
                if (file != null && !saveStatsFile(context, file)) {
                    shareError = saveFailure
                }
            },
        )
    }
}

@Composable
private fun Overview(state: StatsUiState, wide: Boolean) {
    val average = state.averageScore?.let { formatDecimal(it, 2) + "★" } ?: "—"
    if (wide) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            YnStatTile(state.ratedAlbums.toString(), stringResource(R.string.stats_rated_albums), { Icon(Icons.Default.Album, contentDescription = null, modifier = Modifier.size(20.dp)) }, Modifier.weight(1f))
            YnStatTile(state.tracksRated.toString(), stringResource(R.string.stats_tracks_rated), { Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(20.dp)) }, Modifier.weight(1f))
            YnStatTile(average, stringResource(R.string.stats_average_score), { Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(20.dp)) }, Modifier.weight(1f))
            YnStatTile(state.tracksAnalyzed.toString(), stringResource(R.string.stats_tracks_analyzed), { Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(20.dp)) }, Modifier.weight(1f))
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                YnStatTile(state.ratedAlbums.toString(), stringResource(R.string.stats_rated_albums), { Icon(Icons.Default.Album, contentDescription = null, modifier = Modifier.size(20.dp)) }, Modifier.weight(1f))
                YnStatTile(state.tracksRated.toString(), stringResource(R.string.stats_tracks_rated), { Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(20.dp)) }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                YnStatTile(average, stringResource(R.string.stats_average_score), { Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(20.dp)) }, Modifier.weight(1f))
                YnStatTile(state.tracksAnalyzed.toString(), stringResource(R.string.stats_tracks_analyzed), { Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(20.dp)) }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TopAlbumCard(title: String) {
    YnSectionCard(title = stringResource(R.string.stats_top_album)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(22.dp))
            }
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun HistogramCard(rows: List<StatsCountRow>) {
    YnSectionCard(title = stringResource(R.string.stats_score_histogram)) {
        val counts = remember(rows) { rows.associate { it.label to it.count } }
        val max = remember(rows) { rows.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1 }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (5 downTo 1).forEach { star ->
                val count = counts[star.toString()] ?: 0
                BarRow(
                    label = "$star★",
                    value = count.toString(),
                    fraction = count.toFloat() / max.toFloat(),
                )
            }
        }
        if (rows.isEmpty()) {
            Text(stringResource(R.string.stats_no_data), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ValueCard(title: String, rows: List<StatsValueRow>, modifier: Modifier = Modifier) {
    YnSectionCard(title = title, modifier = modifier) {
        if (rows.isEmpty()) {
            Text(stringResource(R.string.stats_no_data), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            rows.take(8).forEach { row ->
                BarRow(
                    label = row.label,
                    value = row.value?.let { formatDecimal(it, 2) + "★" } ?: "—",
                    fraction = ((row.value ?: 0.0) / 5.0).toFloat(),
                )
            }
        }
    }
}

@Composable
private fun BarRow(label: String, value: String, fraction: Float) {
    val reducedMotion = rememberReducedMotion()
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), YnMotion.fadeOrSnap(reducedMotion), label = "bar")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(72.dp),
        )
        Box(
            Modifier
                .weight(1f)
                .height(10.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animated)
                    .clip(CircleShape)
                    .background(
                        Brush.horizontalGradient(
                            listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary),
                        ),
                    ),
            )
        }
        Text(value, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(56.dp), textAlign = TextAlign.End)
    }
}

private enum class QualityGroup(@StringRes val labelRes: Int) {
    LOSSLESS(R.string.verdict_lossless),
    LOSSY(R.string.verdict_lossy),
    UNKNOWN(R.string.verdict_unknown_format),
}

@Composable
private fun QualityCard(rows: List<StatsCountRow>) {
    val grouped = remember(rows) {
        rows.groupBy { row ->
            when {
                row.label.contains("LOSSLESS", ignoreCase = true) || row.label.contains("HI-RES", ignoreCase = true) -> QualityGroup.LOSSLESS
                row.label.contains("LOSSY", ignoreCase = true) -> QualityGroup.LOSSY
                else -> QualityGroup.UNKNOWN
            }
        }.mapValues { (_, list) -> list.sumOf { it.count } }
    }
    val max = grouped.values.maxOrNull()?.coerceAtLeast(1) ?: 1
    YnSectionCard(title = stringResource(R.string.stats_quality_distribution)) {
        if (rows.isEmpty()) {
            Text(stringResource(R.string.stats_no_data), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            QualityGroup.entries.forEach { group ->
                val count = grouped[group] ?: 0
                if (count > 0) {
                    BarRow(stringResource(group.labelRes), count.toString(), count.toFloat() / max.toFloat())
                }
            }
        }
    }
}

@Composable
private fun RankingCard(title: String, rows: List<StatsCountRow>, modifier: Modifier = Modifier) {
    YnSectionCard(title = title, modifier = modifier) {
        if (rows.isEmpty()) {
            Text(stringResource(R.string.stats_no_data), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            rows.take(6).forEachIndexed { index, row ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier.size(26.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${index + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        row.label,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(row.count.toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

internal suspend fun renderStatsImage(
    context: Context,
    state: StatsUiState,
    labels: ShareStatsLabels,
    colors: androidx.compose.material3.ColorScheme,
    parentCompositionContext: CompositionContext,
    lifecycleOwner: LifecycleOwner,
    savedStateOwner: SavedStateRegistryOwner,
    renderHost: ViewGroup?,
): ShareStatsImage {
    val (file, bitmap) = withContext(Dispatchers.Main.immediate) {
        val directory = File(context.cacheDir, "share").apply { mkdirs() }
        val timestamp = LocalDateTime.now(ZoneId.systemDefault())
        val file = File(directory, "stats-${timestamp.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))}.png")
        val view = ComposeView(context)
        renderHost?.addView(view, ViewGroup.LayoutParams(1080, 1350))
        view.setViewTreeLifecycleOwner(lifecycleOwner)
        view.setViewTreeSavedStateRegistryOwner(savedStateOwner)
        view.setParentCompositionContext(parentCompositionContext)
        view.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 1f)) {
                MaterialTheme(colorScheme = colors, typography = YounekoTypography) {
                    ShareStatsCard(state, labels, timestamp)
                }
            }
        }
        view.createComposition()
        val widthSpec = android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY)
        val heightSpec = android.view.View.MeasureSpec.makeMeasureSpec(1350, android.view.View.MeasureSpec.EXACTLY)
        view.measure(widthSpec, heightSpec)
        view.layout(0, 0, 1080, 1350)
        val bitmap = Bitmap.createBitmap(1080, 1350, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        view.disposeComposition()
        renderHost?.removeView(view)
        file to bitmap
    }
    withContext(Dispatchers.IO) {
        file.outputStream().use { output -> check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) }
    }
    return ShareStatsImage(file, bitmap)
}

@Composable
internal fun ShareStatsCard(state: StatsUiState, labels: ShareStatsLabels, timestamp: LocalDateTime) {
    val colors = MaterialTheme.colorScheme
    val locale = LocalConfiguration.current.locales[0]
    Box(
        Modifier
            .size(1080.dp, 1350.dp)
            .clip(RoundedCornerShape(48.dp))
            .background(Brush.verticalGradient(listOf(colors.primaryContainer, colors.surface))),
    ) {
        ShareDecorations(colors)
        Column(Modifier.fillMaxSize().padding(64.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Box(
                    Modifier.size(96.dp).clip(CircleShape).background(colors.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_paw),
                        contentDescription = null,
                        tint = colors.onPrimary,
                        modifier = Modifier.size(56.dp),
                    )
                }
                Column {
                    Text(labels.title, fontSize = 62.sp, lineHeight = 68.sp, fontWeight = FontWeight.SemiBold, color = colors.onPrimaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(labels.subtitle, fontSize = 28.sp, lineHeight = 34.sp, color = colors.onPrimaryContainer.copy(alpha = 0.7f), maxLines = 1)
                }
            }
            Spacer(Modifier.height(40.dp))
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                val average = state.averageScore?.let { formatDecimal(it, 1) } ?: "—"
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    ShareMetricTile(Icons.Default.Album, state.ratedAlbums.toString(), labels.ratedAlbums, Modifier.weight(1f))
                    ShareMetricTile(Icons.Default.Star, state.tracksRated.toString(), labels.tracksRated, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    ShareMetricTile(Icons.Default.Star, average, labels.averageScore, Modifier.weight(1f))
                    ShareMetricTile(Icons.Default.GraphicEq, state.tracksAnalyzed.toString(), labels.tracksAnalyzed, Modifier.weight(1f))
                }
                state.topRatedAlbum?.let { top ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(28.dp))
                            .background(colors.surface.copy(alpha = 0.7f))
                            .padding(horizontal = 32.dp, vertical = 22.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(labels.topAlbum, fontSize = 24.sp, color = colors.onSurfaceVariant)
                            Text(
                                top,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Icon(
                    painter = painterResource(R.drawable.ic_cat_chibi_sit),
                    contentDescription = null,
                    tint = colors.primary.copy(alpha = 0.9f),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 12.dp).size(190.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painter = painterResource(R.drawable.ic_paw), contentDescription = null, tint = colors.primary, modifier = Modifier.size(40.dp))
                Spacer(Modifier.width(12.dp))
                Text(labels.appName, fontSize = 26.sp, fontWeight = FontWeight.Medium, color = colors.onSurface, modifier = Modifier.weight(1f))
                Text(
                    timestamp.format(DateTimeFormatter.ofPattern("HH:mm · dd-MM-yyyy", locale)),
                    fontSize = 24.sp,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ShareMetricTile(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, label: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .heightIn(min = 210.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(colors.surface.copy(alpha = 0.72f))
            .padding(28.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(42.dp))
            Text(value, fontSize = 76.sp, lineHeight = 82.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, fontSize = 24.sp, lineHeight = 30.sp, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ShareDecorations(colors: androidx.compose.material3.ColorScheme) {
    val positions = listOf(120 to 220, 900 to 300, 180 to 800, 880 to 980, 540 to 1180)
    Box(Modifier.fillMaxSize()) {
        positions.forEachIndexed { index, (x, y) ->
            Icon(
                painter = painterResource(R.drawable.ic_paw_small),
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier
                    .offset(x = x.dp, y = y.dp)
                    .size(34.dp)
                    .graphicsLayer { alpha = 0.12f },
            )
        }
    }
}

@Composable
private fun SharePreviewDialog(
    file: File?,
    bitmap: Bitmap?,
    loading: Boolean,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
) {
    Dialog(onDismissRequest = { if (!loading) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        androidx.compose.material3.Card(Modifier.fillMaxWidth(0.92f), shape = RoundedCornerShape(24.dp)) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 760.dp).verticalScroll(rememberScrollState()).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.stats_share_preview), style = MaterialTheme.typography.titleLarge)
                Box(Modifier.fillMaxWidth().aspectRatio(1080f / 1350f), contentAlignment = Alignment.Center) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth().aspectRatio(1080f / 1350f),
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        CircularProgressIndicator()
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDismiss, enabled = !loading, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.backup_cancel)) }
                    TextButton(onClick = onSave, enabled = !loading && file != null, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.stats_share_save), maxLines = 1) }
                    Button(onClick = onShare, enabled = !loading && file != null, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.stats_share_action)) }
                }
            }
        }
    }
}

private fun shareStatsFile(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            null,
        ),
    )
}

private fun saveStatsFile(context: Context, file: File): Boolean = runCatching {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Youneko Rate")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
    resolver.openOutputStream(uri)?.use { output -> file.inputStream().use { it.copyTo(output) } } ?: return false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
        resolver.update(uri, done, null, null)
    }
    true
}.getOrDefault(false)

private fun formatDecimal(value: Double, digits: Int): String =
    NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        minimumFractionDigits = digits
        maximumFractionDigits = digits
    }.format(value)
