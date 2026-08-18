package com.youneko.rate.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.youneko.rate.BuildConfig
import com.youneko.rate.data.musicbrainz.MusicBrainzApi
import com.youneko.rate.data.musicbrainz.MusicBrainzReleaseGroupApi
import com.youneko.rate.data.musicbrainz.CoverArtApi
import com.youneko.rate.data.musicbrainz.MusicBrainzRetryInterceptor
import com.youneko.rate.data.musicbrainz.TokenBucket
import com.youneko.rate.data.musicbrainz.TokenBucketInterceptor
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import coil.ImageLoader
import coil.request.CachePolicy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    private const val USER_AGENT = "YounekoRate/1.0.0 (youneko-rate@users.noreply.github.com)"

    @Provides
    @Singleton
    fun provideMusicBrainzJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        isLenient = true
    }

    @Provides
    @Singleton
    fun provideMusicBrainzTokenBucket(): TokenBucket = TokenBucket(capacity = 5, refillMillis = 1_000L)

    @Provides
    @Singleton
    fun provideMusicBrainzClient(bucket: TokenBucket): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
            }
            .addInterceptor(TokenBucketInterceptor(bucket))
            .addInterceptor(MusicBrainzRetryInterceptor())
        if (BuildConfig.DEBUG) {
            builder.addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
        }
        return builder.build()
    }

    @Provides
    @Singleton
    fun provideMusicBrainzApi(client: OkHttpClient, json: Json): MusicBrainzApi = Retrofit.Builder()
        .baseUrl("https://musicbrainz.org/ws/2/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(MusicBrainzApi::class.java)

    @Provides
    @Singleton
    fun provideMusicBrainzReleaseGroupApi(client: OkHttpClient, json: Json): MusicBrainzReleaseGroupApi = Retrofit.Builder()
        .baseUrl("https://musicbrainz.org/ws/2/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(MusicBrainzReleaseGroupApi::class.java)

    @Provides
    @Singleton
    @Named("coverArt")
    fun provideCoverArtClient(@ApplicationContext context: Context): OkHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .cache(Cache(context.cacheDir.resolve("cover-art-http"), 20L * 1024L * 1024L))
        .build()

    @Provides
    @Singleton
    fun provideCoverArtImageLoader(
        @ApplicationContext context: Context,
        @Named("coverArt") client: OkHttpClient,
    ): ImageLoader = ImageLoader.Builder(context)
        .okHttpClient(client)
        .crossfade(true)
        .memoryCachePolicy(CachePolicy.ENABLED)
        .diskCachePolicy(CachePolicy.ENABLED)
        .build()

    @Provides
    @Singleton
    fun provideCoverArtApi(@Named("coverArt") client: OkHttpClient, json: Json): CoverArtApi = Retrofit.Builder()
        .baseUrl("https://coverartarchive.org/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(CoverArtApi::class.java)
}
