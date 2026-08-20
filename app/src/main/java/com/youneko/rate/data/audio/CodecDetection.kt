package com.youneko.rate.data.audio

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File

/** Canonical source identity used by both format and verdict tiers. */
enum class CodecGroup { LOSSLESS, LOSSY, UNKNOWN }

enum class DetectionSource { EXTRACTOR, MAGIC_BYTES, EXTENSION }

data class ResolvedCodec(
    val canonical: String,
    val group: CodecGroup,
    val detectedBy: DetectionSource,
    val rawInputMime: String?,
)

data class CodecSourceInfo(
    val resolved: ResolvedCodec,
    val container: String?,
    val bitDepth: Int?,
    val fileSizeBytes: Long?,
    val bitrate: Long?,
    val bitrateNote: String?,
    val theoreticalBitrate: Long?,
    val headerHex: String,
) {
    val sourceMime: String? get() = resolved.rawInputMime
    val codecLabel: String get() = resolved.canonical
    val group: CodecGroup get() = resolved.group
    val detectionSource: DetectionSource get() = resolved.detectedBy
}

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
        val extension = uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase()?.takeIf { it.isNotBlank() }
        val resolved = resolve(header, sourceMime, extension)
        val magic = classifyMagic(header)
        val size = fileSize(context, uri)
        val duration = durationMs?.takeIf { it > 0L }
        val bitrate = trackBitrate ?: duration?.let { size?.times(8_000L)?.div(it) }
        val bitrateNote = if (trackBitrate == null && bitrate != null) "estimated" else null
        val bitDepth = magic.bitDepth ?: sourceFormatBitDepth
        val theoretical = if (resolved.group == CodecGroup.LOSSLESS && bitDepth != null && sampleRate > 0 && channels > 0) {
            sampleRate.toLong() * bitDepth * channels
        } else null
        return CodecSourceInfo(
            resolved = resolved,
            container = containerFor(resolved.canonical, magic.container, extension),
            bitDepth = if (resolved.group == CodecGroup.LOSSY) null else bitDepth,
            fileSizeBytes = size,
            bitrate = bitrate,
            bitrateNote = bitrateNote,
            theoreticalBitrate = theoretical,
            headerHex = header.take(12).toByteArray().toHexString(),
        )
    }

    /** Magic bytes win over extractor MIME, then input MIME wins over extension. */
    fun resolve(header: ByteArray, rawInputMime: String?, extension: String?): ResolvedCodec {
        val magic = classifyMagic(header)
        val mime = canonicalFromMime(rawInputMime)
        val ext = canonicalFromExtension(extension)
        val candidate = when {
            magic.group != CodecGroup.UNKNOWN -> ResolvedCodec(magic.codec, magic.group, DetectionSource.MAGIC_BYTES, rawInputMime)
            mime.group != CodecGroup.UNKNOWN -> ResolvedCodec(mime.canonical, mime.group, DetectionSource.EXTRACTOR, rawInputMime)
            ext.group != CodecGroup.UNKNOWN -> ResolvedCodec(ext.canonical, ext.group, DetectionSource.EXTENSION, rawInputMime)
            else -> ResolvedCodec("UNKNOWN", CodecGroup.UNKNOWN, DetectionSource.EXTENSION, rawInputMime)
        }
        return assertAndRepair(candidate)
    }

    fun groupForCanonical(canonical: String?): CodecGroup = when (canonical) {
        "FLAC", "ALAC", "WAV", "AIFF", "WavPack", "APE", "TTA", "DSD", "Ogg FLAC" -> CodecGroup.LOSSLESS
        "MP3", "AAC", "Vorbis", "Opus", "LOSSY" -> CodecGroup.LOSSY
        else -> CodecGroup.UNKNOWN
    }

    fun withTrackBitrate(info: CodecSourceInfo, trackBitrate: Long?): CodecSourceInfo {
        if (trackBitrate == null || trackBitrate <= 0L) return info
        return info.copy(bitrate = trackBitrate, bitrateNote = null)
    }

    private fun assertAndRepair(codec: ResolvedCodec): ResolvedCodec {
        val expectedLossless = setOf("FLAC", "ALAC", "WAV", "AIFF", "WavPack", "APE", "TTA", "DSD")
        if (codec.canonical in expectedLossless && codec.group != CodecGroup.LOSSLESS) {
            Log.e("VERDICT_BUG", "canonical=${codec.canonical} group=${codec.group}; repaired=LOSSLESS")
            return codec.copy(group = CodecGroup.LOSSLESS)
        }
        if (codec.group == CodecGroup.UNKNOWN && codec.canonical != "UNKNOWN") {
            Log.e("VERDICT_BUG", "canonical=${codec.canonical} group=UNKNOWN")
        }
        return codec
    }

    private data class CanonicalResult(val canonical: String, val group: CodecGroup)

    private data class MagicResult(
        val codec: String,
        val group: CodecGroup,
        val container: String?,
        val bitDepth: Int?,
    )

    private fun canonicalFromMime(mime: String?): CanonicalResult {
        val value = mime.orEmpty().lowercase().trim()
        return when {
            value in LOSSLESS_MIMES -> CanonicalResult(
                when {
                    value.contains("flac") -> "FLAC"
                    value.contains("alac") -> "ALAC"
                    value.contains("aiff") -> "AIFF"
                    value.contains("wavpack") -> "WavPack"
                    value.contains("ape") -> "APE"
                    value == "audio/dsd" -> "DSD"
                    value == "audio/x-tta" -> "TTA"
                    else -> "WAV"
                }, CodecGroup.LOSSLESS,
            )
            value in LOSSY_MIMES || value.startsWith("audio/amr") || value.startsWith("audio/g711") -> CanonicalResult(
                when {
                    value.contains("mpeg") -> "MP3"
                    value.contains("mp4a") || value.contains("aac") -> "AAC"
                    value.contains("vorbis") -> "Vorbis"
                    value.contains("opus") -> "Opus"
                    else -> "LOSSY"
                }, CodecGroup.LOSSY,
            )
            else -> CanonicalResult("UNKNOWN", CodecGroup.UNKNOWN)
        }
    }

    private fun canonicalFromExtension(extension: String?): CanonicalResult {
        return when (extension?.lowercase()) {
            "flac" -> CanonicalResult("FLAC", CodecGroup.LOSSLESS)
            "m4a", "mp4" -> CanonicalResult("M4A", CodecGroup.UNKNOWN)
            "wav", "wave" -> CanonicalResult("WAV", CodecGroup.LOSSLESS)
            "aif", "aiff" -> CanonicalResult("AIFF", CodecGroup.LOSSLESS)
            "mp3" -> CanonicalResult("MP3", CodecGroup.LOSSY)
            "ogg", "oga" -> CanonicalResult("Ogg", CodecGroup.UNKNOWN)
            "wv" -> CanonicalResult("WavPack", CodecGroup.LOSSLESS)
            "ape" -> CanonicalResult("APE", CodecGroup.LOSSLESS)
            else -> CanonicalResult("UNKNOWN", CodecGroup.UNKNOWN)
        }
    }

    private fun classifyMagic(bytes: ByteArray): MagicResult {
        if (bytes.size >= 12 && ascii(bytes, 0, 4) == "fLaC") return MagicResult("FLAC", CodecGroup.LOSSLESS, "flac", flacBitDepth(bytes))
        if (bytes.size >= 12 && ascii(bytes, 0, 4) == "RIFF" && ascii(bytes, 8, 4) == "WAVE") return MagicResult("WAV", CodecGroup.LOSSLESS, "wav", wavBitDepth(bytes))
        if (bytes.size >= 12 && ascii(bytes, 0, 4) == "FORM" && (ascii(bytes, 8, 4) == "AIFF" || ascii(bytes, 8, 4) == "AIFC")) return MagicResult("AIFF", CodecGroup.LOSSLESS, "aiff", null)
        if (bytes.size >= 4 && ascii(bytes, 0, 4) == "OggS") {
            return when {
                containsAscii(bytes, "OpusHead") -> MagicResult("Opus", CodecGroup.LOSSY, "ogg", null)
                containsAscii(bytes, "vorbis") -> MagicResult("Vorbis", CodecGroup.LOSSY, "ogg", null)
                containsAscii(bytes, "FLAC") -> MagicResult("Ogg FLAC", CodecGroup.LOSSLESS, "ogg", null)
                else -> MagicResult("UNKNOWN", CodecGroup.UNKNOWN, "ogg", null)
            }
        }
        if (bytes.size >= 12 && ascii(bytes, 4, 4) == "ftyp") {
            return when (mp4SampleEntry(bytes)) {
                "alac" -> MagicResult("ALAC", CodecGroup.LOSSLESS, "m4a", mp4BitDepth(bytes))
                "mp4a" -> MagicResult("AAC", CodecGroup.LOSSY, "m4a", null)
                else -> MagicResult("UNKNOWN", CodecGroup.UNKNOWN, "m4a", null)
            }
        }
        if (bytes.size >= 4 && (ascii(bytes, 0, 3) == "ID3" || bytes[0].toInt() and 0xff == 0xff && bytes[1].toInt() and 0xe0 == 0xe0)) {
            return MagicResult("MP3", CodecGroup.LOSSY, "mp3", null)
        }
        if (bytes.size >= 4 && ascii(bytes, 0, 4) == "wvpk") return MagicResult("WavPack", CodecGroup.LOSSLESS, "wv", null)
        if (bytes.size >= 4 && ascii(bytes, 0, 4) == "MAC ") return MagicResult("APE", CodecGroup.LOSSLESS, "ape", null)
        return MagicResult("UNKNOWN", CodecGroup.UNKNOWN, null, null)
    }

    private fun containerFor(canonical: String, magicContainer: String?, extension: String?): String? = magicContainer ?: when (canonical) {
        "FLAC" -> "flac"
        "ALAC", "AAC" -> "m4a"
        "WAV" -> "wav"
        "AIFF" -> "aiff"
        else -> extension
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
                val b12 = bytes[offset + 4 + 12].toInt() and 0xff
                val b13 = bytes[offset + 4 + 13].toInt() and 0xff
                return (((b12 and 0x01) shl 4) or (b13 ushr 4)) + 1
            }
            offset += 4 + length
            if (last) break
        }
        return null
    }

    private fun wavBitDepth(bytes: ByteArray): Int? {
        val fmt = indexOf(bytes, "fmt ".encodeToByteArray())
        return if (fmt >= 0 && fmt + 18 <= bytes.size) u16(bytes, fmt + 16) else null
    }

    private fun mp4SampleEntry(bytes: ByteArray): String? {
        val stsd = indexOf(bytes, "stsd".encodeToByteArray())
        if (stsd < 0) return null
        val end = (stsd + 64 * 1024).coerceAtMost(bytes.size)
        val sampleEntries = listOf("alac", "mp4a", "ac-3", "ec-3")
        return sampleEntries.firstOrNull { entry -> indexOf(bytes.copyOfRange(stsd, end), entry.encodeToByteArray()) >= 0 }
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
    private fun ByteArray.toHexString(): String = joinToString(" ") { "%02x".format(it.toInt() and 0xff) }

    private val LOSSLESS_MIMES = setOf(
        "audio/flac", "audio/x-flac", "audio/alac", "audio/x-wav", "audio/wav", "audio/vnd.wave", "audio/x-aiff", "audio/wavpack", "audio/x-ape", "audio/dsd", "audio/x-tta",
    )
    private val LOSSY_MIMES = setOf(
        "audio/mpeg", "audio/mp4a-latm", "audio/aac", "audio/aac-adts", "audio/vorbis", "audio/opus", "audio/x-ms-wma", "audio/ac3", "audio/eac3",
    )
}
