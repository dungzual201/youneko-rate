package com.youneko.rate.data.importer

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.youneko.rate.data.AlbumDraft
import com.youneko.rate.data.AlbumRepository
import com.youneko.rate.data.TrackDraft
import com.youneko.rate.data.local.dao.ImportSessionDao
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream

@Serializable
data class ImportSelection(
    val groupKey: String,
    val title: String,
    val artist: String,
    val selectedUris: List<String>,
    val mergeIfExisting: Boolean,
    val coverUri: String? = null,
)

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ImportWorkerEntryPoint {
    fun albumRepository(): AlbumRepository
    fun importSessionDao(): ImportSessionDao
}

class ImportWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun doWork(): Result {
        val sessionId = inputData.getString(KEY_SESSION_ID) ?: return failure("Thiếu import session")
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            ImportWorkerEntryPoint::class.java,
        )
        val session = entryPoint.importSessionDao().findById(sessionId) ?: return failure("Không tìm thấy import session")
        val failures = mutableListOf<String>()
        return try {
            val sourceUris = json.decodeFromString<List<String>>(session.sourceUrisJson)
            val selectedUris = json.decodeFromString<List<String>>(session.selectedUrisJson).toSet()
            val selections = json.decodeFromString<List<ImportSelection>>(session.selectionsJson)
                .associateBy { it.groupKey }
            if (sourceUris.isEmpty() || selectedUris.isEmpty() || selections.isEmpty()) {
                entryPoint.importSessionDao().deleteById(session.id)
                return failure("Import session không có file được chọn")
            }

            val reader = LocalAudioTagReader(applicationContext)
            val audioUris = sourceUris
                .flatMap { reader.collectAudioUris(Uri.parse(it), session.sourceIsTree) }
                .distinctBy(Uri::toString)
                .filter { it.toString() in selectedUris }
            val readResult = reader.readAll(audioUris) { current, total ->
                setProgressAsync(workDataOf(KEY_CURRENT to current, KEY_TOTAL to total))
            }
            readResult.failures.forEach { failures += "${it.fileName}: ${it.reason}" }

            val repository = entryPoint.albumRepository()
            var imported = 0
            val groups = ImportGrouping.group(readResult.tags)
            groups.forEach { rawGroup ->
                val selection = selections[rawGroup.stableKey()]
                if (selection == null) {
                    rawGroup.tracks.forEach { failures += "${it.fileName}: Không tìm thấy lựa chọn import" }
                    return@forEach
                }
                val tracks = rawGroup.tracks.filter { it.uri in selectedUris && it.uri in selection.selectedUris }
                if (tracks.isEmpty()) return@forEach
                val group = rawGroup.copy(
                    album = selection.title.takeIf { rawGroup.album != null },
                    artist = selection.artist,
                    tracks = tracks,
                )
                if (group.isStandalone) {
                    tracks.forEach { track ->
                        runCatching {
                            repository.saveStandalone(track.title, group.artist, track.listenedDate)
                        }.onSuccess { imported++ }
                            .onFailure { failures += "${track.fileName}: Không thể lưu bài" }
                    }
                } else {
                    runCatching {
                        val matching = if (selection.mergeIfExisting) repository.findMatchingAlbum(group) else null
                        if (matching != null) {
                            repository.appendImportedTracks(matching, tracks)
                        } else {
                            val coverUri = saveCover(
                                groupKey = group.stableKey(),
                                embeddedPath = group.embeddedCoverPath,
                                overrideUri = selection.coverUri,
                            )
                            repository.saveAlbumBatched(
                                AlbumDraft(
                                    title = group.displayTitle,
                                    artistName = group.artist,
                                    releaseYear = group.year,
                                    albumType = "ALBUM",
                                    genreTags = group.tracks.mapNotNull { it.genre }
                                        .flatMap { it.split(',') }
                                        .map(String::trim)
                                        .filter(String::isNotEmpty)
                                        .distinct(),
                                    listenedDate = null,
                                    coverUri = coverUri,
                                    tracks = group.tracks.map { track ->
                                        TrackDraft(
                                            title = track.title,
                                            discNumber = track.discNumber ?: 1,
                                            durationMs = track.durationMs,
                                        )
                                    },
                                ),
                            )
                        }
                    }.onSuccess { result ->
                        imported += if (result is Int) result else tracks.size
                    }.onFailure {
                        tracks.forEach { failures += "${it.fileName}: Không thể lưu album" }
                    }
                }
            }
            entryPoint.importSessionDao().deleteById(session.id)
            Result.success(
                workDataOf(
                    KEY_IMPORTED to imported,
                    KEY_TOTAL_SELECTED to selectedUris.size,
                    KEY_FAILURES to failures.joinToString("\n"),
                ),
            )
        } catch (error: Throwable) {
            Result.failure(
                workDataOf(
                    KEY_FAILURES to (failures + "Import thất bại: ${error.message.orEmpty()}").joinToString("\n"),
                ),
            )
        }
    }

    private fun failure(message: String): Result = Result.failure(workDataOf(KEY_FAILURES to message))

    private fun saveCover(groupKey: String, embeddedPath: String?, overrideUri: String?): String? {
        val directory = File(applicationContext.filesDir, "covers").apply { mkdirs() }
        val sourceName = overrideUri?.let { Uri.parse(it).lastPathSegment } ?: embeddedPath?.let { File(it).name }
        val extension = sourceName?.substringAfterLast('.', "img")?.takeIf { it.isNotBlank() } ?: "img"
        val file = File(directory, "${ImportDedupe.normalize(groupKey).take(48)}-${System.nanoTime()}.$extension")
        val copied = runCatching {
            val input = overrideUri?.let { applicationContext.contentResolver.openInputStream(Uri.parse(it)) }
                ?: embeddedPath?.let { File(it).inputStream() }
                ?: return null
            input.use { source -> file.outputStream().use { target -> source.copyTo(target) } }
            file.toURI().toString()
        }.getOrNull()
        if (copied == null) file.delete()
        return copied
    }

    companion object {
        const val KEY_SESSION_ID = "session_id"
        const val KEY_CURRENT = "current"
        const val KEY_TOTAL = "total"
        const val KEY_TOTAL_SELECTED = "total_selected"
        const val KEY_IMPORTED = "imported"
        const val KEY_FAILURES = "failures"
    }
}
