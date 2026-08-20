package com.youneko.rate.data.audio

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.set
import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SpectrogramCacheMetadata(
    val trackKey: String,
    val stableKey: String,
    val spectrogram: SpectrogramMetadata,
    val createdAt: Long,
)

data class CachedSpectrogram(
    val metadata: SpectrogramCacheMetadata,
    val matrix: ByteArray,
    val webp: File,
    val bin: File,
    val metadataFile: File,
)

class SpectrogramCache(private val context: Context) {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    private val directory: File
        get() = File(context.filesDir, "spectrograms")

    fun write(trackKey: String, result: SpectrogramResult): CachedSpectrogram {
        directory.mkdirs()
        val fileKey = fileKey(trackKey)
        val webp = File(directory, "$fileKey.webp")
        val bin = File(directory, "$fileKey.bin")
        val metadataFile = File(directory, "$fileKey.json")
        bin.outputStream().buffered().use { it.write(result.dbMatrix) }
        val metadata = SpectrogramCacheMetadata(
            trackKey = trackKey,
            stableKey = result.metadata.stableKey.orEmpty(),
            spectrogram = result.metadata,
            createdAt = System.currentTimeMillis(),
        )
        metadataFile.writeText(json.encodeToString(SpectrogramCacheMetadata.serializer(), metadata))
        renderWebp(result, webp)
        return CachedSpectrogram(metadata, result.dbMatrix, webp, bin, metadataFile)
    }

    fun read(trackKey: String, stableKey: String): CachedSpectrogram? {
        val fileKey = fileKey(trackKey)
        val webp = File(directory, "$fileKey.webp")
        val bin = File(directory, "$fileKey.bin")
        val metadataFile = File(directory, "$fileKey.json")
        if (!webp.isFile || !bin.isFile || !metadataFile.isFile) return null
        val metadata = runCatching {
            json.decodeFromString(SpectrogramCacheMetadata.serializer(), metadataFile.readText())
        }.getOrNull() ?: return null
        if (metadata.stableKey != stableKey || metadata.spectrogram.stableKey != stableKey) return null
        val expected = metadata.spectrogram.columns.coerceAtLeast(1) * metadata.spectrogram.rows.coerceAtLeast(1)
        val matrix = bin.readBytes()
        if (matrix.size != expected) return null
        return CachedSpectrogram(metadata, matrix, webp, bin, metadataFile)
    }

    fun delete(trackKey: String) {
        val fileKey = fileKey(trackKey)
        listOf("webp", "bin", "json").forEach { extension ->
            File(directory, "$fileKey.$extension").delete()
        }
    }

    fun clear(): Int {
        val files = directory.listFiles().orEmpty()
        var deleted = 0
        files.forEach { if (it.delete()) deleted++ }
        return deleted
    }

    fun sizeBytes(): Long = directory.listFiles().orEmpty().sumOf { it.length() }

    fun directoryPath(): String = directory.absolutePath

    private fun renderWebp(result: SpectrogramResult, target: File) {
        val width = result.metadata.columns.coerceAtLeast(1).coerceAtMost(2_000)
        val height = result.metadata.rows.coerceAtLeast(1).coerceAtMost(1_024)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        for (column in 0 until width) {
            for (row in 0 until height) {
                val sourceRow = (row.toDouble() / height * result.metadata.rows).toInt()
                    .coerceIn(0, result.metadata.rows - 1)
                val matrixIndex = column * result.metadata.rows + sourceRow
                val value = result.dbMatrix.getOrElse(matrixIndex) { 0 }
                pixels[row * width + column] = SpectrogramLut.color(SpectrogramMath.dequantizeDb(value))
            }
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        target.outputStream().buffered().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.WEBP, 92, output)) { "Không thể ghi WebP spectrogram" }
        }
        bitmap.recycle()
    }

    private fun fileKey(trackKey: String): String = sha256(trackKey).take(32)

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }
}

object SpectrogramLut {
    private val stops = intArrayOf(
        0xFF000000.toInt(),
        0xFF14104A.toInt(),
        0xFF46107D.toInt(),
        0xFF8C1246.toInt(),
        0xFFD62B1F.toInt(),
        0xFFF26B12.toInt(),
        0xFFF7D01F.toInt(),
        0xFFFFFFFF.toInt(),
    )

    fun color(db: Float): Int {
        val normalized = ((db - SPECTROGRAM_DB_FLOOR) / (SPECTROGRAM_DB_CEILING - SPECTROGRAM_DB_FLOOR)).coerceIn(0f, 1f)
        val scaled = normalized * (stops.lastIndex)
        val index = scaled.toInt().coerceIn(0, stops.lastIndex - 1)
        val fraction = scaled - index
        val a = stops[index]
        val b = stops[index + 1]
        fun channel(shift: Int): Int {
            val av = (a shr shift) and 0xff
            val bv = (b shr shift) and 0xff
            return (av + ((bv - av) * fraction)).toInt().coerceIn(0, 255)
        }
        return (channel(24) shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
}
