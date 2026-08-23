package com.youneko.rate.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
object ScanNaturalKey {
    private fun normalize(value: String): String = value.trim().lowercase(java.util.Locale.ROOT)

    fun album(title: String?, artist: String?): String? {
        val normalizedTitle = title?.let(::normalize)?.takeIf { it.isNotBlank() } ?: return null
        val normalizedArtist = artist?.let(::normalize).orEmpty()
        return "album|$normalizedTitle|$normalizedArtist"
    }

    fun track(albumId: String?, title: String?, discNumber: Int?, trackNumber: Int?): String? {
        val normalizedTitle = title?.let(::normalize)?.takeIf { it.isNotBlank() } ?: return null
        return "track|${albumId.orEmpty()}|$normalizedTitle|${discNumber ?: 0}|${trackNumber ?: 0}"
    }
}

data class ScanDedupeStats(val merged: Int, val deleted: Int, val albumsRemaining: Int)

/** Idempotent cleanup used by the 20→21 migration and once at startup after upgrade. */
object ScanDedupe {
    fun run(db: SupportSQLiteDatabase): ScanDedupeStats {
        val mergedAlbums = dedupeAlbums(db)
        val mergedTracks = dedupeTracks(db)
        val remaining = count(db, "albums")
        db.execSQL("DROP TABLE IF EXISTS temp.round15_album_map")
        db.execSQL("DROP TABLE IF EXISTS temp.round15_track_map")
        return ScanDedupeStats(mergedAlbums + mergedTracks, mergedAlbums + mergedTracks, remaining)
    }

