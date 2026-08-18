package com.youneko.rate.ui.analyze

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import android.graphics.Paint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
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

enum class AnalyzeStep(val labelVi: String) {
    READING_HEADER("Đang đọc thông tin file…"),
    DECODING("Đang giải mã âm thanh…"),
    FFT("Đang phân tích phổ tần (FFT)…"),
    COMPUTING("Đang tính toán chỉ số…"),
    SAVING("Đang lưu kết quả…"),
}

sealed interface AnalyzeUiState {
    data object Idle : AnalyzeUiState
    data class Running(
        val currentFileName: String,
        val currentIndex: Int,
        val totalFiles: Int,
        val step: AnalyzeStep,
        val stepProgress: Float,
        val elapsedMs: Long,
    ) : AnalyzeUiState
    data class Failed(val fileName: String, val reason: String) : AnalyzeUiState
}

private fun WorkInfo?.toAnalyzeUiState(): AnalyzeUiState {
    if (this == null) return AnalyzeUiState.Idle
    val active = state == WorkInfo.State.RUNNING || state == WorkInfo.State.ENQUEUED
    if (!active) {
        return if (state == WorkInfo.State.FAILED) AnalyzeUiState.Failed(
            progress.getString(AudioAnalysisWorker.KEY_FILE_NAME).orEmpty(),
            progress.getString(AudioAnalysisWorker.KEY_ERROR).orEmpty().ifBlank { "Không thể phân tích file." },
        ) else AnalyzeUiState.Idle
    }
    val step = AnalyzeStep.entries.getOrElse(progress.getInt(AudioAnalysisWorker.KEY_STEP, 0)) { AnalyzeStep.READING_HEADER }
    return AnalyzeUiState.Running(
        progress.getString(AudioAnalysisWorker.KEY_FILE_NAME).orEmpty().ifBlank { "audio" },
        progress.getInt(AudioAnalysisWorker.KEY_FILE_INDEX, 1),
        progress.getInt(AudioAnalysisWorker.KEY_TOTAL_FILES, 1).coerceAtLeast(1),
        step,
        progress.getFloat(AudioAnalysisWorker.KEY_STEP_PROGRESS, -1f),
        0L,
    )
}

@HiltViewModel
class AudioAnalysisViewModel @Inject constructor(
    @ApplicationContext context: android.content.Context,
    dao: AudioAnalysisDao,
) : ViewModel() {
    private val workManager = WorkManager.getInstance(context)
    val analyses = dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val workInfos = workManager.getWorkInfosForUniqueWorkFlow(AudioAnalysisWorker.UNIQUE_WORK).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun enqueue(uri: String) {
        val fileName = Uri.parse(uri).lastPathSegment.orEmpty().substringAfterLast('/').ifBlank { "audio" }
        val request = OneTimeWorkRequestBuilder<AudioAnalysisWorker>()
            .setInputData(workDataOf(
                AudioAnalysisWorker.KEY_URI to uri,
                AudioAnalysisWorker.KEY_FILE_NAME to fileName,
                AudioAnalysisWorker.KEY_FILE_INDEX to 1,
                AudioAnalysisWorker.KEY_TOTAL_FILES to 1,
            ))
            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(AudioAnalysisWorker.UNIQUE_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel() = workManager.cancelUniqueWork(AudioAnalysisWorker.UNIQUE_WORK)
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
    val analyzeState = workInfos.firstOrNull().toAnalyzeUiState()
    val snackbarHostState = remember { SnackbarHostState() }
    var hadRunning by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(analyzeState) {
        if (analyzeState is AnalyzeUiState.Running) hadRunning = true
        if (hadRunning && analyzeState is AnalyzeUiState.Idle) {
            snackbarHostState.showSnackbar("Đã huỷ phân tích")
            hadRunning = false
        }
    }
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.analyze), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.audio_quality_phase8_body), style = MaterialTheme.typography.bodyMedium)
            Button(enabled = analyzeState !is AnalyzeUiState.Running, onClick = { picker.launch(arrayOf("audio/*")) }) {
                Text(stringResource(R.string.audio_analysis_choose_file))
            }
            when (val state = analyzeState) {
                is AnalyzeUiState.Running -> AnalyzeRunningCard(state, viewModel::cancel)
                is AnalyzeUiState.Failed -> Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(state.fileName, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(state.reason, color = MaterialTheme.colorScheme.error)
                    }
                }
                else -> Unit
            }
            latest?.let { AnalysisCard(it, onExplain = { explain = true }) }
                ?: Text(stringResource(R.string.audio_analysis_empty), style = MaterialTheme.typography.bodyLarge)
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
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
private fun AnalyzeRunningCard(state: AnalyzeUiState.Running, onCancel: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Đang phân tích (${state.currentIndex}/${state.totalFiles})", style = MaterialTheme.typography.titleMedium)
            Text(displayFileName(state.currentFileName), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(state.step.labelVi, Modifier.animateContentSize())
            if (state.stepProgress in 0f..1f) {
                LinearProgressIndicator(progress = { state.stepProgress }, Modifier.fillMaxWidth())
                Text("${(state.stepProgress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
            } else {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onCancel) { Text(stringResource(R.string.audio_analysis_cancel)) }
            }
        }
    }
}

private fun displayFileName(value: String): String {
    if (value.length <= 72) return value
    val extension = value.substringAfterLast('.', "").takeIf { it.isNotBlank() }?.let { ".$it" }.orEmpty()
    val prefixLength = ((72 - extension.length - 1) / 2).coerceAtLeast(12)
    return value.take(prefixLength) + "…" + value.takeLast((72 - prefixLength - 1).coerceAtLeast(extension.length))
}

