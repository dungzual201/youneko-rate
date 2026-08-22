package com.youneko.rate.ui.stats

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.content.FileProvider
import com.youneko.rate.BuildConfig
import java.io.File
import java.util.Locale

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.youneko.rate.R
import com.youneko.rate.ui.YnDimens
import com.youneko.rate.ui.YnMotion
import com.youneko.rate.ui.rememberReducedMotion
import com.youneko.rate.ui.components.YnStatCard
import com.youneko.rate.ui.components.YnTabTitle
import com.youneko.rate.data.local.dao.StatsCountRow
import com.youneko.rate.data.local.dao.StatsValueRow
import com.youneko.rate.data.local.dao.StatsDao
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StatsUiState(
    val loading: Boolean = true,
    val ratedAlbums: Int = 0,
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
        item { Button(onClick = { shareStatsImage(context, state) }) { Text(stringResource(R.string.stats_share_image)) } }
        item { DistributionCard(stringResource(R.string.stats_score_histogram), state.histogram) }
        item { ValueCard(stringResource(R.string.stats_average_by_year), state.averageByYear) }
        item { ValueCard(stringResource(R.string.stats_average_by_month), state.averageByMonth) }
        item { DistributionCard(stringResource(R.string.stats_quality_distribution), state.quality) }
        item { RankingCard(stringResource(R.string.stats_top_artists), state.topArtists) }
        item { RankingCard(stringResource(R.string.stats_top_labels), state.topLabels) }
        item { RankingCard(stringResource(R.string.stats_top_credits), state.topCredits) }
        item { Text(stringResource(R.string.stats_year_summary), style = MaterialTheme.typography.titleLarge) }
    }
}

private fun shareStatsImage(context: Context, state: StatsUiState) {
    val bitmap = Bitmap.createBitmap(1200, 1600, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap).apply { drawColor(Color.WHITE) }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 48f }
    val ratedAlbums = context.getString(R.string.stats_rated_albums)
    val averageScore = context.getString(R.string.stats_average_score)
    val scoreDistribution = context.getString(R.string.stats_score_histogram)
    val audioQuality = context.getString(R.string.stats_quality_distribution)
    val topArtists = context.getString(R.string.stats_top_artists)
    canvas.drawText("${context.getString(R.string.app_name)} — ${state.ratedAlbums} $ratedAlbums", 48f, 80f, paint)
    canvas.drawText("$averageScore: ${state.averageScore?.let { "%.2f".format(Locale.getDefault(), it) } ?: "—"}★", 48f, 150f, paint)
    var y = 240f
    listOf(scoreDistribution to state.histogram, audioQuality to state.quality, topArtists to state.topArtists).forEach { (title, rows) ->
        paint.textSize = 38f
        canvas.drawText(title, 48f, y, paint)
        y += 54f
        rows.take(8).forEach { row -> canvas.drawText("${row.label}: ${row.count}", 72f, y, paint); y += 44f }
        y += 24f
    }
    val file = File(context.cacheDir, "youneko-rate-summary.png")
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply { type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    context.startActivity(Intent.createChooser(intent, null))
}

@Composable
private fun StatsOverview(state: StatsUiState) {
    val average = state.averageScore?.let { "%.2f★".format(Locale.getDefault(), it) } ?: "—"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(YnDimens.space3)) {
        YnStatCard(state.ratedAlbums.toString(), stringResource(R.string.stats_rated_albums), { Icon(Icons.Default.Album, contentDescription = null, modifier = Modifier.size(24.dp)) }, Modifier.weight(1f))
        YnStatCard(average, stringResource(R.string.stats_average_score), { Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(24.dp)) }, Modifier.weight(1f))
    }
}

@Composable
private fun SummaryCard(state: StatsUiState) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column { Text(stringResource(R.string.stats_rated_albums), style = MaterialTheme.typography.labelLarge); Text(state.ratedAlbums.toString(), style = MaterialTheme.typography.headlineMedium) }
            Column { Text(stringResource(R.string.stats_average_score), style = MaterialTheme.typography.labelLarge); Text(state.averageScore?.let { "%.2f★".format(Locale.getDefault(), it) } ?: "—", style = MaterialTheme.typography.headlineMedium) }
        }
        state.topRatedAlbum?.let { title ->
            Text("${stringResource(R.string.stats_top_album)}: $title", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
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
            rows.forEach { row -> Text("${row.label}: ${row.value?.let { "%.2f★".format(Locale.getDefault(), it) } ?: "—"}") }
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
