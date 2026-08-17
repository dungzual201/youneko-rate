package com.youneko.rate.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.youneko.rate.data.local.dao.AlbumDao
import com.youneko.rate.data.local.dao.ArtistDao
import com.youneko.rate.data.local.dao.AudioAnalysisDao
import com.youneko.rate.data.local.dao.CreditDao
import com.youneko.rate.data.local.dao.LibrarySearchFtsDao
import com.youneko.rate.data.local.dao.RemoteMetadataCacheDao
import com.youneko.rate.data.local.dao.SearchHistoryDao
import com.youneko.rate.data.local.dao.TrackDao
import com.youneko.rate.data.local.entity.AlbumEntity
import com.youneko.rate.data.local.entity.ArtistEntity
import com.youneko.rate.data.local.entity.AudioAnalysisEntity
import com.youneko.rate.data.local.entity.CreditEntity
import com.youneko.rate.data.local.entity.LibrarySearchFtsEntity
import com.youneko.rate.data.local.entity.RemoteMetadataCacheEntity
import com.youneko.rate.data.local.entity.SearchHistoryEntity
import com.youneko.rate.data.local.entity.TrackEntity

@Database(
    entities = [
        ArtistEntity::class,
        AlbumEntity::class,
        TrackEntity::class,
        CreditEntity::class,
        AudioAnalysisEntity::class,
        RemoteMetadataCacheEntity::class,
        SearchHistoryEntity::class,
        LibrarySearchFtsEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(YounekoTypeConverters::class)
abstract class YounekoDatabase : RoomDatabase() {
    abstract fun albumDao(): AlbumDao
    abstract fun artistDao(): ArtistDao
    abstract fun trackDao(): TrackDao
    abstract fun creditDao(): CreditDao
    abstract fun audioAnalysisDao(): AudioAnalysisDao
    abstract fun remoteMetadataCacheDao(): RemoteMetadataCacheDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun librarySearchFtsDao(): LibrarySearchFtsDao

    companion object {
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("PRAGMA foreign_keys=OFF")
                db.execSQL("ALTER TABLE albums RENAME TO albums_old")
                db.execSQL("""CREATE TABLE IF NOT EXISTS albums (
                    id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, artistId TEXT NOT NULL,
                    releaseYear INTEGER, coverUri TEXT, coverThumbUri TEXT, genreTags TEXT NOT NULL,
                    albumType TEXT NOT NULL, label TEXT, catalogNumber TEXT, barcode TEXT, country TEXT,
                    listenedDate TEXT, isFavorite INTEGER NOT NULL, manualScoreOverride REAL,
                    reviewText TEXT, mbid TEXT, releaseGroupMbid TEXT, discogsReleaseId TEXT,
                    deezerId TEXT, sourceProvider TEXT, metadataFetchedAt INTEGER, createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL, FOREIGN KEY(artistId) REFERENCES artists(id) ON DELETE CASCADE
                )""")
                db.execSQL("INSERT INTO albums SELECT * FROM albums_old")
                db.execSQL("DROP TABLE albums_old")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_albums_artistId ON albums(artistId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_albums_mbid ON albums(mbid)")

                db.execSQL("ALTER TABLE tracks RENAME TO tracks_old")
                db.execSQL("""CREATE TABLE IF NOT EXISTS tracks (
                    id TEXT NOT NULL PRIMARY KEY, albumId TEXT, title TEXT NOT NULL, trackNumber INTEGER,
                    discNumber INTEGER, durationMs INTEGER, isStandalone INTEGER NOT NULL, stars REAL,
                    reviewText TEXT, isSkip INTEGER NOT NULL, isHighlight INTEGER NOT NULL, listenedDate TEXT,
                    recordingMbid TEXT, workMbid TEXT, isrc TEXT, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL,
                    FOREIGN KEY(albumId) REFERENCES albums(id) ON DELETE CASCADE
                )""")
                db.execSQL("INSERT INTO tracks SELECT * FROM tracks_old")
                db.execSQL("DROP TABLE tracks_old")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tracks_albumId ON tracks(albumId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tracks_recordingMbid ON tracks(recordingMbid)")

                db.execSQL("ALTER TABLE credits RENAME TO credits_old")
                db.execSQL("""CREATE TABLE IF NOT EXISTS credits (
                    id TEXT NOT NULL PRIMARY KEY, albumId TEXT, trackId TEXT, personName TEXT NOT NULL,
                    personMbid TEXT, role TEXT NOT NULL, instrumentOrAttribute TEXT, sourceProvider TEXT NOT NULL,
                    sourceUrl TEXT, sortOrder INTEGER NOT NULL, FOREIGN KEY(albumId) REFERENCES albums(id) ON DELETE CASCADE,
                    FOREIGN KEY(trackId) REFERENCES tracks(id) ON DELETE CASCADE
                )""")
                db.execSQL("INSERT INTO credits SELECT * FROM credits_old")
                db.execSQL("DROP TABLE credits_old")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_credits_albumId ON credits(albumId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_credits_trackId ON credits(trackId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_credits_personMbid ON credits(personMbid)")

                db.execSQL("ALTER TABLE audio_analysis RENAME TO audio_analysis_old")
                db.execSQL("""CREATE TABLE IF NOT EXISTS audio_analysis (
                    id TEXT NOT NULL PRIMARY KEY, trackId TEXT, albumId TEXT, fileName TEXT NOT NULL,
                    fileUriOrPath TEXT NOT NULL, fileHash TEXT NOT NULL, container TEXT, codec TEXT,
                    sampleRate INTEGER, bitDepth INTEGER, bitrate INTEGER, isVbr INTEGER, channels INTEGER,
                    durationMs INTEGER, encoderTag TEXT, cutoffHz REAL, verdict TEXT NOT NULL, confidence INTEGER NOT NULL,
                    reasonsJson TEXT NOT NULL, spectrogramPngPath TEXT, analyzedAt INTEGER NOT NULL,
                    FOREIGN KEY(albumId) REFERENCES albums(id) ON DELETE CASCADE, FOREIGN KEY(trackId) REFERENCES tracks(id) ON DELETE CASCADE
                )""")
                db.execSQL("INSERT INTO audio_analysis SELECT * FROM audio_analysis_old")
                db.execSQL("DROP TABLE audio_analysis_old")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_audio_analysis_trackId ON audio_analysis(trackId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_audio_analysis_albumId ON audio_analysis(albumId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_audio_analysis_fileHash ON audio_analysis(fileHash)")
                db.execSQL("PRAGMA foreign_keys=ON")
            }
        }
    }
}
