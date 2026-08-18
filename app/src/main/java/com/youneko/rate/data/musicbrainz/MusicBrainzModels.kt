package com.youneko.rate.data.musicbrainz

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MbSearchResponse(
    val created: String? = null,
    val count: Int = 0,
    val offset: Int = 0,
    @SerialName("release-groups") val releaseGroups: List<MbReleaseGroup> = emptyList(),
    val releases: List<MbRelease> = emptyList(),
    val recordings: List<MbRecording> = emptyList(),
    val artists: List<MbArtist> = emptyList(),
)

@Serializable
data class MbArtist(
    val id: String,
    val name: String = "",
    @SerialName("sort-name") val sortName: String? = null,
    val disambiguation: String? = null,
)

@Serializable
data class MbArtistCredit(
    val artist: MbArtist? = null,
    val name: String? = null,
)

@Serializable
data class MbReleaseGroup(
    val id: String,
    val title: String = "",
    @SerialName("primary-type") val primaryType: String? = null,
    @SerialName("first-release-date") val firstReleaseDate: String? = null,
    val disambiguation: String? = null,
    @SerialName("artist-credit") val artistCredit: List<MbArtistCredit> = emptyList(),
    val releases: List<MbRelease> = emptyList(),
)

@Serializable
data class MbRelease(
    val id: String,
    val title: String = "",
    @SerialName("status-id") val statusId: String? = null,
    @SerialName("date") val date: String? = null,
    val country: String? = null,
    @SerialName("artist-credit") val artistCredit: List<MbArtistCredit> = emptyList(),
    val media: List<MbMedium> = emptyList(),
    val labels: List<MbLabelInfo> = emptyList(),
    @SerialName("release-group") val releaseGroup: MbReleaseGroup? = null,
)

@Serializable
data class MbLabelInfo(
    val label: MbLabel? = null,
    val catalogNumber: String? = null,
)

@Serializable
data class MbLabel(val name: String? = null)

@Serializable
data class MbMedium(
    val position: Int? = null,
    val format: String? = null,
    val title: String? = null,
    val tracks: List<MbTrack> = emptyList(),
)

@Serializable
data class MbTrack(
    val id: String? = null,
    val number: String? = null,
    val position: Int? = null,
    val title: String = "",
    val length: Long? = null,
    val recording: MbRecording? = null,
)

@Serializable
data class MbRecording(
    val id: String,
    val title: String = "",
    val length: Long? = null,
    @SerialName("artist-credit") val artistCredit: List<MbArtistCredit> = emptyList(),
)

data class MusicBrainzSearchItem(
    val entityType: String,
    val id: String,
    val title: String,
    val artist: String,
    val year: String?,
    val score: Int?,
    val subtitle: String?,
)

data class MusicBrainzPreview(
    val releaseId: String,
    val title: String,
    val artist: String,
    val artistMbid: String? = null,
    val releaseGroupId: String? = null,
    val year: String?,
    val country: String?,
    val label: String?,
    val tracks: List<MusicBrainzPreviewTrack>,
    val releaseOptions: List<MusicBrainzReleaseOption> = emptyList(),
)

data class MusicBrainzReleaseOption(
    val id: String,
    val title: String,
    val year: String?,
    val country: String?,
)

@Serializable
data class CoverArtResponse(
    val images: List<CoverArtImage> = emptyList(),
)

@Serializable
data class CoverArtImage(
    val front: Boolean = false,
    val thumbnails: Map<String, String> = emptyMap(),
)

data class MusicBrainzPreviewTrack(
    val discNumber: Int,
    val trackNumber: Int,
    val title: String,
    val durationMs: Long?,
    val recordingMbid: String? = null,
)

sealed interface Resource<out T> {
    data object Loading : Resource<Nothing>
    data class Success<T>(val value: T) : Resource<T>
    data class Error(val kind: NetworkError, val message: String? = null) : Resource<Nothing>
}

enum class NetworkError {
    OFFLINE,
    NO_NETWORK,
    TIMEOUT,
    RATE_LIMITED,
    NO_RESULTS,
    HTTP,
    PARSE,
    UNKNOWN,
}

fun MbSearchResponse.toSearchItems(entity: String = "release-group"): List<MusicBrainzSearchItem> = when (entity) {
    "artist" -> artists.map { MusicBrainzSearchItem("artist", it.id, it.name, "", null, null, it.disambiguation) }
    "release" -> releases.map { it.toSearchItem() }
    "recording" -> recordings.map { it.toSearchItem() }
    else -> releaseGroups.map { it.toSearchItem() }
}

fun MbReleaseGroup.toSearchItem() = MusicBrainzSearchItem(
    entityType = "release-group",
    id = id,
    title = title,
    artist = artistCredit.joinToString(", ") { it.name ?: it.artist?.name.orEmpty() },
    year = firstReleaseDate?.take(4),
    score = null,
    subtitle = listOfNotNull(primaryType, disambiguation).joinToString(" · ").ifBlank { null },
)

fun MbRelease.toSearchItem() = MusicBrainzSearchItem(
    entityType = "release",
    id = id,
    title = title,
    artist = artistCredit.joinToString(", ") { it.name ?: it.artist?.name.orEmpty() },
    year = date?.take(4),
    score = null,
    subtitle = listOfNotNull(country, releaseGroup?.title).joinToString(" · ").ifBlank { null },
)

fun MbRecording.toSearchItem() = MusicBrainzSearchItem(
    entityType = "recording",
    id = id,
    title = title,
    artist = artistCredit.joinToString(", ") { it.name ?: it.artist?.name.orEmpty() },
    year = null,
    score = null,
    subtitle = length?.let { "${it / 1000}s" },
)

fun MbRelease.toPreview(
    releaseGroupId: String? = releaseGroup?.id,
    releaseOptions: List<MusicBrainzReleaseOption> = emptyList(),
): MusicBrainzPreview = MusicBrainzPreview(
    releaseId = id,
    title = title,
    artist = artistCredit.joinToString(", ") { it.name ?: it.artist?.name.orEmpty() },
    artistMbid = artistCredit.firstOrNull()?.artist?.id,
    releaseGroupId = releaseGroupId,
    year = date?.take(4),
    country = country,
    label = labels.firstOrNull()?.label?.name,
    tracks = media.flatMapIndexed { discIndex, medium ->
        medium.tracks.mapIndexed { trackIndex, track ->
            MusicBrainzPreviewTrack(
                discNumber = medium.position ?: discIndex + 1,
                trackNumber = track.position ?: trackIndex + 1,
                title = track.title.ifBlank { track.recording?.title.orEmpty() },
                durationMs = track.length ?: track.recording?.length,
                recordingMbid = track.recording?.id,
            )
        }
    },
)
