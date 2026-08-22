package com.youneko.rate.data.artwork

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette
import com.youneko.rate.data.local.dao.AlbumPaletteDao
import com.youneko.rate.data.local.entity.AlbumEntity
import com.youneko.rate.data.local.entity.AlbumPaletteEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/** Immutable palette result used by the detail screen's static background. */
data class CoverPalette(
    val dominant: Color,
    val vibrant: Color?,
    val darkVibrant: Color?,
    val muted: Color?,
    val darkMuted: Color?,
    val lightVibrant: Color?,
    val onDominant: Color,
) {
    val swatches: List<Color>
        get() = listOfNotNull(dominant, vibrant, darkVibrant, lightVibrant, muted, darkMuted)
}

@Singleton
class CoverPaletteStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val paletteDao: AlbumPaletteDao,
) {
    suspend fun getOrCreate(album: AlbumEntity): CoverPalette? = withContext(Dispatchers.Default) {
        val cached = paletteDao.findByAlbumId(album.id)
        if (cached != null && cached.coverUpdatedAt == album.coverUpdatedAt) return@withContext cached.toPalette()
        val bitmap = decodeCover(album.coverUri) ?: return@withContext null
        val small = resizeForPalette(bitmap)
        val generated = Palette.from(small).maximumColorCount(16).generate()
        if (small !== bitmap) small.recycle()
        bitmap.recycle()
        val result = generated.toPalette()
        paletteDao.upsert(result.toEntity(album.id, album.coverUpdatedAt))
        LogPalette.record(album.id, result)
        result
    }

    suspend fun invalidate(albumId: String) = withContext(Dispatchers.IO) {
        paletteDao.deleteForAlbum(albumId)
    }

    private fun decodeCover(value: String?): Bitmap? {
        if (value.isNullOrBlank()) return null
        return runCatching {
            val uri = android.net.Uri.parse(value)
            val stream = if (uri.scheme != null) context.contentResolver.openInputStream(uri) else File(value).inputStream()
            stream?.use(BitmapFactory::decodeStream)
        }.getOrNull()
    }

    private fun resizeForPalette(bitmap: Bitmap): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= 128) return bitmap
        val ratio = 128f / longest.toFloat()
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt().coerceAtLeast(1), (bitmap.height * ratio).toInt().coerceAtLeast(1), true)
    }
}

private fun Palette.toPalette(): CoverPalette {
    val dominant = dominantSwatch?.rgb?.let(::Color) ?: Color(0xFF403A46)
    val vibrant = vibrantSwatch?.rgb?.let(::Color)
    val darkVibrant = darkVibrantSwatch?.rgb?.let(::Color)
    val muted = mutedSwatch?.rgb?.let(::Color)
    val darkMuted = darkMutedSwatch?.rgb?.let(::Color)
    val lightVibrant = lightVibrantSwatch?.rgb?.let(::Color)
    val onDominant = if (contrastRatio(dominant, Color.White) >= contrastRatio(dominant, Color.Black)) Color.White else Color.Black
    return CoverPalette(dominant, vibrant, darkVibrant, muted, darkMuted, lightVibrant, onDominant)
}

private fun AlbumPaletteEntity.toPalette() = CoverPalette(
    dominant = Color(dominantArgb),
    vibrant = vibrantArgb?.let(::Color),
    darkVibrant = darkVibrantArgb?.let(::Color),
    muted = mutedArgb?.let(::Color),
    darkMuted = darkMutedArgb?.let(::Color),
    lightVibrant = lightVibrantArgb?.let(::Color),
    onDominant = Color(onDominantArgb),
)

private fun CoverPalette.toEntity(albumId: String, coverUpdatedAt: Long?) = AlbumPaletteEntity(
    albumId = albumId,
    dominantArgb = dominant.toArgb(),
    vibrantArgb = vibrant?.toArgb(),
    darkVibrantArgb = darkVibrant?.toArgb(),
    mutedArgb = muted?.toArgb(),
    darkMutedArgb = darkMuted?.toArgb(),
    lightVibrantArgb = lightVibrant?.toArgb(),
    onDominantArgb = onDominant.toArgb(),
    coverUpdatedAt = coverUpdatedAt,
    generatedAt = System.currentTimeMillis(),
)

fun contrastRatio(first: Color, second: Color): Double {
    fun channel(value: Float): Double {
        val v = value.toDouble()
        return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
    }
    fun luminance(color: Color): Double = 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    val lighter = maxOf(luminance(first), luminance(second))
    val darker = minOf(luminance(first), luminance(second))
    return (lighter + 0.05) / (darker + 0.05)
}

private object LogPalette {
    fun record(albumId: String, palette: CoverPalette) {
        val ratio = contrastRatio(palette.dominant, palette.onDominant)
        android.util.Log.d("PALETTE", "album=$albumId dominant=${palette.dominant.toArgb()} adjustedTo=${palette.dominant.toArgb()} ratio=$ratio")
    }
}
