package com.youneko.rate.data.audio

import android.content.Context
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.provider.OpenableColumns
import java.io.FileNotFoundException

/** Metadata validated from a content URI before any audio analysis starts. */
data class AudioSourceInfo(
    val uri: String,
    val displayName: String,
    val sizeBytes: Long,
    val declaredMime: String?,
    val trackMime: String,
    val durationMs: Long,
    val sampleRate: Int,
    val channels: Int,
)

object AudioSourceInspector {
    const val MAX_ANALYZE_BYTES = 512L * 1024L * 1024L

    fun inspect(context: Context, uri: Uri): AudioSourceInfo {
        val resolver = context.contentResolver
        val displayName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) cursor.getString(index) else null
        }?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment.orEmpty().substringAfterLast('/').ifBlank { "audio" }
        val declaredMime = resolver.getType(uri)
        val pfd = resolver.openFileDescriptor(uri, "r") ?: throw FileNotFoundException(uri.toString())
        pfd.use { descriptor ->
            val size = descriptor.statSize.takeIf { it > 0L }
                ?: resolver.openAssetFileDescriptor(uri, "r")?.use { it.length }?.takeIf { it > 0L }
                ?: 0L
            if (size > MAX_ANALYZE_BYTES) throw IllegalArgumentException("file exceeds safe analysis size")
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(descriptor.fileDescriptor)
                val audioIndex = (0 until extractor.trackCount).firstOrNull { index ->
                    extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME).orEmpty().startsWith("audio/")
                } ?: throw IllegalArgumentException("selected file has no audio track")
                val format = extractor.getTrackFormat(audioIndex)
                val trackMime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (trackMime.isBlank()) throw IllegalArgumentException("audio track MIME is empty")
                if (MediaCodecList(MediaCodecList.REGULAR_CODECS).findDecoderForFormat(format) == null) {
                    throw AnalyzeInputException.NoDecoder(trackMime)
                }
                val durationMs = runCatching { format.getLong(MediaFormat.KEY_DURATION) / 1_000L }.getOrDefault(0L)
                val sampleRate = runCatching { format.getInteger(MediaFormat.KEY_SAMPLE_RATE) }.getOrDefault(0)
                val channels = runCatching { format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) }.getOrDefault(0)
                if (durationMs <= 0L || sampleRate <= 0 || channels <= 0) throw IllegalArgumentException("audio format metadata is incomplete")
                android.util.Log.d("ANALYZE", "file size=$size duration=$durationMs sampleRate=$sampleRate channels=$channels")
                return AudioSourceInfo(uri.toString(), displayName, size, declaredMime, trackMime, durationMs, sampleRate, channels)
            } finally {
                extractor.release()
            }
        }
    }
}
