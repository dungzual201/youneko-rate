package com.youneko.rate.data.export

import android.content.Context
import android.net.Uri
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.documentfile.provider.DocumentFile
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val EXPORT_OUTPUT_URI = "export_output_uri"
const val EXPORT_FORMAT = "export_format"
const val EXPORT_FORMAT_JSON = "json"
const val EXPORT_FORMAT_CSV = "csv"
const val BACKUP_INPUT_URI = "backup_input_uri"
const val BACKUP_OUTPUT_URI = "backup_output_uri"
const val BACKUP_RESTORE_MODE = "backup_restore_mode"
const val BACKUP_RESTORE_REPLACE = "replace"
const val BACKUP_RESTORE_MERGE = "merge"
const val AUTO_BACKUP_TREE_URI = "auto_backup_tree_uri"

private val exportJson = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = true }

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ExportWorkerEntryPoint {
    fun database(): com.youneko.rate.data.local.YounekoDatabase
    fun settings(): com.youneko.rate.data.SettingsStore
    fun scanner(): com.youneko.rate.data.scan.MediaStoreScanner
}

class ExportWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun getForegroundInfo(): ForegroundInfo = backupForegroundInfo(applicationContext, "Đang xuất dữ liệu…", id, 0, 1)

    override suspend fun doWork(): Result = runCatching {
        val database = entryPoint().database()
        val snapshot = database.exportSnapshot()
        val outputUri = inputData.getString(EXPORT_OUTPUT_URI)?.let(Uri::parse)
            ?: return Result.failure(workDataOf("error" to "Thiếu URI xuất dữ liệu"))
        val format = inputData.getString(EXPORT_FORMAT) ?: EXPORT_FORMAT_JSON
        setProgress(workDataOf("step" to "Đang chuẩn bị dữ liệu…", "done" to 0, "total" to 1))
        val content = if (format == EXPORT_FORMAT_CSV) {
            byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + snapshot.toReadableCsv().toByteArray(Charsets.UTF_8)
        } else {
            exportJson.encodeToString(snapshot).toByteArray(Charsets.UTF_8)
        }
        applicationContext.contentResolver.openOutputStream(outputUri)?.use { it.write(content) }
            ?: return Result.failure(workDataOf("error" to "Không thể mở nơi lưu"))
        setProgress(workDataOf("step" to "Đang ghi tệp…", "done" to 1, "total" to 1))
        Result.success(workDataOf("format" to format, "count" to snapshot.albums.size))
    }.getOrElse { Result.failure(workDataOf("error" to (it.message ?: "Xuất dữ liệu thất bại"))) }

    private fun entryPoint() = EntryPointAccessors.fromApplication(applicationContext, ExportWorkerEntryPoint::class.java)
}

open class BackupWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun getForegroundInfo(): ForegroundInfo = backupForegroundInfo(applicationContext, "Đang sao lưu dữ liệu…", id, 0, 1)

    override suspend fun doWork(): Result {
        val autoTreeUri = inputData.getString(AUTO_BACKUP_TREE_URI)?.let(Uri::parse)
        val outputUri = inputData.getString(BACKUP_OUTPUT_URI)?.let(Uri::parse)
            ?: autoTreeUri?.let { DocumentFile.fromTreeUri(applicationContext, it)?.createFile("application/octet-stream", "YounekoRate_${timestamp()}.$BACKUP_EXTENSION")?.uri }
        val localFile = if (outputUri == null) {
            File(applicationContext.filesDir, "backups/YounekoRate_${timestamp()}.$BACKUP_EXTENSION").apply { parentFile?.mkdirs() }
        } else null
        return try {
            val output = outputUri?.let { applicationContext.contentResolver.openOutputStream(it) }
                ?: localFile!!.outputStream()
                ?: return Result.failure(workDataOf("error" to "Không thể mở nơi lưu"))
            output.use { stream ->
                writeBackupArchive(stream)
            }
            if (autoTreeUri != null) pruneTreeBackups(autoTreeUri) else pruneBackups()
            Result.success(workDataOf("message" to "Đã sao lưu dữ liệu"))
        } catch (cancelled: CancellationException) {
            localFile?.delete()
            outputUri?.let { runCatching { applicationContext.contentResolver.delete(it, null, null) } }
            throw cancelled
        } catch (error: Throwable) {
            localFile?.delete()
            outputUri?.let { runCatching { applicationContext.contentResolver.delete(it, null, null) } }
            Result.failure(workDataOf("error" to (error.message ?: "Sao lưu thất bại")))
        }
    }

    private suspend fun writeBackupArchive(output: java.io.OutputStream) {
        val entry = entryPoint()
        writeBackupArchive(
            context = applicationContext,
            database = entry.database(),
            settings = entry.settings(),
            output = output,
            includeCovers = inputData.getBoolean("include_covers", true),
            includeReadableExports = inputData.getBoolean("include_exports", true),
            isCancelled = { isStopped },
                            onProgress = { progress ->
                    setProgressAsync(workDataOf("step" to progress.step, "done" to progress.done, "total" to progress.total))
                    setForegroundAsync(backupForegroundInfo(applicationContext, progress.step, id, progress.done, progress.total))
                },

        )
    }

    private fun pruneTreeBackups(treeUri: Uri) {
        DocumentFile.fromTreeUri(applicationContext, treeUri)?.listFiles()
            ?.filter { it.name?.endsWith(".$BACKUP_EXTENSION") == true }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(5)
            ?.forEach { it.delete() }
    }

    private fun pruneBackups() {
        val dir = File(applicationContext.filesDir, "backups")
        dir.listFiles { file -> file.extension == BACKUP_EXTENSION }
            ?.sortedByDescending(File::lastModified)
            ?.drop(5)
            ?.forEach(File::delete)
    }

    private fun entryPoint() = EntryPointAccessors.fromApplication(applicationContext, ExportWorkerEntryPoint::class.java)
}

