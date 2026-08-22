package com.youneko.rate.ui.analyze

import com.youneko.rate.ui.YounekoEmptyState
import com.youneko.rate.ui.YounekoErrorState
import com.youneko.rate.ui.YounekoLoadingState
import com.youneko.rate.ui.YounekoSpacing
import com.youneko.rate.ui.YnDimens

import android.content.Intent
import androidx.annotation.StringRes
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import android.graphics.Paint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Scaffold
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.core.content.FileProvider
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Refresh
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
import com.youneko.rate.data.audio.CachedSpectrogram
import com.youneko.rate.data.audio.SpectrogramCache
import com.youneko.rate.data.artwork.ArtworkStore
import com.youneko.rate.data.local.dao.AudioAnalysisDao
import com.youneko.rate.data.importer.AudioTag
import com.youneko.rate.data.importer.LocalAudioTagReader
import com.youneko.rate.data.local.entity.AudioAnalysisEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

enum class AnalyzeStep(@StringRes val labelRes: Int) {
    READING_HEADER(R.string.analyze_step_reading),
    DECODING(R.string.analyze_step_decoding),
    FFT(R.string.analyze_step_fft),
    COMPUTING(R.string.analyze_step_computing),
    SAVING(R.string.analyze_step_saving),
}

data class AnalyzeHeader(
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val format: String? = null,
)

