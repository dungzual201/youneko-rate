package com.youneko.rate.data.export

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room.withTransaction
import com.youneko.rate.BuildConfig
import com.youneko.rate.data.SettingsStore
import com.youneko.rate.data.local.YounekoDatabase
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val backupJson = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = true }

data class BackupProgress(val step: String, val done: Int = 0, val total: Int = 0)

data class ArchiveResult(val manifest: BackupManifest, val bytesWritten: Long, val databaseFile: File)

suspend fun YounekoDatabase.backupCounts(): BackupCounts {
    val albums = albumDao().findAll()
    val tracks = trackDao().findAll()
    val credits = creditDao().findAll()
    return BackupCounts(
        artists = artistDao().findAll().size,
        albums = albums.size,
        tracks = tracks.size,
        ratings = tracks.count { it.stars != null },
        manualCredits = credits.count { it.sourceProvider.split(',').any { value -> value.trim().equals("manual", true) } },
        credits = credits.size,
        covers = albums.count { !it.coverUri.isNullOrBlank() },
    )
}

suspend fun SettingsStore.safeBackupSnapshot(): SafeSettingsSnapshot = SafeSettingsSnapshot(
    offlineOnly = offlineOnly.first(),
    ratingScale = ratingScale.first(),
    scoreMode = scoreMode.first(),
    gridView = gridView.first(),
    dynamicColor = dynamicColor.first(),
    sortOrder = sortOrder.first(),
    unfinishedOnly = unfinishedOnly.first(),
    showCreditSources = showCreditSources.first(),
    creditSourceOrder = creditSourceOrder.first(),
    activeCreditSources = activeCreditSources.first(),
    creditsMergeMode = creditsMergeMode.first(),
)

suspend fun writeBackupArchive(
    context: Context,
    database: YounekoDatabase,
    settings: SettingsStore,
    output: OutputStream,
    includeCovers: Boolean = true,
    includeReadableExports: Boolean = true,
    isCancelled: () -> Boolean = { false },
    onProgress: (BackupProgress) -> Unit = {},
): ArchiveResult {
    onProgress(BackupProgress("Đang chuẩn bị dữ liệu…"))
    val tempDb = checkpointAndCopyDatabase(context, database)
    try {
        verifyDatabaseCopy(tempDb)
        val sha = sha256(tempDb)
        val snapshot = database.exportSnapshot()
        val counts = database.backupCounts()
        check(counts.albums == snapshot.albums.size && counts.tracks == snapshot.tracks.size) { "COUNT(*) không khớp snapshot" }
        val covers = if (includeCovers) snapshot.albums.mapNotNull { album ->
            val uri = album.coverUri ?: return@mapNotNull null
            if (openLocalInput(context, uri)?.use { true } == true) album.id to uri else null
        } else emptyList()
        val manifest = BackupManifest(
            createdAt = System.currentTimeMillis(),
            app = currentBackupAppInfo(),
            dbSchemaVersion = CURRENT_DATABASE_SCHEMA_VERSION,
            device = currentBackupDeviceInfo(),
            counts = counts.copy(covers = covers.size),
            includesCovers = includeCovers,
            checksum = mapOf(BACKUP_DATABASE_ENTRY to "sha256:$sha"),
        )
        verifyCountsAgainstManifest(tempDb, manifest.counts)
        ZipOutputStream(output).use { zip ->
            writeEntry(zip, BACKUP_MANIFEST, backupJson.encodeToString(manifest).toByteArray(Charsets.UTF_8))
            check(!isCancelled()) { "Đã huỷ" }
            zip.putNextEntry(ZipEntry(BACKUP_DATABASE_ENTRY))
            tempDb.inputStream().use { input -> copyWithProgress(input, zip, isCancelled) }
            zip.closeEntry()
            onProgress(BackupProgress("Đang ghi file…", 1, 1))
            covers.forEachIndexed { index, (albumId, uri) ->
                check(!isCancelled()) { "Đã huỷ" }
                val input = openLocalInput(context, uri) ?: return@forEachIndexed
                zip.putNextEntry(ZipEntry("covers/$albumId.jpg"))
                input.use { copyWithProgress(it, zip, isCancelled) }
                zip.closeEntry()
                onProgress(BackupProgress("Đang nén ảnh bìa (${index + 1}/${covers.size})…", index + 1, covers.size))
            }
            val safeSettings = settings.safeBackupSnapshot()
            writeEntry(zip, BACKUP_SETTINGS, backupJson.encodeToString(safeSettings).toByteArray(Charsets.UTF_8))
            if (includeReadableExports) {
                val ratingsCsv = snapshot.toRatingsCsv().toByteArray(Charsets.UTF_8)
                writeEntry(zip, BACKUP_EXPORT_RATINGS, byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + ratingsCsv)
                writeEntry(zip, BACKUP_EXPORT_CREDITS, backupJson.encodeToString(snapshot.credits).toByteArray(Charsets.UTF_8))
            }
        }
        val size = tempDb.length()
        return ArchiveResult(manifest, size, tempDb)
    } finally {
        tempDb.delete()
    }
}

