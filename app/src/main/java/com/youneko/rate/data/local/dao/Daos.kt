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
import com.youneko.rate.data.local.entity.LibrarySearchFtsEntity
import com.youneko.rate.data.local.entity.RemoteMetadataCacheEntity
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

    @Update(onConflict = OnConflictStrategy.ABORT)
    suspend fun update(track: TrackEntity)

    @Query("DELETE FROM tracks WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM tracks WHERE albumId = :albumId ORDER BY discNumber, trackNumber")
    suspend fun findForAlbum(albumId: String): List<TrackEntity>
}

@Dao
interface CreditDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(credits: List<CreditEntity>)

    @Query("SELECT * FROM credits WHERE albumId = :albumId OR trackId = :trackId ORDER BY sortOrder, personName COLLATE NOCASE")
    fun observeForItem(albumId: String?, trackId: String?): Flow<List<CreditEntity>>
}

@Dao
interface AudioAnalysisDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(analysis: AudioAnalysisEntity)

    @Query("SELECT * FROM audio_analysis ORDER BY analyzedAt DESC")
    fun observeAll(): Flow<List<AudioAnalysisEntity>>
}

@Dao
interface RemoteMetadataCacheDao {
    @Query("SELECT * FROM remote_metadata_cache WHERE key = :key LIMIT 1")
    suspend fun find(key: String): RemoteMetadataCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(value: RemoteMetadataCacheEntity)
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
