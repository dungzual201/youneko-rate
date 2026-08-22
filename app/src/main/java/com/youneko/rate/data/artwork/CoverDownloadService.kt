package com.youneko.rate.data.artwork

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

sealed interface CoverDownloadResult {
    data class Success(val cover: DownloadedCover) : CoverDownloadResult
    data class Failure(val reason: FailureReason) : CoverDownloadResult
}

enum class FailureReason { NETWORK, HOTLINK_BLOCKED, TOO_LARGE, INVALID_IMAGE, TOO_SMALL, WRITE_FAILED }

data class DownloadedCover(
    val albumId: String,
    val originalFile: File,
    val thumbnailFile: File,
    val width: Int,
    val height: Int,
    val source: String,
)

@Singleton
class CoverDownloadService @Inject constructor(
    @ApplicationContext private val context: Context,
    @Named("coverArt") private val client: OkHttpClient,
) {
    suspend fun importFromUri(
        albumId: String,
        uri: Uri,
        source: String = "manual",
    ): CoverDownloadResult = withContext(Dispatchers.IO) {
        val directory = File(context.filesDir, "covers").apply { mkdirs() }
        val temp = File(directory, "$albumId.import")
        try {
            context.contentResolver.openInputStream(uri)?.use { input -> temp.outputStream().use(input::copyTo) }
                ?: return@withContext CoverDownloadResult.Failure(FailureReason.INVALID_IMAGE)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(temp.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext CoverDownloadResult.Failure(FailureReason.INVALID_IMAGE)
            if (bounds.outWidth < MIN_DIMENSION || bounds.outHeight < MIN_DIMENSION) return@withContext CoverDownloadResult.Failure(FailureReason.TOO_SMALL)
            val bitmap = BitmapFactory.decodeFile(temp.absolutePath) ?: return@withContext CoverDownloadResult.Failure(FailureReason.INVALID_IMAGE)
            val original = File(directory, "${albumId}.import_orig.jpg")
            val thumbnail = File(directory, "${albumId}.import.jpg")
            original.outputStream().use { output -> check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)) }
            writeThumbnail(bitmap, thumbnail)
            bitmap.recycle()
            temp.delete()
            CoverDownloadResult.Success(DownloadedCover(albumId, original, thumbnail, bounds.outWidth, bounds.outHeight, source))
        } catch (_: IOException) {
            temp.delete()
            CoverDownloadResult.Failure(FailureReason.NETWORK)
        } catch (_: SecurityException) {
            temp.delete()
            CoverDownloadResult.Failure(FailureReason.WRITE_FAILED)
        }
    }

    suspend fun download(
        albumId: String,
        url: String,
        source: String,
        onProgress: (Int) -> Unit = {},
    ): CoverDownloadResult = withContext(Dispatchers.IO) {
        val directory = File(context.filesDir, "covers").apply { mkdirs() }
        val temp = File(directory, "$albumId.download")
        try {
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext CoverDownloadResult.Failure(if (response.code == 401 || response.code == 403) FailureReason.HOTLINK_BLOCKED else FailureReason.NETWORK)
                }
                val body = response.body ?: return@withContext CoverDownloadResult.Failure(FailureReason.INVALID_IMAGE)
                if (body.contentLength() > MAX_BYTES) return@withContext CoverDownloadResult.Failure(FailureReason.TOO_LARGE)
                var copied = 0L
                val total = body.contentLength()
                body.byteStream().use { input ->
                    temp.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            copied += count
                            if (copied > MAX_BYTES) return@withContext CoverDownloadResult.Failure(FailureReason.TOO_LARGE)
                            output.write(buffer, 0, count)
                            if (total > 0) onProgress((copied * 100L / total).toInt().coerceIn(0, 100))
                        }
                    }
                }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(temp.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext CoverDownloadResult.Failure(FailureReason.INVALID_IMAGE)
            if (bounds.outWidth < MIN_DIMENSION || bounds.outHeight < MIN_DIMENSION) return@withContext CoverDownloadResult.Failure(FailureReason.TOO_SMALL)
            val bitmap = BitmapFactory.decodeFile(temp.absolutePath) ?: return@withContext CoverDownloadResult.Failure(FailureReason.INVALID_IMAGE)
            val original = File(directory, "${albumId}.download_orig.jpg")
            val thumbnail = File(directory, "${albumId}.download.jpg")
            temp.copyTo(original, overwrite = true)
            writeThumbnail(bitmap, thumbnail)
            bitmap.recycle()
            temp.delete()
            CoverDownloadResult.Success(DownloadedCover(albumId, original, thumbnail, bounds.outWidth, bounds.outHeight, source))
        } catch (_: IOException) {
            temp.delete()
            CoverDownloadResult.Failure(FailureReason.NETWORK)
        } catch (_: SecurityException) {
            temp.delete()
            CoverDownloadResult.Failure(FailureReason.WRITE_FAILED)
        }
    }

    private fun writeThumbnail(bitmap: Bitmap, output: File) {
        val maxDimension = maxOf(bitmap.width, bitmap.height)
        val scaled = if (maxDimension > PROCESSED_SIZE) {
            val ratio = PROCESSED_SIZE.toFloat() / maxDimension
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt().coerceAtLeast(1), (bitmap.height * ratio).toInt().coerceAtLeast(1), true)
        } else bitmap
        output.outputStream().use { stream ->
            check(scaled.compress(Bitmap.CompressFormat.JPEG, 90, stream)) { "cover compression failed" }
        }
        if (scaled !== bitmap) scaled.recycle()
    }

    companion object {
        private const val MAX_BYTES = 20L * 1024L * 1024L
        private const val MIN_DIMENSION = 300
        private const val PROCESSED_SIZE = 1500
    }
}
