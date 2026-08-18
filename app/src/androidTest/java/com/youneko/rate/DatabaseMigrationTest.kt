package com.youneko.rate

import android.database.Cursor
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.youneko.rate.data.local.YounekoDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        val columns = migrated.query("PRAGMA table_info(albums)").use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
        }
        assertFalse(columns.contains("isFavorite"))
        assertTrue(columns.contains("manualScoreOverride"))
        assertEquals(1, countRows(migrated, "albums", "id = 'album-1'"))
        migrated.close()
    }

    private fun countRows(database: SupportSQLiteDatabase, table: String, where: String): Int =
        database.query("SELECT COUNT(*) FROM $table WHERE $where").use(Cursor::getIntAtZero)
}

private fun Cursor.getIntAtZero(): Int {
    moveToFirst()
    return getInt(0)
}
