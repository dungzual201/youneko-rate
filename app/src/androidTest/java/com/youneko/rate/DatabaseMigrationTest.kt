package com.youneko.rate

import android.database.Cursor
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.youneko.rate.data.local.YounekoDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        YounekoDatabase::class.java,
    )

    @Test
    fun migrate2To3DropsFavoriteAndPreservesAlbum() {
        val databaseName = "migration-favorite-test"
        helper.createDatabase(databaseName, 2).apply {
            execSQL("INSERT INTO artists (id, name, createdAt, updatedAt) VALUES ('artist-1', 'Artist', 1, 1)")
            execSQL("""INSERT INTO albums (id, title, artistId, genreTags, albumType, isFavorite, createdAt, updatedAt)
                VALUES ('album-1', 'Album', 'artist-1', '[]', 'ALBUM', 1, 1, 1)""")
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            databaseName,
            3,
            true,
            YounekoDatabase.MIGRATION_2_3,
        )
        val columns = tableColumns(migrated, "albums")
        assertFalse(columns.contains("isFavorite"))
        assertTrue(columns.contains("manualScoreOverride"))
        assertEquals(1, countRows(migrated, "albums", "id = 'album-1'"))
        migrated.close()
    }

    @Test
    fun migrate15To16PreservesRatingAndManualCreditAndReconcilesTrackSchema() {
        val databaseName = "migration-track-schema-test"
        helper.createDatabase(databaseName, 15).apply {
            execSQL("INSERT INTO artists (id, name, createdAt, updatedAt) VALUES ('artist-1', 'Artist', 1, 1)")
            execSQL("INSERT INTO albums (id, title, artistId, genreTags, albumType, createdAt, updatedAt) VALUES ('album-1', 'Album', 'artist-1', '[]', 'ALBUM', 1, 1)")
            execSQL("INSERT INTO tracks (id, albumId, title, stars, reviewText, isStandalone, isSkip, isHighlight, createdAt, updatedAt, isMissing) VALUES ('track-1', 'album-1', 'Track', 4.5, 'Giữ nguyên', 0, 0, 0, 1, 1, 0)")
            execSQL("INSERT INTO credits (id, albumId, trackId, personName, role, sourceProvider, sortOrder) VALUES ('credit-1', 'album-1', 'track-1', 'Credit thủ công', 'Producer', 'manual', 0)")
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            databaseName,
            16,
            true,
            YounekoDatabase.MIGRATION_15_16,
        )
        assertEquals(4.5, migrated.query("SELECT stars FROM tracks WHERE id = 'track-1'").use { cursor -> cursor.getDoubleAtFirstColumn() }, 0.0)
        assertEquals("Giữ nguyên", migrated.query("SELECT reviewText FROM tracks WHERE id = 'track-1'").use { cursor -> cursor.getStringAtFirstColumn() })
        assertEquals(1, countRows(migrated, "credits", "id = 'credit-1' AND sourceProvider = 'manual' AND personName = 'Credit thủ công'"))
        assertEquals("0", columnDefault(migrated, "tracks", "isMissing"))
        assertTrue(indexNames(migrated, "tracks").containsAll(setOf("index_tracks_mediaStoreId", "index_tracks_stableKey")))
        assertTrue(tableColumns(migrated, "albums").containsAll(setOf("id", "title", "artistId", "manualScoreOverride")))
        assertTrue(tableColumns(migrated, "credits").containsAll(setOf("id", "albumId", "trackId", "sourceProvider")))
        assertTrue(tableColumns(migrated, "scan_roots").containsAll(setOf("uri", "displayName", "addedAt", "lastScannedAt")))

        // The new migration is deliberately idempotent for a partially-migrated v15 database.
        YounekoDatabase.MIGRATION_15_16.migrate(migrated)
        assertEquals(4.5, migrated.query("SELECT stars FROM tracks WHERE id = 'track-1'").use { cursor -> cursor.getDoubleAtFirstColumn() }, 0.0)
        assertEquals(1, countRows(migrated, "credits", "id = 'credit-1'"))
        migrated.close()
    }

    @Test
    fun openingVersion16DatabaseWithoutMigrationDoesNotCrash() {
        val databaseName = "migration-v16-open-test"
        helper.createDatabase(databaseName, 16).close()
        helper.runMigrationsAndValidate(databaseName, 16, true).close()
    }

    private fun countRows(database: SupportSQLiteDatabase, table: String, where: String): Int =
        database.query("SELECT COUNT(*) FROM $table WHERE $where").use(Cursor::getIntAtZero)

    private fun tableColumns(database: SupportSQLiteDatabase, table: String): Set<String> =
        database.query("PRAGMA table_info($table)").use { cursor ->
            buildSet {
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                while (cursor.moveToNext()) add(cursor.getString(nameIndex))
            }
        }

    private fun columnDefault(database: SupportSQLiteDatabase, table: String, column: String): String? =
        database.query("PRAGMA table_info($table)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            val defaultIndex = cursor.getColumnIndexOrThrow("dflt_value")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == column) return@use cursor.getString(defaultIndex)
            }
            null
        }

    private fun indexNames(database: SupportSQLiteDatabase, table: String): Set<String> =
        database.query("PRAGMA index_list($table)").use { cursor ->
            buildSet {
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                while (cursor.moveToNext()) add(cursor.getString(nameIndex))
            }
        }
}

private fun Cursor.getIntAtZero(): Int {
    moveToFirst()
    return getInt(0)
}

private fun Cursor.getDoubleAtFirstColumn(): Double {
    moveToFirst()
    return getDouble(0)
}

private fun Cursor.getStringAtFirstColumn(): String? {
    moveToFirst()
    return if (isNull(0)) null else getString(0)
}
