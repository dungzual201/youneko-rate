package com.youneko.rate.ui.export

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.youneko.rate.R
import com.youneko.rate.data.export.BACKUP_INPUT_URI
import com.youneko.rate.data.export.BACKUP_OUTPUT_URI
import com.youneko.rate.data.export.CollageAlbum
import com.youneko.rate.data.export.CollageRenderer
import com.youneko.rate.data.export.ExportWorker
import com.youneko.rate.data.export.EXPORT_FORMAT
import com.youneko.rate.data.export.EXPORT_FORMAT_CSV
import com.youneko.rate.data.export.EXPORT_FORMAT_JSON
import com.youneko.rate.data.export.EXPORT_OUTPUT_URI
import com.youneko.rate.data.export.LibrarySnapshot
import com.youneko.rate.data.export.ShareAlbum
import com.youneko.rate.data.export.ShareCardRenderer
import com.youneko.rate.data.export.exportSnapshot
import com.youneko.rate.data.export.scheduleWeeklyAutoBackup
import com.youneko.rate.data.export.BackupWorker
import com.youneko.rate.data.export.RestoreWorker
import com.youneko.rate.data.local.YounekoDatabase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import javax.inject.Inject

@HiltViewModel
class ExportViewModel @Inject constructor(private val database: YounekoDatabase) : ViewModel() {
    private val _snapshot = MutableStateFlow<LibrarySnapshot?>(null)
    val snapshot = _snapshot.asStateFlow()

    fun load() = viewModelScope.launch(Dispatchers.IO) { _snapshot.value = database.exportSnapshot() }
}

@Composable
fun ExportScreen(onBack: () -> Unit, viewModel: ExportViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    var pendingExportFormat by rememberSaveable { mutableStateOf(EXPORT_FORMAT_JSON) }
    LaunchedEffect(Unit) { viewModel.load() }

    fun enqueueExport(uri: android.net.Uri, format: String) {
        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<ExportWorker>().setInputData(workDataOf(EXPORT_OUTPUT_URI to uri.toString(), EXPORT_FORMAT to format)).build())
    }
    fun enqueueBackup(uri: android.net.Uri) {
        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<BackupWorker>().setInputData(workDataOf(BACKUP_OUTPUT_URI to uri.toString())).build())
    }
    fun enqueueRestore(uri: android.net.Uri) {
        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<RestoreWorker>().setInputData(workDataOf(BACKUP_INPUT_URI to uri.toString())).build())
    }

    val createExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> uri?.let { enqueueExport(it, pendingExportFormat) } }
    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> if (uri != null) enqueueBackup(uri) }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) enqueueRestore(uri) }

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.cancel)) }
        Text(stringResource(R.string.export_title), style = MaterialTheme.typography.headlineSmall)
        Button(onClick = { pendingExportFormat = EXPORT_FORMAT_JSON; createExport.launch("youneko-rate.json") }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.export_json)) }
        Button(onClick = { pendingExportFormat = EXPORT_FORMAT_CSV; createExport.launch("youneko-rate.csv") }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.export_csv)) }
        Button(onClick = { createBackup.launch("youneko-rate.${com.youneko.rate.data.export.BACKUP_EXTENSION}") }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.export_backup)) }
        Button(onClick = { openBackup.launch(arrayOf("application/octet-stream", "application/zip")) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.export_restore)) }
        Button(onClick = { scheduleWeeklyAutoBackup(context) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.export_backup)) }
        snapshot?.let { data ->
            Button(onClick = { shareCard(context, data, portrait = false) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.export_share_card)) }
            Button(onClick = { shareCard(context, data, portrait = true) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.export_share_portrait)) }
            Button(onClick = { shareCollage(context, data, 3) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.export_collage_3x3)) }
            Button(onClick = { shareCollage(context, data, 4) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.export_collage_4x4)) }
        }
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