sealed interface AnalyzeEvent {
    data object Cancelled : AnalyzeEvent
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
    @ApplicationContext private val context: android.content.Context,
    dao: AudioAnalysisDao,
) : ViewModel() {
    private val workManager = WorkManager.getInstance(context)
    private val _events = Channel<AnalyzeEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()
    private var currentWorkId: UUID? = null
    private var userRequestedCancel = false
    private val _header = MutableStateFlow<AnalyzeHeader?>(null)
    val header = _header.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val analyses = dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val workInfosFlow = workManager.getWorkInfosForUniqueWorkFlow(AudioAnalysisWorker.UNIQUE_WORK)
    val workInfos = workInfosFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            workInfosFlow.collect { infos ->
                val workId = currentWorkId ?: return@collect
                val workInfo = infos.firstOrNull { it.id == workId } ?: return@collect
                when (workInfo.state) {
                    WorkInfo.State.SUCCEEDED, WorkInfo.State.FAILED -> {
                        userRequestedCancel = false
                        currentWorkId = null
                    }
                    WorkInfo.State.CANCELLED -> {
                        if (userRequestedCancel) _events.trySend(AnalyzeEvent.Cancelled)
                        userRequestedCancel = false
                        currentWorkId = null
                    }
                    else -> Unit
                }
            }
        }
    }

    fun enqueue(uri: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val parsedUri = Uri.parse(uri)
            val fileName = parsedUri.lastPathSegment.orEmpty().substringAfterLast('/').ifBlank { "audio" }
            val tag = runCatching { LocalAudioTagReader(context, ArtworkStore(context)).readAll(listOf(parsedUri)).tags.firstOrNull() }.getOrNull()
            _header.value = tag.toAnalyzeHeader(fileName)
            userRequestedCancel = false
            val request = OneTimeWorkRequestBuilder<AudioAnalysisWorker>()
                .setInputData(workDataOf(
                    AudioAnalysisWorker.KEY_URI to uri,
                    AudioAnalysisWorker.KEY_FILE_NAME to fileName,
                    AudioAnalysisWorker.KEY_FILE_INDEX to 1,
                    AudioAnalysisWorker.KEY_TOTAL_FILES to 1,
                ))
                .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()
            currentWorkId = request.id
            workManager.enqueueUniqueWork(AudioAnalysisWorker.UNIQUE_WORK, ExistingWorkPolicy.REPLACE, request)
        }
    }

    fun onCancelClicked() {
        userRequestedCancel = true
        currentWorkId?.let(workManager::cancelWorkById) ?: workManager.cancelUniqueWork(AudioAnalysisWorker.UNIQUE_WORK)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioAnalysisScreen(viewModel: AudioAnalysisViewModel = hiltViewModel()) {
    val analyses by viewModel.analyses.collectAsStateWithLifecycle()
    val workInfos by viewModel.workInfos.collectAsStateWithLifecycle()
    val header by viewModel.header.collectAsStateWithLifecycle()
    var explain by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        viewModel.enqueue(uri.toString())
    }
    val latest = analyses.firstOrNull()
    var cachedSpectrogram by remember { mutableStateOf<CachedSpectrogram?>(null) }
    LaunchedEffect(latest?.fileUriOrPath, latest?.fileHash, latest?.trackId) {
        cachedSpectrogram = latest?.let { analysis ->
            withContext(Dispatchers.IO) {
                SpectrogramCache(context).read(analysis.trackId ?: analysis.fileUriOrPath, analysis.fileHash)
            }
        }
    }
    val analyzeState = workInfos.firstOrNull().toAnalyzeUiState()
    val snackbarHostState = remember { SnackbarHostState() }
    val cancelledMessage = stringResource(R.string.audio_analysis_cancelled)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                AnalyzeEvent.Cancelled -> {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(cancelledMessage)
                }
            }
        }
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.analyze), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = {
                    if (latest != null || analyzeState is AnalyzeUiState.Running || analyzeState is AnalyzeUiState.Failed) {
                        IconButton(onClick = { picker.launch(arrayOf("audio/*")) }, enabled = analyzeState !is AnalyzeUiState.Running) {
                            Icon(Icons.Default.FolderOpen, contentDescription = stringResource(R.string.audio_analysis_choose_file), modifier = Modifier.size(24.dp))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState, Modifier.navigationBarsPadding()) },
    ) { innerPadding ->
        Column(
            Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            header?.let { selectedHeader ->
                Text(selectedHeader.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(selectedHeader.artist, selectedHeader.album?.takeUnless { it.equals(selectedHeader.title, ignoreCase = true) }, selectedHeader.format).joinToString(" · ").ifBlank { stringResource(R.string.audio_analysis_not_applicable) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                )
            }
            when (val state = analyzeState) {
                is AnalyzeUiState.Running -> AnalyzeRunningCard(state, viewModel::onCancelClicked)
                is AnalyzeUiState.Failed -> YounekoErrorState("${state.fileName}: ${state.reason}", onRetry = { picker.launch(arrayOf("audio/*")) }, modifier = Modifier.fillMaxWidth())
                else -> Unit
            }
            when {
                latest != null -> AnalysisCard(latest, cachedSpectrogram, context, onExplain = { explain = true })
                analyzeState is AnalyzeUiState.Failed -> Unit
                else -> YounekoEmptyState(stringResource(R.string.audio_analysis_empty), actionLabel = stringResource(R.string.audio_analysis_choose_file), onAction = { picker.launch(arrayOf("audio/*")) }, modifier = Modifier.fillMaxWidth())
            }
            Text(stringResource(R.string.analyze_decode_note), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(YnDimens.navigationSafe))
        }
    }
    if (explain) {
        AlertDialog(
            onDismissRequest = { explain = false },
            title = { Text(stringResource(R.string.audio_analysis_explain)) },
            text = { Text(stringResource(R.string.audio_analysis_explanation_body)) },
            confirmButton = { TextButton(onClick = { explain = false }) { Text(stringResource(R.string.close)) } },
        )
    }
}

