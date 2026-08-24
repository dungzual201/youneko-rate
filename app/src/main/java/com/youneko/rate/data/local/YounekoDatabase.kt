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
import com.youneko.rate.data.local.dao.CollectionDao
import com.youneko.rate.data.local.dao.ScanRootDao
import com.youneko.rate.data.local.dao.LibrarySearchFtsDao
import com.youneko.rate.data.local.dao.ImportSessionDao
import com.youneko.rate.data.local.dao.RemoteMetadataCacheDao
import com.youneko.rate.data.local.dao.SearchHistoryDao
import com.youneko.rate.data.local.dao.TrackDao
import com.youneko.rate.data.local.entity.AlbumEntity
import com.youneko.rate.data.local.entity.AlbumPaletteEntity
import com.youneko.rate.data.local.entity.CollectionAlbumEntity
import com.youneko.rate.data.local.entity.CollectionEntity
import com.youneko.rate.data.local.entity.ArtistEntity
import com.youneko.rate.data.local.entity.AudioAnalysisEntity
import com.youneko.rate.data.local.entity.CreditEntity
import com.youneko.rate.data.local.entity.ExternalLinkEntity
import com.youneko.rate.data.local.entity.LibrarySearchFtsEntity
import com.youneko.rate.data.local.entity.ImportSessionEntity
import com.youneko.rate.data.local.entity.ReviewRevisionEntity
import com.youneko.rate.data.local.entity.AlbumTagEntity
import com.youneko.rate.data.local.entity.ListeningLogEntity
import com.youneko.rate.data.local.entity.RemoteMetadataCacheEntity
import com.youneko.rate.data.local.entity.SearchHistoryEntity
import com.youneko.rate.data.local.entity.ScanRootEntity
import com.youneko.rate.data.local.entity.TrackEntity

@Database(
    entities = [
        ArtistEntity::class,
        AlbumEntity::class,
        TrackEntity::class,
        CreditEntity::class,
        ExternalLinkEntity::class,
        ReviewRevisionEntity::class,
        AlbumTagEntity::class,
        ListeningLogEntity::class,
        AudioAnalysisEntity::class,
        RemoteMetadataCacheEntity::class,
        SearchHistoryEntity::class,
        LibrarySearchFtsEntity::class,
        ImportSessionEntity::class,
        CollectionEntity::class,
        CollectionAlbumEntity::class,
        ScanRootEntity::class,
        AlbumPaletteEntity::class,
    ],
    version = 22,
    exportSchema = true,
)
@TypeConverters(YounekoTypeConverters::class)
abstract class YounekoDatabase : RoomDatabase() {
    abstract fun albumDao(): AlbumDao
    abstract fun artistDao(): ArtistDao
    abstract fun trackDao(): TrackDao
    abstract fun creditDao(): CreditDao
    abstract fun externalLinkDao(): com.youneko.rate.data.local.dao.ExternalLinkDao
    abstract fun reviewRevisionDao(): com.youneko.rate.data.local.dao.ReviewRevisionDao
    abstract fun albumTagDao(): com.youneko.rate.data.local.dao.AlbumTagDao
    abstract fun listeningLogDao(): com.youneko.rate.data.local.dao.ListeningLogDao
    abstract fun statsDao(): com.youneko.rate.data.local.dao.StatsDao
    abstract fun audioAnalysisDao(): AudioAnalysisDao
    abstract fun remoteMetadataCacheDao(): RemoteMetadataCacheDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun librarySearchFtsDao(): LibrarySearchFtsDao
    abstract fun importSessionDao(): ImportSessionDao
    abstract fun collectionDao(): CollectionDao
    abstract fun scanRootDao(): ScanRootDao
    abstract fun albumPaletteDao(): com.youneko.rate.data.local.dao.AlbumPaletteDao

