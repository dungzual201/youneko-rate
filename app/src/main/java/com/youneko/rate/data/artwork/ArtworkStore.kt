package com.youneko.rate.data.artwork

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArtworkStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    data class CachedArtwork(
        val path: String,
        val width: Int,
        val source: String,
    )

    fun persistAlbumArtwork(albumId: String, sourcePath: String, source: String): CachedArtwork? {
        val sourceFile = File(sourcePath)
        if (!sourceFile.isFile) return null
        val decoded = BitmapFactory.decodeFile(sourceFile.absolutePath) ?: return null
        return persistBitmap(albumId, decoded, source)
    }

    fun persistBitmap(albumId: String, bitmap: Bitmap, source: String): CachedArtwork? {
        val maxDimension = maxOf(bitmap.width, bitmap.height)
        if (maxDimension < 300) return null
        val scaled = if (maxDimension > 1000) {
            val ratio = 1000f / maxDimension.toFloat()
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt().coerceAtLeast(1), (bitmap.height * ratio).toInt().coerceAtLeast(1), true)
        } else {
            bitmap
        }
        val directory = File(context.filesDir, "covers").apply { mkdirs() }
        val output = File(directory, "$albumId.jpg")
        output.outputStream().use { stream ->
            if (!scaled.compress(Bitmap.CompressFormat.JPEG, 92, stream)) return null
        }
        val outputWidth = scaled.width
        if (scaled !== bitmap) scaled.recycle()
        return CachedArtwork(output.absolutePath, outputWidth, source)
    }
}
