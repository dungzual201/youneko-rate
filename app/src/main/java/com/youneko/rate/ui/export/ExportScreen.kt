package com.youneko.rate.ui.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.youneko.rate.R
import com.youneko.rate.data.AutoBackupStore
import com.youneko.rate.data.export.BACKUP_INPUT_URI
import com.youneko.rate.data.export.BACKUP_OUTPUT_URI
import com.youneko.rate.data.export.BACKUP_RESTORE_MERGE
import com.youneko.rate.data.export.BACKUP_RESTORE_MODE
import com.youneko.rate.data.export.BACKUP_RESTORE_REPLACE
import com.youneko.rate.data.export.BackupPreview
import com.youneko.rate.data.export.BackupValidation
import com.youneko.rate.data.export.BackupWorker
import com.youneko.rate.data.export.CollageAlbum
import com.youneko.rate.data.export.CollageRenderer
import com.youneko.rate.data.export.EXPORT_FORMAT
import com.youneko.rate.data.export.EXPORT_FORMAT_CSV
import com.youneko.rate.data.export.EXPORT_FORMAT_JSON
import com.youneko.rate.data.export.EXPORT_OUTPUT_URI
import com.youneko.rate.data.export.ExportWorker
import com.youneko.rate.data.export.LibrarySnapshot
import com.youneko.rate.data.export.RestoreWorker
import com.youneko.rate.data.export.ShareAlbum
import com.youneko.rate.data.export.ShareCardRenderer
import com.youneko.rate.data.export.validateBackup
import com.youneko.rate.data.export.scheduleWeeklyAutoBackup
import com.youneko.rate.data.export.cancelWeeklyAutoBackup
import com.youneko.rate.data.export.exportSnapshot
import com.youneko.rate.data.local.YounekoDatabase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ExportViewModel @Inject constructor(private val database: YounekoDatabase, @ApplicationContext private val context: Context) : ViewModel() {
    private val _snapshot = MutableStateFlow<LibrarySnapshot?>(null)
    val snapshot = _snapshot.asStateFlow()
    private val _validation = MutableStateFlow<BackupValidation?>(null)
    val validation = _validation.asStateFlow()

    fun load() = viewModelScope.launch(Dispatchers.IO) { _snapshot.value = database.exportSnapshot() }
    fun validate(uri: Uri) = viewModelScope.launch(Dispatchers.IO) { _validation.value = validateBackup(context, database, uri) }
    fun clearValidation() { _validation.value = null }
    fun enqueueRestore(context: Context, uri: Uri, mode: String) {
        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<RestoreWorker>().setInputData(workDataOf(BACKUP_INPUT_URI to uri.toString(), BACKUP_RESTORE_MODE to mode)).build())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(onBack: () -> Unit, viewModel: ExportViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val validation by viewModel.validation.collectAsStateWithLifecycle()
    val autoStore = remember { AutoBackupStore(context.applicationContext) }
    val autoEnabled by autoStore.enabled.collectAsStateWithLifecycle(initialValue = false)
    val autoTree by autoStore.treeUri.collectAsStateWithLifecycle(initialValue = null)
    val lastBackupAt by autoStore.lastBackupAt.collectAsStateWithLifecycle(initialValue = null)
    var pendingExportFormat by rememberSaveable { mutableStateOf(EXPORT_FORMAT_JSON) }
    var includeCovers by rememberSaveable { mutableStateOf(true) }
    var includeExports by rememberSaveable { mutableStateOf(true) }
    var pendingBackupOptions by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var restoreMode by rememberSaveable { mutableStateOf(BACKUP_RESTORE_REPLACE) }
    var replaceConfirm by rememberSaveable { mutableStateOf(false) }
    var replaceChecked by rememberSaveable { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(Unit) { viewModel.load() }

    fun enqueueExport(uri: Uri, format: String) {
        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<ExportWorker>().setInputData(workDataOf(EXPORT_OUTPUT_URI to uri.toString(), EXPORT_FORMAT to format)).build())
    }
    fun enqueueBackup(uri: Uri) {
        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<BackupWorker>().setInputData(workDataOf(BACKUP_OUTPUT_URI to uri.toString(), "include_covers" to includeCovers, "include_exports" to includeExports)).build())
    }

    val createExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> uri?.let { enqueueExport(it, pendingExportFormat) } }
    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> uri?.let { enqueueBackup(it) } }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { pendingImportUri = uri; viewModel.validate(uri) }
    }
    val selectAutoFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            scope.launch {
                autoStore.setFolder(uri.toString())
                autoStore.setEnabled(true)
                scheduleWeeklyAutoBackup(context, uri)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backup_title)) },
                navigationIcon = { androidx.compose.material3.IconButton(onClick = onBack) { androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.a11y_back)) } },
            )
        },
    ) { innerPadding ->
    Column(Modifier.fillMaxSize().padding(innerPadding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.backup_description), style = MaterialTheme.typography.bodyMedium)
        Divider()
        Button(onClick = { pendingBackupOptions = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.backup_export)) }
        Button(onClick = { openBackup.launch(arrayOf("application/octet-stream", "application/zip")) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.backup_import)) }
        Button(onClick = { pendingExportFormat = EXPORT_FORMAT_CSV; createExport.launch("youneko-rate-ratings.csv") }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.backup_export_csv)) }
        Button(onClick = { pendingExportFormat = EXPORT_FORMAT_JSON; createExport.launch("youneko-rate-credits.json") }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.backup_export_json)) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Switch(checked = autoEnabled, onCheckedChange = { enabled -> if (enabled) selectAutoFolder.launch(null) else scope.launch { autoStore.setEnabled(false); cancelWeeklyAutoBackup(context) } })
            Column { Text(stringResource(R.string.backup_auto)); Text(stringResource(if (autoEnabled) R.string.backup_auto_enabled else R.string.backup_auto_disabled), style = MaterialTheme.typography.bodySmall) }
        }
        TextButton(onClick = { selectAutoFolder.launch(null) }) { Text(stringResource(R.string.backup_auto_folder)) }
        lastBackupAt?.let { Text(stringResource(R.string.backup_latest, SimpleDateFormat("yyyy-MM-dd HH:mm", LocalLocale.current.platformLocale).format(Date(it))), style = MaterialTheme.typography.bodySmall) }
        Spacer(Modifier.height(8.dp))
        snapshot?.let { data ->
            Button(onClick = { shareCard(context, data, portrait = false) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.export_share_card)) }
            Button(onClick = { shareCard(context, data, portrait = true) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.export_share_portrait)) }
            Button(onClick = { shareCollage(context, data, 3) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.export_collage_3x3)) }
            Button(onClick = { shareCollage(context, data, 4) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.export_collage_4x4)) }
        }
    }
    }

    if (pendingBackupOptions) {
        AlertDialog(
            onDismissRequest = { pendingBackupOptions = false },
            title = { Text(stringResource(R.string.backup_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Switch(includeCovers, { includeCovers = it }); Text(stringResource(R.string.backup_include_covers)) }
                    Row(verticalAlignment = Alignment.CenterVertically) { Switch(includeExports, { includeExports = it }); Text(stringResource(R.string.backup_include_exports)) }
                    Text(stringResource(R.string.backup_no_token_music), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { pendingBackupOptions = false; createBackup.launch("YounekoRate_${SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())}.younekorate") }) { Text(stringResource(R.string.backup_export)) } },
            dismissButton = { TextButton(onClick = { pendingBackupOptions = false }) { Text(stringResource(R.string.backup_cancel)) } },
        )
    }

    validation?.let { value ->
        val preview = value.preview
        AlertDialog(
            onDismissRequest = { viewModel.clearValidation(); pendingImportUri = null },
            title = { Text(stringResource(if (preview == null) R.string.backup_invalid_format else R.string.backup_preview_title)) },
            text = {
                if (preview == null) Text(value.message ?: stringResource(R.string.backup_corrupt))
                else PreviewContent(preview, restoreMode, { restoreMode = it })
            },
            confirmButton = {
                if (preview != null && pendingImportUri != null) TextButton(onClick = {
                    if (restoreMode == BACKUP_RESTORE_REPLACE) {
                        replaceChecked = false
                        replaceConfirm = true
                    } else {
                        viewModel.enqueueRestore(context, pendingImportUri!!, restoreMode)
                        viewModel.clearValidation()
                        pendingImportUri = null
                    }
                }) { Text(stringResource(R.string.backup_confirm)) }
            },
            dismissButton = { TextButton(onClick = { viewModel.clearValidation(); pendingImportUri = null }) { Text(stringResource(R.string.backup_cancel)) } },
        )
    }
    if (replaceConfirm && pendingImportUri != null) {
        AlertDialog(
            onDismissRequest = { replaceConfirm = false },
            title = { Text(stringResource(R.string.backup_replace_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.backup_replace_confirm_body))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = replaceChecked, onCheckedChange = { replaceChecked = it })
                        Text(stringResource(R.string.backup_replace_confirm_check))
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = replaceChecked, onClick = {
                    viewModel.enqueueRestore(context, pendingImportUri!!, BACKUP_RESTORE_REPLACE)
                    replaceConfirm = false
                    viewModel.clearValidation()
                    pendingImportUri = null
                }) { Text(stringResource(R.string.backup_confirm)) }
            },
            dismissButton = { TextButton(onClick = { replaceConfirm = false }) { Text(stringResource(R.string.backup_cancel)) } },
        )
    }
}

