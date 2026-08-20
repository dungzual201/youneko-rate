package com.youneko.rate.data.export

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import com.youneko.rate.data.PendingRestoreStore
import com.youneko.rate.data.SettingsStore
import com.youneko.rate.data.local.YounekoDatabase
import com.youneko.rate.data.local.entity.AlbumEntity
import com.youneko.rate.data.local.entity.AlbumTagEntity
import com.youneko.rate.data.local.entity.ArtistEntity
import com.youneko.rate.data.local.entity.AudioAnalysisEntity
import com.youneko.rate.data.local.entity.CollectionAlbumEntity
import com.youneko.rate.data.local.entity.CollectionEntity
import com.youneko.rate.data.local.entity.CreditEntity
import com.youneko.rate.data.local.entity.ExternalLinkEntity
import com.youneko.rate.data.local.entity.ListeningLogEntity
import com.youneko.rate.data.local.entity.ReviewRevisionEntity
import com.youneko.rate.data.local.entity.TrackEntity
import com.youneko.rate.data.scan.MediaStoreScanner
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

private val restoreJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

data class RestoreResult(val message: String, val report: RestoreReport? = null)
private data class StagedBackup(val directory: File, val manifest: BackupManifest)

suspend fun validateBackup(context: Context, database: YounekoDatabase, uri: android.net.Uri): BackupValidation = withContext(Dispatchers.IO) {
    runCatching {
        val staged = stageBackup(context, uri)
        val valid = validateStaged(staged)
        if (!valid.ok) valid else BackupValidation(true, preview = BackupPreview(staged.manifest, database.backupCounts(), true))
    }.getOrElse { BackupValidation(false, it.message ?: "File sao lưu không hợp lệ") }
}

suspend fun restoreBackup(
    context: Context,
    database: YounekoDatabase,
    settings: SettingsStore,
    scanner: MediaStoreScanner,
    uri: android.net.Uri,
    mode: RestoreMode,
): RestoreResult = withContext(Dispatchers.IO) {
    val staged = stageBackup(context, uri).let { if (mode == RestoreMode.REPLACE) persistForReplace(context, it) else it }
    val validation = validateStaged(staged)
    check(validation.ok) { validation.message ?: "File sao lưu không hợp lệ" }
    restoreSafeSettings(settings, staged.directory)
    if (mode == RestoreMode.REPLACE) {
        val dbFile = context.getDatabasePath("youneko_rate.db")
        val preRestore = File(context.cacheDir, "pre_restore_backup.db")
        database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
        dbFile.inputStream().use { input -> preRestore.outputStream().use { input.copyTo(it) } }
        val pending = File(staged.directory, "database/youneko.db")
        verifyDatabaseCopy(pending)
        PendingRestoreStore(context).setPending(staged.directory.absolutePath, preRestore.absolutePath)
        database.close()
        File(dbFile.path + "-wal").delete()
        File(dbFile.path + "-shm").delete()
        pending.inputStream().use { input -> dbFile.outputStream().use { input.copyTo(it) } }
        return@withContext RestoreResult("Đã nhập xong. Cần mở lại app để áp dụng.")
    }
    val backupDb = openBackupDatabase(context, staged.directory)
    try {
        val snapshot = backupDb.exportSnapshot()
        val report = mergeSnapshot(context, database, snapshot, staged.directory)
        database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
        scanner.scan(forceFull = true)
        RestoreResult(report.summary, report)
    } finally {
        backupDb.close()
    }
}