    companion object {
        val MIGRATION_14_15: Migration = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tracks ADD COLUMN mediaStoreId INTEGER")
                db.execSQL("ALTER TABLE tracks ADD COLUMN stableKey TEXT")
                db.execSQL("ALTER TABLE tracks ADD COLUMN fileSizeBytes INTEGER")
                db.execSQL("ALTER TABLE tracks ADD COLUMN fileHash64k TEXT")
                db.execSQL("ALTER TABLE tracks ADD COLUMN isMissing INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tracks ADD COLUMN missingSince INTEGER")
                db.execSQL("ALTER TABLE tracks ADD COLUMN mediaStoreModifiedSeconds INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tracks_mediaStoreId ON tracks(mediaStoreId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tracks_stableKey ON tracks(stableKey)")
                db.execSQL("CREATE TABLE IF NOT EXISTS scan_roots (uri TEXT NOT NULL PRIMARY KEY, displayName TEXT, addedAt INTEGER NOT NULL, lastScannedAt INTEGER)")
            }
        }

        val MIGRATION_16_17: Migration = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfMissing(db, "audio_analysis", "noiseFloorDb", "REAL")
                addColumnIfMissing(db, "audio_analysis", "cliffDb", "REAL")
                addColumnIfMissing(db, "audio_analysis", "quietAboveFraction", "REAL")
                addColumnIfMissing(db, "audio_analysis", "analyzedFrames", "INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_17_18: Migration = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfMissing(db, "audio_analysis", "sourceMime", "TEXT")
                addColumnIfMissing(db, "audio_analysis", "codecDetectionSource", "TEXT")
                addColumnIfMissing(db, "audio_analysis", "bitrateNote", "TEXT")
                addColumnIfMissing(db, "audio_analysis", "theoreticalBitrate", "INTEGER")
                addColumnIfMissing(db, "audio_analysis", "energyAboveCutoffRatio", "REAL")
                addColumnIfMissing(db, "audio_analysis", "cutoffRetries", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(db, "audio_analysis", "formatVerdict", "TEXT")
                addColumnIfMissing(db, "audio_analysis", "transcodeVerdict", "TEXT")
            }
        }

        val MIGRATION_20_21: Migration = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfMissing(db, "albums", "scanNaturalKey", "TEXT")
                addColumnIfMissing(db, "tracks", "scanNaturalKey", "TEXT")
                ScanDedupe.run(db)
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_albums_scanNaturalKey ON albums(scanNaturalKey)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_tracks_scanNaturalKey ON tracks(scanNaturalKey)")
            }
        }

        val MIGRATION_21_22: Migration = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS index_albums_scanNaturalKey")
                db.execSQL("DROP INDEX IF EXISTS index_tracks_scanNaturalKey")
                ScanDedupe.rebuildNaturalKeys(db)
                ScanDedupe.run(db)
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_albums_scanNaturalKey ON albums(scanNaturalKey)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_tracks_scanNaturalKey ON tracks(scanNaturalKey)")
            }
        }

        val MIGRATION_19_20: Migration = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfMissing(db, "albums", "coverHeight", "INTEGER")
                db.execSQL("CREATE TABLE IF NOT EXISTS album_palette (albumId TEXT NOT NULL PRIMARY KEY, dominantArgb INTEGER NOT NULL, vibrantArgb INTEGER, darkVibrantArgb INTEGER, mutedArgb INTEGER, darkMutedArgb INTEGER, lightVibrantArgb INTEGER, onDominantArgb INTEGER NOT NULL, coverUpdatedAt INTEGER, generatedAt INTEGER NOT NULL)")
            }
        }

        val MIGRATION_18_19: Migration = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfMissing(db, "audio_analysis", "rawHeaderHex", "TEXT")
            }
        }

        val MIGRATION_15_16: Migration = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfMissing(db, "tracks", "mediaStoreId", "INTEGER")
                addColumnIfMissing(db, "tracks", "stableKey", "TEXT")
                addColumnIfMissing(db, "tracks", "fileSizeBytes", "INTEGER")
                addColumnIfMissing(db, "tracks", "fileHash64k", "TEXT")
                addColumnIfMissing(db, "tracks", "isMissing", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(db, "tracks", "missingSince", "INTEGER")
                addColumnIfMissing(db, "tracks", "mediaStoreModifiedSeconds", "INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tracks_mediaStoreId ON tracks(mediaStoreId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tracks_stableKey ON tracks(stableKey)")

                db.execSQL("CREATE TABLE IF NOT EXISTS scan_roots (uri TEXT NOT NULL PRIMARY KEY, displayName TEXT, addedAt INTEGER NOT NULL, lastScannedAt INTEGER)")
                addColumnIfMissing(db, "scan_roots", "displayName", "TEXT")
                addColumnIfMissing(db, "scan_roots", "addedAt", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(db, "scan_roots", "lastScannedAt", "INTEGER")
            }
        }

        private fun addColumnIfMissing(
            db: SupportSQLiteDatabase,
            table: String,
            column: String,
            definition: String,
        ) {
            val hasColumn = db.query("PRAGMA table_info($table)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                var found = false
                while (cursor.moveToNext()) {
                    if (nameIndex >= 0 && cursor.getString(nameIndex) == column) {
                        found = true
                        break
                    }
                }
                found
            }
            if (!hasColumn) {
                db.execSQL("ALTER TABLE $table ADD COLUMN $column $definition")
            }
        }

        val MIGRATION_13_14: Migration = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS collections (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, description TEXT, createdAt INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS collection_albums (collectionId TEXT NOT NULL, albumId TEXT NOT NULL, sortOrder INTEGER NOT NULL, PRIMARY KEY(collectionId, albumId), FOREIGN KEY(collectionId) REFERENCES collections(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(albumId) REFERENCES albums(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_collection_albums_albumId ON collection_albums(albumId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_collection_albums_collectionId_sortOrder ON collection_albums(collectionId, sortOrder)")
            }
        }

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

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("PRAGMA foreign_keys=OFF")
                db.execSQL("""CREATE TABLE albums_new (
                    id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, artistId TEXT NOT NULL,
                    releaseYear INTEGER, coverUri TEXT, coverThumbUri TEXT, genreTags TEXT NOT NULL,
                    albumType TEXT NOT NULL, label TEXT, catalogNumber TEXT, barcode TEXT, country TEXT,
                    listenedDate TEXT, manualScoreOverride REAL, reviewText TEXT, mbid TEXT,
                    releaseGroupMbid TEXT, discogsReleaseId TEXT, deezerId TEXT, sourceProvider TEXT,
                    metadataFetchedAt INTEGER, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL,
                    FOREIGN KEY(artistId) REFERENCES artists(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )""")
                db.execSQL("""INSERT INTO albums_new (
                    id, title, artistId, releaseYear, coverUri, coverThumbUri, genreTags, albumType,
                    label, catalogNumber, barcode, country, listenedDate, manualScoreOverride, reviewText,
                    mbid, releaseGroupMbid, discogsReleaseId, deezerId, sourceProvider, metadataFetchedAt,
                    createdAt, updatedAt
                ) SELECT
                    id, title, artistId, releaseYear, coverUri, coverThumbUri, genreTags, albumType,
                    label, catalogNumber, barcode, country, listenedDate, manualScoreOverride, reviewText,
                    mbid, releaseGroupMbid, discogsReleaseId, deezerId, sourceProvider, metadataFetchedAt,
                    createdAt, updatedAt
                FROM albums""")
                db.execSQL("DROP TABLE albums")
                db.execSQL("ALTER TABLE albums_new RENAME TO albums")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_albums_artistId ON albums(artistId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_albums_mbid ON albums(mbid)")
                db.execSQL("PRAGMA foreign_keys=ON")
            }
        }

        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS import_sessions (
                    id TEXT NOT NULL PRIMARY KEY,
                    sourceUrisJson TEXT NOT NULL,
                    sourceIsTree INTEGER NOT NULL,
                    selectedUrisJson TEXT NOT NULL,
                    selectionsJson TEXT NOT NULL,
                    createdAt INTEGER NOT NULL
                )""")
            }
        }

        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE credits ADD COLUMN beginDate TEXT")
                db.execSQL("ALTER TABLE credits ADD COLUMN endDate TEXT")
            }
        }

        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tracks ADD COLUMN sourceUri TEXT")
                db.execSQL("ALTER TABLE tracks ADD COLUMN fileName TEXT")
            }
        }

        val MIGRATION_6_7: Migration = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE audio_analysis ADD COLUMN rolloffSlope REAL")
                db.execSQL("ALTER TABLE audio_analysis ADD COLUMN dynamicRangeDb REAL")
                db.execSQL("ALTER TABLE audio_analysis ADD COLUMN truePeakDbtp REAL")
                db.execSQL("ALTER TABLE audio_analysis ADD COLUMN clippingPercent REAL")
                db.execSQL("ALTER TABLE audio_analysis ADD COLUMN engineVersion TEXT NOT NULL DEFAULT 'phase8-v1'")
            }
        }

        val MIGRATION_7_8: Migration = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE audio_analysis ADD COLUMN spectrumJson TEXT NOT NULL DEFAULT '[]'")
            }
        }

        val MIGRATION_8_9: Migration = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE albums ADD COLUMN coverSource TEXT")
                db.execSQL("ALTER TABLE albums ADD COLUMN coverWidth INTEGER")
            }
        }

        val MIGRATION_9_10: Migration = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE albums ADD COLUMN coverUpdatedAt INTEGER")
            }
        }

        val MIGRATION_10_11: Migration = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS external_links (id TEXT NOT NULL PRIMARY KEY, albumId TEXT, trackId TEXT, sourceId TEXT NOT NULL, externalId TEXT NOT NULL, sourceUrl TEXT NOT NULL, createdAt INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_external_links_trackId_sourceId ON external_links(trackId, sourceId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_external_links_albumId_sourceId ON external_links(albumId, sourceId)")
            }
        }

        val MIGRATION_11_12: Migration = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DELETE FROM credits")
            }
        }

        val MIGRATION_12_13: Migration = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS review_revisions (id TEXT NOT NULL PRIMARY KEY, albumId TEXT NOT NULL, trackId TEXT, body TEXT NOT NULL, createdAt INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_review_revisions_albumId_trackId_createdAt ON review_revisions(albumId, trackId, createdAt)")
                db.execSQL("CREATE TABLE IF NOT EXISTS album_tags (id TEXT NOT NULL PRIMARY KEY, albumId TEXT NOT NULL, name TEXT NOT NULL, createdAt INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_album_tags_albumId_name ON album_tags(albumId, name)")
                db.execSQL("CREATE TABLE IF NOT EXISTS listening_logs (id TEXT NOT NULL PRIMARY KEY, albumId TEXT NOT NULL, trackId TEXT, listenedAt TEXT NOT NULL, note TEXT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_listening_logs_albumId_listenedAt ON listening_logs(albumId, listenedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_listening_logs_trackId_listenedAt ON listening_logs(trackId, listenedAt)")
            }
        }
    }
}
