package com.youneko.rate.data.artwork

import android.content.Context
import coil.ImageLoader
import coil.memory.MemoryCache
import com.youneko.rate.data.AlbumRepository
import com.youneko.rate.data.local.dao.AlbumPaletteDao
import com.youneko.rate.data.local.entity.AlbumEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import java.io.File
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
        downloaded.originalFile.copyTo(finalOriginal, overwrite = true)
        downloaded.thumbnailFile.copyTo(finalThumbnail, overwrite = true)
        downloaded.originalFile.delete()
        downloaded.thumbnailFile.delete()
        val updated = album.album.copy(
            coverUri = finalOriginal.absolutePath,
            coverThumbUri = finalThumbnail.absolutePath,
            coverSource = "musichoarders:${downloaded.source}",
            coverWidth = downloaded.width,
            coverHeight = downloaded.height,
            coverUpdatedAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        repository.updateAlbum(updated)
        paletteDao.deleteForAlbum(albumId)
        evictCoil(oldCacheKey)
        Result.success(AppliedCover(album, updated, previous, System.currentTimeMillis() + UNDO_WINDOW_MILLIS))
    }

    suspend fun undo(applied: AppliedCover): Boolean = withContext(Dispatchers.IO) {
        if (System.currentTimeMillis() > applied.undoExpiresAtMillis) return@withContext false
        val previous = applied.previousFile
        val priorUri = applied.previousAlbum.album.coverUri
        if (previous.isFile && !priorUri.isNullOrBlank() && !priorUri.startsWith("content://")) {
            runCatching { previous.copyTo(File(priorUri), overwrite = true) }
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

    private fun evictCoil(key: String) {
        imageLoader.memoryCache?.remove(MemoryCache.Key(key))
        imageLoader.diskCache?.remove(key)
    }

    private fun imageKey(album: com.youneko.rate.data.LibraryAlbum): String = "album_${album.album.id}_${album.album.title}_${album.album.coverUri.orEmpty()}"
    private fun imageKey(album: AlbumEntity): String = "album_${album.id}_${album.title}_${album.coverUri.orEmpty()}"

    private suspend fun <T> kotlinx.coroutines.flow.Flow<T>.firstValue(): T? = first()

    companion object { private const val UNDO_WINDOW_MILLIS = 30_000L }
}

data class AppliedCover(
    val previousAlbum: com.youneko.rate.data.LibraryAlbum,
    val updatedAlbum: AlbumEntity,
    val previousFile: File,
    val undoExpiresAtMillis: Long,
)
