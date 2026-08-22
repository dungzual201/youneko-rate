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
        if (cached != null && cached.coverUpdatedAt == album.coverUpdatedAt) {
            return@withContext cached.toPalette().also { logPalette(album.id, it) }
        }
        val bitmap = decodeCover(album.coverUri) ?: return@withContext null
        val small = resizeForPalette(bitmap)
        val generated = Palette.from(small).maximumColorCount(24).clearFilters().generate()
        if (small !== bitmap) small.recycle()
        bitmap.recycle()
        val result = generated.toPalette()
        paletteDao.upsert(result.toEntity(album.id, album.coverUpdatedAt))
        LogPalette.record(album.id, result)
        logPalette(album.id, result)
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
    val detectedDominant = dominantSwatch?.rgb?.let(::Color) ?: Color(0xFF403A46)
    val vibrant = vibrantSwatch?.rgb?.let(::Color)
    val darkVibrant = darkVibrantSwatch?.rgb?.let(::Color)
    val muted = mutedSwatch?.rgb?.let(::Color)
    val darkMuted = darkMutedSwatch?.rgb?.let(::Color)
    val lightVibrant = lightVibrantSwatch?.rgb?.let(::Color)
    val dominant = vibrant ?: lightVibrant ?: muted ?: detectedDominant
    val onDominant = if (contrastRatio(dominant, Color.White) >= contrastRatio(dominant, Color.Black)) Color.White else Color.Black
    return CoverPalette(dominant, vibrant, darkVibrant, muted, darkMuted, lightVibrant, onDominant)
}

private fun logPalette(albumId: String, palette: CoverPalette) {
    fun Color?.hex(): String = this?.toArgb()?.let { "#%08X".format(it) } ?: "null"
    android.util.Log.d(
        "PALETTE",
        "album=$albumId vibrant=${palette.vibrant.hex()} lightVibrant=${palette.lightVibrant.hex()} darkVibrant=${palette.darkVibrant.hex()} muted=${palette.muted.hex()} darkMuted=${palette.darkMuted.hex()} dominant=${palette.dominant.hex()} CHOSEN=${palette.dominant.hex()}",
    )
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

fun coverDetailGradient(palette: CoverPalette?, seed: String, darkTheme: Boolean): androidx.compose.ui.graphics.Brush {
    val fallbackHue = (seed.hashCode().toUInt().toLong() % 360L).toFloat()
    val fallback = Color.hsv(fallbackHue, 0.42f, if (darkTheme) 0.55f else 0.78f)
    val chosen = palette?.vibrant ?: palette?.lightVibrant ?: palette?.muted ?: palette?.dominant ?: fallback
    val top = normalizeHslLightness(chosen)
    android.util.Log.d("PALETTE", "album=$seed CHOSEN=${chosen.toHex()} afterHSL=${top.toHex()}")
    val alpha = if (darkTheme) 0.90f else 0.55f
    return androidx.compose.ui.graphics.Brush.verticalGradient(
        colorStops = arrayOf(
            0.0f to top.copy(alpha = alpha),
            0.35f to top.copy(alpha = alpha * 0.5f),
            0.70f to if (darkTheme) Color(0xFF151118) else Color(0xFFFFF9FC),
            1.0f to if (darkTheme) Color(0xFF151118) else Color(0xFFFFF9FC),
        ),
    )
}

private fun Color.toHex(): String = "#%08X".format(toArgb())

fun normalizeHslLightness(color: Color): Color {
    val red = color.red.toDouble()
    val green = color.green.toDouble()
    val blue = color.blue.toDouble()
    val max = maxOf(red, green, blue)
    val min = minOf(red, green, blue)
    val lightness = (max + min) / 2.0
    val target = lightness.coerceIn(0.28, 0.45)
    return adjustHslLightness(color, (target - lightness).toFloat())
}

fun adjustHslLightness(color: Color, delta: Float): Color {
    val red = color.red.toDouble()
    val green = color.green.toDouble()
    val blue = color.blue.toDouble()
    val max = maxOf(red, green, blue)
    val min = minOf(red, green, blue)
    val lightness = (max + min) / 2.0
    if (max == min) {
        val gray = (lightness + delta).coerceIn(0.0, 1.0)
        return Color(gray.toFloat(), gray.toFloat(), gray.toFloat(), color.alpha)
    }
    val chroma = max - min
    val saturation = chroma / (1.0 - kotlin.math.abs(2.0 * lightness - 1.0))
    val hue = when (max) {
        red -> ((green - blue) / chroma).let { if (it < 0) it + 6.0 else it }
        green -> (blue - red) / chroma + 2.0
        else -> (red - green) / chroma + 4.0
    } / 6.0
    val adjustedLightness = (lightness + delta).coerceIn(0.0, 1.0)
    val c = (1.0 - kotlin.math.abs(2.0 * adjustedLightness - 1.0)) * saturation
    val x = c * (1.0 - kotlin.math.abs((hue * 6.0) % 2.0 - 1.0))
    val (r1, g1, b1) = when ((hue * 6.0).toInt()) {
        0 -> Triple(c, x, 0.0)
        1 -> Triple(x, c, 0.0)
        2 -> Triple(0.0, c, x)
        3 -> Triple(0.0, x, c)
        4 -> Triple(x, 0.0, c)
        else -> Triple(c, 0.0, x)
    }
    val m = adjustedLightness - c / 2.0
    return Color((r1 + m).toFloat().coerceIn(0f, 1f), (g1 + m).toFloat().coerceIn(0f, 1f), (b1 + m).toFloat().coerceIn(0f, 1f), color.alpha)
}

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
