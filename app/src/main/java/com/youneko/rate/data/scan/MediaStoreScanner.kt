package com.youneko.rate.data.scan

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.room.withTransaction
import com.youneko.rate.data.MediaScanStore
import com.youneko.rate.data.importer.AudioTag
import com.youneko.rate.data.importer.ImportDedupe
import com.youneko.rate.data.importer.LocalAudioTagReader
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
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

private const val HASH_BYTES = 64 * 1024

/** A local MediaStore row. This class never exposes audio playback APIs. */
data class MediaStoreAudioRow(
    val id: Long,
    val uri: Uri,
    val title: String?,
    val artist: String?,
    val album: String?,
    val albumArtist: String?,
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
) {
    suspend fun scan(forceFull: Boolean = false, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): MediaScanResult {
        val checkpoint = scanStore.checkpoint.first()
        val generation = currentGeneration()
        if (!forceFull && generation != null && MediaScanPolicy.shouldSkip(forceFull, checkpoint, generation)) {
            return MediaScanResult(0, 0, 0, 0, skipped = true)
        }
        val full = MediaScanPolicy.requiresFull(forceFull, checkpoint, generation)
        val rows = queryRows(MediaScanPolicy.changedAfter(checkpoint, forceFull, generation))
        if (rows.isEmpty()) {
            val missing = markMissingIfNeeded(rows, full)
            scanStore.save(System.currentTimeMillis(), generation ?: -1L, MediaScanPolicy.PROVIDER_VERSION)
            return MediaScanResult(0, 0, 0, missing)
        }
        val tagResult = tagReader.readAll(rows.map { it.uri })
        val tags = tagResult.tags.associateBy { it.uri }
        var added = 0
        var updated = 0
        val seenMediaIds = rows.mapTo(mutableSetOf()) { it.id }
        rows.forEachIndexed { index, row ->
            val tag = tags[row.uri.toString()]
            val hash = first64kHash(row.uri)
            val stableKey = StableMediaKey.from(row.sizeBytes, row.durationMs, hash)
            val existing = findMatch(row, stableKey, hash)
            val albumId = resolveAlbumId(row, tag)
            if (existing == null) {
                val track = TrackEntity(
                    id = UUID.randomUUID().toString(),
                    albumId = albumId,
                    title = tag?.title?.takeIf(String::isNotBlank) ?: row.title?.takeIf(String::isNotBlank) ?: row.displayName,
                    trackNumber = tag?.trackNumber ?: row.trackNumber,
                    discNumber = tag?.discNumber ?: row.discNumber,
                    durationMs = tag?.durationMs ?: row.durationMs,
                    sourceUri = row.uri.toString(),
                    fileName = row.displayName,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    mediaStoreId = row.id,
                    stableKey = stableKey,
                    fileSizeBytes = row.sizeBytes,
                    fileHash64k = hash,
                    isMissing = false,
                    missingSince = null,
                    mediaStoreModifiedSeconds = row.dateModifiedSeconds,
                )
                database.withTransaction {
                    trackDao.insert(track)
                    ftsDao.upsert(LibrarySearchFtsEntity(track.id, "track", "${track.title} ${row.artist.orEmpty()}"))
                }
                added++
            } else {
                val refreshed = MissingTrackPolicy.markPresent(existing.copy(
                    sourceUri = row.uri.toString(),
                    fileName = row.displayName,
                    mediaStoreId = row.id,
                    stableKey = stableKey ?: existing.stableKey,
                    fileSizeBytes = row.sizeBytes,
                    fileHash64k = hash ?: existing.fileHash64k,
                    isMissing = false,
                    missingSince = null,
                    mediaStoreModifiedSeconds = row.dateModifiedSeconds,
                    durationMs = existing.durationMs ?: tag?.durationMs ?: row.durationMs,
                ), System.currentTimeMillis())
                trackDao.update(refreshed)
                updated++
            }
            onProgress(index + 1, rows.size)
        }
        val missing = markMissingIfNeeded(rows, full, seenMediaIds)
        scanStore.save(System.currentTimeMillis(), generation ?: -1L, MediaScanPolicy.PROVIDER_VERSION)
        return MediaScanResult(rows.size, added, updated, missing)
    }

    suspend fun hasSafRoots(): Boolean = scanRootDao.findAll().isNotEmpty()

    suspend fun scanSafRoots(onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): MediaScanResult {
        val uris = scanRootDao.findAll().flatMap { root -> tagReader.collectAudioUris(Uri.parse(root.uri), isTree = true) }
        if (uris.isEmpty()) return MediaScanResult(0, 0, 0, 0)
        val tags = tagReader.readAll(uris).tags
        val existing = trackDao.findAll()
        var added = 0
        var updated = 0
        tags.forEachIndexed { index, tag ->
            val uri = Uri.parse(tag.uri)
            val size = StableMediaKey.sizeBytes(context, uri)
            val hash = StableMediaKey.first64kHash(context, uri)
            val stableKey = StableMediaKey.from(size, tag.durationMs, hash)
            val current = existing.firstOrNull { it.sourceUri == tag.uri || (stableKey != null && it.stableKey == stableKey) }
            val row = MediaStoreAudioRow(
                id = Long.MIN_VALUE,
                uri = uri,
                title = tag.title,
                artist = tag.artist,
                album = tag.album,
                albumArtist = tag.albumArtist,
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
                    title = tag.title?.takeIf(String::isNotBlank) ?: tag.fileName,
                    trackNumber = tag.trackNumber, discNumber = tag.discNumber, durationMs = tag.durationMs,
                    sourceUri = tag.uri, fileName = tag.fileName, createdAt = now, updatedAt = now,
                    stableKey = stableKey, fileSizeBytes = size, fileHash64k = hash,
                )
                trackDao.insert(created)
                ftsDao.upsert(LibrarySearchFtsEntity(created.id, "track", "${created.title} ${tag.artist.orEmpty()}"))
                added++
            } else {
                trackDao.update(current.copy(sourceUri = tag.uri, fileName = tag.fileName, stableKey = stableKey ?: current.stableKey, fileSizeBytes = size ?: current.fileSizeBytes, fileHash64k = hash ?: current.fileHash64k, isMissing = false, missingSince = null, updatedAt = System.currentTimeMillis()))
                updated++
            }
            onProgress(index + 1, tags.size)
        }
        return MediaScanResult(tags.size, added, updated, 0)
    }

    private suspend fun resolveAlbumId(row: MediaStoreAudioRow, tag: AudioTag?): String? {
        val album = tag?.album?.takeIf(String::isNotBlank) ?: row.album?.takeIf(String::isNotBlank) ?: return null
        val artistName = tag?.albumArtist?.takeIf(String::isNotBlank)
            ?: row.albumArtist?.takeIf(String::isNotBlank)
            ?: tag?.artist?.takeIf(String::isNotBlank)
            ?: row.artist?.takeIf(String::isNotBlank)
            ?: "Nghệ sĩ chưa rõ"
        val year = tag?.year ?: row.year
        val existing = albumDao.findAll().firstOrNull { candidate ->
            val artist = artistDao.findById(candidate.artistId)
            artist != null && ImportDedupe.normalize(candidate.title) == ImportDedupe.normalize(album) &&
                ImportDedupe.normalize(artist.name) == ImportDedupe.normalize(artistName) && candidate.releaseYear == year
        }
        if (existing != null) return existing.id
        val now = System.currentTimeMillis()
        val artist = artistDao.findByName(artistName) ?: ArtistEntity(UUID.randomUUID().toString(), artistName, createdAt = now, updatedAt = now).also { artistDao.insert(it) }
        val created = AlbumEntity(
            id = UUID.randomUUID().toString(),
            title = album,
            artistId = artist.id,
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
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.ALBUM_ARTIST)
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
        val selection = buildString {
            append("${MediaStore.Audio.Media.IS_MUSIC} != 0")
            if (changedAfterMs != null) append(" AND ${MediaStore.Audio.Media.DATE_MODIFIED} > ?")
        }
        val args = changedAfterMs?.let { arrayOf((it / 1000L).toString()) }
        return resolver.query(uri, projection, selection, args, "${MediaStore.Audio.Media.DATE_MODIFIED} ASC")?.use { cursor ->
            val result = mutableListOf<MediaStoreAudioRow>()
            val index = { name: String -> cursor.getColumnIndex(name) }
            while (cursor.moveToNext()) {
                val id = cursor.getLong(index(MediaStore.Audio.Media._ID))
                result += MediaStoreAudioRow(
                    id = id,
                    uri = ContentUris.withAppendedId(uri, id),
                    title = cursor.string(index(MediaStore.Audio.Media.TITLE)),
                    artist = cursor.string(index(MediaStore.Audio.Media.ARTIST)),
                    album = cursor.string(index(MediaStore.Audio.Media.ALBUM)),
                    albumArtist = cursor.string(index(MediaStore.Audio.Media.ALBUM_ARTIST)),
                    trackNumber = cursor.int(index(MediaStore.Audio.Media.TRACK)),
                    discNumber = cursor.int(index(MediaStore.Audio.Media.DISC_NUMBER)),
                    year = cursor.int(index(MediaStore.Audio.Media.YEAR)),
                    durationMs = cursor.long(index(MediaStore.Audio.Media.DURATION)),
                    mimeType = cursor.string(index(MediaStore.Audio.Media.MIME_TYPE)),
                    sizeBytes = cursor.long(index(MediaStore.Audio.Media.SIZE)),
                    relativePath = cursor.string(index(MediaStore.Audio.Media.RELATIVE_PATH)),
                    displayName = cursor.string(index(MediaStore.Audio.Media.DISPLAY_NAME)) ?: "audio_$id",
                    dateModifiedSeconds = cursor.long(index(MediaStore.Audio.Media.DATE_MODIFIED)) ?: 0L,
                    dateAddedSeconds = cursor.long(index(MediaStore.Audio.Media.DATE_ADDED)) ?: 0L,
                )
            }
            result
        }.orEmpty()
    }

    private fun queryCurrentMediaIds(): Set<Long> {
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        return context.contentResolver.query(
            uri,
            arrayOf(MediaStore.Audio.Media._ID),
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            null,
        )?.use { cursor ->
            buildSet {
                while (cursor.moveToNext()) add(cursor.getLong(0))
            }
        }.orEmpty()
    }

    private fun currentGeneration(): Long? = if (Build.VERSION.SDK_INT >= 30) {
        runCatching { MediaStore.getGeneration(context, MediaStore.VOLUME_EXTERNAL_PRIMARY) }.getOrNull()
    } else null

    private fun first64kHash(uri: Uri): String? = StableMediaKey.first64kHash(context, uri)

    private fun pathKey(relativePath: String?, displayName: String?): String =
        listOf(relativePath.orEmpty(), displayName.orEmpty()).joinToString("/").lowercase()

    private fun android.database.Cursor.string(index: Int): String? = if (index < 0 || isNull(index)) null else getString(index)
    private fun android.database.Cursor.int(index: Int): Int? = if (index < 0 || isNull(index)) null else getInt(index)
    private fun android.database.Cursor.long(index: Int): Long? = if (index < 0 || isNull(index)) null else getLong(index)
}
