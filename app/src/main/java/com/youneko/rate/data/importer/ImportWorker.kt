package com.youneko.rate.data.importer

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.youneko.rate.data.AlbumDraft
import com.youneko.rate.data.AlbumRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

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
}

class ImportWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val uriStrings = inputData.getStringArray(KEY_URIS).orEmpty().toList()
        val selected = inputData.getStringArray(KEY_SELECTED).orEmpty().toSet()
        val selections = Json.decodeFromString<List<ImportSelection>>(inputData.getString(KEY_SELECTIONS).orEmpty())
            .associateBy { it.groupKey }
        if (uriStrings.isEmpty() || selections.isEmpty()) return Result.failure()

        val reader = LocalAudioTagReader(applicationContext)
        val readResult = reader.readAll(uriStrings.map(android.net.Uri::parse)) { current, total ->
            setProgressAsync(workDataOf(KEY_CURRENT to current, KEY_TOTAL to total))
        }
        val groups = ImportGrouping.group(readResult.tags)
        val repository = EntryPointAccessors.fromApplication(
            applicationContext,
            ImportWorkerEntryPoint::class.java,
        ).albumRepository()
        var imported = 0
        groups.forEach { rawGroup ->
            val selection = selections[rawGroup.stableKey()] ?: return@forEach
            val tracks = rawGroup.tracks.filter { it.uri in selected && it.uri in selection.selectedUris }
            if (tracks.isEmpty()) return@forEach
            val group = rawGroup.copy(
                album = selection.title.takeIf { rawGroup.album != null },
                artist = selection.artist,
                tracks = tracks,
            )
            if (group.isStandalone) {
                tracks.forEach { track -> repository.saveStandalone(track.title, group.artist, track.listenedDate) }
                imported += tracks.size
            } else {
                val matching = if (selection.mergeIfExisting) repository.findMatchingAlbum(group) else null
                if (matching != null) {
                    imported += repository.appendImportedTracks(matching, tracks)
                } else {
                    val coverUri = saveCover(group.stableKey(), group.embeddedCover, selection.coverUri)
                    repository.saveAlbum(
                        AlbumDraft(
                            title = group.displayTitle,
                            artistName = group.artist,
                            releaseYear = group.year,
                            albumType = "ALBUM",
                            genreTags = group.tracks.mapNotNull { it.genre }.flatMap { it.split(',') }.map(String::trim).distinct(),
                            listenedDate = null,
                            coverUri = coverUri,
                            tracks = group.tracks.map { track ->
                                com.youneko.rate.data.TrackDraft(
                                    title = track.title,
                                    discNumber = track.discNumber ?: 1,
                                    durationMs = track.durationMs,
                                )
                            },
                        ),
                    )
                    imported += tracks.size
                }
            }
        }
        val failures = readResult.failures.joinToString("\n") { "${it.fileName}: ${it.reason}" }
        return Result.success(
            workDataOf(
                KEY_IMPORTED to imported,
                KEY_FAILURES to failures,
            ),
        )
    }

    private fun saveCover(groupKey: String, embedded: ByteArray?, overrideUri: String?): String? {
        val bytes = overrideUri?.let { uri ->
            applicationContext.contentResolver.openInputStream(android.net.Uri.parse(uri))?.use { it.readBytes() }
        } ?: embedded ?: return null
        val directory = File(applicationContext.filesDir, "covers").apply { mkdirs() }
        val file = File(directory, "${ImportDedupe.normalize(groupKey).take(48)}-${bytes.hashCode()}.jpg")
        file.writeBytes(bytes)
        return file.toURI().toString()
    }

    companion object {
        const val KEY_URIS = "uris"
        const val KEY_SELECTED = "selected_uris"
        const val KEY_SELECTIONS = "selections"
        const val KEY_CURRENT = "current"
        const val KEY_TOTAL = "total"
        const val KEY_IMPORTED = "imported"
        const val KEY_FAILURES = "failures"
    }
}


private fun workDataOf(vararg pairs: Pair<String, Any?>): Data = Data.Builder().apply {
    pairs.forEach { (key, value) ->
        when (value) {
            is Int -> putInt(key, value)
            is String -> putString(key, value)
            null -> Unit
        }
    }
}.build()