    private fun dedupeAlbums(db: SupportSQLiteDatabase): Int {
        db.execSQL("UPDATE albums SET scanNaturalKey = 'album|' || lower(trim(title)) || '|' || lower(trim(COALESCE((SELECT name FROM artists WHERE artists.id = albums.artistId), ''))) WHERE scanNaturalKey IS NULL")
        db.execSQL("DROP TABLE IF EXISTS temp.round15_album_map")
        db.execSQL(
            """CREATE TEMP TABLE round15_album_map AS
                SELECT d.id AS duplicateId,
                    (SELECT k.id FROM albums k
                     WHERE k.scanNaturalKey = d.scanNaturalKey
                     ORDER BY (k.coverUri IS NOT NULL) DESC,
                              (k.manualScoreOverride IS NOT NULL) DESC,
                              (k.reviewText IS NOT NULL) DESC,
                              (k.metadataFetchedAt IS NOT NULL) DESC,
                              k.updatedAt DESC, k.rowid ASC LIMIT 1) AS keepId
                FROM albums d
                WHERE d.scanNaturalKey IS NOT NULL AND trim(d.scanNaturalKey) != ''""".trimIndent(),
        )
        val duplicates = count(db, "temp.round15_album_map", "duplicateId != keepId")
        if (duplicates == 0) return 0

        db.execSQL("""UPDATE albums SET
            coverUri = COALESCE(coverUri, (SELECT d.coverUri FROM albums d WHERE d.scanNaturalKey = albums.scanNaturalKey AND d.coverUri IS NOT NULL ORDER BY d.updatedAt DESC, d.rowid ASC LIMIT 1)),
            coverThumbUri = COALESCE(coverThumbUri, (SELECT d.coverThumbUri FROM albums d WHERE d.scanNaturalKey = albums.scanNaturalKey AND d.coverThumbUri IS NOT NULL ORDER BY d.updatedAt DESC, d.rowid ASC LIMIT 1)),
            coverSource = COALESCE(coverSource, (SELECT d.coverSource FROM albums d WHERE d.scanNaturalKey = albums.scanNaturalKey AND d.coverSource IS NOT NULL ORDER BY d.updatedAt DESC, d.rowid ASC LIMIT 1)),
            manualScoreOverride = COALESCE(manualScoreOverride, (SELECT d.manualScoreOverride FROM albums d WHERE d.scanNaturalKey = albums.scanNaturalKey AND d.manualScoreOverride IS NOT NULL ORDER BY d.updatedAt DESC, d.rowid ASC LIMIT 1)),
            reviewText = COALESCE(reviewText, (SELECT d.reviewText FROM albums d WHERE d.scanNaturalKey = albums.scanNaturalKey AND d.reviewText IS NOT NULL ORDER BY d.updatedAt DESC, d.rowid ASC LIMIT 1)),
            updatedAt = max(updatedAt, COALESCE((SELECT max(d.updatedAt) FROM albums d WHERE d.scanNaturalKey = albums.scanNaturalKey), updatedAt))
            WHERE id IN (SELECT keepId FROM temp.round15_album_map WHERE duplicateId != keepId)""")
        db.execSQL("DELETE FROM album_palette WHERE albumId IN (SELECT duplicateId FROM temp.round15_album_map WHERE duplicateId != keepId) AND EXISTS (SELECT 1 FROM album_palette k JOIN temp.round15_album_map m ON m.keepId = k.albumId WHERE m.duplicateId = album_palette.albumId)")
        db.execSQL("UPDATE album_palette SET albumId = (SELECT keepId FROM temp.round15_album_map WHERE duplicateId = album_palette.albumId) WHERE albumId IN (SELECT duplicateId FROM temp.round15_album_map WHERE duplicateId != keepId)")
        db.execSQL("DELETE FROM collection_albums WHERE albumId IN (SELECT duplicateId FROM temp.round15_album_map WHERE duplicateId != keepId) AND EXISTS (SELECT 1 FROM collection_albums k JOIN temp.round15_album_map m ON m.keepId = k.albumId WHERE m.duplicateId = collection_albums.albumId AND k.collectionId = collection_albums.collectionId)")
        db.execSQL("UPDATE collection_albums SET albumId = (SELECT keepId FROM temp.round15_album_map WHERE duplicateId = collection_albums.albumId) WHERE albumId IN (SELECT duplicateId FROM temp.round15_album_map WHERE duplicateId != keepId)")
        db.execSQL("DELETE FROM album_tags WHERE albumId IN (SELECT duplicateId FROM temp.round15_album_map WHERE duplicateId != keepId) AND EXISTS (SELECT 1 FROM album_tags k JOIN temp.round15_album_map m ON m.keepId = k.albumId WHERE m.duplicateId = album_tags.albumId AND k.name = album_tags.name)")
        db.execSQL("UPDATE album_tags SET albumId = (SELECT keepId FROM temp.round15_album_map WHERE duplicateId = album_tags.albumId) WHERE albumId IN (SELECT duplicateId FROM temp.round15_album_map WHERE duplicateId != keepId)")
        db.execSQL("UPDATE tracks SET albumId = (SELECT keepId FROM temp.round15_album_map WHERE duplicateId = tracks.albumId) WHERE albumId IN (SELECT duplicateId FROM temp.round15_album_map WHERE duplicateId != keepId)")
        db.execSQL("UPDATE credits SET albumId = (SELECT keepId FROM temp.round15_album_map WHERE duplicateId = credits.albumId) WHERE albumId IN (SELECT duplicateId FROM temp.round15_album_map WHERE duplicateId != keepId)")
        db.execSQL("UPDATE external_links SET albumId = (SELECT keepId FROM temp.round15_album_map WHERE duplicateId = external_links.albumId) WHERE albumId IN (SELECT duplicateId FROM temp.round15_album_map WHERE duplicateId != keepId)")
        db.execSQL("UPDATE review_revisions SET albumId = (SELECT keepId FROM temp.round15_album_map WHERE duplicateId = review_revisions.albumId) WHERE albumId IN (SELECT duplicateId FROM temp.round15_album_map WHERE duplicateId != keepId)")
        db.execSQL("UPDATE listening_logs SET albumId = (SELECT keepId FROM temp.round15_album_map WHERE duplicateId = listening_logs.albumId) WHERE albumId IN (SELECT duplicateId FROM temp.round15_album_map WHERE duplicateId != keepId)")
        db.execSQL("UPDATE audio_analysis SET albumId = (SELECT keepId FROM temp.round15_album_map WHERE duplicateId = audio_analysis.albumId) WHERE albumId IN (SELECT duplicateId FROM temp.round15_album_map WHERE duplicateId != keepId)")
        db.execSQL("DELETE FROM library_search_fts WHERE entityType = 'album' AND entityId IN (SELECT duplicateId FROM temp.round15_album_map WHERE duplicateId != keepId)")
        db.execSQL("DELETE FROM albums WHERE id IN (SELECT duplicateId FROM temp.round15_album_map WHERE duplicateId != keepId)")
        return duplicates
    }

