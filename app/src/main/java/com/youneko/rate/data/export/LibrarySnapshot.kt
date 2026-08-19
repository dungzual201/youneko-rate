package com.youneko.rate.data.export

import com.youneko.rate.data.local.YounekoDatabase
import com.youneko.rate.data.local.entity.AlbumEntity
import com.youneko.rate.data.local.entity.AlbumTagEntity
import com.youneko.rate.data.local.entity.ArtistEntity
import com.youneko.rate.data.local.entity.AudioAnalysisEntity
import com.youneko.rate.data.local.entity.CreditEntity
import com.youneko.rate.data.local.entity.ExternalLinkEntity
import com.youneko.rate.data.local.entity.ListeningLogEntity
import com.youneko.rate.data.local.entity.ReviewRevisionEntity
import com.youneko.rate.data.local.entity.TrackEntity
import com.youneko.rate.data.local.entity.CollectionEntity
import com.youneko.rate.data.local.entity.CollectionAlbumEntity
import com.youneko.rate.data.local.entity.ScanRootEntity
import kotlinx.serialization.Serializable

const val CURRENT_BACKUP_SCHEMA = CURRENT_BACKUP_FORMAT_VERSION

@Serializable
data class LibrarySnapshot(
    val schemaVersion: Int = CURRENT_BACKUP_SCHEMA,
    val exportedAt: Long,
    val artists: List<ArtistSnapshot>,
    val albums: List<AlbumSnapshot>,
    val tracks: List<TrackSnapshot>,
    val credits: List<CreditSnapshot>,
    val analyses: List<AudioAnalysisSnapshot>,
    val reviewRevisions: List<ReviewRevisionSnapshot>,
    val albumTags: List<AlbumTagSnapshot>,
    val listeningLogs: List<ListeningLogSnapshot>,
    val externalLinks: List<ExternalLinkSnapshot>,
    val collections: List<CollectionSnapshot> = emptyList(),
    val collectionAlbums: List<CollectionAlbumSnapshot> = emptyList(),
    val scanRoots: List<ScanRootSnapshot> = emptyList(),
)

@Serializable
data class ArtistSnapshot(
    val id: String, val name: String, val sortName: String?, val imageUri: String?, val mbid: String?, val note: String?, val createdAt: Long, val updatedAt: Long,
)

@Serializable
data class AlbumSnapshot(
    val id: String, val title: String, val artistId: String, val releaseYear: Int?, val coverUri: String?, val coverThumbUri: String?, val genreTags: List<String>, val albumType: String, val label: String?, val catalogNumber: String?, val barcode: String?, val country: String?, val listenedDate: String?, val manualScoreOverride: Double?, val reviewText: String?, val mbid: String?, val releaseGroupMbid: String?, val discogsReleaseId: String?, val deezerId: String?, val sourceProvider: String?, val coverSource: String?, val coverWidth: Int?, val coverUpdatedAt: Long?, val metadataFetchedAt: Long?, val createdAt: Long, val updatedAt: Long,
)

@Serializable
data class TrackSnapshot(
    val id: String,
    val albumId: String?,
    val title: String,
    val trackNumber: Int?,
    val discNumber: Int?,
    val durationMs: Long?,
    val isStandalone: Boolean,
    val stars: Double?,
    val reviewText: String?,
    val isSkip: Boolean,
    val isHighlight: Boolean,
    val listenedDate: String?,
    val recordingMbid: String?,
    val workMbid: String?,
    val isrc: String?,
    val sourceUri: String?,
    val fileName: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val stableKey: String? = null,
    val fileSizeBytes: Long? = null,
    val fileHash64k: String? = null,
    val isMissing: Boolean = false,
)

@Serializable
data class CreditSnapshot(
    val id: String, val albumId: String?, val trackId: String?, val personName: String, val personMbid: String?, val role: String, val instrumentOrAttribute: String?, val sourceProvider: String, val sourceUrl: String?, val sortOrder: Int, val beginDate: String?, val endDate: String?,
)

@Serializable
data class AudioAnalysisSnapshot(
    val id: String, val trackId: String?, val albumId: String?, val fileName: String, val fileUriOrPath: String, val fileHash: String, val container: String?, val codec: String?, val sampleRate: Int?, val bitDepth: Int?, val bitrate: Long?, val isVbr: Boolean?, val channels: Int?, val durationMs: Long?, val encoderTag: String?, val cutoffHz: Double?, val rolloffSlope: Double?, val dynamicRangeDb: Double?, val truePeakDbtp: Double?, val clippingPercent: Double?, val verdict: String, val confidence: Int, val reasonsJson: String, val spectrumJson: String, val spectrogramPngPath: String?, val engineVersion: String, val analyzedAt: Long,
)

