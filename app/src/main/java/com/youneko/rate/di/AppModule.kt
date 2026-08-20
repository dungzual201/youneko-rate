package com.youneko.rate.di

import android.content.Context
import androidx.room.Room
import com.youneko.rate.data.SettingsDataStore
import com.youneko.rate.data.local.YounekoDatabase
import com.youneko.rate.data.local.dao.AlbumDao
import com.youneko.rate.data.local.dao.ArtistDao
import com.youneko.rate.data.local.dao.AudioAnalysisDao
import com.youneko.rate.data.local.dao.CreditDao
import com.youneko.rate.data.local.dao.ExternalLinkDao
import com.youneko.rate.data.local.dao.ReviewRevisionDao
import com.youneko.rate.data.local.dao.AlbumTagDao
import com.youneko.rate.data.local.dao.ListeningLogDao
import com.youneko.rate.data.local.dao.StatsDao
import com.youneko.rate.data.local.dao.LibrarySearchFtsDao
import com.youneko.rate.data.local.dao.ImportSessionDao
import com.youneko.rate.data.local.dao.RemoteMetadataCacheDao
import com.youneko.rate.data.local.dao.SearchHistoryDao
import com.youneko.rate.data.local.dao.ScanRootDao
import com.youneko.rate.data.local.dao.TrackDao
import com.youneko.rate.data.AlbumRepository
import com.youneko.rate.data.RateRepository
import com.youneko.rate.data.musicbrainz.AlbumMetadataRefreshService
import com.youneko.rate.data.musicbrainz.MusicBrainzImportService
import com.youneko.rate.data.discogs.CoverDiscogsProvider
import com.youneko.rate.data.discogs.DiscogsCreditsService
import com.youneko.rate.data.credits.CreditSource
import com.youneko.rate.data.credits.DeezerCreditSource
import com.youneko.rate.data.credits.DiscogsCreditSource
import com.youneko.rate.data.credits.GeniusCreditSource
import com.youneko.rate.data.credits.ItunesCreditSource
import com.youneko.rate.data.credits.MusicBrainzCreditSource
import com.youneko.rate.data.credits.ManualCreditSource
import com.youneko.rate.data.credits.TagCreditSource
import com.youneko.rate.data.SettingsStore
import com.youneko.rate.data.MediaScanStore
import com.youneko.rate.domain.usecase.CalculateAlbumScoreUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): YounekoDatabase =
        Room.databaseBuilder(context, YounekoDatabase::class.java, "youneko_rate.db")
            .addMigrations(YounekoDatabase.MIGRATION_1_2, YounekoDatabase.MIGRATION_2_3, YounekoDatabase.MIGRATION_3_4, YounekoDatabase.MIGRATION_4_5, YounekoDatabase.MIGRATION_5_6, YounekoDatabase.MIGRATION_6_7, YounekoDatabase.MIGRATION_7_8, YounekoDatabase.MIGRATION_8_9, YounekoDatabase.MIGRATION_9_10, YounekoDatabase.MIGRATION_10_11, YounekoDatabase.MIGRATION_11_12, YounekoDatabase.MIGRATION_12_13, YounekoDatabase.MIGRATION_13_14, YounekoDatabase.MIGRATION_14_15, YounekoDatabase.MIGRATION_15_16, YounekoDatabase.MIGRATION_16_17)
            .build()

    @Provides
    @Singleton
    fun provideCoverDiscogsProvider(service: DiscogsCreditsService): CoverDiscogsProvider = service

    @Provides
    @IntoSet
    fun provideTagCreditSource(source: TagCreditSource): CreditSource = source

    @Provides
    @IntoSet
    fun provideMusicBrainzCreditSource(source: MusicBrainzCreditSource): CreditSource = source

    @Provides
    @IntoSet
    fun provideManualCreditSource(source: ManualCreditSource): CreditSource = source

    @Provides
    @IntoSet
    fun provideDiscogsCreditSource(source: DiscogsCreditSource): CreditSource = source

    @Provides
    @IntoSet
    fun provideGeniusCreditSource(source: GeniusCreditSource): CreditSource = source

    @Provides
    @IntoSet
    fun provideDeezerCreditSource(source: DeezerCreditSource): CreditSource = source

    @Provides
    @IntoSet
    fun provideItunesCreditSource(source: ItunesCreditSource): CreditSource = source

    @Provides
    fun provideAlbumDao(database: YounekoDatabase): AlbumDao = database.albumDao()

    @Provides
    fun provideArtistDao(database: YounekoDatabase): ArtistDao = database.artistDao()

    @Provides
    fun provideTrackDao(database: YounekoDatabase): TrackDao = database.trackDao()

    @Provides
    fun provideCreditDao(database: YounekoDatabase): CreditDao = database.creditDao()

    @Provides
    fun provideExternalLinkDao(database: YounekoDatabase): ExternalLinkDao = database.externalLinkDao()

    @Provides
    fun provideReviewRevisionDao(database: YounekoDatabase): ReviewRevisionDao = database.reviewRevisionDao()

    @Provides
    fun provideAlbumTagDao(database: YounekoDatabase): AlbumTagDao = database.albumTagDao()

    @Provides
    fun provideListeningLogDao(database: YounekoDatabase): ListeningLogDao = database.listeningLogDao()

    @Provides
    fun provideStatsDao(database: YounekoDatabase): StatsDao = database.statsDao()

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
    fun provideImportSessionDao(database: YounekoDatabase): ImportSessionDao = database.importSessionDao()

    @Provides
    fun provideScanRootDao(database: YounekoDatabase): ScanRootDao = database.scanRootDao()

    @Provides
    @Singleton
    fun provideAlbumRepository(repository: RateRepository): AlbumRepository = repository

    @Provides
    @Singleton
    fun provideAlbumMetadataRefreshService(service: MusicBrainzImportService): AlbumMetadataRefreshService = service

    @Provides
    fun provideCalculateAlbumScoreUseCase(): CalculateAlbumScoreUseCase = CalculateAlbumScoreUseCase()

    @Provides
    @Singleton
    fun provideSettingsStore(@ApplicationContext context: Context): SettingsStore =
        SettingsDataStore(context)

    @Provides
    @Singleton
    fun provideMediaScanStore(@ApplicationContext context: Context): MediaScanStore =
        MediaScanStore(context)
}
