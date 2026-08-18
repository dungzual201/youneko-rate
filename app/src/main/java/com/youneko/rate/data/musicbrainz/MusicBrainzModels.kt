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
    val id: String = "",
    val name: String = "",
    @SerialName("sort-name") val sortName: String? = null,
    val disambiguation: String? = null,
    val aliases: List<MbAlias> = emptyList(),
)

@Serializable
data class MbArtistCredit(
    val artist: MbArtist? = null,
    val name: String? = null,
)

@Serializable
data class MbReleaseGroup(
    val id: String = "",
    val title: String = "",
    @SerialName("type-id") val typeId: String? = null,
    val score: Int? = null,
    @SerialName("primary-type") val primaryType: String? = null,
    @SerialName("primary-type-id") val primaryTypeId: String? = null,
    @SerialName("first-release-date") val firstReleaseDate: String? = null,
    @SerialName("secondary-types") val secondaryTypes: List<String> = emptyList(),
    @SerialName("secondary-type-ids") val secondaryTypeIds: List<String> = emptyList(),
    val disambiguation: String? = null,
    @SerialName("artist-credit-id") val artistCreditId: String? = null,
    @SerialName("artist-credit") val artistCredit: List<MbArtistCredit> = emptyList(),
    val releases: List<MbRelease> = emptyList(),
    val tags: List<MbTag> = emptyList(),
)

@Serializable
data class MbRelease(
    val id: String = "",
    val title: String = "",
    val score: Int? = null,
    val count: Int? = null,
    val status: String? = null,
    @SerialName("status-id") val statusId: String? = null,
    val packaging: String? = null,
    @SerialName("packaging-id") val packagingId: String? = null,
    @SerialName("artist-credit-id") val artistCreditId: String? = null,
    @SerialName("date") val date: String? = null,
    val country: String? = null,
    @SerialName("text-representation") val textRepresentation: MbTextRepresentation? = null,
    @SerialName("release-events") val releaseEvents: List<MbReleaseEvent> = emptyList(),
    val barcode: String? = null,
    val asin: String? = null,
    @SerialName("artist-credit") val artistCredit: List<MbArtistCredit> = emptyList(),
    @SerialName("release-group") val releaseGroup: MbReleaseGroup? = null,
    @SerialName("label-info") val labelInfo: List<MbLabelInfo> = emptyList(),
    @SerialName("track-count") val trackCount: Int? = null,
    val media: List<MbMedium> = emptyList(),
    val tags: List<MbTag> = emptyList(),
    val relations: List<MbRelation> = emptyList(),
)

@Serializable
data class MbLabelInfo(
    val label: MbLabel? = null,
    @SerialName("catalog-number") val catalogNumber: String? = null,
)

@Serializable
data class MbLabel(val id: String? = null, val name: String? = null)

@Serializable
data class MbAlias(
    @SerialName("sort-name") val sortName: String? = null,
    @SerialName("type-id") val typeId: String? = null,
    val name: String = "",
    val locale: String? = null,
    val type: String? = null,
    val primary: Boolean? = null,
    @SerialName("begin-date") val beginDate: String? = null,
    @SerialName("end-date") val endDate: String? = null,
)

@Serializable
data class MbTextRepresentation(
    val language: String? = null,
    val script: String? = null,
)

@Serializable
data class MbArea(
    val id: String = "",
    val name: String = "",
    @SerialName("sort-name") val sortName: String? = null,
    @SerialName("iso-3166-1-codes") val iso31661Codes: List<String> = emptyList(),
)

@Serializable
data class MbReleaseEvent(
    val date: String? = null,
    val area: MbArea? = null,
)

@Serializable
data class MbTag(
    val count: Int = 0,
    val name: String = "",
)

@Serializable
data class MbUrl(val resource: String? = null)

@Serializable
data class MbWork(
    val id: String = "",
    val title: String = "",
    val relations: List<MbRelation> = emptyList(),
)

@Serializable
data class MbRelation(
    val type: String = "",
    @SerialName("type-id") val typeId: String? = null,
    val direction: String? = null,
    @SerialName("target-type") val targetType: String? = null,
    @SerialName("target-credit") val targetCredit: String? = null,
    @SerialName("source-credit") val sourceCredit: String? = null,
    val attributes: List<String> = emptyList(),
    @SerialName("attribute-values") val attributeValues: Map<String, String> = emptyMap(),
    @SerialName("attribute-ids") val attributeIds: Map<String, String> = emptyMap(),
    @SerialName("attribute-credits") val attributeCredits: Map<String, String> = emptyMap(),
    val artist: MbArtist? = null,
    val recording: MbRecording? = null,
    val work: MbWork? = null,
    val label: MbLabel? = null,
    val url: MbUrl? = null,
)

@Serializable
data class MbMedium(
    val id: String? = null,
    val position: Int? = null,
    val format: String? = null,
    val title: String? = null,
    @SerialName("disc-count") val discCount: Int? = null,
    @SerialName("track-count") val trackCount: Int? = null,
    val tracks: List<MbTrack> = emptyList(),
)

@Serializable
data class MbTrack(
    val id: String? = null,
    val number: String? = null,
    val position: Int? = null,
    @SerialName("track-offset") val trackOffset: Int? = null,
    val title: String = "",
    val length: Long? = null,
    val recording: MbRecording? = null,
)

@Serializable
data class MbRecording(
    val id: String = "",
    val title: String = "",
    val length: Long? = null,
    @SerialName("artist-credit") val artistCredit: List<MbArtistCredit> = emptyList(),
    val relations: List<MbRelation> = emptyList(),
)

data class MusicBrainzSearchItem(
    val entityType: String,
    val id: String,
    val title: String,
    val artist: String,
    val year: String?,
    val score: Int?,
    val subtitle: String?,
    val releaseGroupMbid: String? = null,
    val trackCount: Int? = null,
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
    NO_CONNECTION,
    TIMEOUT,
    RATE_LIMITED,
    SERVER_ERROR,
    BAD_REQUEST,
    PARSE_ERROR,
    NO_RESULTS,
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
    score = score,
    subtitle = listOfNotNull(primaryType, disambiguation).joinToString(" · ").ifBlank { null },
    releaseGroupMbid = id,
    trackCount = releases.firstOrNull()?.trackCount,
)

fun MbRelease.toSearchItem() = MusicBrainzSearchItem(
    entityType = "release",
    id = id,
    title = title,
    artist = artistCredit.joinToString(", ") { it.name ?: it.artist?.name.orEmpty() },
    year = date?.take(4),
    score = score,
    subtitle = listOfNotNull(country, releaseGroup?.title).joinToString(" · ").ifBlank { null },
    releaseGroupMbid = releaseGroup?.id,
    trackCount = trackCount,
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
    label = labelInfo.firstOrNull()?.label?.name,
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
