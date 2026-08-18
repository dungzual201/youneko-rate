package com.youneko.rate.data.importer

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import com.youneko.rate.data.musicbrainz.CreditCandidate
import java.io.File
import java.util.Locale
import java.util.UUID

class LocalAudioTagReader(private val context: Context) {
    data class ReadResult(val tags: List<AudioTag>, val failures: List<ImportFailure>)
    data class ImportFailure(val fileName: String, val reason: String)

    fun collectAudioUris(selection: Uri, isTree: Boolean): List<Uri> = if (isTree) {
        val root = DocumentFile.fromTreeUri(context, selection) ?: return emptyList()
        root.walkFiles()
            .filter { it.isFile && isAudioFile(it) }
            .map { it.uri }
            .toList()
    } else {
        listOf(selection)
    }

    fun readAll(uris: List<Uri>, onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> }): ReadResult {
        val tags = mutableListOf<AudioTag>()
        val failures = mutableListOf<ImportFailure>()
        uris.forEachIndexed { index, uri ->
            val displayName = displayNameForUri(uri) ?: "Tệp âm thanh ${index + 1}"
            runCatching { read(uri, displayName) }
                .onSuccess(tags::add)
                .onFailure { failures += ImportFailure(displayName, friendlyReason(it)) }
            onProgress(index + 1, uris.size)
        }
        return ReadResult(tags, failures)
    }

    private fun read(uri: Uri, fileName: String): AudioTag {
        val header = readHeader(uri)
        val extension = extensionFromDisplayName(fileName) ?: extensionFromMagic(header)
        var tagResult: AudioTag? = null
        var tagFailure: Throwable? = null

        if (extension != null) {
            val temp = File(context.cacheDir, "import_${UUID.randomUUID()}.$extension")
            try {
                copyToTemp(uri, temp)
                tagResult = runCatching { readWithJaudiotagger(temp, uri, fileName) }
                    .onFailure { tagFailure = it }
                    .getOrNull()
            } finally {
                temp.delete()
            }
        } else {
            tagFailure = IllegalArgumentException("Không xác định được định dạng file")
        }

        val fallback = readWithMediaMetadataRetriever(uri, fileName)
        tagResult?.let { return merge(it, fallback) }
        if (fallback != null) return fallback
        throw IllegalArgumentException(
            tagFailure?.let(::friendlyReason) ?: "Không đọc được metadata của file",
        )
    }

    private fun copyToTemp(uri: Uri, temp: File) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            temp.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Không thể mở file âm thanh")
    }

    private fun readWithJaudiotagger(temp: File, uri: Uri, fileName: String): AudioTag {
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
            embeddedCoverPath = persistCover(tag?.firstArtwork?.binaryData),
            embeddedCredits = readEmbeddedCredits(tag),
        )
    }

    private fun readEmbeddedCredits(tag: org.jaudiotagger.tag.Tag?): List<CreditCandidate> = buildList {
        val standardFields = listOf(
            "COMPOSER" to "Composer",
            "LYRICIST" to "Lyricist",
            "PRODUCER" to "Producer",
            "ARRANGER" to "Arranger",
            "PERFORMER" to "Performer",
            "MIXER" to "Mixer",
            "ENGINEER" to "Engineer",
        )
        standardFields.forEach { (field, role) ->
            val raw = runCatching { tag?.getFirst(field) }.getOrNull().orEmpty()
            splitTagValues(raw).forEach { name -> addCredit(name, role) }
        }
        val involvedPeople = runCatching { tag?.getFirst("TIPL") }.getOrNull().orEmpty()
        parseId3People(involvedPeople).forEach { (role, name) -> addCredit(name, role) }
        val musicianCredits = runCatching { tag?.getFirst("TMCL") }.getOrNull().orEmpty()
        parseId3People(musicianCredits).forEach { (role, name) -> addCredit(name, role) }
    }.distinctBy { it.personName.trim().lowercase() to it.role.lowercase() }

    private fun MutableList<CreditCandidate>.addCredit(name: String, role: String) {
        val cleaned = name.trim().takeIf { it.isNotEmpty() } ?: return
        add(CreditCandidate(cleaned, null, role, null, "file_tags", null))
    }

    private fun splitTagValues(raw: String): List<String> = raw
        .split('\u0000', ';', '\n')
        .map(String::trim)
        .filter(String::isNotEmpty)

    private fun parseId3People(raw: String): List<Pair<String, String>> {
        if (raw.isBlank()) return emptyList()
        val parts = raw.split('\u0000').map(String::trim).filter(String::isNotEmpty)
        if (parts.size >= 2 && parts.size % 2 == 0) return parts.chunked(2).map { it[0] to it[1] }
        return raw.split(';', '\n').mapNotNull { item ->
            val separator = item.indexOf(':').takeIf { it >= 0 } ?: item.indexOf('=').takeIf { it >= 0 } ?: return@mapNotNull null
            item.substring(0, separator).trim() to item.substring(separator + 1).trim()
        }.filter { it.first.isNotEmpty() && it.second.isNotEmpty() }
    }

    private fun persistCover(bytes: ByteArray?): String? {
        if (bytes == null || bytes.isEmpty()) return null
        val directory = File(context.cacheDir, "import_covers").apply { mkdirs() }
        val file = File(directory, "${UUID.randomUUID()}.img")
        file.outputStream().use { it.write(bytes) }
        return file.absolutePath
    }

    private fun readWithMediaMetadataRetriever(uri: Uri, fileName: String): AudioTag? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            if (listOf(title, artist, album, duration).all { it == null }) null
            else AudioTag(uri.toString(), fileName, artist, null, album, title, null, null, null, null, duration)
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    private fun merge(primary: AudioTag, fallback: AudioTag?): AudioTag = if (fallback == null) primary else primary.copy(
        artist = primary.artist ?: fallback.artist,
        albumArtist = primary.albumArtist ?: fallback.albumArtist,
        album = primary.album ?: fallback.album,
        title = primary.title ?: fallback.title,
        year = primary.year ?: fallback.year,
        durationMs = primary.durationMs ?: fallback.durationMs,
    )

    private fun readHeader(uri: Uri): ByteArray = context.contentResolver.openInputStream(uri)?.use { input ->
        val buffer = ByteArray(64)
        val count = input.read(buffer)
        if (count <= 0) ByteArray(0) else buffer.copyOf(count)
    } ?: ByteArray(0)

    private fun displayNameForUri(uri: Uri): String? =
        DocumentFile.fromSingleUri(context, uri)?.name?.takeIf { it.isNotBlank() }
            ?: context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
            }

    private fun first(tag: org.jaudiotagger.tag.Tag?, vararg keys: FieldKey): String? = keys
        .asSequence()
        .mapNotNull { key -> runCatching { tag?.getFirst(key) }.getOrNull()?.trim()?.takeIf(String::isNotEmpty) }
        .firstOrNull()

    private fun parseNumber(value: String?): Int? = value?.let { it.substringBefore('/').substringBefore('-').trim().toIntOrNull() }

    private fun friendlyReason(error: Throwable): String {
        val message = error.message.orEmpty()
        return when {
            message.contains("No Reader associated", ignoreCase = true) -> "Không nhận diện được định dạng audio"
            message.contains("Permission", ignoreCase = true) -> "Không có quyền đọc file"
            message.contains("open", ignoreCase = true) -> "Không thể mở file"
            message.isBlank() -> "Không đọc được metadata"
            else -> "Không đọc được metadata audio"
        }
    }

    private fun isAudioFile(file: DocumentFile): Boolean =
        file.type?.startsWith("audio/", ignoreCase = true) == true || extensionFromDisplayName(file.name) != null
}