@Composable
private fun AnalysisCard(analysis: AudioAnalysisEntity, onExplain: () -> Unit) {
    val spectrum = runCatching { Json.decodeFromString<List<Float>>(analysis.spectrumJson) }.getOrDefault(emptyList())
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.audio_analysis_verdict), style = MaterialTheme.typography.titleLarge)
            Text(analysis.verdict, style = MaterialTheme.typography.headlineSmall, color = verdictColor(analysis.verdict))
            Text("Confidence: ${analysis.confidence}%")
            SpectrumChart(spectrum, analysis.cutoffHz, analysis.sampleRate)
            MetricRow(stringResource(R.string.audio_analysis_cutoff), analysis.cutoffHz?.let { "%.1f kHz".format(java.util.Locale.US, it / 1000.0) } ?: "—")
            MetricRow(stringResource(R.string.audio_analysis_slope), analysis.rolloffSlope?.let { "%.1f dB/kHz".format(java.util.Locale.US, it) } ?: "—")
            MetricRow(stringResource(R.string.audio_analysis_dynamic), analysis.dynamicRangeDb?.let { "%.1f dB".format(java.util.Locale.US, it) } ?: "—")
            MetricRow(stringResource(R.string.audio_analysis_peak), analysis.truePeakDbtp?.let { "%.1f dBTP".format(java.util.Locale.US, it) } ?: "—")
            MetricRow(stringResource(R.string.audio_analysis_clipping), analysis.clippingPercent?.let { "%.3f%%".format(java.util.Locale.US, it) } ?: "—")
            Text(
                listOfNotNull(
                    analysis.codec,
                    analysis.sampleRate?.let { "$it Hz" },
                    analysis.bitDepth?.let { "$it-bit" },
                    analysis.channels?.let { "$it ch" },
                    analysis.bitrate?.let { "$it bps" },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
            )
            val reasons = runCatching { Json.decodeFromString<List<String>>(analysis.reasonsJson) }.getOrDefault(listOf(analysis.reasonsJson))
            reasons.filter { it.isNotBlank() }.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
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
private fun SpectrumChart(values: List<Float>, cutoffHz: Double?, sampleRate: Int?) {
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    val background = MaterialTheme.colorScheme.surfaceVariant
    Box(Modifier.fillMaxWidth().height(220.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            if (values.size < 2) return@Canvas
            val left = 48f
            val top = 18f
            val right = size.width - 12f
            val bottom = size.height - 28f
            val width = (right - left).coerceAtLeast(1f)
            val height = (bottom - top).coerceAtLeast(1f)
            val nyquist = (sampleRate?.takeIf { it > 0 } ?: 44_100) / 2.0
            val minDb = -100f
            val maxDb = 0f
            val yForDb: (Float) -> Float = { db -> bottom - ((db.coerceIn(minDb, maxDb) - minDb) / (maxDb - minDb)) * height }
            val xForHz: (Double) -> Float = { hz -> left + (hz / nyquist).coerceIn(0.0, 1.0).toFloat() * width }
            listOf(0f, -20f, -40f, -60f, -80f).forEach { db ->
                val y = yForDb(db)
                drawLine(background, Offset(left, y), Offset(right, y), strokeWidth = 1f)
                drawIntoCanvas { canvas ->
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = outline.toArgb(); textSize = 24f }
                    canvas.nativeCanvas.drawText("${db.toInt()}", 4f, y + 8f, paint)
                }
            }
            val xTicks = listOf(0, 5_000, 10_000, 15_000, 20_000).filter { it <= nyquist }
            xTicks.forEach { hz ->
                val x = xForHz(hz.toDouble())
                drawLine(background, Offset(x, top), Offset(x, bottom), strokeWidth = 1f)
                drawIntoCanvas { canvas ->
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = outline.toArgb(); textSize = 24f }
                    canvas.nativeCanvas.drawText(if (hz == 0) "0" else "${hz / 1000}k", x - 12f, size.height - 5f, paint)
                }
            }
            drawLine(outline, Offset(left, top), Offset(left, bottom), strokeWidth = 2f)
            drawLine(outline, Offset(left, bottom), Offset(right, bottom), strokeWidth = 2f)
            val path = Path()
            values.forEachIndexed { index, value ->
                val x = left + width * index / values.lastIndex.coerceAtLeast(1)
                val y = yForDb(value)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, primary, style = Stroke(width = 2f))
            cutoffHz?.let { cutoff ->
                val markerX = xForHz(cutoff)
                drawLine(primary, Offset(markerX, top), Offset(markerX, bottom), strokeWidth = 2f)
                drawIntoCanvas { canvas ->
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = primary.toArgb(); textSize = 26f; isFakeBoldText = true }
                    canvas.nativeCanvas.drawText("%.1f kHz".format(java.util.Locale.US, cutoff / 1000.0), (markerX + 4f).coerceAtMost(right - 90f), top - 3f, paint)
                }
            }
        }
    }
}

private fun verdictColor(verdict: String): Color = when (verdict) {
    "LOSSLESS THẬT" -> androidx.compose.ui.graphics.Color(0xFF2E7D32)
    "LOSSY CHẤT LƯỢNG CAO", "LOSSY", "LOSSY CHẤT LƯỢNG THẤP" -> Color(0xFF1565C0)
    "NGHI NGỜ NÂNG CẤP GIẢ" -> Color(0xFFC62828)
    else -> Color(0xFF6D4C41)
}
