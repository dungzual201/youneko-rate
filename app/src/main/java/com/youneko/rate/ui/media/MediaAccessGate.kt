package com.youneko.rate.ui.media

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.youneko.rate.R
import com.youneko.rate.data.local.dao.ScanRootDao
import com.youneko.rate.data.local.entity.ScanRootEntity
import com.youneko.rate.data.scan.MediaStoreScanWorker
import com.youneko.rate.data.scan.UNIQUE_ON_RESUME
import com.youneko.rate.data.scan.enqueueMediaScan
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.work.WorkInfo
import androidx.work.WorkManager

private fun requiredAudioPermission(): String = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

fun hasAudioPermission(context: Context): Boolean = ContextCompat.checkSelfPermission(context, requiredAudioPermission()) == PackageManager.PERMISSION_GRANTED

@HiltViewModel
class MediaAccessViewModel @Inject constructor(
    private val scanRootDao: ScanRootDao,
) : ViewModel() {
    val scanRoots = scanRootDao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addScanRoot(uri: Uri, displayName: String?) {
        viewModelScope.launch {
            scanRootDao.upsert(ScanRootEntity(uri.toString(), displayName, System.currentTimeMillis()))
        }
    }

    fun removeScanRoot(uri: String) {
        viewModelScope.launch { scanRootDao.delete(uri) }
    }
}

@Composable
fun MediaAccessGate(
    content: @Composable () -> Unit,
    viewModel: MediaAccessViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val roots by viewModel.scanRoots.collectAsStateWithLifecycle()
    val scanInfos by remember(context) { WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(UNIQUE_ON_RESUME) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val scanInfo = scanInfos.firstOrNull()
    var permissionGranted by rememberSaveable { mutableStateOf(hasAudioPermission(context)) }
    var showEducation by rememberSaveable { mutableStateOf(!permissionGranted) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionGranted = hasAudioPermission(context)
        showEducation = !permissionGranted
        if (permissionGranted) enqueueMediaScan(context, forceFull = true)
    }
    val treeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val name = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)?.name
            viewModel.addScanRoot(uri, name)
            enqueueMediaScan(context)
        }
    }
    LaunchedEffect(Unit) { permissionGranted = hasAudioPermission(context) }
    Box {
        content()
        if (permissionGranted && scanInfo?.state in setOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING)) {
            val done = scanInfo?.progress?.getInt(MediaStoreScanWorker.KEY_DONE, 0) ?: 0
            val total = scanInfo?.progress?.getInt(MediaStoreScanWorker.KEY_TOTAL, 0) ?: 0
            Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
                Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.media_scan_progress, done, total), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                    IconButton(onClick = { WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_ON_RESUME) }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel), modifier = Modifier.size(20.dp))
                    }
                }
                LinearProgressIndicator(progress = { if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f }, modifier = Modifier.fillMaxWidth())
            }
        }
        if (!permissionGranted) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                Column(
                    Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.media_permission_empty_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.media_permission_empty_body), style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { showEducation = true }) { Text(stringResource(R.string.media_permission_grant)) }
                        OutlinedButton(onClick = { treeLauncher.launch(null) }) { Text(stringResource(R.string.media_scan_add_folder)) }
                    }
                    TextButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                    }) { Text(stringResource(R.string.media_permission_open_settings)) }
                    if (roots.isNotEmpty()) {
                        Text(stringResource(R.string.media_scan_roots), style = MaterialTheme.typography.titleSmall)
                        roots.forEach { root ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(root.displayName ?: root.uri, modifier = Modifier.weight(1f))
                                TextButton(onClick = { viewModel.removeScanRoot(root.uri) }) { Text(stringResource(R.string.media_scan_remove_folder)) }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showEducation && !permissionGranted) {
        AlertDialog(
            onDismissRequest = { showEducation = false },
            title = { Text(stringResource(R.string.media_permission_title)) },
            text = { Text(stringResource(R.string.media_permission_explanation)) },
            confirmButton = {
                Button(onClick = {
                    showEducation = false
                    permissionLauncher.launch(requiredAudioPermission())
                }) { Text(stringResource(R.string.media_permission_continue)) }
            },
            dismissButton = { TextButton(onClick = { showEducation = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
fun MediaScanRootManager(viewModel: MediaAccessViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val roots by viewModel.scanRoots.collectAsStateWithLifecycle()
    val treeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val name = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)?.name
            viewModel.addScanRoot(uri, name)
            enqueueMediaScan(context)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.media_scan_roots), style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { treeLauncher.launch(null) }) {
            Text(stringResource(R.string.media_scan_add_folder))
        }
        roots.forEach { root ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(root.displayName ?: root.uri, modifier = Modifier.weight(1f))
                TextButton(onClick = { viewModel.removeScanRoot(root.uri) }) { Text(stringResource(R.string.media_scan_remove_folder)) }
            }
        }
    }
}
