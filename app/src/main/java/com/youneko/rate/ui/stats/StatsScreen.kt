package com.youneko.rate.ui.stats

import android.content.ContentValues
import android.content.Context
import android.content.res.Configuration
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.ViewGroup
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.youneko.rate.BuildConfig
import com.youneko.rate.R
import com.youneko.rate.data.local.dao.StatsCountRow
import com.youneko.rate.data.local.dao.StatsDao
import com.youneko.rate.data.local.dao.StatsValueRow
import com.youneko.rate.ui.YnDimens
import com.youneko.rate.ui.YnMotion
import com.youneko.rate.ui.components.YnStatCard
import com.youneko.rate.ui.components.YnTabTitle
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
import androidx.compose.animation.core.animateFloatAsState

internal data class ShareStatsLabels(
    val appName: String,
    val tracksRated: String,
    val averageScore: String,
    val tracksAnalyzed: String,
)

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

    init { reload() }

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
fun StatsScreen(contentPadding: PaddingValues = PaddingValues(), viewModel: StatsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shareFailureFallback = stringResource(R.string.stats_share_failed)
    val colors = MaterialTheme.colorScheme
    val shareColors = remember(colors) { shareColorScheme(context, colors) }
    val parentCompositionContext = rememberCompositionContext()
    val lifecycleOwner = LocalLifecycleOwner.current
    val renderHost = LocalView.current.rootView as? ViewGroup
    val savedStateOwner = lifecycleOwner as SavedStateRegistryOwner
    val labels = ShareStatsLabels(
        appName = stringResource(R.string.app_name),
        tracksRated = stringResource(R.string.share_tracks_rated),
        averageScore = stringResource(R.string.share_average_score),
        tracksAnalyzed = stringResource(R.string.share_tracks_analyzed),
    )
    var shareLoading by remember { mutableStateOf(false) }
    var shareFile by remember { mutableStateOf<File?>(null) }
    var shareError by remember { mutableStateOf<String?>(null) }

    if (state.loading) {
        Column(Modifier.fillMaxWidth().padding(horizontal = YnDimens.space4)) {
            YnTabTitle(R.string.stats)
            CircularProgressIndicator()
        }
        return
    }
    if (state.ratedAlbums == 0 && state.quality.isEmpty()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = YnDimens.space4)) {
            YnTabTitle(R.string.stats)
            Column(verticalArrangement = Arrangement.spacedBy(YnDimens.space3)) {
                Text(stringResource(R.string.stats_empty_title), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.stats_no_data))
            }
        }
        return
    }
    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = YnDimens.space4, end = YnDimens.space4, top = 0.dp, bottom = contentPadding.calculateBottomPadding() + YnDimens.navigationSafe),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { YnTabTitle(R.string.stats) }
        item {
            Text(stringResource(R.string.stats_empty_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.stats_empty_body), style = MaterialTheme.typography.bodyMedium)
        }
        item { StatsOverview(state) }
        item {
            Button(
                onClick = {
                    shareError = null
                    shareLoading = true
                    scope.launch {
                        runCatching { renderStatsImage(context, state, labels, shareColors, parentCompositionContext, lifecycleOwner, savedStateOwner, renderHost) }
                            .onSuccess { shareFile = it }
                            .onFailure { shareError = it.message ?: shareFailureFallback }
                        shareLoading = false
                    }
                },
                enabled = !shareLoading,
            ) {
                if (shareLoading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.Default.BarChart, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.stats_share_image))
            }
        }
        item { DistributionCard(stringResource(R.string.stats_score_histogram), state.histogram) }
        item { ValueCard(stringResource(R.string.stats_average_by_year), state.averageByYear) }
        item { ValueCard(stringResource(R.string.stats_average_by_month), state.averageByMonth) }
        item { DistributionCard(stringResource(R.string.stats_quality_distribution), state.quality) }
        item { RankingCard(stringResource(R.string.stats_top_artists), state.topArtists) }
        item { RankingCard(stringResource(R.string.stats_top_labels), state.topLabels) }
        item { RankingCard(stringResource(R.string.stats_top_credits), state.topCredits) }
        item { Text(stringResource(R.string.stats_year_summary), style = MaterialTheme.typography.titleLarge) }
    }

    shareError?.let { message ->
        AlertDialog(
            onDismissRequest = { shareError = null },
            title = { Text(stringResource(R.string.stats_share_failed)) },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { shareError = null }) { Text(stringResource(R.string.backup_cancel)) } },
        )
    }
    shareFile?.let { file ->
        SharePreviewDialog(
            file = file,
            onDismiss = { shareFile = null },
            onShare = { shareStatsFile(context, file); shareFile = null },
            onSave = { saveStatsFile(context, file); shareFile = null },
        )
    }
}

private fun shareColorScheme(context: Context, fallback: ColorScheme): ColorScheme {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return fallback
    val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    return runCatching {
        if (night) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }.getOrDefault(fallback)
}

private fun formatShareCount(value: Int): String = NumberFormat.getIntegerInstance(Locale.getDefault()).format(value)

internal suspend fun renderStatsImage(context: Context, state: StatsUiState, labels: ShareStatsLabels, colors: ColorScheme, parentCompositionContext: CompositionContext, lifecycleOwner: androidx.lifecycle.LifecycleOwner, savedStateOwner: SavedStateRegistryOwner, renderHost: ViewGroup?): File {
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
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                MaterialTheme(colorScheme = colors) {
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
        bitmap.recycle()
    }
    return file
}

