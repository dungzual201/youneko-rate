package com.youneko.rate.data.export

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val EXPORT_OUTPUT_URI = "export_output_uri"
const val EXPORT_FORMAT = "export_format"
const val EXPORT_FORMAT_JSON = "json"
const val EXPORT_FORMAT_CSV = "csv"
const val BACKUP_INPUT_URI = "backup_input_uri"
const val BACKUP_OUTPUT_URI = "backup_output_uri"
const val BACKUP_MANIFEST = "manifest.json"
const val BACKUP_LIBRARY = "library.json"
const val BACKUP_SETTINGS = "settings.json"

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ExportWorkerEntryPoint {
    fun database(): com.youneko.rate.data.local.YounekoDatabase
}

private val exportJson = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = true }

class ExportWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = runCatching {
        val database = entryPoint().database()
        val snapshot = database.exportSnapshot()
        val outputUri = inputData.getString(EXPORT_OUTPUT_URI)?.let(Uri::parse)
            ?: return Result.failure(workDataOf("error" to "Missing output URI"))
        val format = inputData.getString(EXPORT_FORMAT) ?: EXPORT_FORMAT_JSON
        val content = if (format == EXPORT_FORMAT_CSV) snapshot.toCsv() else exportJson.encodeToString(snapshot)
        applicationContext.contentResolver.openOutputStream(outputUri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
            ?: return Result.failure(workDataOf("error" to "Cannot open output URI"))
        Result.success(workDataOf("format" to format, "count" to snapshot.albums.size))
    }.getOrElse { Result.failure(workDataOf("error" to (it.message ?: "Export failed"))) }

    private fun entryPoint() = EntryPointAccessors.fromApplication(applicationContext, ExportWorkerEntryPoint::class.java)
}

open class BackupWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = runCatching {
        val outputUri = inputData.getString(BACKUP_OUTPUT_URI)?.let(Uri::parse)
        val target = outputUri?.let { applicationContext.contentResolver.openOutputStream(it) }
            ?: run {
                val dir = File(applicationContext.filesDir, "backups").apply { mkdirs() }
                File(dir, "backup-${System.currentTimeMillis()}.$BACKUP_EXTENSION").outputStream()
            }
        target.use { output ->
            ZipOutputStream(output).use { zip -> writeArchive(zip) }
        }
        pruneBackups()
        Result.success()
    }.getOrElse { Result.failure(workDataOf("error" to (it.message ?: "Backup failed"))) }

    private suspend fun writeArchive(zip: ZipOutputStream) {
        val database = entryPoint().database()
        val snapshot = database.exportSnapshot()
        zip.writeText(BACKUP_MANIFEST, exportJson.encodeToString(BackupManifest(CURRENT_BACKUP_SCHEMA, System.currentTimeMillis())))
        zip.writeText(BACKUP_LIBRARY, exportJson.encodeToString(snapshot))
        zip.writeText(BACKUP_SETTINGS, exportJson.encodeToString(SafeSettingsSnapshot()))
        val dbFile = applicationContext.getDatabasePath("youneko_rate.db")
        listOf(dbFile, File(dbFile.path + "-wal"), File(dbFile.path + "-shm")).filter(File::exists).forEach { file ->
            zip.putNextEntry(ZipEntry("database/${file.name}"))
            file.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
        snapshot.albums.forEach { album ->
            val uri = album.coverUri ?: return@forEach
            val input = runCatching { applicationContext.contentResolver.openInputStream(Uri.parse(uri)) }.getOrNull() ?: return@forEach
            zip.putNextEntry(ZipEntry("covers/${album.id}.bin"))
            input.use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }

    private fun ZipOutputStream.writeText(name: String, text: String) {
        putNextEntry(ZipEntry(name)); write(text.toByteArray(Charsets.UTF_8)); closeEntry()
    }

    private fun pruneBackups() {
        val dir = File(applicationContext.filesDir, "backups")
        dir.listFiles { file -> file.extension == BACKUP_EXTENSION }
            ?.sortedByDescending(File::lastModified)
            ?.drop(3)
            ?.forEach(File::delete)
    }

    private fun entryPoint() = EntryPointAccessors.fromApplication(applicationContext, ExportWorkerEntryPoint::class.java)
}

class AutoBackupWorker(appContext: Context, params: WorkerParameters) : BackupWorker(appContext, params)

class RestoreWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = runCatching {
        val uri = inputData.getString(BACKUP_INPUT_URI)?.let(Uri::parse)
            ?: return Result.failure(workDataOf("error" to "Missing backup URI"))
        val staging = File(applicationContext.cacheDir, "restore-staging-${System.currentTimeMillis()}").apply { mkdirs() }
        applicationContext.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                var schema: Int? = null
                while (entry != null) {
                    val target = File(staging, entry.name).canonicalFile
                    require(target.path.startsWith(staging.canonicalPath + File.separator)) { "Unsafe backup entry" }
                    if (!entry.isDirectory) {
                        target.parentFile?.mkdirs()
                        target.outputStream().use { zip.copyTo(it) }
                        if (entry.name == BACKUP_MANIFEST) schema = exportJson.decodeFromString<BackupManifest>(target.readText()).schemaVersion
                    }
                    zip.closeEntry(); entry = zip.nextEntry
                }
                require(schema != null && schema!! <= CURRENT_BACKUP_SCHEMA) { "Incompatible backup schema: $schema" }
            }
        } ?: return Result.failure(workDataOf("error" to "Cannot open backup URI"))
        Result.success(workDataOf("staging" to staging.path))
    }.getOrElse { Result.failure(workDataOf("error" to (it.message ?: "Restore failed"))) }
}

fun scheduleWeeklyAutoBackup(context: Context) {
    val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(7, TimeUnit.DAYS)
        .setConstraints(androidx.work.Constraints.Builder().setRequiresBatteryNotLow(true).build())
        .build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork("youneko-weekly-backup", ExistingPeriodicWorkPolicy.UPDATE, request)
}

@kotlinx.serialization.Serializable
data class BackupManifest(val schemaVersion: Int, val createdAt: Long, val format: String = BACKUP_EXTENSION)

@kotlinx.serialization.Serializable
data class SafeSettingsSnapshot(
    val note: String = "Provider credentials are intentionally excluded from backups.",
)

private fun LibrarySnapshot.toCsv(): String = buildString {
    appendLine("album_id,album_title,artist_id,release_year,album_score,track_id,track_title,track_score,track_review,credit_person,credit_role,credit_source")
    val albumById = albums.associateBy { it.id }
    val tracksByAlbum = tracks.groupBy { it.albumId }
    val creditsByScope = credits.groupBy { it.albumId to it.trackId }
    albums.forEach { album ->
        val albumTracks = tracksByAlbum[album.id].orEmpty().ifEmpty { listOf(null) }
        albumTracks.forEach { track ->
            val credits = creditsByScope[album.id to track?.id].orEmpty().ifEmpty { listOf(null) }
            credits.forEach { credit ->
                appendLine(listOf(album.id, album.title, album.artistId, album.releaseYear, album.manualScoreOverride, track?.id, track?.title, track?.stars, track?.reviewText, credit?.personName, credit?.role, credit?.sourceProvider).joinToString(",") { csv(it?.toString().orEmpty()) })
            }
        }
    }
}

private fun csv(value: String): String = "\"${value.replace("\"", "\"\"")}\""
