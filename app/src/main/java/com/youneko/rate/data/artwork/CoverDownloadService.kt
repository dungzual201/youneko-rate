package com.youneko.rate.data.artwork

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
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
        Log.d("COVER", "src uri=$uri")
        val directory = File(context.filesDir, "covers").apply { mkdirs() }
        val temp = File(directory, "$albumId.import.tmp")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(temp).use { output -> input.copyTo(output); output.flush(); output.fd.sync() }
            } ?: return@withContext CoverDownloadResult.Failure(FailureReason.INVALID_IMAGE)
            processTemp(albumId, temp, directory, source)
        } catch (_: IOException) {
            CoverDownloadResult.Failure(FailureReason.WRITE_FAILED)
        } catch (_: SecurityException) {
            CoverDownloadResult.Failure(FailureReason.WRITE_FAILED)
        } finally {
            temp.delete()
        }
    }

    suspend fun download(
        albumId: String,
        url: String,
        source: String,
        onProgress: (Int) -> Unit = {},
    ): CoverDownloadResult = withContext(Dispatchers.IO) {
        Log.d("COVER", "src uri=$url")
        val directory = File(context.filesDir, "covers").apply { mkdirs() }
        val temp = File(directory, "$albumId.download.tmp")
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
                    FileOutputStream(temp).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            copied += count
                            if (copied > MAX_BYTES) return@withContext CoverDownloadResult.Failure(FailureReason.TOO_LARGE)
                            output.write(buffer, 0, count)
                            if (total > 0) onProgress((copied * 100L / total).toInt().coerceIn(0, 100))
                        }
                        output.flush()
                        output.fd.sync()
                    }
                }
            }
            processTemp(albumId, temp, directory, source)
        } catch (_: IOException) {
            CoverDownloadResult.Failure(FailureReason.NETWORK)
        } catch (_: SecurityException) {
            CoverDownloadResult.Failure(FailureReason.WRITE_FAILED)
        } finally {
            temp.delete()
        }
    }

    private fun processTemp(albumId: String, sourceFile: File, directory: File, source: String): CoverDownloadResult {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(sourceFile.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return CoverDownloadResult.Failure(FailureReason.INVALID_IMAGE)
        if (bounds.outWidth < MIN_DIMENSION || bounds.outHeight < MIN_DIMENSION) return CoverDownloadResult.Failure(FailureReason.TOO_SMALL)

        val decoded = decodeBitmap(sourceFile) ?: return CoverDownloadResult.Failure(FailureReason.INVALID_IMAGE)
        val bitmap = applyExifOrientation(decoded, sourceFile)
        val width = bitmap.width
        val height = bitmap.height
        Log.d("COVER", "decoded w=$width h=$height config=${bitmap.config} bytes=${bitmap.byteCount}")
        val original = File(directory, "${albumId}.import_orig.jpg")
        val thumbnail = File(directory, "${albumId}.import.jpg")
        val originalOk = writeJpegAtomic(bitmap, original)
        val thumbnailOk = originalOk && writeThumbnail(bitmap, thumbnail)
        val compressOk = originalOk && thumbnailOk
        Log.d("COVER", "saved path=${thumbnail.absolutePath} fileSize=${thumbnail.length()} compressOk=$compressOk")
        val verify = BitmapFactory.decodeFile(thumbnail.absolutePath)
        Log.d("COVER", "verify reload w=${verify?.width ?: 0} h=${verify?.height ?: 0} notNull=${verify != null}")
        verify?.recycle()
        bitmap.recycle()
        if (!compressOk) {
            original.delete()
            thumbnail.delete()
            return CoverDownloadResult.Failure(FailureReason.WRITE_FAILED)
        }
        return CoverDownloadResult.Success(DownloadedCover(albumId, original, thumbnail, width, height, source))
    }

    private fun decodeBitmap(file: File): Bitmap? {
        val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    private fun applyExifOrientation(bitmap: Bitmap, file: File): Bitmap {
        val orientation = runCatching {
            file.inputStream().use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        }
            .getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(-90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
            if (it !== bitmap) bitmap.recycle()
        }
    }

    private fun writeThumbnail(bitmap: Bitmap, output: File): Boolean {
        val maxDimension = maxOf(bitmap.width, bitmap.height)
        val scaled = if (maxDimension > PROCESSED_SIZE) {
            val ratio = PROCESSED_SIZE.toFloat() / maxDimension
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt().coerceAtLeast(1), (bitmap.height * ratio).toInt().coerceAtLeast(1), true)
        } else bitmap
        val ok = writeJpegAtomic(scaled, output)
        if (scaled !== bitmap) scaled.recycle()
        return ok
    }

    private fun writeJpegAtomic(bitmap: Bitmap, target: File): Boolean {
        val temporary = File(target.parentFile, "${target.name}.tmp")
        return try {
            var ok = false
            FileOutputStream(temporary).use { output ->
                ok = bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
                output.flush()
                output.fd.sync()
            }
            if (!ok) {
                temporary.delete()
                false
            } else if (!temporary.renameTo(target)) {
                temporary.delete()
                false
            } else true
        } catch (_: IOException) {
            temporary.delete()
            false
        }
    }

    companion object {
        private const val MAX_BYTES = 20L * 1024L * 1024L
        private const val MIN_DIMENSION = 300
        private const val PROCESSED_SIZE = 1500
    }
}
