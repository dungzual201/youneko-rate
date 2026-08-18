package com.youneko.rate.data.importer

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.util.Locale

class LocalAudioTagReader(private val context: Context) {
    data class ReadResult(val tags: List<AudioTag>, val failures: List<ImportFailure>)
    data class ImportFailure(val uri: String, val fileName: String, val reason: String)

    fun collectAudioUris(selection: Uri, isTree: Boolean): List<Uri> = if (isTree) {
        val root = DocumentFile.fromTreeUri(context, selection) ?: return emptyList()
        root.walkFiles()
            .filter { it.isFile && isSupported(it.name.orEmpty()) }
            .map { it.uri }
            .toList()
    } else {
        listOf(selection)
    }

    fun readAll(uris: List<Uri>, onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> }): ReadResult {
        val tags = mutableListOf<AudioTag>()
        val failures = mutableListOf<ImportFailure>()
        uris.forEachIndexed { index, uri ->
            val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: uri.toString()
            runCatching { read(uri, fileName) }
                .onSuccess(tags::add)
                .onFailure { failures += ImportFailure(uri.toString(), fileName, it.message ?: "Không đọc được tag") }
            onProgress(index + 1, uris.size)
        }
        return ReadResult(tags, failures)
    }

    private fun read(uri: Uri, fileName: String): AudioTag {
        val temp = File.createTempFile("youneko-import-", ".audio", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Không thể mở file")
            val audioFile = AudioFileIO.read(temp)
            val tag = audioFile.tag
            val yearText = first(tag, FieldKey.YEAR) ?: runCatching { tag?.getFirst("DATE")?.trim() }.getOrNull()
            return AudioTag(
                uri = uri.toString(),
                fileName = fileName,
                artist = first(tag, FieldKey.ARTIST),
                albumArtist = first(tag, FieldKey.ALBUM_ARTIST),
                album = first(tag, FieldKey.ALBUM),
                title = first(tag, FieldKey.TITLE),
                trackNumber = parseNumber(first(tag, FieldKey.TRACK)),
                discNumber = parseNumber(first(tag, FieldKey.DISC_NO)),
                year = yearText?.take(4)?.toIntOrNull(),
                genre = first(tag, FieldKey.GENRE),
                durationMs = audioFile.audioHeader?.trackLength?.times(1000L),
                embeddedCover = tag?.firstArtwork?.binaryData,
            )
        } finally {
            temp.delete()
        }
    }

    private fun first(tag: org.jaudiotagger.tag.Tag?, vararg keys: FieldKey): String? = keys
        .asSequence()
        .mapNotNull { key -> runCatching { tag?.getFirst(key) }.getOrNull()?.trim()?.takeIf(String::isNotEmpty) }
        .firstOrNull()

    private fun parseNumber(value: String?): Int? = value
        ?.substringBefore('/')
        ?.substringBefore('-')
        ?.trim()
        ?.toIntOrNull()

    private fun isSupported(name: String): Boolean = name.substringAfterLast('.', "").lowercase(Locale.ROOT) in
        setOf("mp3", "flac", "m4a", "mp4", "ogg", "oga", "opus", "wav", "aif", "aiff", "wma", "dsf")
}

private fun DocumentFile.walkFiles(): Sequence<DocumentFile> = sequence {
    listFiles().forEach { child ->
        if (child.isDirectory) yieldAll(child.walkFiles()) else yield(child)
    }
}