fun checkpointAndCopyDatabase(context: Context, database: YounekoDatabase): File {
    database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
    val tempDb = File.createTempFile("youneko-backup-", ".db", context.cacheDir)
    val dbPath = File(database.openHelper.writableDatabase.path ?: error("Không tìm thấy đường dẫn database"))
    dbPath.inputStream().use { input -> tempDb.outputStream().use { outputFile -> input.copyTo(outputFile) } }
    return tempDb
}

fun verifyCountsAgainstManifest(file: File, expected: BackupCounts) {
    SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
        fun count(sql: String): Int = db.rawQuery(sql, null).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }
        check(count("SELECT COUNT(*) FROM albums") == expected.albums) { "COUNT(*) albums không khớp manifest" }
        check(count("SELECT COUNT(*) FROM tracks") == expected.tracks) { "COUNT(*) tracks không khớp manifest" }
        check(count("SELECT COUNT(*) FROM tracks WHERE stars IS NOT NULL") == expected.ratings) { "COUNT(*) ratings không khớp manifest" }
        check(count("SELECT COUNT(*) FROM credits") == expected.credits) { "COUNT(*) credits không khớp manifest" }
        check(count("SELECT COUNT(*) FROM credits WHERE lower(sourceProvider) LIKE '%manual%'") == expected.manualCredits) { "COUNT(*) manual credits không khớp manifest" }
    }
}

fun verifyDatabaseCopy(file: File) {
    SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
        db.rawQuery("PRAGMA integrity_check", null).use { cursor ->
            val result = if (cursor.moveToFirst()) cursor.getString(0) else "<empty>"
            check(result.equals("ok", true)) { "Database integrity_check thất bại: $result" }
        }
        db.rawQuery("SELECT COUNT(*) FROM albums", null).use { cursor -> check(cursor.moveToFirst()) }
        db.rawQuery("SELECT COUNT(*) FROM tracks", null).use { cursor -> check(cursor.moveToFirst()) }
    }
}

fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

fun openLocalInput(context: Context, uriString: String): InputStream? {
    val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return null
    return if (uri.scheme == "file" || uri.scheme == null) {
        uri.path?.let(::File)?.takeIf(File::exists)?.inputStream()
    } else {
        runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
    }
}

private fun writeEntry(zip: ZipOutputStream, name: String, bytes: ByteArray) {
    zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
}

private fun copyWithProgress(input: InputStream, output: OutputStream, isCancelled: () -> Boolean) {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        check(!isCancelled()) { "Đã huỷ" }
        val read = input.read(buffer)
        if (read <= 0) break
        output.write(buffer, 0, read)
    }
}

private fun LibrarySnapshot.toRatingsCsv(): String = buildString {
    appendLine("album,artist,track,trackNumber,stars,albumScore,tags,listenedDate,reviewExcerpt")
    val artistsById = artists.associateBy { it.id }
    val albumsById = albums.associateBy { it.id }
    tracks.forEach { track ->
        val album = track.albumId?.let(albumsById::get)
        val artist = album?.artistId?.let(artistsById::get)?.name.orEmpty()
        appendLine(listOf(album?.title.orEmpty(), artist, track.title, track.trackNumber, track.stars, album?.manualScoreOverride, album?.genreTags?.joinToString("|"), track.listenedDate, track.reviewText.orEmpty().take(160)).joinToString(",") { csv(it?.toString().orEmpty()) })
    }
}

private fun csv(value: String): String = "\"${value.replace("\"", "\"\"")}\""