class AutoBackupWorker(appContext: Context, params: WorkerParameters) : BackupWorker(appContext, params)

class RestoreWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun getForegroundInfo(): ForegroundInfo = backupForegroundInfo(applicationContext, "Đang khôi phục dữ liệu…", id, 0, 1)

    override suspend fun doWork(): Result = try {
        val uri = inputData.getString(BACKUP_INPUT_URI)?.let(Uri::parse)
            ?: return Result.failure(workDataOf("error" to "Thiếu file sao lưu"))
        val mode = if (inputData.getString(BACKUP_RESTORE_MODE) == BACKUP_RESTORE_MERGE) RestoreMode.MERGE else RestoreMode.REPLACE
        val entry = entryPoint()
        val result = restoreBackup(applicationContext, entry.database(), entry.settings(), entry.scanner(), uri, mode)
        Result.success(workDataOf("message" to result.message, "matched" to (result.report?.matchedTracks ?: 0), "total" to (result.report?.totalTracks ?: 0)))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(workDataOf("error" to (error.message ?: "Khôi phục thất bại")))
    }

    private fun entryPoint() = EntryPointAccessors.fromApplication(applicationContext, ExportWorkerEntryPoint::class.java)
}

fun scheduleWeeklyAutoBackup(context: Context, treeUri: Uri? = null) {
    val data = treeUri?.let { workDataOf(AUTO_BACKUP_TREE_URI to it.toString()) } ?: androidx.work.Data.EMPTY
    val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(7, TimeUnit.DAYS)
        .setInputData(data)
        .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).setRequiresDeviceIdle(true).build())
        .build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork("youneko-weekly-backup", ExistingPeriodicWorkPolicy.UPDATE, request)
}

private fun timestamp(): String = java.text.SimpleDateFormat("yyyy-MM-dd_HHmm", java.util.Locale.US).format(java.util.Date())

private fun backupForegroundInfo(context: Context, title: String, workId: UUID, done: Int, total: Int): ForegroundInfo {
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.createNotificationChannel(NotificationChannel("background_tasks", "Tác vụ nền", NotificationManager.IMPORTANCE_LOW))
    val cancel = WorkManager.getInstance(context).createCancelPendingIntent(workId)
    return ForegroundInfo(2202, androidx.core.app.NotificationCompat.Builder(context, "background_tasks")
        .setSmallIcon(android.R.drawable.stat_sys_upload)
        .setContentTitle("Youneko Rate!")
        .setContentText(title)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setProgress(total.coerceAtLeast(1), done.coerceIn(0, total.coerceAtLeast(1)), total <= 0)
        .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Huỷ", cancel)
        .build())
}

private fun LibrarySnapshot.toReadableCsv(): String = buildString {
    appendLine("album,artist,track,trackNumber,stars,albumScore,tags,listenedDate,reviewExcerpt")
    val artistsById = artists.associateBy { it.id }
    val albumsById = albums.associateBy { it.id }
    tracks.forEach { track ->
        val album = track.albumId?.let(albumsById::get)
        val artist = album?.artistId?.let(artistsById::get)?.name.orEmpty()
        appendLine(listOf(album?.title.orEmpty(), artist, track.title, track.trackNumber, track.stars, album?.manualScoreOverride, album?.genreTags?.joinToString("|"), track.listenedDate, track.reviewText.orEmpty().take(160)).joinToString(",") { "\"${it?.toString().orEmpty().replace("\"", "\"\"")}\"" })
    }
}
