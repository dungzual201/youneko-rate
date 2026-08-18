package com.youneko.rate.data

import androidx.room.withTransaction
import com.youneko.rate.data.local.YounekoDatabase
import com.youneko.rate.data.local.dao.AlbumDao
import com.youneko.rate.data.local.dao.ArtistDao
import com.youneko.rate.data.local.dao.LibrarySearchFtsDao
import com.youneko.rate.data.local.dao.TrackDao
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
)

data class TrackDraft(
    val title: String,
    val discNumber: Int = 1,
    val durationMs: Long? = null,
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
    suspend fun saveStandalone(title: String, artistName: String, listenedDate: String?): String
    suspend fun updateTrack(track: TrackEntity)
    suspend fun updateAlbum(album: AlbumEntity)
    suspend fun deleteAlbum(id: String)
    suspend fun findMatchingAlbum(group: ImportGroup): String?
    suspend fun appendImportedTracks(albumId: String, tracks: List<ImportedTrack>): Int
}

@Singleton
class RateRepository @Inject constructor(
    private val database: YounekoDatabase,
    private val albumDao: AlbumDao,
    private val artistDao: ArtistDao,
    private val trackDao: TrackDao,
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
            listenedDate = draft.listenedDate,
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
                createdAt = now,
                updatedAt = now,
            )
        }
        database.withTransaction {
            albumDao.insert(album)
            trackDao.insertAll(tracks)
            rebuildSearchIndex(albumId, album, artist, tracks)
        }
        return albumId
    }

    override suspend fun saveStandalone(title: String, artistName: String, listenedDate: String?): String {
        require(title.isNotBlank()) { "Tên bài không được để trống" }
        val now = System.currentTimeMillis()
        val artist = artistDao.findByName(artistName.trim()) ?: ArtistEntity(
            id = UUID.randomUUID().toString(), name = artistName.trim(), createdAt = now, updatedAt = now,
        )
        if (artistDao.findById(artist.id) == null) artistDao.insert(artist)
        val track = TrackEntity(
            id = UUID.randomUUID().toString(), title = title.trim(), isStandalone = true,
            listenedDate = listenedDate, createdAt = now, updatedAt = now,
        )
        trackDao.insert(track)
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
                createdAt = now,
                updatedAt = now,
            )
        }
        database.withTransaction {
            updates.forEach { trackDao.update(it) }
            if (entities.isNotEmpty()) trackDao.insertAll(entities)
            val album = albumDao.findById(albumId) ?: return@withTransaction
            val artist = artistDao.findById(album.artistId)
            if (artist != null) rebuildSearchIndex(albumId, album, artist, trackDao.findForAlbum(albumId))
        }
        return entities.size
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