    private fun dedupeTracks(db: SupportSQLiteDatabase): Int {
        db.execSQL("UPDATE tracks SET scanNaturalKey = 'track|' || lower(trim(COALESCE(albumId, ''))) || '|' || lower(trim(title)) || '|' || COALESCE(discNumber, 0) || '|' || COALESCE(trackNumber, 0) WHERE scanNaturalKey IS NULL AND sourceUri IS NOT NULL")
        db.execSQL("DROP TABLE IF EXISTS temp.round15_track_map")
        db.execSQL(
            """CREATE TEMP TABLE round15_track_map AS
                SELECT d.id AS duplicateId,
                    (SELECT k.id FROM tracks k
                     WHERE k.scanNaturalKey = d.scanNaturalKey
                     ORDER BY (k.stars IS NOT NULL) DESC,
                              (k.reviewText IS NOT NULL) DESC,
                              (k.stableKey IS NOT NULL) DESC,
                              (k.fileHash64k IS NOT NULL) DESC,
                              (k.isMissing = 0) DESC,
                              k.updatedAt DESC, k.rowid ASC LIMIT 1) AS keepId
                FROM tracks d
                WHERE d.scanNaturalKey IS NOT NULL AND trim(d.scanNaturalKey) != ''""".trimIndent(),
        )
        val duplicates = count(db, "temp.round15_track_map", "duplicateId != keepId")
        if (duplicates == 0) return 0

        db.execSQL("""UPDATE tracks SET
            stars = COALESCE(stars, (SELECT d.stars FROM tracks d WHERE d.scanNaturalKey = tracks.scanNaturalKey AND d.stars IS NOT NULL ORDER BY d.updatedAt DESC, d.rowid ASC LIMIT 1)),
            reviewText = COALESCE(reviewText, (SELECT d.reviewText FROM tracks d WHERE d.scanNaturalKey = tracks.scanNaturalKey AND d.reviewText IS NOT NULL ORDER BY d.updatedAt DESC, d.rowid ASC LIMIT 1)),
            sourceUri = COALESCE(sourceUri, (SELECT d.sourceUri FROM tracks d WHERE d.scanNaturalKey = tracks.scanNaturalKey AND d.sourceUri IS NOT NULL ORDER BY d.updatedAt DESC, d.rowid ASC LIMIT 1)),
            fileName = COALESCE(fileName, (SELECT d.fileName FROM tracks d WHERE d.scanNaturalKey = tracks.scanNaturalKey AND d.fileName IS NOT NULL ORDER BY d.updatedAt DESC, d.rowid ASC LIMIT 1)),
            stableKey = COALESCE(stableKey, (SELECT d.stableKey FROM tracks d WHERE d.scanNaturalKey = tracks.scanNaturalKey AND d.stableKey IS NOT NULL ORDER BY d.updatedAt DESC, d.rowid ASC LIMIT 1)),
            fileSizeBytes = COALESCE(fileSizeBytes, (SELECT d.fileSizeBytes FROM tracks d WHERE d.scanNaturalKey = tracks.scanNaturalKey AND d.fileSizeBytes IS NOT NULL ORDER BY d.updatedAt DESC, d.rowid ASC LIMIT 1)),
            fileHash64k = COALESCE(fileHash64k, (SELECT d.fileHash64k FROM tracks d WHERE d.scanNaturalKey = tracks.scanNaturalKey AND d.fileHash64k IS NOT NULL ORDER BY d.updatedAt DESC, d.rowid ASC LIMIT 1)),
            isMissing = CASE WHEN isMissing = 0 THEN 0 ELSE COALESCE((SELECT min(d.isMissing) FROM tracks d WHERE d.scanNaturalKey = tracks.scanNaturalKey), isMissing) END,
            updatedAt = max(updatedAt, COALESCE((SELECT max(d.updatedAt) FROM tracks d WHERE d.scanNaturalKey = tracks.scanNaturalKey), updatedAt))
            WHERE id IN (SELECT keepId FROM temp.round15_track_map WHERE duplicateId != keepId)""")
        db.execSQL("DELETE FROM external_links WHERE trackId IN (SELECT duplicateId FROM temp.round15_track_map WHERE duplicateId != keepId) AND EXISTS (SELECT 1 FROM external_links k JOIN temp.round15_track_map m ON m.keepId = k.trackId WHERE m.duplicateId = external_links.trackId AND k.sourceId = external_links.sourceId)")
        db.execSQL("UPDATE external_links SET trackId = (SELECT keepId FROM temp.round15_track_map WHERE duplicateId = external_links.trackId) WHERE trackId IN (SELECT duplicateId FROM temp.round15_track_map WHERE duplicateId != keepId)")
        db.execSQL("UPDATE credits SET trackId = (SELECT keepId FROM temp.round15_track_map WHERE duplicateId = credits.trackId) WHERE trackId IN (SELECT duplicateId FROM temp.round15_track_map WHERE duplicateId != keepId)")
        db.execSQL("UPDATE review_revisions SET trackId = (SELECT keepId FROM temp.round15_track_map WHERE duplicateId = review_revisions.trackId) WHERE trackId IN (SELECT duplicateId FROM temp.round15_track_map WHERE duplicateId != keepId)")
        db.execSQL("UPDATE listening_logs SET trackId = (SELECT keepId FROM temp.round15_track_map WHERE duplicateId = listening_logs.trackId) WHERE trackId IN (SELECT duplicateId FROM temp.round15_track_map WHERE duplicateId != keepId)")
        db.execSQL("UPDATE audio_analysis SET trackId = (SELECT keepId FROM temp.round15_track_map WHERE duplicateId = audio_analysis.trackId) WHERE trackId IN (SELECT duplicateId FROM temp.round15_track_map WHERE duplicateId != keepId)")
        db.execSQL("DELETE FROM library_search_fts WHERE entityType = 'track' AND entityId IN (SELECT duplicateId FROM temp.round15_track_map WHERE duplicateId != keepId)")
        db.execSQL("DELETE FROM tracks WHERE id IN (SELECT duplicateId FROM temp.round15_track_map WHERE duplicateId != keepId)")
        return duplicates
    }

    private fun count(db: SupportSQLiteDatabase, table: String, where: String? = null): Int {
        val sql = "SELECT COUNT(*) FROM $table" + (where?.let { " WHERE $it" } ?: "")
        return db.query(sql).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }
    }
}
