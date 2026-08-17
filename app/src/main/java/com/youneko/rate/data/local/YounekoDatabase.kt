package com.youneko.rate.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.youneko.rate.data.local.dao.AlbumDao
import com.youneko.rate.data.local.dao.AudioAnalysisDao
import com.youneko.rate.data.local.dao.CreditDao
import com.youneko.rate.data.local.dao.LibrarySearchFtsDao
import com.youneko.rate.data.local.dao.RemoteMetadataCacheDao
import com.youneko.rate.data.local.dao.SearchHistoryDao
import com.youneko.rate.data.local.dao.TrackDao
import com.youneko.rate.data.local.entity.AlbumEntity
import com.youneko.rate.data.local.entity.ArtistEntity
import com.youneko.rate.data.local.entity.AudioAnalysisEntity
import com.youneko.rate.data.local.entity.CreditEntity
import com.youneko.rate.data.local.entity.LibrarySearchFtsEntity
import com.youneko.rate.data.local.entity.RemoteMetadataCacheEntity
import com.youneko.rate.data.local.entity.SearchHistoryEntity
import com.youneko.rate.data.local.entity.TrackEntity

@Database(
    entities = [
        ArtistEntity::class,
        AlbumEntity::class,
        TrackEntity::class,
        CreditEntity::class,
        AudioAnalysisEntity::class,
        RemoteMetadataCacheEntity::class,
        SearchHistoryEntity::class,
        LibrarySearchFtsEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(YounekoTypeConverters::class)
abstract class YounekoDatabase : RoomDatabase() {
    abstract fun albumDao(): AlbumDao
    abstract fun trackDao(): TrackDao
    abstract fun creditDao(): CreditDao
    abstract fun audioAnalysisDao(): AudioAnalysisDao
    abstract fun remoteMetadataCacheDao(): RemoteMetadataCacheDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun librarySearchFtsDao(): LibrarySearchFtsDao
}