suspend fun reconcilePendingRestore(context: Context, database: YounekoDatabase, scanner: MediaStoreScanner): RestoreResult? = withContext(Dispatchers.IO) {
    val state = PendingRestoreStore(context).state()
    if (!state.first) return@withContext null
    val staging = state.second?.let(::File)
    val rollback = state.third?.let(::File)
    runCatching {
        database.openHelper.writableDatabase.query("PRAGMA integrity_check").use { cursor ->
            check(cursor.moveToFirst() && cursor.getString(0).equals("ok", true))
        }
        if (staging != null) remapRestoredCovers(context, database, staging)
        val scan = scanner.scan(forceFull = true)
        PendingRestoreStore(context).clear()
        rollback?.delete()
        staging?.deleteRecursively()
        val total = database.trackDao().findAll().size
        RestoreResult("Đã khôi phục dữ liệu và bắt đầu khớp lại thư viện.", RestoreReport(total - scan.missing, total, scan.missing, 0, 0))
    }.getOrElse { error ->
        database.close()
        val dbFile = context.getDatabasePath("youneko_rate.db")
        if (rollback?.exists() == true) rollback.inputStream().use { input -> dbFile.outputStream().use { input.copyTo(it) } }
        PendingRestoreStore(context).clear()
        RestoreResult("Khôi phục thất bại; đã rollback bản DB trước đó: ${error.message}")
    }
}

private fun persistForReplace(context: Context, staged: StagedBackup): StagedBackup {
    val target = File(context.filesDir, "restore/pending-${System.currentTimeMillis()}").apply { mkdirs() }
    staged.directory.copyRecursively(target, overwrite = true)
    staged.directory.deleteRecursively()
    return StagedBackup(target, staged.manifest)
}

private suspend fun restoreSafeSettings(settings: SettingsStore, staging: File) {
    val file = File(staging, BACKUP_SETTINGS)
    if (!file.exists()) return
    val safe = restoreJson.decodeFromString<SafeSettingsSnapshot>(file.readText())
    settings.setOfflineOnly(safe.offlineOnly)
    settings.setRatingScale(safe.ratingScale)
    settings.setScoreMode(safe.scoreMode)
    settings.setGridView(safe.gridView)
    settings.setDynamicColor(safe.dynamicColor)
    settings.setSortOrder(safe.sortOrder)
    settings.setUnfinishedOnly(safe.unfinishedOnly)
    settings.setShowCreditSources(safe.showCreditSources)
    settings.setCreditSourceOrder(safe.creditSourceOrder)
    settings.setActiveCreditSources(safe.activeCreditSources)
    settings.setCreditsMergeMode(safe.creditsMergeMode)
}

