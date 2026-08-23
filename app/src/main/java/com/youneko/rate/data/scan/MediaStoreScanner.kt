package com.youneko.rate.data.scan

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.util.Log
import androidx.room.withTransaction
import com.youneko.rate.data.MediaScanStore
import com.youneko.rate.data.artwork.ArtworkStore
import com.youneko.rate.data.importer.AudioTag
import com.youneko.rate.data.importer.ImportDedupe
import com.youneko.rate.data.importer.LocalAudioTagReader
import com.youneko.rate.data.local.ScanDedupe
import com.youneko.rate.data.local.ScanDedupeStats
import com.youneko.rate.data.local.ScanNaturalKey
import com.youneko.rate.data.local.YounekoDatabase
import com.youneko.rate.data.local.dao.AlbumDao
import com.youneko.rate.data.local.dao.ArtistDao
import com.youneko.rate.data.local.dao.LibrarySearchFtsDao
import com.youneko.rate.data.local.dao.ScanRootDao
import com.youneko.rate.data.local.dao.TrackDao
import com.youneko.rate.data.local.entity.AlbumEntity
import com.youneko.rate.data.local.entity.ArtistEntity
import com.youneko.rate.data.local.entity.LibrarySearchFtsEntity
import com.youneko.rate.data.local.entity.TrackEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first

private const val HASH_BYTES = 64 * 1024
private const val SCAN_BATCH_SIZE = 400
private const val SCAN_TAG = "SCAN"

/** A local MediaStore row. This class never exposes audio playback APIs. */
data class MediaStoreAudioRow(
    val id: Long,
    val uri: Uri,
    val title: String?,
    val artist: String?,
    val album: String?,
    val albumArtist: String?,
    val mediaStoreAlbumId: Long?,
    val trackNumber: Int?,
    val discNumber: Int?,
    val year: Int?,
    val durationMs: Long?,
    val mimeType: String?,
    val sizeBytes: Long?,
    val relativePath: String?,
    val displayName: String,
    val dateModifiedSeconds: Long,
    val dateAddedSeconds: Long,
)

data class MediaScanResult(
    val scanned: Int,
    val added: Int,
    val updated: Int,
    val missing: Int,
    val skipped: Boolean = false,
)

object StableMediaKey {
    fun from(sizeBytes: Long?, durationMs: Long?, first64kHash: String?): String? {
        if (sizeBytes == null || durationMs == null || first64kHash.isNullOrBlank()) return null
        return "$sizeBytes|${durationMs / 1000}|$first64kHash"
    }

    fun durationMatches(left: Long?, right: Long?): Boolean = left != null && right != null && kotlin.math.abs(left - right) <= 1_000L

    fun sizeBytes(context: Context, uri: Uri): Long? = runCatching {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
        }
    }.getOrNull()

    fun first64kHash(context: Context, uri: Uri): String? = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(8192)
            var remaining = HASH_BYTES
            while (remaining > 0) {
                val count = input.read(buffer, 0, minOf(buffer.size, remaining))
                if (count <= 0) break
                digest.update(buffer, 0, count)
                remaining -= count
            }
        } ?: return null
        digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()
}

