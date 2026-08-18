package com.youneko.rate.ui.analyze

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.youneko.rate.R
import com.youneko.rate.data.audio.AudioAnalysisWorker
import com.youneko.rate.data.local.dao.AudioAnalysisDao
import com.youneko.rate.data.local.entity.AudioAnalysisEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json

@HiltViewModel
class AudioAnalysisViewModel @Inject constructor(
    @ApplicationContext context: android.content.Context,
    dao: AudioAnalysisDao,
) : ViewModel() {
    private val workManager = WorkManager.getInstance(context)
    val analyses = dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val workInfos = workManager.getWorkInfosForUniqueWorkFlow(UNIQUE_WORK).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun enqueue(uri: String) {
        val request = OneTimeWorkRequestBuilder<AudioAnalysisWorker>()
            .setInputData(workDataOf(AudioAnalysisWorker.KEY_URI to uri))
            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel() = workManager.cancelUniqueWork(UNIQUE_WORK)

    companion object {
        private const val UNIQUE_WORK = "audio-quality-analysis"
    }
}

@Composable
fun AudioAnalysisScreen(viewModel: AudioAnalysisViewModel = hiltViewModel()) {
    val analyses by viewModel.analyses.collectAsStateWithLifecycle()
    val workInfos by viewModel.workInfos.collectAsStateWithLifecycle()
    var explain by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        viewModel.enqueue(uri.toString())
    }
    val latest = analyses.firstOrNull()
    val work = workInfos.firstOrNull()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.analyze), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.audio_quality_phase8_body), style = MaterialTheme.typography.bodyMedium)
        Button(onClick = { picker.launch(arrayOf("audio/*")) }) { Text(stringResource(R.string.audio_analysis_choose_file)) }
        if (work?.state == WorkInfo.State.RUNNING || work?.state == WorkInfo.State.ENQUEUED) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = viewModel::cancel) { Text(stringResource(R.string.audio_analysis_cancel)) }
            }
        }
        latest?.let { AnalysisCard(it, onExplain = { explain = true }) }
            ?: Text(stringResource(R.string.audio_analysis_empty), style = MaterialTheme.typography.bodyLarge)
    }
    if (explain) {
        AlertDialog(
            onDismissRequest = { explain = false },
            title = { Text(stringResource(R.string.audio_analysis_explain)) },
            text = { Text("Cutoff là nơi năng lượng phổ giảm mạnh; rolloff mô tả độ dốc vách cắt; dynamic range là crest factor; true peak và clipping mô tả biên độ cực đại. Verdict là heuristic, không phải chứng nhận nguồn phát hành.") },
            confirmButton = { TextButton(onClick = { explain = false }) { Text(stringResource(R.string.close)) } },
        )
    }
}

@Composable
private fun AnalysisCard(analysis: AudioAnalysisEntity, onExplain: () -> Unit) {
    val spectrum = runCatching { Json.decodeFromString<List<Float>>(analysis.spectrumJson) }.getOrDefault(emptyList())
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.audio_analysis_verdict), style = MaterialTheme.typography.titleLarge)
            Text(analysis.verdict, style = MaterialTheme.typography.headlineSmall, color = verdictColor(analysis.verdict))
            Text("Confidence: ${analysis.confidence}%")
            SpectrumChart(spectrum)
            MetricRow(stringResource(R.string.audio_analysis_cutoff), analysis.cutoffHz?.let { "%.0f Hz".format(it) } ?: "—")
            MetricRow(stringResource(R.string.audio_analysis_slope), analysis.rolloffSlope?.let { "%.3f dB/bin".format(it) } ?: "—")
            MetricRow(stringResource(R.string.audio_analysis_dynamic), analysis.dynamicRangeDb?.let { "%.1f dB".format(it) } ?: "—")
            MetricRow(stringResource(R.string.audio_analysis_peak), analysis.truePeakDbtp?.let { "%.1f dBTP".format(it) } ?: "—")
            MetricRow(stringResource(R.string.audio_analysis_clipping), analysis.clippingPercent?.let { "%.3f%%".format(it) } ?: "—")
            Text("${analysis.codec.orEmpty()} · ${analysis.sampleRate ?: 0} Hz · ${analysis.bitrate ?: 0} bps", style = MaterialTheme.typography.bodySmall)
            Text(analysis.reasonsJson, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onExplain) { Text(stringResource(R.string.audio_analysis_explain)) }
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SpectrumChart(values: List<Float>) {
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    Canvas(Modifier.fillMaxWidth().height(170.dp)) {
        if (values.size < 2) return@Canvas
        val minValue = values.minOrNull() ?: -100f
        val maxValue = values.maxOrNull()?.coerceAtLeast(minValue + 1f) ?: 0f
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = size.width * index / (values.lastIndex.coerceAtLeast(1))
            val y = size.height - ((value - minValue) / (maxValue - minValue)) * size.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, primary, style = Stroke(width = 2f))
        drawLine(outline, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f))
    }
}

private fun verdictColor(verdict: String): androidx.compose.ui.graphics.Color = when (verdict) {
    "LOSSLESS THẬT" -> androidx.compose.ui.graphics.Color(0xFF2E7D32)
    "LOSSY CHẤT LƯỢNG CAO" -> androidx.compose.ui.graphics.Color(0xFF1565C0)
    "NGHI NGỜ NÂNG CẤP GIẢ" -> androidx.compose.ui.graphics.Color(0xFFC62828)
    else -> androidx.compose.ui.graphics.Color(0xFF6D4C41)
}
