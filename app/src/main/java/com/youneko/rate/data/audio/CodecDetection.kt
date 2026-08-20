package com.youneko.rate.data.audio

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import kotlin.math.roundToLong

/** Codec identity from the encoded input, never from MediaCodec's decoded audio/raw output. */
enum class CodecGroup { LOSSLESS, LOSSY, UNKNOWN }

data class CodecSourceInfo(
    val sourceMime: String?,
    val codecLabel: String,
    val group: CodecGroup,
    val detectionSource: String,
    val container: String?,
    val bitDepth: Int?,
    val fileSizeBytes: Long?,
    val bitrate: Long?,
    val bitrateNote: String?,
    val theoreticalBitrate: Long?,
)

object CodecDetector {
    fun detect(
        context: Context,
        uri: Uri,
        sourceMime: String?,
        sampleRate: Int,
        channels: Int,
        durationMs: Long?,
        sourceFormatBitDepth: Int? = null,
        trackBitrate: Long? = null,
    ): CodecSourceInfo {
        val header = readHeader(context, uri)
        val magic = classifyMagic(header)
        val extension = uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase()?.takeIf { it.isNotBlank() }
        val normalizedMime = sourceMime?.lowercase()?.trim()
        val codec = when {
            magic.codec != null -> magic.codec
            normalizedMime == "audio/raw" && extension in setOf("wav", "wave") -> "WAV (PCM)"
            normalizedMime != null -> mimeLabel(normalizedMime)
            extension != null -> extension.uppercase()
            else -> "Không xác định được định dạng nguồn"
        }
        val group = when {
            magic.codec != null -> magic.group
            normalizedMime != null -> classifyMime(normalizedMime)
            else -> CodecGroup.UNKNOWN
        }
        val detectionSource = when {
            magic.codec != null -> "magic bytes"
            normalizedMime != null -> "extractor"
            extension != null -> "đuôi file"
            else -> "không xác định"
        }
        val size = fileSize(context, uri)
        val duration = durationMs?.takeIf { it > 0L }
        val bitrate = trackBitrate ?: duration?.let { size?.times(8_000L)?.div(it) }
        val bitrateNote = if (trackBitrate == null && bitrate != null) "tính từ dung lượng" else null
        val bitDepth = magic.bitDepth ?: sourceFormatBitDepth
        val theoretical = if (group == CodecGroup.LOSSLESS && bitDepth != null && sampleRate > 0 && channels > 0) {
            sampleRate.toLong() * bitDepth * channels
        } else null
        return CodecSourceInfo(
            sourceMime = sourceMime,
            codecLabel = codec,
            group = group,
            detectionSource = detectionSource,
            container = magic.container ?: extension,
            bitDepth = if (group == CodecGroup.LOSSY) null else bitDepth,
            fileSizeBytes = size,
            bitrate = bitrate,
            bitrateNote = bitrateNote,
            theoreticalBitrate = theoretical,
        )
    }

    fun withTrackBitrate(info: CodecSourceInfo, trackBitrate: Long?): CodecSourceInfo {
        if (trackBitrate == null || trackBitrate <= 0L) return info
        return info.copy(bitrate = trackBitrate, bitrateNote = null)
    }

    fun classifyMime(mime: String?): CodecGroup {
        val value = mime.orEmpty().lowercase()
        return when {
            value in LOSSLESS_MIMES -> CodecGroup.LOSSLESS
            value in LOSSY_MIMES || value.startsWith("audio/amr") || value.startsWith("audio/g711") -> CodecGroup.LOSSY
            else -> CodecGroup.UNKNOWN
        }
    }

    private data class MagicResult(
        val codec: String?,
        val group: CodecGroup,
        val container: String?,
        val bitDepth: Int?,
    )

    private fun classifyMagic(bytes: ByteArray): MagicResult {
        if (bytes.size >= 12 && ascii(bytes, 0, 4) == "fLaC") return MagicResult("FLAC", CodecGroup.LOSSLESS, "flac", flacBitDepth(bytes))
        if (bytes.size >= 12 && ascii(bytes, 0, 4) == "RIFF" && ascii(bytes, 8, 4) == "WAVE") return MagicResult("WAV (PCM)", CodecGroup.LOSSLESS, "wav", wavBitDepth(bytes))
        if (bytes.size >= 12 && ascii(bytes, 0, 4) == "FORM" && (ascii(bytes, 8, 4) == "AIFF" || ascii(bytes, 8, 4) == "AIFC")) return MagicResult("AIFF", CodecGroup.LOSSLESS, "aiff", null)
        if (bytes.size >= 4 && ascii(bytes, 0, 4) == "OggS") {
            return when {
                containsAscii(bytes, "OpusHead") -> MagicResult("Opus", CodecGroup.LOSSY, "ogg", null)
                containsAscii(bytes, "vorbis") -> MagicResult("Vorbis", CodecGroup.LOSSY, "ogg", null)
                containsAscii(bytes, "FLAC") -> MagicResult("Ogg FLAC", CodecGroup.LOSSLESS, "ogg", null)
                else -> MagicResult("Ogg", CodecGroup.UNKNOWN, "ogg", null)
            }
        }
        if (bytes.size >= 12 && ascii(bytes, 4, 4) == "ftyp") {
            return when (mp4SampleEntry(bytes)) {
                "alac" -> MagicResult("ALAC", CodecGroup.LOSSLESS, "m4a", mp4BitDepth(bytes))
                "mp4a" -> MagicResult("AAC", CodecGroup.LOSSY, "m4a", null)
                else -> MagicResult("MP4 audio", CodecGroup.UNKNOWN, "m4a", null)
            }
        }
        if (bytes.size >= 4 && (ascii(bytes, 0, 3) == "ID3" || bytes[0].toInt() and 0xff == 0xff && bytes[1].toInt() and 0xe0 == 0xe0)) {
            return MagicResult("MP3", CodecGroup.LOSSY, "mp3", null)
        }
        if (bytes.size >= 4 && ascii(bytes, 0, 4) == "wvpk") return MagicResult("WavPack", CodecGroup.LOSSLESS, "wv", null)
        if (bytes.size >= 4 && ascii(bytes, 0, 4) == "MAC ") return MagicResult("APE", CodecGroup.LOSSLESS, "ape", null)
        return MagicResult(null, CodecGroup.UNKNOWN, null, null)
    }