@Composable
private fun AnalyzeRunningCard(state: AnalyzeUiState.Running, onCancel: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.analyze_running, state.currentIndex, state.totalFiles), style = MaterialTheme.typography.titleMedium)
            Text(displayFileName(state.currentFileName), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(stringResource(state.step.labelRes), Modifier.animateContentSize())
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

private fun AudioTag?.toAnalyzeHeader(fileName: String): AnalyzeHeader {
    val cleanFileName = fileName.substringBeforeLast('.', fileName).replace(Regex("^\\s*\\d{1,3}\\s*[-.]\\s*"), "").trim().ifBlank { "audio" }
    return AnalyzeHeader(
        title = this?.title?.trim().takeUnless { it.isNullOrBlank() } ?: cleanFileName,
        artist = this?.artist?.trim()?.takeIf { it.isNotBlank() },
        album = this?.album?.trim()?.takeIf { it.isNotBlank() },
        format = fileName.substringAfterLast('.', "").uppercase().takeIf { it.isNotBlank() },
    )
}

private fun displayFileName(value: String): String {
    if (value.length <= 72) return value
    val extension = value.substringAfterLast('.', "").takeIf { it.isNotBlank() }?.let { ".$it" }.orEmpty()
    val prefixLength = ((72 - extension.length - 1) / 2).coerceAtLeast(12)
    return value.take(prefixLength) + "…" + value.takeLast((72 - prefixLength - 1).coerceAtLeast(extension.length))
}

@Composable
private fun AnalysisCard(analysis: AudioAnalysisEntity, cached: CachedSpectrogram?, context: android.content.Context, onExplain: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AnalysisHero(analysis)
            SpectrogramPanel(cached, context)
            AnalysisDetailsCard(analysis)
            val reasons = runCatching { Json.decodeFromString<List<String>>(analysis.reasonsJson) }.getOrDefault(listOf(analysis.reasonsJson))
            reasons.filter { it.isNotBlank() }.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            TextButton(onClick = onExplain) { Text(stringResource(R.string.audio_analysis_explain)) }
        }
    }
}