@Singleton
class MediaStoreScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: YounekoDatabase,
    private val albumDao: AlbumDao,
    private val artistDao: ArtistDao,
    private val trackDao: TrackDao,
    private val ftsDao: LibrarySearchFtsDao,
    private val scanRootDao: ScanRootDao,
    private val scanStore: MediaScanStore,
    private val tagReader: LocalAudioTagReader,
    private val artworkStore: ArtworkStore,
) {
    suspend fun scan(
        forceFull: Boolean = false,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
        onPhaseChanged: (ScanPhase) -> Unit = {},
    ): MediaScanResult {
        val scanStartedAt = System.currentTimeMillis()
        Log.i(
            SCAN_TAG,
            "SCAN: sdk=${Build.VERSION.SDK_INT} " +
                "READ_MEDIA_AUDIO=${permissionStatus(android.Manifest.permission.READ_MEDIA_AUDIO)} " +
                "READ_EXTERNAL_STORAGE=${permissionStatus(android.Manifest.permission.READ_EXTERNAL_STORAGE)}",
        )
        val checkpoint = scanStore.checkpoint.first()
        val generation = currentGeneration()
        Log.i(SCAN_TAG, "SCAN: lastScanTime=${checkpoint.lastScanTimeMs} lastGeneration=${checkpoint.lastGeneration} currentGeneration=$generation")
        if (!forceFull && generation != null && MediaScanPolicy.shouldSkip(forceFull, checkpoint, generation)) {
            Log.i(SCAN_TAG, "SCAN: gen stored=${checkpoint.lastGeneration} current=$generation decision=skip")
            return MediaScanResult(0, 0, 0, 0, skipped = true)
        }
        Log.i(SCAN_TAG, "SCAN: gen stored=${checkpoint.lastGeneration} current=$generation decision=run")
        onPhaseChanged(ScanPhase.METADATA)
        val full = MediaScanPolicy.requiresFull(forceFull, checkpoint, generation)
        val rows = queryRows(MediaScanPolicy.changedAfter(checkpoint, forceFull, generation))
        if (rows.isEmpty()) {
            val missing = markMissingIfNeeded(rows, full)
            val albumCount = albumDao.findAll().size
            val trackCount = trackDao.findAll().size
            Log.i(SCAN_TAG, "SCAN: phase1 durationMs=${System.currentTimeMillis() - scanStartedAt} rows=0")
            Log.i(SCAN_TAG, "SCAN: inserted=0 updated=0 albums=$albumCount")
            Log.i(SCAN_TAG, "SCAN: db tracks=$trackCount albums=$albumCount")
            scanStore.save(System.currentTimeMillis(), generation ?: -1L, MediaScanPolicy.PROVIDER_VERSION)
            return MediaScanResult(0, 0, 0, missing)
        }

        val phase1StartedAt = System.currentTimeMillis()
        val existing = trackDao.findAll()
        val inserts = mutableListOf<TrackEntity>()
        val updates = mutableListOf<TrackEntity>()
        val seenMediaIds = rows.mapTo(mutableSetOf()) { it.id }
        rows.forEachIndexed { index, row ->
            val candidates = existing + inserts
            val direct = findMatchWithoutHash(row, candidates)
            val possibleRematch = if (direct == null) findPossibleRematch(row, candidates) else null
            val lazyHash = if (possibleRematch != null) first64kHash(row.uri) else null
            val lazyStableKey = if (possibleRematch != null) StableMediaKey.from(row.sizeBytes, row.durationMs, lazyHash) else null
            val current = direct ?: candidates.firstOrNull { candidate ->
                lazyStableKey != null && candidate.stableKey == lazyStableKey
            }
            val albumId = resolveAlbumId(row, null)
            val now = System.currentTimeMillis()
            if (current == null) {
                val title = row.title?.takeIf(String::isNotBlank) ?: row.displayName
                inserts += TrackEntity(
                    id = UUID.randomUUID().toString(),
                    albumId = albumId,
                    title = title,
                    scanNaturalKey = ScanNaturalKey.track(albumId, title, row.discNumber, row.trackNumber),
                    trackNumber = row.trackNumber,
                    discNumber = row.discNumber,
                    durationMs = row.durationMs,
                    isStandalone = albumId == null,
                    sourceUri = row.uri.toString(),
                    fileName = row.displayName,
                    createdAt = now,
                    updatedAt = now,
                    mediaStoreId = row.id,
                    fileSizeBytes = row.sizeBytes,
                    stableKey = null,
                    fileHash64k = null,
                    isMissing = false,
                    missingSince = null,
                    mediaStoreModifiedSeconds = row.dateModifiedSeconds,
                )
            } else {
                updates += MissingTrackPolicy.markPresent(
                    current.copy(
                        albumId = albumId ?: current.albumId,
                        isStandalone = albumId == null && current.albumId == null,
                        scanNaturalKey = current.scanNaturalKey ?: ScanNaturalKey.track(albumId ?: current.albumId, row.title ?: current.title, row.discNumber ?: current.discNumber, row.trackNumber ?: current.trackNumber),
                        sourceUri = row.uri.toString(),
                        fileName = row.displayName,
                        mediaStoreId = row.id,
                        stableKey = lazyStableKey ?: current.stableKey,
                        fileSizeBytes = row.sizeBytes,
                        fileHash64k = lazyHash ?: current.fileHash64k,
                        isMissing = false,
                        missingSince = null,
                        mediaStoreModifiedSeconds = row.dateModifiedSeconds,
                        durationMs = current.durationMs ?: row.durationMs,
                    ),
                    now,
                )
            }
            onProgress(index + 1, rows.size)
        }
        inserts.chunked(SCAN_BATCH_SIZE).forEach { batch ->
            database.withTransaction {
                trackDao.insertAll(batch)
                batch.forEach { track -> ftsDao.upsert(LibrarySearchFtsEntity(track.id, "track", "${track.title} ${track.fileName.orEmpty()}")) }
            }
        }
        updates.chunked(SCAN_BATCH_SIZE).forEach { batch ->
            database.withTransaction {
                trackDao.updateAll(batch)
                batch.forEach { track -> ftsDao.upsert(LibrarySearchFtsEntity(track.id, "track", "${track.title} ${track.fileName.orEmpty()}")) }
            }
        }
        val missing = markMissingIfNeeded(rows, full, seenMediaIds)
        val phase1Duration = System.currentTimeMillis() - phase1StartedAt
        Log.i(SCAN_TAG, "SCAN: phase1 durationMs=$phase1Duration rows=${rows.size}")
        Log.i(SCAN_TAG, "SCAN: inserted=${inserts.size} updated=${updates.size} albums=${albumDao.findAll().size}")
        onPhaseChanged(ScanPhase.ARTWORK)
        val phase2StartedAt = System.currentTimeMillis()
        val enriched = enrichRows(rows, onProgress)
        Log.i(SCAN_TAG, "SCAN: phase2 durationMs=${System.currentTimeMillis() - phase2StartedAt} enriched=$enriched")
        scanStore.save(System.currentTimeMillis(), generation ?: -1L, MediaScanPolicy.PROVIDER_VERSION)
        val albumCount = albumDao.findAll().size
        val trackCount = trackDao.findAll().size
        Log.i(SCAN_TAG, "SCAN: db tracks=$trackCount albums=$albumCount")
        return MediaScanResult(rows.size, inserts.size, updates.size, missing)
    }

    suspend fun dedupeIfNeeded(): ScanDedupeStats {
        if (scanStore.dedupeCompleted.first()) {
            val existingAlbums = albumDao.findAll().size
            val stats = ScanDedupeStats(merged = 0, deleted = 0, albumsRemaining = existingAlbums)
            Log.d(SCAN_TAG, "dedupe merged=0 deleted=0 albums remaining=$existingAlbums")
            return stats
        }
        val stats = database.withTransaction { ScanDedupe.run(database.openHelper.writableDatabase) }
        scanStore.markDedupeCompleted()
        Log.d(SCAN_TAG, "dedupe merged=${stats.merged} deleted=${stats.deleted} albums remaining=${stats.albumsRemaining}")
        return stats
    }

    suspend fun hasSafRoots(): Boolean = scanRootDao.findAll().isNotEmpty()

    suspend fun scanSafRoots(onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): MediaScanResult {
        val uris = scanRootDao.findAll().flatMap { root -> tagReader.collectAudioUris(Uri.parse(root.uri), isTree = true) }
        if (uris.isEmpty()) return MediaScanResult(0, 0, 0, 0)
        val tags = tagReader.readAll(uris).tags
        val existing = trackDao.findAll().toMutableList()
        var added = 0
        var updated = 0
        tags.forEachIndexed { index, tag ->
            val uri = Uri.parse(tag.uri)
            val size = StableMediaKey.sizeBytes(context, uri)
            val hash = StableMediaKey.first64kHash(context, uri)
            val stableKey = StableMediaKey.from(size, tag.durationMs, hash)
            val title = tag.title?.takeIf(String::isNotBlank) ?: tag.fileName
            val trackNaturalKey = ScanNaturalKey.track(null, title, tag.discNumber, tag.trackNumber)
            val current = existing.firstOrNull { it.sourceUri == tag.uri || (stableKey != null && it.stableKey == stableKey) || (trackNaturalKey != null && it.scanNaturalKey == ScanNaturalKey.track(it.albumId, title, tag.discNumber, tag.trackNumber)) }
            val row = MediaStoreAudioRow(
                id = Long.MIN_VALUE,
                uri = uri,
                title = tag.title,
                artist = tag.artist,
                album = tag.album,
                albumArtist = tag.albumArtist,
                mediaStoreAlbumId = null,
                trackNumber = tag.trackNumber,
                discNumber = tag.discNumber,
                year = tag.year,
                durationMs = tag.durationMs,
                mimeType = null,
                sizeBytes = size,
                relativePath = null,
                displayName = tag.fileName,
                dateModifiedSeconds = 0L,
                dateAddedSeconds = 0L,
            )
            val albumId = resolveAlbumId(row, tag)
            if (current == null) {
                val now = System.currentTimeMillis()
                val created = TrackEntity(
                    id = UUID.randomUUID().toString(), albumId = albumId,
                    title = title,
                    scanNaturalKey = ScanNaturalKey.track(albumId, title, tag.discNumber, tag.trackNumber),
                    isStandalone = albumId == null,
                    isMissing = false,
                    trackNumber = tag.trackNumber, discNumber = tag.discNumber, durationMs = tag.durationMs,
                    sourceUri = tag.uri, fileName = tag.fileName, createdAt = now, updatedAt = now,
                    stableKey = stableKey, fileSizeBytes = size, fileHash64k = hash,
                )
                trackDao.insert(created)
                existing += created
                ftsDao.upsert(LibrarySearchFtsEntity(created.id, "track", "${created.title} ${tag.artist.orEmpty()}"))
                added++
            } else {
                trackDao.update(current.copy(sourceUri = tag.uri, fileName = tag.fileName, scanNaturalKey = current.scanNaturalKey ?: ScanNaturalKey.track(albumId, title, tag.discNumber, tag.trackNumber), stableKey = stableKey ?: current.stableKey, fileSizeBytes = size ?: current.fileSizeBytes, fileHash64k = hash ?: current.fileHash64k, isMissing = false, missingSince = null, updatedAt = System.currentTimeMillis()))
                updated++
            }
            onProgress(index + 1, tags.size)
        }
        return MediaScanResult(tags.size, added, updated, 0)
    }

    private suspend fun enrichRows(rows: List<MediaStoreAudioRow>, onProgress: (done: Int, total: Int) -> Unit): Int = coroutineScope {
        val dispatcher = Dispatchers.IO.limitedParallelism(4)
        val enriched = rows.map { row ->
            async(dispatcher) {
                row to runCatching { tagReader.readAll(listOf(row.uri)).tags.firstOrNull() }.getOrNull()
            }
        }.awaitAll()
        var count = 0
        val currentByMediaId = trackDao.findAll().filter { it.mediaStoreId != null }.associateBy { it.mediaStoreId }
        val currentByUri = trackDao.findAll().filter { it.sourceUri != null }.associateBy { it.sourceUri }
        enriched.forEachIndexed { index, (row, tag) ->
            if (tag != null) {
                val current = currentByMediaId[row.id] ?: currentByUri[row.uri.toString()]
                if (current != null) {
                    val albumId = resolveAlbumId(row, tag)
                    val cover = if (albumId != null && albumDao.findById(albumId)?.coverUri == null) {
                        if (tag.artwork != null) {
                            artworkStore.persistAlbumArtwork(albumId, tag.artwork.path, tag.artwork.source)
                        } else {
                            tagReader.extractArtwork(row.uri, albumId, row.mediaStoreAlbumId)
                        }
                    } else null
                    val now = System.currentTimeMillis()
                    database.withTransaction {
                        trackDao.update(
                            MissingTrackPolicy.markPresent(
                                current.copy(
                                    albumId = albumId ?: current.albumId,
                                    isStandalone = albumId == null && current.albumId == null,
                                    title = tag.title?.takeIf(String::isNotBlank) ?: current.title,
                                    trackNumber = tag.trackNumber ?: current.trackNumber,
                                    discNumber = tag.discNumber ?: current.discNumber,
                                    durationMs = tag.durationMs ?: current.durationMs,
                                    sourceUri = row.uri.toString(),
                                    fileName = row.displayName,
                                    mediaStoreId = row.id,
                                    mediaStoreModifiedSeconds = row.dateModifiedSeconds,
                                ),
                                now,
                            ),
                        )
                        if (cover != null && albumId != null) {
                            albumDao.findById(albumId)?.let { album ->
                                if (album.coverUri == null) {
                                    albumDao.update(
                                        album.copy(
                                            coverUri = java.io.File(cover.path).toURI().toString(),
                                            coverThumbUri = java.io.File(cover.path).toURI().toString(),
                                            coverSource = cover.source,
                                            coverWidth = cover.width,
                                            coverUpdatedAt = now,
                                            updatedAt = now,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                    count++
                }
            }
            onProgress(index + 1, rows.size)
        }
        count
    }

    private fun findMatchWithoutHash(row: MediaStoreAudioRow, candidates: List<TrackEntity>): TrackEntity? {
        candidates.firstOrNull { it.mediaStoreId == row.id }?.let { return it }
        val path = pathKey(row.relativePath, row.displayName)
        return candidates.firstOrNull { pathKey(null, it.fileName) == path && it.fileName != null }
    }

    private fun findPossibleRematch(row: MediaStoreAudioRow, candidates: List<TrackEntity>): TrackEntity? = candidates.firstOrNull { candidate ->
        ImportDedupe.normalize(candidate.title) == ImportDedupe.normalize(row.title) &&
            StableMediaKey.durationMatches(candidate.durationMs, row.durationMs)
    }

    private suspend fun resolveAlbumId(row: MediaStoreAudioRow, tag: AudioTag?): String? {
        val album = tag?.album?.takeIf(String::isNotBlank) ?: row.album?.takeIf(String::isNotBlank) ?: return null
        val artistName = tag?.albumArtist?.takeIf(String::isNotBlank)
            ?: row.albumArtist?.takeIf(String::isNotBlank)
            ?: tag?.artist?.takeIf(String::isNotBlank)
            ?: row.artist?.takeIf(String::isNotBlank)
            ?: "Không rõ nghệ sĩ"
        val year = tag?.year ?: row.year
        val existing = albumDao.findAll().firstOrNull { candidate ->
            val artist = artistDao.findById(candidate.artistId)
            artist != null && ImportDedupe.normalize(candidate.title) == ImportDedupe.normalize(album) &&
                ImportDedupe.normalize(artist.name) == ImportDedupe.normalize(artistName) && candidate.releaseYear == year
        }
        val naturalKey = ScanNaturalKey.album(album, artistName)
        if (existing != null) {
            if (existing.scanNaturalKey != naturalKey) albumDao.update(existing.copy(scanNaturalKey = naturalKey))
            return existing.id
        }
        val now = System.currentTimeMillis()
        val artist = artistDao.findByName(artistName) ?: ArtistEntity(UUID.randomUUID().toString(), artistName, createdAt = now, updatedAt = now).also { artistDao.insert(it) }
        val created = AlbumEntity(
            id = UUID.randomUUID().toString(),
            title = album,
            artistId = artist.id,
            scanNaturalKey = ScanNaturalKey.album(album, artistName),
            releaseYear = year,
            createdAt = now,
            updatedAt = now,
        )
        albumDao.insert(created)
        ftsDao.upsert(LibrarySearchFtsEntity(created.id, "album", "$album $artistName"))
        return created.id
    }

    private suspend fun findMatch(row: MediaStoreAudioRow, stableKey: String?, hash: String?): TrackEntity? {
        val all = trackDao.findAll()
        all.firstOrNull { it.mediaStoreId == row.id }?.let { return it }
        val path = pathKey(row.relativePath, row.displayName)
        all.firstOrNull { pathKey(null, it.fileName) == path && it.fileName != null }?.let { return it }
        if (stableKey != null) {
            all.firstOrNull { candidate ->
                candidate.stableKey == stableKey ||
                    (candidate.fileSizeBytes == row.sizeBytes && StableMediaKey.durationMatches(candidate.durationMs, row.durationMs) && candidate.fileHash64k == hash)
            }?.let { return it }
        }
        return null
    }

    private suspend fun markMissingIfNeeded(rows: List<MediaStoreAudioRow>, full: Boolean, seenIds: Set<Long> = emptySet()): Int {
        val present = if (full) {
            if (seenIds.isNotEmpty()) seenIds else rows.mapTo(mutableSetOf()) { it.id }
        } else {
            queryCurrentMediaIds()
        }
        var count = 0
        trackDao.findAll().filter { it.mediaStoreId != null }.forEach { track ->
            if (track.mediaStoreId !in present && !track.isMissing) {
                val marked = MissingTrackPolicy.markMissing(track, System.currentTimeMillis())
                if (marked != null) trackDao.update(marked)
                count++
            }
        }
        return count
    }

    private fun queryRows(changedAfterMs: Long?): List<MediaStoreAudioRow> {
        val resolver = context.contentResolver
        val volumes = mediaVolumeUris()
        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.DATA)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.ALBUM_ARTIST)
            add(MediaStore.Audio.Media.ALBUM_ID)
            add(MediaStore.Audio.Media.TRACK)
            add(MediaStore.Audio.Media.DISC_NUMBER)
            add(MediaStore.Audio.Media.YEAR)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.MIME_TYPE)
            add(MediaStore.Audio.Media.SIZE)
            if (Build.VERSION.SDK_INT >= 29) add(MediaStore.Audio.Media.RELATIVE_PATH)
            add(MediaStore.Audio.Media.DISPLAY_NAME)
            add(MediaStore.Audio.Media.DATE_MODIFIED)
            add(MediaStore.Audio.Media.DATE_ADDED)
        }.toTypedArray()
        val baseSelection = if (Build.VERSION.SDK_INT >= 29) {
            "(${MediaStore.Audio.Media.IS_MUSIC} != 0 OR is_podcast != 0)"
        } else {
            "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        }
        val selection = if (changedAfterMs == null) baseSelection else "$baseSelection AND ${MediaStore.Audio.Media.DATE_MODIFIED} > ?"
        val args = changedAfterMs?.let { arrayOf((it / 1000L).toString()) }
        var rawCount = 0
        var afterIsMusic = 0
        var skipped = 0
        var firstError: String? = null
        val result = mutableListOf<MediaStoreAudioRow>()
        volumes.forEach { uri ->
            rawCount += countRows(resolver, uri, null, null)
            afterIsMusic += countRows(resolver, uri, baseSelection, null)
            runCatching {
                resolver.query(uri, projection, selection, args, "${MediaStore.Audio.Media.DATE_MODIFIED} ASC")?.use { cursor ->
                    val index = { name: String -> cursor.getColumnIndex(name) }
                    val idIndex = index(MediaStore.Audio.Media._ID)
                    if (idIndex < 0) error("missing _id column")
                    while (cursor.moveToNext()) {
                        val row = runCatching {
                            val id = cursor.long(idIndex) ?: error("null _id")
                            val data = cursor.string(index(MediaStore.Audio.Media.DATA)) ?: error("null DATA")
                            require(File(data).exists()) { "file not found" }
                            val displayName = cursor.string(index(MediaStore.Audio.Media.DISPLAY_NAME)) ?: error("null DISPLAY_NAME")
                            val durationMs = cursor.long(index(MediaStore.Audio.Media.DURATION)) ?: error("null DURATION")
                            require(durationMs > 0L) { "zero DURATION" }
                            MediaStoreAudioRow(
                                id = id,
                                uri = ContentUris.withAppendedId(uri, id),
                                title = cursor.string(index(MediaStore.Audio.Media.TITLE)),
                                artist = cursor.string(index(MediaStore.Audio.Media.ARTIST)),
                                album = cursor.string(index(MediaStore.Audio.Media.ALBUM)),
                                albumArtist = cursor.string(index(MediaStore.Audio.Media.ALBUM_ARTIST)),
                                mediaStoreAlbumId = cursor.long(index(MediaStore.Audio.Media.ALBUM_ID)),
                                trackNumber = cursor.int(index(MediaStore.Audio.Media.TRACK)),
                                discNumber = cursor.int(index(MediaStore.Audio.Media.DISC_NUMBER)),
                                year = cursor.int(index(MediaStore.Audio.Media.YEAR)),
                                durationMs = durationMs,
                                mimeType = cursor.string(index(MediaStore.Audio.Media.MIME_TYPE)),
                                sizeBytes = cursor.long(index(MediaStore.Audio.Media.SIZE)),
                                relativePath = cursor.string(index(MediaStore.Audio.Media.RELATIVE_PATH)),
                                displayName = displayName,
                                dateModifiedSeconds = cursor.long(index(MediaStore.Audio.Media.DATE_MODIFIED)) ?: 0L,
                                dateAddedSeconds = cursor.long(index(MediaStore.Audio.Media.DATE_ADDED)) ?: 0L,
                            )
                        }.getOrElse { error ->
                            skipped++
                            if (firstError == null) firstError = error.message ?: error::class.java.simpleName
                            null
                        }
                        if (row != null) result += row
                    }
                }
            }.onFailure { error ->
                skipped++
                if (firstError == null) firstError = error.message ?: error::class.java.simpleName
                Log.e(SCAN_TAG, "SCAN: volume query exception uri=$uri", error)
            }
        }
        Log.i(SCAN_TAG, "SCAN: cursor rawCount=$rawCount")
        Log.i(SCAN_TAG, "SCAN: afterIsMusic=$afterIsMusic")
        Log.d(SCAN_TAG, "SCAN: skipped=$skipped total=$rawCount reason sample=${firstError ?: "none"}")
        return result
    }

    private fun countRows(resolver: android.content.ContentResolver, uri: Uri, selection: String?, args: Array<String>?): Int =
        runCatching {
            resolver.query(uri, arrayOf(MediaStore.Audio.Media._ID), selection, args, null)?.use { cursor ->
                var count = 0
                while (cursor.moveToNext()) count++
                count
            } ?: 0
        }.onFailure { Log.e(SCAN_TAG, "SCAN: count query exception uri=$uri", it) }.getOrDefault(0)

    private fun mediaVolumeUris(): List<Uri> = if (Build.VERSION.SDK_INT >= 29) {
        MediaStore.getExternalVolumeNames(context).map { volume -> MediaStore.Audio.Media.getContentUri(volume) }
    } else {
        listOf(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
    }.ifEmpty { listOf(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI) }

    private fun queryCurrentMediaIds(): Set<Long> = buildSet {
        val selection = if (Build.VERSION.SDK_INT >= 29) {
            "(${MediaStore.Audio.Media.IS_MUSIC} != 0 OR is_podcast != 0)"
        } else {
            "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        }
        mediaVolumeUris().forEach { uri ->
            context.contentResolver.query(uri, arrayOf(MediaStore.Audio.Media._ID), selection, null, null)?.use { cursor ->
                while (cursor.moveToNext()) add(cursor.getLong(0))
            }
        }
    }

    private fun permissionStatus(permission: String): String =
        if (context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) "granted" else "denied"

    private fun currentGeneration(): Long? = if (Build.VERSION.SDK_INT >= 30) {
        runCatching {
            MediaStore.getExternalVolumeNames(context)
                .mapNotNull { volume -> runCatching { MediaStore.getGeneration(context, volume) }.getOrNull() }
                .maxOrNull()
        }.getOrNull()
    } else null

    private fun first64kHash(uri: Uri): String? = StableMediaKey.first64kHash(context, uri)

    private fun pathKey(relativePath: String?, displayName: String?): String =
        listOf(relativePath.orEmpty(), displayName.orEmpty()).joinToString("/").lowercase()

    private fun android.database.Cursor.string(index: Int): String? = if (index < 0 || isNull(index)) null else getString(index)
    private fun android.database.Cursor.int(index: Int): Int? = if (index < 0 || isNull(index)) null else getInt(index)
    private fun android.database.Cursor.long(index: Int): Long? = if (index < 0 || isNull(index)) null else getLong(index)
}
