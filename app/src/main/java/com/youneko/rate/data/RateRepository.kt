package com.youneko.rate.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.youneko.rate.data.local.YounekoDatabase
import com.youneko.rate.data.local.dao.AlbumDao
import com.youneko.rate.data.local.dao.ArtistDao
import com.youneko.rate.data.local.dao.LibrarySearchFtsDao
import com.youneko.rate.data.local.dao.TrackDao
import com.youneko.rate.data.local.dao.CreditDao
import com.youneko.rate.data.musicbrainz.CreditMerger
import com.youneko.rate.data.local.entity.AlbumEntity
import com.youneko.rate.data.local.entity.ArtistEntity
import com.youneko.rate.data.local.entity.LibrarySearchFtsEntity
import com.youneko.rate.data.local.entity.TrackEntity
import com.youneko.rate.domain.usecase.AlbumScoreResult
import com.youneko.rate.domain.usecase.CalculateAlbumScoreUseCase
import com.youneko.rate.domain.usecase.ScoreMode
import com.youneko.rate.domain.usecase.TrackScoreInput
import com.youneko.rate.data.importer.ImportDedupe
import com.youneko.rate.data.importer.ImportGroup
import com.youneko.rate.data.importer.ImportMergePolicy
import com.youneko.rate.data.importer.ImportedTrack
import com.youneko.rate.data.scan.StableMediaKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class AlbumDraft(
    val title: String,
    val artistName: String,
    val releaseYear: Int?,
    val albumType: String,
    val genreTags: List<String>,
    val listenedDate: String?,
    val coverUri: String?,
    val tracks: List<TrackDraft>,
    val mbid: String? = null,
    val releaseGroupMbid: String? = null,
    val label: String? = null,
    val catalogNumber: String? = null,
    val country: String? = null,
    val sourceProvider: String? = null,
    val coverSource: String? = null,
    val coverWidth: Int? = null,
    val coverUpdatedAt: Long? = null,
    val metadataFetchedAt: Long? = null,
)

data class TrackDraft(
    val title: String,
    val discNumber: Int = 1,
    val durationMs: Long? = null,
    val recordingMbid: String? = null,
    val sourceUri: String? = null,
    val fileName: String? = null,
    val embeddedCredits: List<com.youneko.rate.data.musicbrainz.CreditCandidate> = emptyList(),
    val id: String = UUID.randomUUID().toString(),
)

data class LibraryAlbum(
    val album: AlbumEntity,
    val artist: ArtistEntity?,
    val tracks: List<TrackEntity>,
    val score: AlbumScoreResult?,
)

interface AlbumRepository {
    fun observeAlbums(scoreMode: ScoreMode = ScoreMode.SIMPLE): Flow<List<LibraryAlbum>>
    fun observeAlbum(id: String, scoreMode: ScoreMode = ScoreMode.SIMPLE): Flow<LibraryAlbum?>
    suspend fun searchEntityIds(query: String): Set<String>
    suspend fun saveAlbum(draft: AlbumDraft): String
    suspend fun saveAlbumBatched(draft: AlbumDraft, batchSize: Int = 50): String
    suspend fun saveStandalone(title: String, artistName: String, listenedDate: String?, sourceUri: String? = null, fileName: String? = null, embeddedCredits: List<com.youneko.rate.data.musicbrainz.CreditCandidate> = emptyList()): String
    suspend fun updateTrack(track: TrackEntity)
    suspend fun updateAlbum(album: AlbumEntity)
    suspend fun deleteAlbum(id: String)
    suspend fun findMatchingAlbum(group: ImportGroup): String?
    suspend fun appendImportedTracks(albumId: String, tracks: List<ImportedTrack>): Int
    suspend fun findMusicBrainzMatch(draft: AlbumDraft): String?
    suspend fun mergeMusicBrainzMetadata(albumId: String, draft: AlbumDraft)
}