@Serializable
data class ReviewRevisionSnapshot(val id: String, val albumId: String, val trackId: String?, val body: String, val createdAt: Long)
@Serializable
data class AlbumTagSnapshot(val id: String, val albumId: String, val name: String, val createdAt: Long)
@Serializable
data class ListeningLogSnapshot(val id: String, val albumId: String, val trackId: String?, val listenedAt: String, val note: String?)
@Serializable
data class ExternalLinkSnapshot(val id: String, val albumId: String?, val trackId: String?, val sourceId: String, val externalId: String, val sourceUrl: String, val createdAt: Long)
@Serializable data class CollectionSnapshot(val id: String, val name: String, val description: String?, val createdAt: Long)
@Serializable data class CollectionAlbumSnapshot(val collectionId: String, val albumId: String, val sortOrder: Int)
@Serializable data class ScanRootSnapshot(val uri: String, val displayName: String?, val addedAt: Long, val lastScannedAt: Long?)

suspend fun YounekoDatabase.exportSnapshot(now: Long = System.currentTimeMillis()): LibrarySnapshot = LibrarySnapshot(
    exportedAt = now,
    artists = artistDao().findAll().map { it.toSnapshot() },
    albums = albumDao().findAll().map { it.toSnapshot() },
    tracks = trackDao().findAll().map { it.toSnapshot() },
    credits = creditDao().findAll().map { it.toSnapshot() },
    analyses = audioAnalysisDao().findAll().map { it.toSnapshot() },
    reviewRevisions = reviewRevisionDao().findAll().map { it.toSnapshot() },
    albumTags = albumTagDao().findAll().map { it.toSnapshot() },
    listeningLogs = listeningLogDao().findAll().map { it.toSnapshot() },
    externalLinks = externalLinkDao().findAll().map { it.toSnapshot() },
    collections = collectionDao().findAllCollections().map { CollectionSnapshot(it.id, it.name, it.description, it.createdAt) },
    collectionAlbums = collectionDao().findAllAlbums().map { CollectionAlbumSnapshot(it.collectionId, it.albumId, it.sortOrder) },
    scanRoots = scanRootDao().findAll().map { ScanRootSnapshot(it.uri, it.displayName, it.addedAt, it.lastScannedAt) },
)

private fun ArtistEntity.toSnapshot() = ArtistSnapshot(id, name, sortName, imageUri, mbid, note, createdAt, updatedAt)
private fun AlbumEntity.toSnapshot() = AlbumSnapshot(id, title, artistId, releaseYear, coverUri, coverThumbUri, genreTags, albumType, label, catalogNumber, barcode, country, listenedDate, manualScoreOverride, reviewText, mbid, releaseGroupMbid, discogsReleaseId, deezerId, sourceProvider, coverSource, coverWidth, coverUpdatedAt, metadataFetchedAt, createdAt, updatedAt)
private fun TrackEntity.toSnapshot() = TrackSnapshot(
    id, albumId, title, trackNumber, discNumber, durationMs, isStandalone, stars, reviewText,
    isSkip, isHighlight, listenedDate, recordingMbid, workMbid, isrc, sourceUri, fileName,
    createdAt, updatedAt, stableKey, fileSizeBytes, fileHash64k, isMissing,
)
private fun CreditEntity.toSnapshot() = CreditSnapshot(id, albumId, trackId, personName, personMbid, role, instrumentOrAttribute, sourceProvider, sourceUrl, sortOrder, beginDate, endDate)
private fun AudioAnalysisEntity.toSnapshot() = AudioAnalysisSnapshot(id, trackId, albumId, fileName, fileUriOrPath, fileHash, container, codec, sampleRate, bitDepth, bitrate, isVbr, channels, durationMs, encoderTag, cutoffHz, rolloffSlope, dynamicRangeDb, truePeakDbtp, clippingPercent, verdict, confidence, reasonsJson, spectrumJson, spectrogramPngPath, engineVersion, analyzedAt)
private fun ReviewRevisionEntity.toSnapshot() = ReviewRevisionSnapshot(id, albumId, trackId, body, createdAt)
private fun AlbumTagEntity.toSnapshot() = AlbumTagSnapshot(id, albumId, name, createdAt)
private fun ListeningLogEntity.toSnapshot() = ListeningLogSnapshot(id, albumId, trackId, listenedAt, note)
private fun ExternalLinkEntity.toSnapshot() = ExternalLinkSnapshot(id, albumId, trackId, sourceId, externalId, sourceUrl, createdAt)