@Composable
private fun PreviewContent(preview: BackupPreview, mode: String, onMode: (String) -> Unit) {
    val locale = LocalLocale.current.platformLocale
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.backup_preview_created, SimpleDateFormat("yyyy-MM-dd HH:mm", locale).format(Date.from(runCatching { Instant.parse(preview.manifest.createdAt) }.getOrElse { Instant.EPOCH }))) )
        Text(stringResource(R.string.backup_preview_format, preview.manifest.format, preview.manifest.formatVersion, preview.manifest.dbSchemaVersion))
        Text(stringResource(R.string.backup_preview_sha, preview.manifest.sha256.take(16).ifBlank { "—" } + "…"))
        Text(stringResource(R.string.backup_preview_device, preview.manifest.device.model, preview.manifest.device.sdkInt))
        Text(stringResource(R.string.backup_preview_counts, preview.manifest.counts.albums, preview.manifest.counts.tracks, preview.manifest.counts.ratings, preview.manifest.counts.manualCredits))
        Text(stringResource(R.string.backup_preview_current, preview.current.albums, preview.current.tracks))
        Text(stringResource(R.string.backup_mode))
        Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(mode == BACKUP_RESTORE_REPLACE, { onMode(BACKUP_RESTORE_REPLACE) }); Text(stringResource(R.string.backup_replace)) }
        Text(stringResource(R.string.backup_replace_warning), style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(mode == BACKUP_RESTORE_MERGE, { onMode(BACKUP_RESTORE_MERGE) }); Text(stringResource(R.string.backup_merge)) }
        Text(stringResource(R.string.backup_merge_note), style = MaterialTheme.typography.bodySmall)
    }
}

private fun shareCard(context: Context, snapshot: LibrarySnapshot, portrait: Boolean) {
    val album = snapshot.albums.firstOrNull() ?: return
    val artist = snapshot.artists.firstOrNull { it.id == album.artistId }?.name.orEmpty()
    val bitmap = ShareCardRenderer.render(ShareAlbum(album.title, artist, album.manualScoreOverride, album.genreTags, album.reviewText), portrait)
    val file = File(context.cacheDir, "share-card-${if (portrait) "portrait" else "square"}.png")
    file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, null))
}

private fun shareCollage(context: Context, snapshot: LibrarySnapshot, columns: Int) {
    val bitmap = CollageRenderer.render(snapshot.albums.map { CollageAlbum(it.title, it.manualScoreOverride) }, columns)
    val file = File(context.cacheDir, "share-collage-${columns}x$columns.png")
    file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, null))
}