@Singleton
class RateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: YounekoDatabase,
    private val albumDao: AlbumDao,
    private val artistDao: ArtistDao,
    private val trackDao: TrackDao,
    private val creditDao: CreditDao,
    private val ftsDao: LibrarySearchFtsDao,
    private val scoreUseCase: CalculateAlbumScoreUseCase,
) : AlbumRepository {
    override fun observeAlbums(scoreMode: ScoreMode): Flow<List<LibraryAlbum>> =
        combine(albumDao.observeAll(), artistDao.observeAll(), trackDao.observeAll()) { albums, artists, tracks ->
            albums.map { album ->
                val albumTracks = tracks.filter { it.albumId == album.id }
                LibraryAlbum(
                    album = album,
                    artist = artists.firstOrNull { it.id == album.artistId },
                    tracks = albumTracks,
                    score = scoreUseCase(
                        albumTracks.map { TrackScoreInput(it.stars, it.durationMs) },
                        scoreMode,
                        album.manualScoreOverride,
                    ),
                )
            }
        }

    override fun observeAlbum(id: String, scoreMode: ScoreMode): Flow<LibraryAlbum?> =
        combine(albumDao.observeById(id), artistDao.observeAll(), trackDao.observeForAlbum(id)) { album, artists, tracks ->
            album?.let {
                LibraryAlbum(
                    album = it,
                    artist = artists.firstOrNull { artist -> artist.id == it.artistId },
                    tracks = tracks,
                    score = scoreUseCase(
                        tracks.map { track -> TrackScoreInput(track.stars, track.durationMs) },
                        scoreMode,
                        it.manualScoreOverride,
                    ),
                )
            }
        }

    override suspend fun searchEntityIds(query: String): Set<String> {
        if (query.isBlank()) return emptySet()
        val normalized = query.trim().replace("\"", "")
        return ftsDao.search("*$normalized*").map { it.entityId }.toSet()
    }

    override suspend fun saveAlbum(draft: AlbumDraft): String {
        require(draft.title.isNotBlank()) { "Tên album không được để trống" }
        require(draft.artistName.isNotBlank()) { "Tên nghệ sĩ không được để trống" }
        require(draft.tracks.all { it.title.isNotBlank() }) { "Tên bài không được để trống" }
        val now = System.currentTimeMillis()
        val artist = artistDao.findByName(draft.artistName.trim()) ?: ArtistEntity(
            id = UUID.randomUUID().toString(),
            name = draft.artistName.trim(),
            createdAt = now,
            updatedAt = now,
        )
        if (artistDao.findById(artist.id) == null) artistDao.insert(artist)
        val albumId = UUID.randomUUID().toString()
        val album = AlbumEntity(
            id = albumId,
            title = draft.title.trim(),
            artistId = artist.id,
            releaseYear = draft.releaseYear,
            coverUri = draft.coverUri,
            coverThumbUri = draft.coverUri,
            genreTags = draft.genreTags.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
            albumType = draft.albumType,
            label = draft.label,
            catalogNumber = draft.catalogNumber,
            country = draft.country,
            listenedDate = draft.listenedDate,
            mbid = draft.mbid,
            releaseGroupMbid = draft.releaseGroupMbid,
            sourceProvider = draft.sourceProvider,
            coverSource = draft.coverSource,
            coverWidth = draft.coverWidth,
            coverUpdatedAt = draft.coverUpdatedAt ?: draft.coverUri?.let { now },
            metadataFetchedAt = draft.metadataFetchedAt,
            createdAt = now,
            updatedAt = now,
        )
        val tracks = draft.tracks.mapIndexed { index, item ->
            TrackEntity(
                id = item.id,
                albumId = albumId,
                title = item.title.trim(),
                trackNumber = index + 1,
                discNumber = item.discNumber,
                durationMs = item.durationMs,
                recordingMbid = item.recordingMbid,
                sourceUri = item.sourceUri,
                fileName = item.fileName,
                createdAt = now,
                updatedAt = now,
                fileSizeBytes = item.sourceUri?.let { StableMediaKey.sizeBytes(context, Uri.parse(it)) },
                fileHash64k = item.sourceUri?.let { StableMediaKey.first64kHash(context, Uri.parse(it)) },
                stableKey = item.sourceUri?.let { source ->
                    val uri = Uri.parse(source)
                    StableMediaKey.from(StableMediaKey.sizeBytes(context, uri), item.durationMs, StableMediaKey.first64kHash(context, uri))
                },
            )
        }
        database.withTransaction {
            albumDao.insert(album)
            trackDao.insertAll(tracks)
            persistEmbeddedCredits(draft.tracks)
            rebuildSearchIndex(albumId, album, artist, tracks)
        }
        return albumId
    }

    override suspend fun saveAlbumBatched(draft: AlbumDraft, batchSize: Int): String {
        require(batchSize > 0) { "batchSize phải lớn hơn 0" }
        require(draft.title.isNotBlank()) { "Tên album không được để trống" }
        require(draft.artistName.isNotBlank()) { "Tên nghệ sĩ không được để trống" }
        require(draft.tracks.all { it.title.isNotBlank() }) { "Tên bài không được để trống" }
        val now = System.currentTimeMillis()
        val artist = artistDao.findByName(draft.artistName.trim()) ?: ArtistEntity(
            id = UUID.randomUUID().toString(),
            name = draft.artistName.trim(),
            createdAt = now,
            updatedAt = now,
        )
        val albumId = UUID.randomUUID().toString()
        val album = AlbumEntity(
            id = albumId,
            title = draft.title.trim(),
            artistId = artist.id,
            releaseYear = draft.releaseYear,
            coverUri = draft.coverUri,
            coverThumbUri = draft.coverUri,
            genreTags = draft.genreTags.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
            albumType = draft.albumType,
            label = draft.label,
            catalogNumber = draft.catalogNumber,
            country = draft.country,
            listenedDate = draft.listenedDate,
            mbid = draft.mbid,
            releaseGroupMbid = draft.releaseGroupMbid,
            sourceProvider = draft.sourceProvider,
            coverSource = draft.coverSource,
            coverWidth = draft.coverWidth,
            coverUpdatedAt = draft.coverUpdatedAt ?: draft.coverUri?.let { now },
            metadataFetchedAt = draft.metadataFetchedAt,
            createdAt = now,
            updatedAt = now,
        )
        val tracks = draft.tracks.mapIndexed { index, item ->
            TrackEntity(
                id = item.id,
                albumId = albumId,
                title = item.title.trim(),
                trackNumber = index + 1,
                discNumber = item.discNumber,
                durationMs = item.durationMs,
                recordingMbid = item.recordingMbid,
                sourceUri = item.sourceUri,
                fileName = item.fileName,
                createdAt = now,
                updatedAt = now,
                fileSizeBytes = item.sourceUri?.let { StableMediaKey.sizeBytes(context, Uri.parse(it)) },
                fileHash64k = item.sourceUri?.let { StableMediaKey.first64kHash(context, Uri.parse(it)) },
                stableKey = item.sourceUri?.let { source ->
                    val uri = Uri.parse(source)
                    StableMediaKey.from(StableMediaKey.sizeBytes(context, uri), item.durationMs, StableMediaKey.first64kHash(context, uri))
                },
            )
        }
        database.withTransaction {
            if (artistDao.findById(artist.id) == null) artistDao.insert(artist)
            albumDao.insert(album)
        }
        tracks.chunked(batchSize).forEach { batch ->
            database.withTransaction { trackDao.insertAllIgnore(batch) }
        }
        database.withTransaction {
            persistEmbeddedCredits(draft.tracks)
            rebuildSearchIndex(albumId, album, artistDao.findById(artist.id) ?: artist, trackDao.findForAlbum(albumId))
        }
        return albumId
    }

    override suspend fun findMusicBrainzMatch(draft: AlbumDraft): String? {
        val albums = albumDao.findAll()
        draft.mbid?.let { mbid -> albums.firstOrNull { it.mbid == mbid }?.let { return it.id } }
        draft.releaseGroupMbid?.let { groupMbid -> albums.firstOrNull { it.releaseGroupMbid == groupMbid }?.let { return it.id } }
        return albums.firstOrNull { album ->
            val artist = artistDao.findById(album.artistId) ?: return@firstOrNull false
            ImportDedupe.sameAlbum(
                title = draft.title,
                artist = draft.artistName,
                year = draft.releaseYear,
                existingTitle = album.title,
                existingArtist = artist.name,
                existingYear = album.releaseYear,
            )
        }?.id
    }

    override suspend fun mergeMusicBrainzMetadata(albumId: String, draft: AlbumDraft) {
        val existing = albumDao.findById(albumId) ?: return
        val now = System.currentTimeMillis()
        database.withTransaction {
            albumDao.update(
                existing.copy(
                    coverUri = existing.coverUri ?: draft.coverUri,
                    coverThumbUri = existing.coverThumbUri ?: draft.coverUri,
                    coverSource = existing.coverSource ?: draft.coverSource,
                    coverWidth = existing.coverWidth ?: draft.coverWidth,
                    coverUpdatedAt = existing.coverUpdatedAt ?: draft.coverUpdatedAt,
                    label = existing.label ?: draft.label,
                    catalogNumber = existing.catalogNumber ?: draft.catalogNumber,
                    country = existing.country ?: draft.country,
                    mbid = existing.mbid ?: draft.mbid,
                    releaseGroupMbid = existing.releaseGroupMbid ?: draft.releaseGroupMbid,
                    sourceProvider = existing.sourceProvider ?: draft.sourceProvider,
                    metadataFetchedAt = now,
                    updatedAt = now,
                ),
            )
            val incomingByTitle = draft.tracks.associateBy { ImportDedupe.normalize(it.title) }
            trackDao.findForAlbum(albumId).forEach { track ->
                val incoming = incomingByTitle[ImportDedupe.normalize(track.title)]
                if (track.recordingMbid.isNullOrBlank() && !incoming?.recordingMbid.isNullOrBlank()) {
                    trackDao.update(track.copy(recordingMbid = incoming?.recordingMbid, updatedAt = now))
                }
            }
        }
    }

    override suspend fun saveStandalone(title: String, artistName: String, listenedDate: String?, sourceUri: String?, fileName: String?, embeddedCredits: List<com.youneko.rate.data.musicbrainz.CreditCandidate>): String {
        require(title.isNotBlank()) { "Tên bài không được để trống" }
        val now = System.currentTimeMillis()
        val artist = artistDao.findByName(artistName.trim()) ?: ArtistEntity(
            id = UUID.randomUUID().toString(), name = artistName.trim(), createdAt = now, updatedAt = now,
        )
        if (artistDao.findById(artist.id) == null) artistDao.insert(artist)
        val track = TrackEntity(
            id = UUID.randomUUID().toString(), title = title.trim(), isStandalone = true,
            listenedDate = listenedDate, sourceUri = sourceUri, fileName = fileName, createdAt = now, updatedAt = now,
        )
        trackDao.insert(track)
        creditDao.upsertAll(CreditMerger.merge(null, track.id, embeddedCredits))
        ftsDao.upsert(LibrarySearchFtsEntity(track.id, "track", "$title $artistName"))
        return track.id
    }

    override suspend fun updateTrack(track: TrackEntity) {
        val updated = track.copy(updatedAt = System.currentTimeMillis())
        trackDao.update(updated)
        ftsDao.deleteForEntity(track.id)
        ftsDao.upsert(LibrarySearchFtsEntity(track.id, "track", "${track.title} ${track.reviewText.orEmpty()}"))
    }

    override suspend fun updateAlbum(album: AlbumEntity) {
        albumDao.update(album.copy(updatedAt = System.currentTimeMillis()))
    }

    override suspend fun deleteAlbum(id: String) {
        database.withTransaction {
            albumDao.deleteById(id)
            ftsDao.deleteForEntity(id)
        }
    }

    override suspend fun findMatchingAlbum(group: ImportGroup): String? {
        val albumTitle = group.album ?: return null
        for (album in albumDao.findAll()) {
            val artist = artistDao.findById(album.artistId)
            if (artist != null && ImportDedupe.sameAlbum(
                    title = albumTitle,
                    artist = group.artist,
                    year = group.year,
                    existingTitle = album.title,
                    existingArtist = artist.name,
                    existingYear = album.releaseYear,
                )) return album.id
        }
        return null
    }

    override suspend fun appendImportedTracks(albumId: String, tracks: List<ImportedTrack>): Int {
        if (tracks.isEmpty()) return 0
        val existing = trackDao.findForAlbum(albumId)
        val existingByTitle = existing.associateBy { ImportDedupe.normalize(it.title) }
        val selected = ImportDedupe.newTracks(emptyList(), tracks)
        if (selected.isEmpty()) return 0
        val updates = selected.mapNotNull { incoming ->
            existingByTitle[ImportDedupe.normalize(incoming.title)]?.let { ImportMergePolicy.preserveUserData(it, incoming) }
        }
        val newTracks = selected.filter { ImportDedupe.normalize(it.title) !in existingByTitle }
        val nextNumber = (existing.maxOfOrNull { it.trackNumber ?: 0 } ?: 0) + 1
        val now = System.currentTimeMillis()
        val entities = newTracks.mapIndexed { index, track ->
            TrackEntity(
                id = UUID.randomUUID().toString(),
                albumId = albumId,
                title = track.title,
                trackNumber = track.trackNumber ?: nextNumber + index,
                discNumber = track.discNumber,
                durationMs = track.durationMs,
                listenedDate = track.listenedDate,
                sourceUri = track.uri,
                fileName = track.fileName,
                createdAt = now,
                updatedAt = now,
                fileSizeBytes = track.uri.let { StableMediaKey.sizeBytes(context, Uri.parse(it)) },
                fileHash64k = track.uri.let { StableMediaKey.first64kHash(context, Uri.parse(it)) },
                stableKey = track.uri.let { source ->
                    val uri = Uri.parse(source)
                    StableMediaKey.from(StableMediaKey.sizeBytes(context, uri), track.durationMs, StableMediaKey.first64kHash(context, uri))
                },
            )
        }
        database.withTransaction {
            updates.forEach { trackDao.update(it) }
            if (entities.isNotEmpty()) trackDao.insertAll(entities)
            newTracks.zip(entities).forEach { (incoming, entity) ->
                creditDao.upsertAll(CreditMerger.merge(null, entity.id, incoming.embeddedCredits))
            }
            updates.forEach { existing ->
                val incoming = tracks.firstOrNull { ImportDedupe.normalize(it.title) == ImportDedupe.normalize(existing.title) }
                if (incoming != null && incoming.embeddedCredits.isNotEmpty()) {
                    val existingCredits = creditDao.findTrackCredits(existing.id)
                    val candidates = existingCredits.map { credit -> com.youneko.rate.data.musicbrainz.CreditCandidate(credit.personName, credit.personMbid, credit.role, credit.instrumentOrAttribute, credit.sourceProvider, credit.sourceUrl, credit.beginDate, credit.endDate) } + incoming.embeddedCredits
                    creditDao.deleteTrackCredits(existing.id)
                    creditDao.upsertAll(CreditMerger.merge(null, existing.id, candidates))
                }
            }
            val album = albumDao.findById(albumId) ?: return@withTransaction
            val artist = artistDao.findById(album.artistId)
            if (artist != null) rebuildSearchIndex(albumId, album, artist, trackDao.findForAlbum(albumId))
        }
        return entities.size
    }

    private suspend fun persistEmbeddedCredits(drafts: List<TrackDraft>) {
        drafts.filter { it.embeddedCredits.isNotEmpty() }.forEach { draft ->
            creditDao.upsertAll(CreditMerger.merge(null, draft.id, draft.embeddedCredits))
        }
    }

    suspend fun rebuildSearchIndex(albumId: String, album: AlbumEntity, artist: ArtistEntity, tracks: List<TrackEntity>) {
        ftsDao.deleteForEntity(albumId)
        ftsDao.upsert(
            LibrarySearchFtsEntity(
                entityId = albumId,
                entityType = "album",
                searchableText = buildString {
                    append(album.title).append(' ').append(artist.name).append(' ')
                    append(album.genreTags.joinToString(" ")).append(' ')
                    append(album.reviewText.orEmpty()).append(' ')
                    append(tracks.joinToString(" ") { "${it.title} ${it.reviewText.orEmpty()}" })
                },
            ),
        )
        tracks.forEach { track ->
            ftsDao.deleteForEntity(track.id)
            ftsDao.upsert(LibrarySearchFtsEntity(track.id, "track", "${track.title} ${track.reviewText.orEmpty()}"))
        }
    }
}