@Composable
private fun AnalysisHero(analysis: AudioAnalysisEntity) {
    val rawFormatVerdict = analysis.formatVerdict ?: analysis.verdict.substringBefore('\n')
    val rawTranscodeVerdict = analysis.transcodeVerdict ?: analysis.verdict.substringAfter('\n', "")
    val formatVerdict = localizedFormatVerdict(analysis, rawFormatVerdict)
    val transcodeVerdict = localizedTranscodeVerdict(rawTranscodeVerdict)
    val groupLabel = when {
        rawFormatVerdict.startsWith("LOSSLESS") -> stringResource(R.string.verdict_lossless)
        rawFormatVerdict.startsWith("LOSSY") -> stringResource(R.string.verdict_lossy)
        else -> stringResource(R.string.verdict_unknown_format)
    }
    val chipColor = when {
        rawFormatVerdict.startsWith("LOSSLESS") -> MaterialTheme.colorScheme.primaryContainer
        rawFormatVerdict.startsWith("LOSSY") -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.errorContainer
    }
    val identity = listOfNotNull(
        analysis.codec?.takeUnless { it == "UNKNOWN" },
        analysis.sampleRate?.let { formatDecimal(it / 1000.0, 1) + " kHz" },
        analysis.bitDepth?.let { "$it-bit" },
    ).joinToString(" · ")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.audio_analysis_verdict), style = MaterialTheme.typography.titleLarge, )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistChip(onClick = {}, label = { Text(groupLabel) }, colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(containerColor = chipColor))
                Text(formatVerdict, style = MaterialTheme.typography.headlineSmall, color = verdictColor(rawFormatVerdict))
                if (identity.isNotBlank()) Text(identity, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(transcodeVerdict, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(Modifier.size(68.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { (analysis.confidence / 100f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxSize(), strokeWidth = 6.dp)
                Text("${analysis.confidence}%", style = MaterialTheme.typography.labelMedium)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SummaryChip(stringResource(R.string.audio_analysis_cutoff), analysis.cutoffHz?.let { formatDecimal(it / 1000.0, 1) + " kHz" } ?: stringResource(R.string.audio_analysis_not_applicable))
            SummaryChip(stringResource(R.string.audio_analysis_dynamic), analysis.dynamicRangeDb?.let { formatDecimal(it, 1) + " dB" } ?: stringResource(R.string.audio_analysis_not_applicable))
            SummaryChip(stringResource(R.string.audio_analysis_peak), analysis.truePeakDbtp?.let { formatDecimal(it, 1) + " dBTP" } ?: stringResource(R.string.audio_analysis_not_applicable))
        }
    }
}

@Composable
private fun localizedFormatVerdict(analysis: AudioAnalysisEntity, raw: String): String {
    val identity = analysis.codec ?: stringResource(R.string.audio_analysis_unknown)
    return when {
        raw.startsWith("LOSSLESS") -> stringResource(R.string.verdict_lossless_detail, identity, analysis.sampleRate?.let { formatDecimal(it / 1000.0, 1) + " kHz" } ?: stringResource(R.string.audio_analysis_not_applicable))
        raw.startsWith("LOSSY") -> stringResource(R.string.verdict_lossy_detail, identity, analysis.bitrate?.let { "$it bps" } ?: stringResource(R.string.audio_analysis_not_applicable))
        else -> stringResource(R.string.verdict_unknown_detail)
    }
}

@Composable
private fun localizedTranscodeVerdict(raw: String): String = when {
    raw.startsWith("CÓ DẤU HIỆU") -> stringResource(R.string.verdict_transcode_suspicious)
    raw.startsWith("HI-RES") -> stringResource(R.string.verdict_transcode_hires)
    raw.startsWith("NGHI NGỜ UPSAMPLE") -> stringResource(R.string.verdict_transcode_upsample)
    raw.startsWith("PHỔ ĐẦY ĐỦ") -> stringResource(R.string.verdict_transcode_full)
    raw.startsWith("CHƯA ĐỦ") -> stringResource(R.string.verdict_transcode_insufficient)
    raw.contains("DẢI CAO") -> stringResource(R.string.verdict_transcode_limited)
    else -> stringResource(R.string.verdict_transcode_inconclusive)
}

@Composable
private fun SummaryChip(label: String, value: String) {
    AssistChip(onClick = {}, label = { Text("$label: $value", maxLines = 1, overflow = TextOverflow.Ellipsis) })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnalysisDetailsCard(analysis: AudioAnalysisEntity) {
    var expanded by rememberSaveable(analysis.id) { mutableStateOf(false) }
    var help by rememberSaveable { mutableStateOf<Int?>(null) }
    CompositionLocalProvider(LocalMetricHelp provides { help = it }) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.animateContentSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.audio_analysis_raw_metrics), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, )
                IconButton(onClick = { expanded = !expanded }) { Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = stringResource(R.string.audio_analysis_expand_details), modifier = Modifier.size(24.dp)) }
            }
            if (expanded) {
                MetricGroup(stringResource(R.string.audio_analysis_group_source)) {
                    DetailedMetricRow(stringResource(R.string.audio_analysis_codec), analysis.codec ?: stringResource(R.string.audio_analysis_unknown), R.string.audio_analysis_codec)
                    DetailedMetricRow(stringResource(R.string.audio_analysis_source_mime), analysis.sourceMime ?: stringResource(R.string.audio_analysis_not_applicable), R.string.audio_analysis_source_mime)
                    DetailedMetricRow(stringResource(R.string.audio_analysis_detection_source), analysis.codecDetectionSource ?: stringResource(R.string.audio_analysis_not_applicable), R.string.audio_analysis_detection_source)
                    DetailedMetricRow(stringResource(R.string.audio_analysis_sample_rate), analysis.sampleRate?.let { "$it Hz" } ?: stringResource(R.string.audio_analysis_not_applicable), R.string.audio_analysis_sample_rate)
                    DetailedMetricRow(stringResource(R.string.audio_analysis_bit_depth), analysis.bitDepth?.let { "$it-bit" } ?: stringResource(R.string.audio_analysis_not_applicable), R.string.audio_analysis_bit_depth)
                    DetailedMetricRow(stringResource(R.string.audio_analysis_channels), analysis.channels?.toString() ?: stringResource(R.string.audio_analysis_not_applicable), R.string.audio_analysis_channels)
                    DetailedMetricRow(stringResource(R.string.audio_analysis_bitrate), analysis.bitrate?.let { "$it bps" } ?: stringResource(R.string.audio_analysis_not_applicable), R.string.audio_analysis_bitrate)
                    if (analysis.bitrateNote != null) {
                        Text(stringResource(R.string.audio_analysis_bitrate_note_estimated), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = YnDimens.metricCaptionIndent))
                    }
                    DetailedMetricRow(stringResource(R.string.audio_analysis_theoretical_bitrate), analysis.theoreticalBitrate?.let { "$it bps" } ?: stringResource(R.string.audio_analysis_not_applicable), R.string.audio_analysis_theoretical_bitrate)
                    if (analysis.bitrate != null && analysis.theoreticalBitrate != null && analysis.theoreticalBitrate > 0) DetailedMetricRow(stringResource(R.string.audio_analysis_compression_ratio), formatDecimal(analysis.bitrate.toDouble() / analysis.theoreticalBitrate * 100.0, 0) + "%", R.string.audio_analysis_compression_ratio)
                    if (analysis.codec == "UNKNOWN") DetailedMetricRow(stringResource(R.string.audio_analysis_header_hex), analysis.rawHeaderHex ?: stringResource(R.string.audio_analysis_not_applicable), R.string.audio_analysis_header_hex)
                }
                MetricGroup(stringResource(R.string.audio_analysis_group_spectrum)) {
                    DetailedMetricRow(stringResource(R.string.audio_analysis_cutoff), analysis.cutoffHz?.let { formatDecimal(it / 1000.0, 1) + " kHz" } ?: stringResource(R.string.audio_analysis_not_applicable_full_spectrum), R.string.audio_analysis_cutoff)
                    DetailedMetricRow(stringResource(R.string.audio_analysis_slope), analysis.rolloffSlope?.let { formatDecimal(it, 1) + " dB/kHz" } ?: stringResource(R.string.audio_analysis_not_applicable), R.string.audio_analysis_slope)
                    DetailedMetricRow(stringResource(R.string.audio_analysis_noise_floor), analysis.noiseFloorDb?.let { formatDecimal(it, 1) + " dB" } ?: stringResource(R.string.audio_analysis_not_applicable), R.string.audio_analysis_noise_floor)
                    DetailedMetricRow(stringResource(R.string.audio_analysis_quiet_above), analysis.quietAboveFraction?.let { formatDecimal(it * 100.0, 1) + "%" } ?: stringResource(R.string.audio_analysis_not_applicable), R.string.audio_analysis_quiet_above)
                    DetailedMetricRow(stringResource(R.string.audio_analysis_energy_above), analysis.energyAboveCutoffRatio?.let { formatDecimal(it * 100.0, 2) + "%" } ?: stringResource(R.string.audio_analysis_not_applicable), R.string.audio_analysis_energy_above)
                    DetailedMetricRow(stringResource(R.string.audio_analysis_cutoff_retries), analysis.cutoffRetries.toString(), R.string.audio_analysis_cutoff_retries)
                    DetailedMetricRow(stringResource(R.string.audio_analysis_analyzed_frames), analysis.analyzedFrames.toString(), R.string.audio_analysis_analyzed_frames)
                }
                MetricGroup(stringResource(R.string.audio_analysis_group_loudness)) {
                    DetailedMetricRow(stringResource(R.string.audio_analysis_dynamic), analysis.dynamicRangeDb?.let { formatDecimal(it, 1) + " dB" } ?: stringResource(R.string.audio_analysis_not_applicable), R.string.audio_analysis_dynamic, warning = (analysis.dynamicRangeDb ?: 0.0) < 6.0)
                    DetailedMetricRow(stringResource(R.string.audio_analysis_peak), analysis.truePeakDbtp?.let { formatDecimal(it, 1) + " dBTP" } ?: stringResource(R.string.audio_analysis_not_applicable), R.string.audio_analysis_peak)
                    DetailedMetricRow(stringResource(R.string.audio_analysis_clipping), analysis.clippingPercent?.let { formatDecimal(it, 3) + "%" } ?: stringResource(R.string.audio_analysis_not_applicable), R.string.audio_analysis_clipping, warning = (analysis.clippingPercent ?: 0.0) > 0.1)
                }
            }
        }
    }
    }
    help?.let { titleRes ->
        ModalBottomSheet(onDismissRequest = { help = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), modifier = Modifier.navigationBarsPadding()) {
            Column(Modifier.fillMaxWidth().padding(24.dp).heightIn(min = 120.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(titleRes), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.audio_analysis_explanation_body))
                TextButton(onClick = { help = null }) { Text(stringResource(R.string.close)) }
            }
        }
    }
}

