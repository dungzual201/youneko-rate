package com.youneko.rate

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.withTransaction
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.runner.RunWith
import com.youneko.rate.data.SettingsStore
import com.youneko.rate.data.export.BackupCounts
import com.youneko.rate.data.export.BACKUP_DATABASE_ENTRY
import com.youneko.rate.data.export.BACKUP_MANIFEST
import com.youneko.rate.data.export.BackupManifest
import com.youneko.rate.data.export.writeBackupArchive
import com.youneko.rate.data.export.backupCounts
import com.youneko.rate.data.export.verifyCountsAgainstManifest
import com.youneko.rate.data.export.CURRENT_BACKUP_FORMAT_VERSION
import com.youneko.rate.data.export.CURRENT_DATABASE_SCHEMA_VERSION
import com.youneko.rate.data.export.checkpointAndCopyDatabase
import com.youneko.rate.data.export.sha256
import com.youneko.rate.data.export.trackMatchesForRestore
import com.youneko.rate.data.export.restoreMissingState
import com.youneko.rate.data.export.validateBackupManifest
import com.youneko.rate.data.export.TrackSnapshot
import com.youneko.rate.data.local.YounekoDatabase
import com.youneko.rate.data.local.entity.AlbumEntity
import com.youneko.rate.data.local.entity.ArtistEntity
import com.youneko.rate.data.local.entity.CreditEntity
import com.youneko.rate.data.local.entity.TrackEntity
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupArchiveTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun exportImmediatelyAfterRatingCheckpointKeepsRatingAndManualCredit() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("backup-wal-test.db")
        val database = Room.databaseBuilder(context, YounekoDatabase::class.java, "backup-wal-test.db").build()
        try {
            database.withTransaction {
                database.artistDao().insert(ArtistEntity("artist", "Artist", createdAt = 1L, updatedAt = 1L))
                database.albumDao().insert(AlbumEntity("album", "Album", "artist", createdAt = 1L, updatedAt = 1L))
                database.trackDao().insert(TrackEntity("track", "album", "Track", durationMs = 1000L, createdAt = 1L, updatedAt = 1L))
                database.creditDao().upsertAll(listOf(CreditEntity("credit", albumId = "album", personName = "Manual Person", role = "Producer", sourceProvider = "manual")))
                database.trackDao().update(database.trackDao().findById("track")!!.copy(stars = 4.5, updatedAt = 2L))
            }
            val copied = checkpointAndCopyDatabase(context, database)
            try {
                SQLiteDatabase.openDatabase(copied.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                    db.rawQuery("SELECT stars FROM tracks WHERE id = 'track'", null).use { cursor ->
                        assertTrue(cursor.moveToFirst())
                        assertEquals(4.5, cursor.getDouble(0), 0.0001)
                    }
                    db.rawQuery("SELECT COUNT(*) FROM credits WHERE sourceProvider = 'manual'", null).use { cursor ->
                        assertTrue(cursor.moveToFirst())
                        assertEquals(1, cursor.getInt(0))
                    }
                }
                assertTrue(sha256(copied).length == 64)
            } finally { copied.delete() }
        } finally { database.close(); context.deleteDatabase("backup-wal-test.db") }
    }

    @Test
    fun streamedZipContainsRequiredEntriesAndNoMusicOrTokenFields() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("backup-zip-test.db")
        val database = Room.databaseBuilder(context, YounekoDatabase::class.java, "backup-zip-test.db").build()
        try {
            val output = ByteArrayOutputStream()
            writeBackupArchive(context, database, FakeSettingsStore(), output, includeCovers = false, includeReadableExports = true)
            val entries = mutableListOf<String>()
            ZipInputStream(output.toByteArray().inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) { entries += entry.name; zip.closeEntry(); entry = zip.nextEntry }
            }
            assertTrue(entries.contains("manifest.json"))
            assertTrue(entries.contains("database/youneko.db"))
            assertTrue(entries.contains("settings.json"))
            assertTrue(entries.contains("export/ratings.csv"))
            assertTrue(entries.contains("export/credits.json"))
            assertTrue(entries.none { it.endsWith(".mp3") || it.endsWith(".flac") || it.contains("token", true) })
        } finally { database.close(); context.deleteDatabase("backup-zip-test.db") }
    }

    @Test
    fun manifestSeparatesFormatAndDatabaseVersionsAndStoresDatabaseChecksumKey() {
        val manifest = BackupManifest(
            createdAt = "1970-01-01T00:00:00Z",
            app = com.youneko.rate.data.export.BackupAppInfo("0.1.0", 1),
            dbSchemaVersion = CURRENT_DATABASE_SCHEMA_VERSION,
            device = com.youneko.rate.data.export.BackupDeviceInfo("test", 35),
            counts = BackupCounts(1, 1, 1, 1, 1, 1, 0),
            includesCovers = false,
            sha256 = "a".repeat(64),
            checksum = mapOf(BACKUP_DATABASE_ENTRY to "sha256:" + "a".repeat(64)),
        )
        val encoded = json.encodeToString(manifest)
        assertTrue(encoded.contains("formatVersion"))
        assertTrue(encoded.contains("dbSchemaVersion"))
        assertTrue(manifest.formatVersion <= CURRENT_BACKUP_FORMAT_VERSION)
        assertTrue(manifest.checksum.containsKey(BACKUP_DATABASE_ENTRY))
    }

    @Test
    fun newerVersionAndChecksumMismatchAreRejected() {
        val base = BackupManifest(createdAt = "1970-01-01T00:00:00Z", app = com.youneko.rate.data.export.BackupAppInfo("0.1.0", 1), dbSchemaVersion = CURRENT_DATABASE_SCHEMA_VERSION, device = com.youneko.rate.data.export.BackupDeviceInfo("test", 35), counts = BackupCounts(0, 0, 0, 0, 0, 0, 0), includesCovers = false, sha256 = "a".repeat(64), checksum = mapOf(BACKUP_DATABASE_ENTRY to "sha256:" + "a".repeat(64)))
        assertTrue(!validateBackupManifest(base.copy(formatVersion = CURRENT_BACKUP_FORMAT_VERSION + 1), "a".repeat(64), true).ok)
        assertTrue(!validateBackupManifest(base.copy(dbSchemaVersion = CURRENT_DATABASE_SCHEMA_VERSION + 1), "a".repeat(64), true).ok)
        assertTrue(!validateBackupManifest(base, "b".repeat(64), true).ok)
    }

    @Test
    fun exportedEvidenceArchiveHasManifestFirstNoWalNoMusicAndMatchingCounts() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("backup-evidence.db")
        val database = Room.databaseBuilder(context, YounekoDatabase::class.java, "backup-evidence.db").build()
        try {
            database.withTransaction {
                database.artistDao().insert(ArtistEntity("evidence-artist", "Nghệ sĩ", createdAt = 1L, updatedAt = 1L))
                database.albumDao().insert(AlbumEntity("evidence-album", "Album kiểm thử", "evidence-artist", createdAt = 1L, updatedAt = 1L))
                database.trackDao().insert(TrackEntity("evidence-track", "evidence-album", "Bài kiểm thử", stars = 4.0, reviewText = "Giữ nguyên", createdAt = 1L, updatedAt = 2L))
                database.creditDao().upsertAll(listOf(CreditEntity("evidence-credit", trackId = "evidence-track", personName = "Credit tay", role = "Producer", sourceProvider = "manual")))
            }
            val before = database.backupCounts()
            val bytes = ByteArrayOutputStream()
            writeBackupArchive(context, database, FakeSettingsStore(), bytes, includeCovers = false, includeReadableExports = true)
            val evidence = File(System.getProperty("user.dir"), "app/build/test-artifacts/evidence.younekorate").apply { parentFile?.mkdirs(); writeBytes(bytes.toByteArray()) }
            val entries = mutableListOf<String>()
            var manifest: BackupManifest? = null
            var extractedDb: File? = null
            ZipInputStream(bytes.toByteArray().inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    entries += entry.name
                    val data = zip.readBytes()
                    if (entry.name == BACKUP_MANIFEST) manifest = json.decodeFromString<BackupManifest>(data.toString(Charsets.UTF_8))
                    if (entry.name == BACKUP_DATABASE_ENTRY) extractedDb = File.createTempFile("evidence-import-", ".db").apply { writeBytes(data) }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            val after = manifest!!.counts
            assertEquals(before.albums, after.albums)
            assertEquals(before.tracks, after.tracks)
            assertEquals(before.ratings, after.ratings)
            assertEquals(before.reviews, after.reviews)
            assertEquals(before.manualCredits, after.manualCredits)
            verifyCountsAgainstManifest(extractedDb!!, manifest!!.counts)
            assertEquals(BACKUP_MANIFEST, entries.first())
            assertFalse(entries.any { it.endsWith("-wal") || it.endsWith("-shm") || it.endsWith(".mp3") || it.endsWith(".flac") })
            println("BACKUP_EVIDENCE_ARCHIVE=${evidence.absolutePath}")
            println("BACKUP_EVIDENCE_TREE=${entries.joinToString(",")}")
            println("BACKUP_COUNTS_BEFORE=$before")
            println("BACKUP_COUNTS_AFTER_IMPORT=$after")
        } finally { database.close(); context.deleteDatabase("backup-evidence.db") }
    }

    @Test
    fun changedMusicPathStillMatchesByStableKeyAndUnmatchedTrackKeepsUserFields() {
        val backup = TrackSnapshot("old", "album", "Track", 1, 1, 120_000L, false, 4.0, "review", false, false, null, null, null, null, "file:///old/path.mp3", "old.mp3", 1L, 2L, "size|120|hash", 100L, "hash", false)
        val candidate = TrackEntity("new", "album", "Track", durationMs = 120_000L, sourceUri = "content://new/path", stableKey = "size|120|hash", createdAt = 3L, updatedAt = 3L)
        assertTrue(trackMatchesForRestore(backup, candidate, "Artist", "Artist"))
        val preserved = restoreMissingState(candidate.copy(stars = 4.0, reviewText = "keep"), matched = false, nowMs = 9L)
        assertTrue(preserved.isMissing)
        assertEquals(4.0, preserved.stars!!, 0.0001)
        assertEquals("keep", preserved.reviewText)
    }

    private class FakeSettingsStore : SettingsStore {
        override val offlineOnly: Flow<Boolean> = MutableStateFlow(false)
        override val ratingStep: Flow<Double> = MutableStateFlow(0.5)
        override val ratingScale: Flow<String> = MutableStateFlow("FIVE_STARS")
        override val scoreMode: Flow<String> = MutableStateFlow("SIMPLE")
        override val gridView: Flow<Boolean> = MutableStateFlow(true)
        override val dynamicColor: Flow<Boolean> = MutableStateFlow(false)
        override val reducedMotion: Flow<Boolean> = MutableStateFlow(false)
        override val sortOrder: Flow<String> = MutableStateFlow("NEWEST")
        override val unfinishedOnly: Flow<Boolean> = MutableStateFlow(false)
        override val discogsEnabled: Flow<Boolean> = MutableStateFlow(false)
        override val discogsToken: Flow<String> = MutableStateFlow("")
        override val lastFmEnabled: Flow<Boolean> = MutableStateFlow(false)
        override val lastFmApiKey: Flow<String> = MutableStateFlow("")
        override val geniusEnabled: Flow<Boolean> = MutableStateFlow(false)
        override val geniusToken: Flow<String> = MutableStateFlow("")
        override val showCreditSources: Flow<Boolean> = MutableStateFlow(false)
        override val creditSourceOrder: Flow<String> = MutableStateFlow("FILE_TAG,MUSICBRAINZ")
        override val activeCreditSources: Flow<String> = MutableStateFlow("FILE_TAG,MUSICBRAINZ")
        override val creditsMergeMode: Flow<Boolean> = MutableStateFlow(false)
        override suspend fun setOfflineOnly(value: Boolean) = Unit
        override suspend fun setRatingStep(value: Double) = Unit
        override suspend fun setRatingScale(value: String) = Unit
        override suspend fun setScoreMode(value: String) = Unit
        override suspend fun setGridView(value: Boolean) = Unit
        override suspend fun setDynamicColor(value: Boolean) = Unit
        override suspend fun setReducedMotion(value: Boolean) = Unit
        override suspend fun setSortOrder(value: String) = Unit
        override suspend fun setUnfinishedOnly(value: Boolean) = Unit
        override suspend fun setDiscogsEnabled(value: Boolean) = Unit
        override suspend fun setDiscogsToken(value: String) = Unit
        override suspend fun setLastFmEnabled(value: Boolean) = Unit
        override suspend fun setLastFmApiKey(value: String) = Unit
        override suspend fun setGeniusEnabled(value: Boolean) = Unit
        override suspend fun setGeniusToken(value: String) = Unit
        override suspend fun setShowCreditSources(value: Boolean) = Unit
        override suspend fun setCreditSourceOrder(value: String) = Unit
        override suspend fun setActiveCreditSources(value: String) = Unit
        override suspend fun setCreditsMergeMode(value: Boolean) = Unit
    }
}
