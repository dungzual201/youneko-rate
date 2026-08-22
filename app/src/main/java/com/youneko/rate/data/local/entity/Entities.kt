package com.youneko.rate.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "artists")
data class ArtistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sortName: String? = null,
    val imageUri: String? = null,
    val mbid: String? = null,
    val note: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "albums",
    foreignKeys = [
        ForeignKey(
            entity = ArtistEntity::class,
            parentColumns = ["id"],
            childColumns = ["artistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["artistId"]), Index(value = ["mbid"], unique = false)],
)
data class AlbumEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artistId: String,
    val releaseYear: Int? = null,
    val coverUri: String? = null,
    val coverThumbUri: String? = null,
    val genreTags: List<String> = emptyList(),
    val albumType: String = "ALBUM",
    val label: String? = null,
    val catalogNumber: String? = null,
    val barcode: String? = null,
    val country: String? = null,
    val listenedDate: String? = null,
    val manualScoreOverride: Double? = null,
    val reviewText: String? = null,
    val mbid: String? = null,
    val releaseGroupMbid: String? = null,
    val discogsReleaseId: String? = null,
    val deezerId: String? = null,
    val sourceProvider: String? = null,
    val coverSource: String? = null,
    val coverWidth: Int? = null,
    val coverHeight: Int? = null,
    val coverUpdatedAt: Long? = null,
    val metadataFetchedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "album_palette")
data class AlbumPaletteEntity(
    @PrimaryKey val albumId: String,
    val dominantArgb: Int,
    val vibrantArgb: Int? = null,
    val darkVibrantArgb: Int? = null,
    val mutedArgb: Int? = null,
    val darkMutedArgb: Int? = null,
    val lightVibrantArgb: Int? = null,
    val onDominantArgb: Int,
    val coverUpdatedAt: Long? = null,
    val generatedAt: Long,
)

@Entity(
    tableName = "tracks",
    foreignKeys = [
        ForeignKey(
            entity = AlbumEntity::class,
            parentColumns = ["id"],
            childColumns = ["albumId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["albumId"]),
        Index(value = ["recordingMbid"], unique = false),
        Index(value = ["mediaStoreId"]),
        Index(value = ["stableKey"]),
    ],
)
data class TrackEntity(
    @PrimaryKey val id: String,
    val albumId: String? = null,
    val title: String,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val durationMs: Long? = null,
    val isStandalone: Boolean = false,
    val stars: Double? = null,
    val reviewText: String? = null,
    val isSkip: Boolean = false,
    val isHighlight: Boolean = false,
    val listenedDate: String? = null,
    val recordingMbid: String? = null,
    val workMbid: String? = null,
    val isrc: String? = null,
    val sourceUri: String? = null,
    val fileName: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val mediaStoreId: Long? = null,
    val stableKey: String? = null,
    val fileSizeBytes: Long? = null,
    val fileHash64k: String? = null,
    @ColumnInfo(defaultValue = "0")
    val isMissing: Boolean = false,
    val missingSince: Long? = null,
    val mediaStoreModifiedSeconds: Long? = null,
)

@Entity(
    tableName = "credits",
    foreignKeys = [
        ForeignKey(
            entity = AlbumEntity::class,
            parentColumns = ["id"],
            childColumns = ["albumId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["albumId"]), Index(value = ["trackId"]), Index(value = ["personMbid"])],
)
data class CreditEntity(
    @PrimaryKey val id: String,
    val albumId: String? = null,
    val trackId: String? = null,
    val personName: String,
    val personMbid: String? = null,
    val role: String,
    val instrumentOrAttribute: String? = null,
    val sourceProvider: String,
    val sourceUrl: String? = null,
    val sortOrder: Int = 0,
    val beginDate: String? = null,
    val endDate: String? = null,
)

@Entity(
    tableName = "audio_analysis",
    foreignKeys = [
        ForeignKey(
            entity = AlbumEntity::class,
            parentColumns = ["id"],
            childColumns = ["albumId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["trackId"]), Index(value = ["albumId"]), Index(value = ["fileHash"])],
)
data class AudioAnalysisEntity(
    @PrimaryKey val id: String,
    val trackId: String? = null,
    val albumId: String? = null,
    val fileName: String,
    val fileUriOrPath: String,
    val fileHash: String,
    val container: String? = null,
    val codec: String? = null,
    val sampleRate: Int? = null,
    val bitDepth: Int? = null,
    val bitrate: Long? = null,
    val isVbr: Boolean? = null,
    val channels: Int? = null,
    val durationMs: Long? = null,
    val encoderTag: String? = null,
    val cutoffHz: Double? = null,
    val rolloffSlope: Double? = null,
    val dynamicRangeDb: Double? = null,
    val truePeakDbtp: Double? = null,
    val clippingPercent: Double? = null,
    val verdict: String = "KHÔNG XÁC ĐỊNH",
    val confidence: Int = 0,
    val reasonsJson: String = "[]",
    val spectrumJson: String = "[]",
    val spectrogramPngPath: String? = null,
    val engineVersion: String = "phase8-v1",
    val analyzedAt: Long,
    val noiseFloorDb: Double? = null,
    val cliffDb: Double? = null,
    val quietAboveFraction: Double? = null,
    val analyzedFrames: Int = 0,
    val sourceMime: String? = null,
    val codecDetectionSource: String? = null,
    val bitrateNote: String? = null,
    val theoreticalBitrate: Long? = null,
    val energyAboveCutoffRatio: Double? = null,
    val cutoffRetries: Int = 0,
    val formatVerdict: String? = null,
    val transcodeVerdict: String? = null,
    val rawHeaderHex: String? = null,
)

@Entity(
    tableName = "external_links",
    indices = [Index(value = ["trackId", "sourceId"], unique = true), Index(value = ["albumId", "sourceId"], unique = true)],
)
data class ExternalLinkEntity(
    @PrimaryKey val id: String,
    val albumId: String? = null,
    val trackId: String? = null,
    val sourceId: String,
    val externalId: String,
    val sourceUrl: String,
    val createdAt: Long,
)

@Entity(
    tableName = "review_revisions",
    indices = [Index(value = ["albumId", "trackId", "createdAt"])],
)
data class ReviewRevisionEntity(
    @PrimaryKey val id: String,
    val albumId: String,
    val trackId: String? = null,
    val body: String,
    val createdAt: Long,
)

@Entity(
    tableName = "album_tags",
    indices = [Index(value = ["albumId", "name"], unique = true)],
)
data class AlbumTagEntity(
    @PrimaryKey val id: String,
    val albumId: String,
    val name: String,
    val createdAt: Long,
)

@Entity(
    tableName = "listening_logs",
    indices = [Index(value = ["albumId", "listenedAt"]), Index(value = ["trackId", "listenedAt"])],
)
data class ListeningLogEntity(
    @PrimaryKey val id: String,
    val albumId: String,
    val trackId: String? = null,
    val listenedAt: String,
    val note: String? = null,
)

@Entity(tableName = "remote_metadata_cache")
data class RemoteMetadataCacheEntity(
    @PrimaryKey val key: String,
    val provider: String,
    val jsonBody: String,
    val etag: String? = null,
    val fetchedAt: Long,
    val expiresAt: Long,
)

@Entity(tableName = "import_sessions")
data class ImportSessionEntity(
    @PrimaryKey val id: String,
    val sourceUrisJson: String,
    val sourceIsTree: Boolean,
    val selectedUrisJson: String,
    val selectionsJson: String,
    val createdAt: Long,
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val id: String,
    val query: String,
    val searchedAt: Long,
)

@Fts4
@Entity(tableName = "library_search_fts")
data class LibrarySearchFtsEntity(
    val entityId: String,
    val entityType: String,
    val searchableText: String,
)

@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String? = null,
    val createdAt: Long,
)

@Entity(
    tableName = "collection_albums",
    primaryKeys = ["collectionId", "albumId"],
    foreignKeys = [
        ForeignKey(entity = CollectionEntity::class, parentColumns = ["id"], childColumns = ["collectionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = AlbumEntity::class, parentColumns = ["id"], childColumns = ["albumId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["albumId"]), Index(value = ["collectionId", "sortOrder"])],
)
data class CollectionAlbumEntity(
    val collectionId: String,
    val albumId: String,
    val sortOrder: Int = 0,
)

@Entity(tableName = "scan_roots")
data class ScanRootEntity(
    @PrimaryKey val uri: String,
    val displayName: String? = null,
    val addedAt: Long,
    val lastScannedAt: Long? = null,
)
