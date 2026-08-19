package com.youneko.rate.data.importer

import com.youneko.rate.data.local.entity.TrackEntity
import com.youneko.rate.data.lyrics.Lyrics
import com.youneko.rate.data.musicbrainz.CreditCandidate
import java.text.Normalizer
import java.util.Locale

/** Metadata extracted from one local audio file; no audio decoding is performed here. */
data class AudioTag(
    val uri: String,
    val fileName: String,
    val artist: String?,
    val albumArtist: String?,
    val album: String?,
    val title: String?,
    val trackNumber: Int?,
    val discNumber: Int?,
    val year: Int?,
    val genre: String?,
    val durationMs: Long?,
    val embeddedCoverPath: String? = null,
    val embeddedCredits: List<CreditCandidate> = emptyList(),
    val lyrics: Lyrics? = null,
)

data class ImportedTrack(
    val uri: String,
    val fileName: String,
    val title: String,
    val artist: String,
    val trackNumber: Int?,
    val discNumber: Int?,
    val durationMs: Long?,
    val genre: String?,
    val listenedDate: String? = null,
    val embeddedCredits: List<CreditCandidate> = emptyList(),
    val lyrics: Lyrics? = null,
)

data class ImportGroup(
    val album: String?,
    val artist: String,
    val year: Int?,
    val tracks: List<ImportedTrack>,
    val embeddedCoverPath: String? = null,
) {
    val isStandalone: Boolean get() = album == null
    val displayTitle: String get() = album ?: "Bài lẻ"
}

object ImportGrouping {
    fun group(tags: List<AudioTag>): List<ImportGroup> = tags
        .groupBy { tag ->
            val artist = effectiveArtist(tag)
            val album = tag.album?.trim()?.takeIf { it.isNotEmpty() }
            if (album == null) {
                GroupKey(ImportDedupe.normalize(artist), "__standalone__", null)
            } else {
                GroupKey(ImportDedupe.normalize(artist), ImportDedupe.normalize(album), tag.year)
            }
        }
        .map { (_, group) ->
            val first = group.first()
            val album = first.album?.trim()?.takeIf { it.isNotEmpty() }
            ImportGroup(
                album = album,
                artist = effectiveArtist(first),
                year = first.year,
                tracks = group.map { tag ->
                    ImportedTrack(
                        uri = tag.uri,
                        fileName = tag.fileName,
                        title = tag.title?.trim()?.takeIf { it.isNotEmpty() } ?: tag.fileName,
                        artist = effectiveArtist(tag),
                        trackNumber = tag.trackNumber,
                        discNumber = tag.discNumber,
                        durationMs = tag.durationMs,
                        genre = tag.genre,
                        embeddedCredits = tag.embeddedCredits,
                        lyrics = tag.lyrics,
                    )
                }.sortedWith(compareBy<ImportedTrack> { it.discNumber ?: Int.MAX_VALUE }
                    .thenBy { it.trackNumber ?: Int.MAX_VALUE }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }),
                embeddedCoverPath = group.firstNotNullOfOrNull { it.embeddedCoverPath },
            )
        }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayTitle })

    private fun effectiveArtist(tag: AudioTag): String =
        tag.albumArtist?.trim()?.takeIf { it.isNotEmpty() }
            ?: tag.artist?.trim()?.takeIf { it.isNotEmpty() }
            ?: "Nghệ sĩ chưa rõ"

    private data class GroupKey(val artist: String, val album: String, val year: Int?)
}

object ImportDedupe {
    fun normalize(value: String?): String = Normalizer.normalize(value.orEmpty(), Normalizer.Form.NFD)
        .replace("\\p{M}+".toRegex(), "")
        .lowercase(Locale.ROOT)
        .replace("[^a-z0-9]+".toRegex(), " ")
        .trim()
        .replace("\\s+".toRegex(), " ")

    fun sameAlbum(title: String, artist: String, year: Int?, existingTitle: String, existingArtist: String, existingYear: Int?): Boolean =
        normalize(title) == normalize(existingTitle) &&
            normalize(artist) == normalize(existingArtist) &&
            year == existingYear

    fun newTracks(existingTitles: Collection<String>, incoming: List<ImportedTrack>): List<ImportedTrack> {
        val seen = existingTitles.mapTo(mutableSetOf(), ::normalize)
        return incoming.filter { track ->
            val key = normalize(track.title)
            key.isNotEmpty() && seen.add(key)
        }
    }
}

fun ImportGroup.stableKey(): String = listOf(
    ImportDedupe.normalize(artist),
    ImportDedupe.normalize(album),
    year?.toString().orEmpty(),
).joinToString("|")

object ImportMergePolicy {
    fun preserveUserData(existing: TrackEntity, incoming: ImportedTrack): TrackEntity = existing.copy(
        title = existing.title,
        trackNumber = existing.trackNumber ?: incoming.trackNumber,
        discNumber = existing.discNumber ?: incoming.discNumber,
        durationMs = existing.durationMs ?: incoming.durationMs,
        listenedDate = existing.listenedDate ?: incoming.listenedDate,
        sourceUri = existing.sourceUri ?: incoming.uri,
        fileName = existing.fileName ?: incoming.fileName,
        stars = existing.stars,
        reviewText = existing.reviewText,
        isSkip = existing.isSkip,
        isHighlight = existing.isHighlight,
        updatedAt = existing.updatedAt,
    )
}