@Composable
private fun MetricGroup(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Composable
private fun DetailedMetricRow(label: String, value: String, helpRes: Int, warning: Boolean = false) {
    val onHelp = LocalMetricHelp.current
    val stacked = value.length > 22
    Row(
        Modifier.fillMaxWidth().heightIn(min = YnDimens.metricRowMinHeight).padding(vertical = YnDimens.space1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onHelp(helpRes) }, modifier = Modifier.size(YnDimens.minTouchTarget)) {
            Icon(Icons.Default.HelpOutline, contentDescription = stringResource(R.string.audio_analysis_help), modifier = Modifier.size(YnDimens.metricHelpIcon))
        }
        androidx.compose.foundation.layout.Spacer(Modifier.width(YnDimens.space2))
        if (stacked) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(YnDimens.space1)) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodyLarge, color = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            }
        } else {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            androidx.compose.foundation.layout.Spacer(Modifier.width(YnDimens.space3))
            Text(value, style = MaterialTheme.typography.bodyLarge, textAlign = androidx.compose.ui.text.style.TextAlign.End, maxLines = 2, color = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        }
    }
}

private val LocalMetricHelp = compositionLocalOf<(Int) -> Unit> { {} }

private fun formatDecimal(value: Double, decimals: Int): String = String.format(java.util.Locale.getDefault(), "%.${decimals}f", value)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpectrogramPanel(cached: CachedSpectrogram?, context: android.content.Context) {
    var logarithmic by rememberSaveable(cached?.metadata?.stableKey) { mutableStateOf(false) }
    var showAxes by rememberSaveable(cached?.metadata?.stableKey) { mutableStateOf(true) }
    var dbFloor by rememberSaveable(cached?.metadata?.stableKey) { mutableStateOf(-120f) }
    var resetToken by rememberSaveable(cached?.metadata?.stableKey) { mutableStateOf(0) }
    var fullscreen by rememberSaveable(cached?.metadata?.stableKey) { mutableStateOf(false) }
    var tooltip by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Card(Modifier.fillMaxWidth().navigationBarsPadding()) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.spectrogram_title), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, )
                IconButton(onClick = { fullscreen = true }) { Icon(Icons.Default.Fullscreen, contentDescription = stringResource(R.string.spectrogram_fullscreen), modifier = Modifier.size(24.dp)) }
                IconButton(onClick = {
                    cached?.let { value ->
                        scope.launch(Dispatchers.IO) {
                            val file = File(context.cacheDir, "spectrogram-${System.currentTimeMillis()}.png")
                            SpectrogramBitmapRenderer.writePng(value, logarithmic, dbFloor, file)
                            withContext(kotlinx.coroutines.Dispatchers.Main) {
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                val send = Intent(Intent.ACTION_SEND).apply { type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                                context.startActivity(Intent.createChooser(send, context.getString(R.string.spectrogram_share)))
                            }
                        }
                    }
                }) { Icon(Icons.Default.FileDownload, contentDescription = stringResource(R.string.spectrogram_export_png), modifier = Modifier.size(20.dp)) }
            }
            if (cached == null) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    YounekoLoadingState(Modifier.fillMaxWidth())
                    Text(stringResource(R.string.spectrogram_waiting), style = MaterialTheme.typography.bodySmall)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = !logarithmic, onClick = { logarithmic = false }, label = { Text(stringResource(R.string.spectrogram_linear)) })
                    FilterChip(selected = logarithmic, onClick = { logarithmic = true }, label = { Text(stringResource(R.string.spectrogram_log)) })
                    FilterChip(selected = showAxes, onClick = { showAxes = !showAxes }, label = { Text(if (showAxes) stringResource(R.string.spectrogram_hide_axes) else stringResource(R.string.spectrogram_show_axes)) })
                    FilterChip(selected = false, onClick = { dbFloor = if (dbFloor <= -100f) -90f else if (dbFloor <= -90f) -100f else -120f }, label = { Text(stringResource(R.string.spectrogram_db_range, dbFloor.toInt())) })
                    FilterChip(selected = false, onClick = { resetToken++ }, label = { Text(stringResource(R.string.spectrogram_reset_zoom)) })
                }
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f)) {
                    SpectrogramView(cached, logarithmic, dbFloor, showAxes, resetToken = resetToken, modifier = Modifier.fillMaxSize(), onTooltip = { tooltip = it })
                    if (showAxes) SpectrogramAxesOverlay(cached, logarithmic)
                    DbLegend(
                        dbFloor = dbFloor,
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 6.dp, top = 8.dp, bottom = if (showAxes) 36.dp else 8.dp),
                    )
                }
                tooltip?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
            }
        }
    }
    if (fullscreen && cached != null) {
        ModalBottomSheet(onDismissRequest = { fullscreen = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), modifier = Modifier.navigationBarsPadding()) {
            Column(Modifier.fillMaxWidth().padding(12.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.spectrogram_title), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = { fullscreen = false }) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close), modifier = Modifier.size(24.dp)) }
                }
                Box(Modifier.fillMaxWidth().aspectRatio(0.9f)) {
                    SpectrogramView(cached, logarithmic, dbFloor, showAxes, resetToken = resetToken, modifier = Modifier.fillMaxSize(), onTooltip = { tooltip = it })
                    if (showAxes) SpectrogramAxesOverlay(cached, logarithmic)
                    DbLegend(dbFloor, Modifier.align(Alignment.CenterEnd).padding(end = 6.dp, top = 8.dp, bottom = if (showAxes) 36.dp else 8.dp))
                }
            }
        }
    }
}