fun extensionFromDisplayName(displayName: String?): String? {
    val name = displayName?.trim().orEmpty()
    val dot = name.lastIndexOf('.')
    if (dot <= 0 || dot == name.lastIndex) return null
    return name.substring(dot + 1).lowercase(Locale.ROOT).takeIf { it in SUPPORTED_EXTENSIONS }
}

fun extensionFromMagic(bytes: ByteArray): String? {
    if (bytes.startsWithAscii("fLaC")) return "flac"
    if (bytes.startsWithAscii("ID3") || bytes.size >= 2 && bytes[0].toInt() and 0xFF == 0xFF && bytes[1].toInt() and 0xE0 == 0xE0) return "mp3"
    if (bytes.startsWithAscii("RIFF") && bytes.containsAsciiAt(8, "WAVE")) return "wav"
    if (bytes.startsWithAscii("OggS")) return if (bytes.containsAscii("OpusHead")) "opus" else "ogg"
    if (bytes.containsAsciiAt(4, "ftyp")) return "m4a"
    if (bytes.startsWithAscii("FORM") && bytes.containsAsciiAt(8, "AIFF")) return "aiff"
    return null
}

private val SUPPORTED_EXTENSIONS = setOf("mp3", "flac", "m4a", "mp4", "ogg", "oga", "opus", "wav", "aif", "aiff", "wma", "dsf")

private fun ByteArray.startsWithAscii(value: String): Boolean = containsAsciiAt(0, value)

private fun ByteArray.containsAsciiAt(offset: Int, value: String): Boolean {
    if (offset < 0 || offset + value.length > size) return false
    return value.indices.all { index -> this[offset + index].toInt() and 0xFF == value[index].code }
}

private fun ByteArray.containsAscii(value: String): Boolean = (0..(size - value.length)).any { containsAsciiAt(it, value) }

private fun DocumentFile.walkFiles(): Sequence<DocumentFile> = sequence {
    listFiles().forEach { child ->
        if (child.isDirectory) yieldAll(child.walkFiles()) else yield(child)
    }
}