private fun stageBackup(context: Context, uri: android.net.Uri): StagedBackup {
    val staging = File(context.cacheDir, "restore-staging-${System.currentTimeMillis()}").apply { mkdirs() }
    val input = context.contentResolver.openInputStream(uri) ?: error("Không thể mở file sao lưu")
    input.use { source ->
        ZipInputStream(source).use { zip ->
            val first = zip.nextEntry ?: error("File sao lưu trống")
            check(first.name == BACKUP_MANIFEST) { "Thiếu manifest.json ở đầu file sao lưu" }
            val manifestFile = File(staging, BACKUP_MANIFEST)
            manifestFile.outputStream().use { zip.copyTo(it) }
            val manifest = restoreJson.decodeFromString<BackupManifest>(manifestFile.readText())
            zip.closeEntry()
            var entry = zip.nextEntry
            while (entry != null) {
                val target = File(staging, entry.name).canonicalFile
                check(target.path == staging.canonicalPath || target.path.startsWith(staging.canonicalPath + File.separator)) { "Entry backup không an toàn" }
                if (!entry.isDirectory) {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { zip.copyTo(it) }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
            return StagedBackup(staging, manifest)
        }
    }
}

fun validateBackupManifest(manifest: BackupManifest, actualSha256: String?, databaseExists: Boolean): BackupValidation {
    if (manifest.format != "younekorate" && manifest.format != "younekorate-backup") return BackupValidation(false, "Sai định dạng file sao lưu")
    if (manifest.formatVersion > CURRENT_BACKUP_FORMAT_VERSION) return BackupValidation(false, "Bản sao lưu được tạo bởi phiên bản app mới hơn. Vui lòng cập nhật app rồi thử lại.")
    if (manifest.dbSchemaVersion > CURRENT_DATABASE_SCHEMA_VERSION) return BackupValidation(false, "Schema database của bản sao lưu mới hơn phiên bản app này.")
    if (!databaseExists) return BackupValidation(false, "File sao lưu thiếu database/youneko.db")
    val expected = manifest.sha256.takeIf { it.isNotBlank() }
        ?: manifest.checksum[BACKUP_DATABASE_ENTRY]?.removePrefix("sha256:")
    if (expected.isNullOrBlank() || actualSha256.isNullOrBlank() || !expected.equals(actualSha256, true)) return BackupValidation(false, "File sao lưu bị hỏng hoặc không đầy đủ.")
    return BackupValidation(true)
}

private fun validateStaged(staged: StagedBackup): BackupValidation {
    val db = File(staged.directory, BACKUP_DATABASE_ENTRY)
    val validation = validateBackupManifest(staged.manifest, if (db.exists()) sha256(db) else null, db.exists())
    if (!validation.ok) return validation
    verifyDatabaseCopy(db)
    return validation
}

private fun openBackupDatabase(context: Context, directory: File): YounekoDatabase =
    Room.databaseBuilder(context, YounekoDatabase::class.java, File(directory, BACKUP_DATABASE_ENTRY).absolutePath)
        .addMigrations(
            YounekoDatabase.MIGRATION_1_2, YounekoDatabase.MIGRATION_2_3, YounekoDatabase.MIGRATION_3_4,
            YounekoDatabase.MIGRATION_4_5, YounekoDatabase.MIGRATION_5_6, YounekoDatabase.MIGRATION_6_7,
            YounekoDatabase.MIGRATION_7_8, YounekoDatabase.MIGRATION_8_9, YounekoDatabase.MIGRATION_9_10,
            YounekoDatabase.MIGRATION_10_11, YounekoDatabase.MIGRATION_11_12, YounekoDatabase.MIGRATION_12_13,
            YounekoDatabase.MIGRATION_13_14, YounekoDatabase.MIGRATION_14_15, YounekoDatabase.MIGRATION_15_16,
        ).build()

private suspend fun mergeSnapshot(context: Context, database: YounekoDatabase, snapshot: LibrarySnapshot, staging: File): RestoreReport {
    val currentArtists = database.artistDao().findAll().associateBy { it.id }.toMutableMap()
    val currentAlbums = database.albumDao().findAll().associateBy { it.id }.toMutableMap()
    val currentTracks = database.trackDao().findAll().toMutableList()
    val backupArtists = snapshot.artists.associateBy { it.id }
    val artistMap = mutableMapOf<String, String>()
    var inserted = 0
    var merged = 0
    database.withTransaction {
        snapshot.artists.forEach { value ->
            val existing = currentArtists.values.firstOrNull { it.mbid == value.mbid && !value.mbid.isNullOrBlank() } ?: currentArtists.values.firstOrNull { it.name.equals(value.name, true) }
            val target = existing ?: ArtistEntity(value.id, value.name, value.sortName, value.imageUri, value.mbid, value.note, value.createdAt, value.updatedAt)
            artistMap[value.id] = target.id
            if (existing == null) { database.artistDao().insert(target); currentArtists[target.id] = target; inserted++ }
        }
        val albumMap = mutableMapOf<String, String>()
        snapshot.albums.forEach { value ->
            val artistId = artistMap[value.artistId] ?: value.artistId
            val existing = currentAlbums.values.firstOrNull { candidate ->
                candidate.mbid == value.mbid && !value.mbid.isNullOrBlank() ||
                    (candidate.title.equals(value.title, true) && candidate.artistId == artistId && candidate.releaseYear == value.releaseYear)
            }
            val target = AlbumEntity(value.id, value.title, artistId, value.releaseYear, null, null, value.genreTags, value.albumType, value.label, value.catalogNumber, value.barcode, value.country, value.listenedDate, value.manualScoreOverride, value.reviewText, value.mbid, value.releaseGroupMbid, value.discogsReleaseId, value.deezerId, value.sourceProvider, value.coverSource, value.coverWidth, value.coverUpdatedAt, value.metadataFetchedAt, value.createdAt, value.updatedAt)
            if (existing == null) { database.albumDao().insert(target); currentAlbums[target.id] = target; inserted++ } else {
                val review = mergeText(existing.reviewText, value.reviewText)
                val newer = if (value.updatedAt > existing.updatedAt) target.copy(id = existing.id, coverUri = existing.coverUri, coverThumbUri = existing.coverThumbUri, reviewText = review) else existing.copy(reviewText = review)
                database.albumDao().update(newer); currentAlbums[existing.id] = newer; merged++
            }
            albumMap[value.id] = existing?.id ?: value.id
        }
        val trackMap = mutableMapOf<String, String>()
        snapshot.tracks.forEach { value ->
            val albumId = value.albumId?.let { albumMap[it] }
            val artistId = albumId?.let { currentAlbums[it]?.artistId }
            val artistName = artistId?.let { currentArtists[it]?.name }.orEmpty()
            val backupArtistName = value.albumId?.let { backupAlbumId -> snapshot.albums.firstOrNull { it.id == backupAlbumId }?.artistId }?.let { backupArtists[it]?.name }
            val existing = currentTracks.firstOrNull { candidate -> trackMatchesForRestore(value, candidate, backupArtistName, artistName) }
            val presentOnThisDevice = value.sourceUri?.let { source -> openLocalInput(context, source)?.use { true } } ?: false
            val target = TrackEntity(value.id, albumId, value.title, value.trackNumber, value.discNumber, value.durationMs, value.isStandalone, value.stars, value.reviewText, value.isSkip, value.isHighlight, value.listenedDate, value.recordingMbid, value.workMbid, value.isrc, value.sourceUri, value.fileName, value.createdAt, value.updatedAt, stableKey = value.stableKey, fileSizeBytes = value.fileSizeBytes, fileHash64k = value.fileHash64k, isMissing = !presentOnThisDevice, missingSince = if (presentOnThisDevice) null else System.currentTimeMillis())
            if (existing == null) { database.trackDao().insert(target); currentTracks += target; trackMap[value.id] = target.id; inserted++ } else {
                val newer = if (value.updatedAt > existing.updatedAt) target.copy(id = existing.id, sourceUri = existing.sourceUri, fileName = existing.fileName, mediaStoreId = existing.mediaStoreId, stableKey = existing.stableKey ?: target.stableKey, isMissing = existing.isMissing, missingSince = existing.missingSince, stars = target.stars ?: existing.stars, reviewText = mergeText(existing.reviewText, target.reviewText)) else existing.copy(reviewText = mergeText(existing.reviewText, target.reviewText))
                database.trackDao().update(newer); trackMap[value.id] = existing.id; merged++
            }
        }
        snapshot.credits.forEach { value ->
            val mapped = value.copyIds(albumMap, trackMap)
            val duplicate = database.creditDao().findAll().firstOrNull { sameCredit(it, mapped) }
            if (duplicate == null) database.creditDao().upsertAll(listOf(if (database.creditDao().findAll().any { it.id == mapped.id }) mapped.copy(id = UUID.randomUUID().toString()) else mapped))
        }
        snapshot.reviewRevisions.forEach { value -> database.reviewRevisionDao().insert(ReviewRevisionEntity(value.id, albumMap[value.albumId] ?: value.albumId, value.trackId?.let { trackMap[it] }, value.body, value.createdAt)) }
        snapshot.albumTags.forEach { value -> database.albumTagDao().insert(AlbumTagEntity(UUID.randomUUID().toString(), albumMap[value.albumId] ?: value.albumId, value.name, value.createdAt)) }
        snapshot.listeningLogs.forEach { value -> database.listeningLogDao().insert(ListeningLogEntity(UUID.randomUUID().toString(), albumMap[value.albumId] ?: value.albumId, value.trackId?.let { trackMap[it] }, value.listenedAt, value.note)) }
        snapshot.externalLinks.forEach { value -> database.externalLinkDao().upsert(ExternalLinkEntity(UUID.randomUUID().toString(), value.albumId?.let { albumMap[it] }, value.trackId?.let { trackMap[it] }, value.sourceId, value.externalId, value.sourceUrl, value.createdAt)) }
        snapshot.analyses.forEach { value -> database.audioAnalysisDao().upsert(AudioAnalysisEntity(value.id, value.trackId?.let { trackMap[it] }, value.albumId?.let { albumMap[it] }, value.fileName, value.fileUriOrPath, value.fileHash, value.container, value.codec, value.sampleRate, value.bitDepth, value.bitrate, value.isVbr, value.channels, value.durationMs, value.encoderTag, value.cutoffHz, value.rolloffSlope, value.dynamicRangeDb, value.truePeakDbtp, value.clippingPercent, value.verdict, value.confidence, value.reasonsJson, value.spectrumJson, value.spectrogramPngPath, value.engineVersion, value.analyzedAt)) }
        snapshot.collections.forEach { value -> database.collectionDao().upsertCollection(CollectionEntity(value.id, value.name, value.description, value.createdAt)) }
        snapshot.collectionAlbums.forEach { value -> albumMap[value.albumId]?.let { database.collectionDao().upsertAlbum(CollectionAlbumEntity(value.collectionId, it, value.sortOrder)) } }
        snapshot.scanRoots.forEach { value -> database.scanRootDao().upsert(com.youneko.rate.data.local.entity.ScanRootEntity(value.uri, value.displayName, value.addedAt, value.lastScannedAt)) }
        remapStagedCovers(context, database, staging, albumMap)
    }
    val matched = currentTracks.count { it.sourceUri != null && !it.isMissing }
    val total = snapshot.tracks.size
    return RestoreReport(matched, total, total - matched, merged, inserted)
}

private suspend fun remapStagedCovers(context: Context, database: YounekoDatabase, staging: File, albumMap: Map<String, String>) {
    val source = File(staging, "covers")
    val destination = File(context.filesDir, "covers").apply { mkdirs() }
    source.listFiles()?.forEach { file ->
        val sourceId = file.nameWithoutExtension
        val targetId = albumMap[sourceId] ?: sourceId
        val target = File(destination, "$targetId.jpg")
        file.copyTo(target, overwrite = true)
        database.albumDao().findById(targetId)?.let { album ->
            database.albumDao().update(album.copy(coverUri = target.toURI().toString(), coverThumbUri = target.toURI().toString(), coverSource = "backup", coverUpdatedAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()))
        }
    }
}

private suspend fun remapRestoredCovers(context: Context, database: YounekoDatabase, staging: File) = remapStagedCovers(context, database, staging, emptyMap())

private fun CreditSnapshot.copyIds(albumMap: Map<String, String>, trackMap: Map<String, String>) = CreditEntity(id, albumId?.let { albumMap[it] }, trackId?.let { trackMap[it] }, personName, personMbid, role, instrumentOrAttribute, sourceProvider, sourceUrl, sortOrder, beginDate, endDate)
private fun sameCredit(a: CreditEntity, b: CreditEntity): Boolean = a.albumId == b.albumId && a.trackId == b.trackId && a.personName.equals(b.personName, true) && a.role.equals(b.role, true) && a.instrumentOrAttribute == b.instrumentOrAttribute && a.sourceProvider == b.sourceProvider
private fun mergeText(old: String?, incoming: String?): String? = when {
    old.isNullOrBlank() -> incoming
    incoming.isNullOrBlank() -> old
    old == incoming -> old
    else -> "$old\n\n---\n\n$incoming"
}
