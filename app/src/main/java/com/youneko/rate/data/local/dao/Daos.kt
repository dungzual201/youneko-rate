package com.youneko.rate.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.youneko.rate.data.local.entity.AlbumEntity
import com.youneko.rate.data.local.entity.AudioAnalysisEntity
import com.youneko.rate.data.local.entity.CreditEntity
import com.youneko.rate.data.local.entity.LibrarySearchFtsEntity
import com.youneko.rate.data.local.entity.RemoteMetadataCacheEntity
import com.youneko.rate.data.local.entity.SearchHistoryEntity
import com.youneko.rate.data.local.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {
    @Query("SELECT * FROM albums ORDER BY title COLLATE NOCASE")
    fun observeAll(): Flow<List<AlbumEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(album: AlbumEntity)

    @Query("SELECT * FROM albums WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): AlbumEntity?
}

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks WHERE albumId = :albumId ORDER BY discNumber, trackNumber, title COLLATE NOCASE")
    fun observeForAlbum(albumId: String): Flow<List<TrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(track: TrackEntity)
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(value: LibrarySearchFtsEntity)
}
