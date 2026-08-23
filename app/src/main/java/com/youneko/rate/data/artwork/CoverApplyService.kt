package com.youneko.rate.data.artwork

import android.content.Context
import coil.ImageLoader
import coil.memory.MemoryCache
import com.youneko.rate.data.AlbumRepository
import com.youneko.rate.data.local.dao.AlbumPaletteDao
import com.youneko.rate.data.local.entity.AlbumEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoverApplyService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: AlbumRepository,
    private val paletteDao: AlbumPaletteDao,
    private val imageLoader: ImageLoader,
) {
    suspend fun apply(albumId: String, downloaded: DownloadedCover): Result<AppliedCover> = withContext(Dispatchers.IO) {
        val album = repository.observeAlbum(albumId).firstValue() ?: return@withContext Result.failure(IllegalStateException("album not found"))
        val directory = File(context.filesDir, "covers").apply { mkdirs() }
        val previous = File(directory, "${albumId}_prev.jpg")
        preservePrevious(album, previous)
        val oldCacheKey = imageKey(album)
        val finalOriginal = File(directory, "${albumId}_orig.jpg")
        val finalThumbnail = File(directory, "$albumId.jpg")
        val originalOk = copyAtomically(downloaded.originalFile, finalOriginal)
        val thumbnailOk = originalOk && copyAtomically(downloaded.thumbnailFile, finalThumbnail)
        if (!originalOk || !thumbnailOk) {
            if (originalOk) finalOriginal.delete()
            if (thumbnailOk) finalThumbnail.delete()
            return@withContext Result.failure(IllegalStateException("cover file replacement failed"))
        }
        downloaded.originalFile.delete()
        downloaded.thumbnailFile.delete()
        val now = System.currentTimeMillis()
        val updated = album.album.copy(
            coverUri = finalOriginal.absolutePath,
            coverThumbUri = finalThumbnail.absolutePath,
            coverSource = downloaded.source,
            coverWidth = downloaded.width,
            coverHeight = downloaded.height,
            coverUpdatedAt = now,
            updatedAt = now,
        )
        repository.updateAlbum(updated)
        paletteDao.deleteForAlbum(albumId)
        evictCoil(oldCacheKey)
        android.util.Log.d("COVER", "applied album=$albumId source=${updated.coverSource} path=${finalThumbnail.absolutePath}")
        Result.success(AppliedCover(album, updated, previous, now + UNDO_WINDOW_MILLIS))
    }

    suspend fun undo(applied: AppliedCover): Boolean = withContext(Dispatchers.IO) {
        if (System.currentTimeMillis() > applied.undoExpiresAtMillis) return@withContext false
        val previous = applied.previousFile
        val priorUri = applied.previousAlbum.album.coverUri
        if (previous.isFile && !priorUri.isNullOrBlank() && !priorUri.startsWith("content://")) {
            copyAtomically(previous, File(priorUri))
        }
        repository.updateAlbum(applied.previousAlbum.album)
        paletteDao.deleteForAlbum(applied.updatedAlbum.id)
        evictCoil(imageKey(applied.updatedAlbum))
        true
    }

    private fun preservePrevious(album: com.youneko.rate.data.LibraryAlbum, previous: File) {
        val uri = album.album.coverUri ?: return
        runCatching {
            if (uri.startsWith("content://")) {
                context.contentResolver.openInputStream(android.net.Uri.parse(uri))?.use { input -> previous.outputStream().use(input::copyTo) }
            } else {
                File(uri).takeIf { it.isFile }?.copyTo(previous, overwrite = true)
            }
        }
    }

    private fun copyAtomically(source: File, target: File): Boolean {
        val temporary = File(target.parentFile, "${target.name}.tmp")
        return try {
            FileOutputStream(temporary).use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
                output.flush()
                output.fd.sync()
            }
            if (!temporary.renameTo(target)) {
                temporary.delete()
                false
            } else true
        } catch (_: Exception) {
            temporary.delete()
            false
        }
    }

    private fun evictCoil(key: String) {
        imageLoader.memoryCache?.remove(MemoryCache.Key(key))
        imageLoader.diskCache?.remove(key)
    }

    private fun imageKey(album: com.youneko.rate.data.LibraryAlbum): String = imageKey(album.album)
    private fun imageKey(album: AlbumEntity): String = "${album.coverUri.orEmpty()}-${album.coverUpdatedAt ?: 0L}"

    private suspend fun <T> kotlinx.coroutines.flow.Flow<T>.firstValue(): T? = first()

    companion object { private const val UNDO_WINDOW_MILLIS = 30_000L }
}

data class AppliedCover(
    val previousAlbum: com.youneko.rate.data.LibraryAlbum,
    val updatedAlbum: AlbumEntity,
    val previousFile: File,
    val undoExpiresAtMillis: Long,
)