@Composable
internal fun ShareStatsCard(state: StatsUiState, labels: ShareStatsLabels, timestamp: LocalDateTime) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(48.dp))
            .background(Brush.verticalGradient(listOf(colors.primaryContainer, colors.surface)))
            .padding(72.dp),
    ) {
        Text(labels.appName, style = MaterialTheme.typography.headlineMedium, color = colors.onPrimaryContainer, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(36.dp))
        ShareMetricCard(Icons.Default.Star, formatShareCount(state.tracksRated), labels.tracksRated)
        Spacer(Modifier.height(32.dp))
        val average = state.averageScore?.let { NumberFormat.getNumberInstance(Locale.getDefault()).apply { minimumFractionDigits = 1; maximumFractionDigits = 1 }.format(it) + " ★" } ?: "—"
        ShareMetricCard(Icons.Default.Grade, average, labels.averageScore)
        Spacer(Modifier.height(32.dp))
        ShareMetricCard(Icons.Default.GraphicEq, formatShareCount(state.tracksAnalyzed), labels.tracksAnalyzed)
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(painter = painterResource(com.youneko.rate.R.drawable.ic_paw), contentDescription = null, tint = colors.onSurface, modifier = Modifier.size(64.dp))
                Spacer(Modifier.width(16.dp))
                Text(labels.appName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(timestamp.format(DateTimeFormatter.ofPattern("HH:mm")), style = MaterialTheme.typography.labelLarge, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(timestamp.format(DateTimeFormatter.ofPattern("dd-MM-yyyy")), style = MaterialTheme.typography.labelLarge, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ShareMetricCard(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, label: String) {
    val colors = MaterialTheme.colorScheme
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(32.dp), colors = CardDefaults.cardColors(containerColor = colors.surface.copy(alpha = 0.78f))) {
        Row(Modifier.fillMaxWidth().padding(28.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Icon(icon, contentDescription = null, tint = colors.onSurface, modifier = Modifier.size(56.dp))
            Column(Modifier.weight(1f)) {
                Text(value, style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold), color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(label, style = MaterialTheme.typography.titleMedium, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun SharePreviewDialog(file: File, onDismiss: () -> Unit, onShare: () -> Unit, onSave: () -> Unit) {
    val bitmap = remember(file) { BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(Modifier.fillMaxWidth(0.92f), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.stats_share_preview), style = MaterialTheme.typography.titleLarge)
                bitmap?.let { Image(it, contentDescription = stringResource(R.string.stats_share_preview), modifier = Modifier.fillMaxWidth().height(420.dp), contentScale = androidx.compose.ui.layout.ContentScale.Fit) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.backup_cancel)) }
                    TextButton(onClick = onSave, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.stats_share_save)) }
                    Button(onClick = onShare, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.stats_share_action)) }
                }
            }
        }
    }
}

private fun shareStatsFile(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, null))
}

private fun saveStatsFile(context: Context, file: File) {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Youneko Rate")
    }
    context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)?.let { uri ->
        context.contentResolver.openOutputStream(uri)?.use { output -> file.inputStream().use { it.copyTo(output) } }
    }
}

@Composable
private fun StatsOverview(state: StatsUiState) {
    val average = state.averageScore?.let { NumberFormat.getNumberInstance(Locale.getDefault()).apply { minimumFractionDigits = 2; maximumFractionDigits = 2 }.format(it) + "★" } ?: "—"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(YnDimens.space3)) {
        YnStatCard(state.ratedAlbums.toString(), stringResource(R.string.stats_rated_albums), { Icon(Icons.Default.Album, contentDescription = null, modifier = Modifier.size(24.dp)) }, Modifier.weight(1f))
        YnStatCard(average, stringResource(R.string.stats_average_score), { Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(24.dp)) }, Modifier.weight(1f))
    }
}

@Composable
private fun DistributionCard(title: String, rows: List<StatsCountRow>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            val max = rows.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
            val reducedMotion = rememberReducedMotion()
            rows.forEach { row ->
                Text("${row.label}: ${row.count}")
                val animatedProgress by animateFloatAsState(row.count.toFloat() / max, YnMotion.fadeOrSnap(reducedMotion), label = "statsBar")
                LinearProgressIndicator(progress = { animatedProgress }, modifier = Modifier.fillMaxWidth())
            }
            if (rows.isEmpty()) Text(stringResource(R.string.stats_no_data), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ValueCard(title: String, rows: List<StatsValueRow>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            rows.forEach { row -> Text("${row.label}: ${row.value?.let { NumberFormat.getNumberInstance(Locale.getDefault()).apply { minimumFractionDigits = 2; maximumFractionDigits = 2 }.format(it) + "★" } ?: "—"}") }
            if (rows.isEmpty()) Text(stringResource(R.string.stats_no_data), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun RankingCard(title: String, rows: List<StatsCountRow>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            rows.forEachIndexed { index, row -> Text("${index + 1}. ${row.label} — ${row.count}") }
            if (rows.isEmpty()) Text(stringResource(R.string.stats_no_data), style = MaterialTheme.typography.bodySmall)
        }
    }
}