    private fun mimeLabel(mime: String): String = when {
        mime.contains("flac") -> "FLAC"
        mime.contains("alac") -> "ALAC"
        mime.contains("mpeg") -> "MP3"
        mime.contains("mp4a") || mime.contains("aac") -> "AAC"
        mime.contains("vorbis") -> "Vorbis"
        mime.contains("opus") -> "Opus"
        mime.contains("wav") -> "WAV (PCM)"
        mime == "audio/raw" -> "Không xác định được định dạng nguồn (audio/raw)"
        mime.contains("aiff") -> "AIFF"
        mime.contains("wavpack") -> "WavPack"
        mime.contains("ape") -> "APE"
        else -> "Không xác định được định dạng nguồn ($mime)"
    }

    private fun readHeader(context: Context, uri: Uri): ByteArray = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream(1024 * 1024)
            val buffer = ByteArray(16 * 1024)
            var total = 0
            while (total < 1024 * 1024) {
                val wanted = input.read(buffer, 0, minOf(buffer.size, 1024 * 1024 - total))
                if (wanted <= 0) break
                output.write(buffer, 0, wanted)
                total += wanted
            }
            output.toByteArray()
        } ?: byteArrayOf()
    }.getOrDefault(byteArrayOf())

    private fun fileSize(context: Context, uri: Uri): Long? = runCatching {
        when (uri.scheme?.lowercase()) {
            "file" -> File(uri.path.orEmpty()).length().takeIf { it > 0L }
            else -> context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length.takeIf { length -> length > 0L } }
        }
    }.getOrNull()

    private fun flacBitDepth(bytes: ByteArray): Int? {
        if (bytes.size < 42) return null
        var offset = 4
        while (offset + 4 <= bytes.size) {
            val last = bytes[offset].toInt() and 0x80 != 0
            val type = bytes[offset].toInt() and 0x7f
            val length = u24(bytes, offset + 1)
            if (type == 0 && length >= 34 && offset + 4 + 34 <= bytes.size) {
                val b10 = bytes[offset + 4 + 10].toInt() and 0xff
                val b11 = bytes[offset + 4 + 11].toInt() and 0xff
                return (((b10 and 0x01) shl 4) or (b11 ushr 4)) + 1
            }
            offset += 4 + length
            if (last) break
        }
        return null
    }

    private fun wavBitDepth(bytes: ByteArray): Int? {
        val fmt = indexOf(bytes, byteArrayOf('f'.code.toByte(), 'm'.code.toByte(), 't'.code.toByte(), ' '.code.toByte()))
        return if (fmt >= 0 && fmt + 18 <= bytes.size) u16(bytes, fmt + 16) else null
    }

    private fun mp4SampleEntry(bytes: ByteArray): String? {
        val stsd = indexOf(bytes, "stsd".encodeToByteArray())
        if (stsd < 0) return null
        val end = (stsd + 64 * 1024).coerceAtMost(bytes.size)
        val sampleEntries = listOf("alac", "mp4a", "ac-3", "ec-3")
        return sampleEntries.firstOrNull { entry ->
            val position = indexOf(bytes.copyOfRange(stsd, end), entry.encodeToByteArray())
            position >= 0
        }
    }

    private fun mp4BitDepth(bytes: ByteArray): Int? = mp4SampleEntry(bytes)?.let { entry ->
        val position = indexOf(bytes, entry.encodeToByteArray())
        if (entry == "alac" && position + 12 < bytes.size) bytes[position + 8].toInt() and 0xff else null
    }

    private fun ascii(bytes: ByteArray, offset: Int, length: Int): String = if (offset + length <= bytes.size) String(bytes, offset, length, Charsets.US_ASCII) else ""
    private fun containsAscii(bytes: ByteArray, value: String): Boolean = indexOf(bytes, value.encodeToByteArray()) >= 0
    private fun indexOf(bytes: ByteArray, needle: ByteArray): Int = bytes.indices.firstOrNull { index -> index + needle.size <= bytes.size && needle.indices.all { bytes[index + it] == needle[it] } } ?: -1
    private fun u16(bytes: ByteArray, offset: Int): Int = (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)
    private fun u24(bytes: ByteArray, offset: Int): Int = ((bytes[offset].toInt() and 0xff) shl 16) or ((bytes[offset + 1].toInt() and 0xff) shl 8) or (bytes[offset + 2].toInt() and 0xff)

    private val LOSSLESS_MIMES = setOf(
        "audio/flac", "audio/x-flac", "audio/alac", "audio/x-wav", "audio/wav", "audio/vnd.wave", "audio/x-aiff", "audio/wavpack", "audio/x-ape", "audio/dsd", "audio/x-tta",
    )
    private val LOSSY_MIMES = setOf(
        "audio/mpeg", "audio/mp4a-latm", "audio/aac", "audio/aac-adts", "audio/vorbis", "audio/opus", "audio/x-ms-wma", "audio/ac3", "audio/eac3",
    )
}
