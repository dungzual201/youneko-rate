package com.youneko.rate.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.youneko.rate.data.local.entity.AlbumEntity
import com.youneko.rate.data.local.entity.ArtistEntity
import com.youneko.rate.data.local.entity.AudioAnalysisEntity
import com.youneko.rate.data.local.entity.CreditEntity
import com.youneko.rate.data.local.entity.ExternalLinkEntity
import com.youneko.rate.data.local.entity.LibrarySearchFtsEntity
import com.youneko.rate.data.local.entity.ImportSessionEntity
import com.youneko.rate.data.local.entity.RemoteMetadataCacheEntity
import com.youneko.rate.data.local.entity.ReviewRevisionEntity
import com.youneko.rate.data.local.entity.AlbumTagEntity
import com.youneko.rate.data.local.entity.ListeningLogEntity
import com.youneko.rate.data.local.entity.SearchHistoryEntity
import com.youneko.rate.data.local.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {
    @Query("SELECT * FROM albums ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<AlbumEntity?>

    @Query("SELECT * FROM albums WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): AlbumEntity?

    @Query("SELECT * FROM albums")
    suspend fun findAll(): List<AlbumEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(album: AlbumEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(albums: List<AlbumEntity>)

    @Update(onConflict = OnConflictStrategy.ABORT)
    suspend fun update(album: AlbumEntity)

    @Query("DELETE FROM albums WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM albums")
    fun observeCount(): Flow<Int>
}

@Dao
interface ArtistDao {
    @Query("SELECT * FROM artists ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<ArtistEntity>>

    @Query("SELECT * FROM artists WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): ArtistEntity?

    @Query("SELECT * FROM artists WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): ArtistEntity?

    @Query("SELECT * FROM artists ORDER BY name COLLATE NOCASE")
    suspend fun findAll(): List<ArtistEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(artist: ArtistEntity)

    @Update(onConflict = OnConflictStrategy.ABORT)
    suspend fun update(artist: ArtistEntity)
}

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE albumId = :albumId ORDER BY discNumber, trackNumber, title COLLATE NOCASE")
    fun observeForAlbum(albumId: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE albumId IS NULL AND isStandalone = 1 ORDER BY createdAt DESC")
    fun observeStandalone(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): TrackEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(track: TrackEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(tracks: List<TrackEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(tracks: List<TrackEntity>)

    @Update(onConflict = OnConflictStrategy.ABORT)
    suspend fun update(track: TrackEntity)

    @Query("DELETE FROM tracks WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM tracks WHERE albumId = :albumId ORDER BY discNumber, trackNumber")
    suspend fun findForAlbum(albumId: String): List<TrackEntity>

    @Query("SELECT * FROM tracks ORDER BY createdAt DESC")
    suspend fun findAll(): List<TrackEntity>
}

data class StatsCountRow(val label: String, val count: Int)
data class StatsAverageRow(val value: Double?)
data class StatsValueRow(val label: String, val value: Double?)

@Dao
interface StatsDao {
    @Query("SELECT COUNT(DISTINCT albumId) FROM tracks WHERE stars IS NOT NULL")
    suspend fun ratedAlbumCount(): Int

    @Query("SELECT AVG(stars) AS value FROM tracks WHERE stars IS NOT NULL")
    suspend fun averageTrackScore(): StatsAverageRow

    @Query("SELECT albums.title FROM albums JOIN tracks ON tracks.albumId = albums.id WHERE tracks.stars IS NOT NULL GROUP BY albums.id ORDER BY AVG(tracks.stars) DESC, albums.title COLLATE NOCASE LIMIT 1")
    suspend fun topRatedAlbum(): String?

    @Query("SELECT CAST(ROUND(stars) AS INTEGER) AS label, COUNT(*) AS count FROM tracks WHERE stars IS NOT NULL GROUP BY CAST(ROUND(stars) AS INTEGER) ORDER BY label")
    suspend fun scoreHistogram(): List<StatsCountRow>

    @Query("SELECT artists.name AS label, COUNT(*) AS count FROM albums JOIN artists ON artists.id = albums.artistId JOIN tracks ON tracks.albumId = albums.id WHERE tracks.stars IS NOT NULL GROUP BY artists.name ORDER BY count DESC, label COLLATE NOCASE LIMIT 10")
    suspend fun topArtists(): List<StatsCountRow>

    @Query("SELECT COALESCE(albums.label, '—') AS label, COUNT(*) AS count FROM albums JOIN tracks ON tracks.albumId = albums.id WHERE tracks.stars IS NOT NULL GROUP BY albums.label ORDER BY count DESC, label COLLATE NOCASE LIMIT 10")
    suspend fun topLabels(): List<StatsCountRow>

    @Query("SELECT credits.personName AS label, COUNT(*) AS count FROM credits JOIN tracks ON tracks.id = credits.trackId WHERE tracks.stars IS NOT NULL AND (LOWER(credits.role) LIKE '%producer%' OR LOWER(credits.role) LIKE '%mix%') GROUP BY credits.personName ORDER BY count DESC, label COLLATE NOCASE LIMIT 10")
    suspend fun topProducersAndMixers(): List<StatsCountRow>

    @Query("SELECT verdict AS label, COUNT(*) AS count FROM audio_analysis GROUP BY verdict ORDER BY count DESC")
    suspend fun qualityDistribution(): List<StatsCountRow>

    @Query("SELECT COALESCE(substr(listenedDate, 1, 4), '—') AS label, AVG(stars) AS value FROM tracks WHERE stars IS NOT NULL GROUP BY substr(listenedDate, 1, 4) ORDER BY label")
    suspend fun averageByYear(): List<StatsValueRow>

    @Query("SELECT COALESCE(substr(listenedDate, 1, 7), '—') AS label, AVG(stars) AS value FROM tracks WHERE stars IS NOT NULL GROUP BY substr(listenedDate, 1, 7) ORDER BY label")
    suspend fun averageByMonth(): List<StatsValueRow>
}

@Dao
interface ReviewRevisionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(value: ReviewRevisionEntity)

    @Query("SELECT * FROM review_revisions WHERE albumId = :albumId AND ((:trackId IS NULL AND trackId IS NULL) OR trackId = :trackId) ORDER BY createdAt DESC LIMIT 5")
    fun observeRecent(albumId: String, trackId: String?): Flow<List<ReviewRevisionEntity>>

    @Query("SELECT * FROM review_revisions ORDER BY createdAt DESC")
    suspend fun findAll(): List<ReviewRevisionEntity>
}

@Dao
interface AlbumTagDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(value: AlbumTagEntity)

    @Query("SELECT * FROM album_tags WHERE albumId = :albumId ORDER BY name COLLATE NOCASE")
    fun observeForAlbum(albumId: String): Flow<List<AlbumTagEntity>>

    @Query("SELECT * FROM album_tags ORDER BY albumId, name COLLATE NOCASE")
    suspend fun findAll(): List<AlbumTagEntity>

    @Query("DELETE FROM album_tags WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface ListeningLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(value: ListeningLogEntity)

    @Query("SELECT * FROM listening_logs WHERE albumId = :albumId ORDER BY listenedAt DESC")
    fun observeForAlbum(albumId: String): Flow<List<ListeningLogEntity>>

    @Query("SELECT COUNT(*) FROM listening_logs WHERE albumId = :albumId")
    suspend fun countForAlbum(albumId: String): Int

    @Query("SELECT * FROM listening_logs ORDER BY listenedAt DESC")
    suspend fun findAll(): List<ListeningLogEntity>
}

@Dao
interface ExternalLinkDao {
    @Query("SELECT * FROM external_links WHERE ((:trackId IS NOT NULL AND trackId = :trackId) OR (:trackId IS NULL AND albumId = :albumId)) AND sourceId = :sourceId LIMIT 1")
    suspend fun find(albumId: String?, trackId: String?, sourceId: String): ExternalLinkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(value: ExternalLinkEntity)

    @Query("SELECT * FROM external_links ORDER BY createdAt DESC")
    suspend fun findAll(): List<ExternalLinkEntity>

    @Query("DELETE FROM external_links WHERE ((:trackId IS NOT NULL AND trackId = :trackId) OR (:trackId IS NULL AND albumId = :albumId)) AND sourceId = :sourceId")
    suspend fun delete(albumId: String?, trackId: String?, sourceId: String)
}

@Dao
interface CreditDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(credits: List<CreditEntity>)

    @Query("SELECT * FROM credits WHERE ((:trackId IS NOT NULL AND trackId = :trackId) OR (:trackId IS NULL AND albumId = :albumId AND trackId IS NULL)) ORDER BY sortOrder, personName COLLATE NOCASE")
    fun observeForItem(albumId: String, trackId: String?): Flow<List<CreditEntity>>

    @Query("SELECT * FROM credits WHERE albumId = :albumId AND trackId IS NULL ORDER BY sortOrder, personName COLLATE NOCASE")
    fun observeForAlbum(albumId: String): Flow<List<CreditEntity>>

    @Query("SELECT * FROM credits WHERE albumId = :albumId AND trackId IS NULL")
    suspend fun findAlbumCredits(albumId: String): List<CreditEntity>

    @Query("SELECT c.* FROM credits c LEFT JOIN tracks t ON c.trackId = t.id WHERE c.albumId = :albumId OR t.albumId = :albumId ORDER BY c.trackId, c.sortOrder, c.personName COLLATE NOCASE")
    fun observeForAlbumWithTracks(albumId: String): Flow<List<CreditEntity>>

    @Query("SELECT c.* FROM credits c LEFT JOIN tracks t ON c.trackId = t.id WHERE c.albumId = :albumId OR t.albumId = :albumId ORDER BY c.trackId, c.sortOrder, c.personName COLLATE NOCASE")
    suspend fun findForAlbumWithTracks(albumId: String): List<CreditEntity>

    @Query("SELECT * FROM credits WHERE trackId = :trackId")
    suspend fun findTrackCredits(trackId: String): List<CreditEntity>

    @Query("DELETE FROM credits WHERE albumId = :albumId AND trackId IS NULL")
    suspend fun deleteAlbumCredits(albumId: String)

    @Query("DELETE FROM credits WHERE trackId = :trackId")
    suspend fun deleteTrackCredits(trackId: String)

    @Query("DELETE FROM credits WHERE trackId IN (SELECT id FROM tracks WHERE albumId = :albumId)")
    suspend fun deleteTrackCreditsForAlbum(albumId: String)

    @Query("SELECT * FROM credits ORDER BY albumId, trackId, sortOrder")
    suspend fun findAll(): List<CreditEntity>
}

@Dao
interface AudioAnalysisDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(analysis: AudioAnalysisEntity)

    @Query("SELECT * FROM audio_analysis ORDER BY analyzedAt DESC")
    fun observeAll(): Flow<List<AudioAnalysisEntity>>

    @Query("SELECT * FROM audio_analysis WHERE trackId = :trackId ORDER BY analyzedAt DESC LIMIT 1")
    fun observeLatestForTrack(trackId: String): Flow<AudioAnalysisEntity?>

    @Query("SELECT * FROM audio_analysis ORDER BY analyzedAt DESC")
    suspend fun findAll(): List<AudioAnalysisEntity>
}

@Dao
interface RemoteMetadataCacheDao {
    @Query("SELECT * FROM remote_metadata_cache WHERE key = :key LIMIT 1")
    suspend fun find(key: String): RemoteMetadataCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(value: RemoteMetadataCacheEntity)

    @Query("DELETE FROM remote_metadata_cache")
    suspend fun deleteAll()

    @Query("DELETE FROM remote_metadata_cache WHERE key = :key")
    suspend fun delete(key: String)
}

@Dao
interface ImportSessionDao {
    @Query("SELECT * FROM import_sessions WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): ImportSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: ImportSessionEntity)

    @Query("DELETE FROM import_sessions WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC LIMIT 10")
    fun observeRecent(): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(value: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface LibrarySearchFtsDao {
    @Query("SELECT * FROM library_search_fts WHERE library_search_fts MATCH :query ORDER BY rowid DESC")
    suspend fun search(query: String): List<LibrarySearchFtsEntity>

    @Query("SELECT entityId FROM library_search_fts WHERE library_search_fts MATCH :query ORDER BY rowid DESC")
    fun observeEntityIds(query: String): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(value: LibrarySearchFtsEntity)

    @Query("DELETE FROM library_search_fts WHERE entityId = :entityId")
    suspend fun deleteForEntity(entityId: String)

    @Query("DELETE FROM library_search_fts")
    suspend fun deleteAll()
}

@Dao
interface CollectionDao {
    @Query("SELECT * FROM collections ORDER BY createdAt DESC")
    suspend fun findAllCollections(): List<com.youneko.rate.data.local.entity.CollectionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCollection(value: com.youneko.rate.data.local.entity.CollectionEntity)

    @Query("DELETE FROM collections WHERE id = :id")
    suspend fun deleteCollection(id: String)

    @Query("SELECT * FROM collection_albums WHERE collectionId = :collectionId ORDER BY sortOrder, albumId")
    suspend fun findAlbums(collectionId: String): List<com.youneko.rate.data.local.entity.CollectionAlbumEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAlbum(value: com.youneko.rate.data.local.entity.CollectionAlbumEntity)

    @Query("DELETE FROM collection_albums WHERE collectionId = :collectionId AND albumId = :albumId")
    suspend fun removeAlbum(collectionId: String, albumId: String)
}
