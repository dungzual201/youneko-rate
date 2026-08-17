package com.youneko.rate.di

import android.content.Context
import androidx.room.Room
import com.youneko.rate.data.SettingsDataStore
import com.youneko.rate.data.local.YounekoDatabase
import com.youneko.rate.data.local.dao.AlbumDao
import com.youneko.rate.data.local.dao.ArtistDao
import com.youneko.rate.data.local.dao.AudioAnalysisDao
import com.youneko.rate.data.local.dao.CreditDao
import com.youneko.rate.data.local.dao.LibrarySearchFtsDao
import com.youneko.rate.data.local.dao.RemoteMetadataCacheDao
import com.youneko.rate.data.local.dao.SearchHistoryDao
import com.youneko.rate.data.local.dao.TrackDao
import com.youneko.rate.data.AlbumRepository
import com.youneko.rate.data.RateRepository
import com.youneko.rate.data.SettingsStore
import com.youneko.rate.domain.usecase.CalculateAlbumScoreUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): YounekoDatabase =
        Room.databaseBuilder(context, YounekoDatabase::class.java, "youneko_rate.db")
            .addMigrations(YounekoDatabase.MIGRATION_1_2)
            .build()

    @Provides
    fun provideAlbumDao(database: YounekoDatabase): AlbumDao = database.albumDao()

    @Provides
    fun provideArtistDao(database: YounekoDatabase): ArtistDao = database.artistDao()

    @Provides
    fun provideTrackDao(database: YounekoDatabase): TrackDao = database.trackDao()

    @Provides
    fun provideCreditDao(database: YounekoDatabase): CreditDao = database.creditDao()

    @Provides
    fun provideAudioAnalysisDao(database: YounekoDatabase): AudioAnalysisDao = database.audioAnalysisDao()

    @Provides
    fun provideRemoteMetadataCacheDao(database: YounekoDatabase): RemoteMetadataCacheDao =
        database.remoteMetadataCacheDao()

    @Provides
    fun provideSearchHistoryDao(database: YounekoDatabase): SearchHistoryDao = database.searchHistoryDao()

    @Provides
    fun provideLibrarySearchFtsDao(database: YounekoDatabase): LibrarySearchFtsDao =
        database.librarySearchFtsDao()

    @Provides
    @Singleton
    fun provideAlbumRepository(repository: RateRepository): AlbumRepository = repository

    @Provides
    fun provideCalculateAlbumScoreUseCase(): CalculateAlbumScoreUseCase = CalculateAlbumScoreUseCase()

    @Provides
    @Singleton
    fun provideSettingsStore(@ApplicationContext context: Context): SettingsStore =
        SettingsDataStore(context)
}