@Composable
private fun SpectrogramAxesOverlay(cached: CachedSpectrogram, logarithmic: Boolean) {
    val labelTextSize = with(LocalDensity.current) { 12.dp.toPx() }
    val axisLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f).toArgb()
    Canvas(Modifier.fillMaxSize()) {
        val left = 52f
        val top = 12f
        val right = size.width - 42f
        val bottom = size.height - 34f
        val sampleRate = cached.metadata.spectrogram.sampleRate
        val nyquist = sampleRate / 2.0
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = axisLabelColor
            textSize = labelTextSize
        }
        listOf(0.0, 5_000.0, 10_000.0, 15_000.0, 20_000.0, nyquist).distinct().filter { it <= nyquist + 1 }.forEach { frequency ->
            val normalized = if (!logarithmic) frequency / nyquist else if (frequency <= 0.0) 0.0 else kotlin.math.ln(frequency / 20.0) / kotlin.math.ln(nyquist / 20.0)
            val y = bottom - (normalized * (bottom - top)).toFloat()
            val label = if (frequency >= 1000.0) String.format(java.util.Locale.getDefault(), "%.1f kHz", frequency / 1000.0) else "0 kHz"
            drawIntoCanvas { canvas -> canvas.nativeCanvas.drawText(label, 2f, y + 4f, paint) }
        }
        val duration = cached.metadata.spectrogram.durationMs
        (0..4).forEach { tick ->
            val x = left + (right - left) * tick / 4f
            val totalSeconds = (duration * tick / 4L) / 1000L
            val label = String.format(java.util.Locale.getDefault(), "%d:%02d", totalSeconds / 60L, totalSeconds % 60L)
            drawIntoCanvas { canvas -> canvas.nativeCanvas.drawText(label, x - 12f, size.height - 8f, paint) }
        }
    }
}

@Composable
private fun DbLegend(dbFloor: Float, modifier: Modifier = Modifier) {
    Row(modifier.width(56.dp).fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier.width(18.dp).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(
                Brush.verticalGradient(listOf(Color.White, Color.Yellow, Color.Red, Color(0xFFB0006D), Color.Black)),
            ),
        )
        Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.spectrogram_db_zero), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.spectrogram_db_mid), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.spectrogram_db_floor, dbFloor.toInt()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
                    canvas.nativeCanvas.drawText("%.1f kHz".format(java.util.Locale.getDefault(), cutoff / 1000.0), (markerX + 4f).coerceAtMost(right - 90f), top - 3f, paint)
                }
            }
        }
    }
}

@Composable
private fun verdictColor(verdict: String): Color = when {
    verdict.startsWith("LOSSLESS") || verdict == "HI-RES THỰC" -> MaterialTheme.colorScheme.primary
    verdict.startsWith("LOSSY") -> MaterialTheme.colorScheme.tertiary
    verdict == "CÓ DẤU HIỆU NGUỒN LOSSY" || verdict == "NGHI NGỜ UPSAMPLE" -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
