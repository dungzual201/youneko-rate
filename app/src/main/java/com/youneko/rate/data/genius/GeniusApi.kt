package com.youneko.rate.data.genius

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

interface GeniusApi {
    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Header("Authorization") authorization: String,
    ): GeniusSearchResponse

    @GET("songs/{id}")
    suspend fun song(
        @Path("id") id: Long,
        @Query("text_format") textFormat: String = "plain",
        @Header("Authorization") authorization: String,
    ): GeniusSongResponse
}

@Serializable
data class GeniusSearchResponse(
    val response: GeniusSearchBody = GeniusSearchBody(),
)

@Serializable
data class GeniusSearchBody(
    val hits: List<GeniusHit> = emptyList(),
)

@Serializable
data class GeniusHit(
    val result: GeniusSearchResult = GeniusSearchResult(),
)

@Serializable
data class GeniusSearchResult(
    val id: Long = 0,
    val title: String = "",
    @SerialName("artist_names") val artistNames: String = "",
    @SerialName("primary_artist") val primaryArtist: GeniusArtist? = null,
    @SerialName("api_path") val apiPath: String? = null,
    @SerialName("url") val url: String? = null,
)

@Serializable
data class GeniusSongResponse(
    val response: GeniusSongBody = GeniusSongBody(),
)

@Serializable
data class GeniusSongBody(
    val song: GeniusSong = GeniusSong(),
)

@Serializable
data class GeniusSong(
    val id: Long = 0,
    val title: String = "",
    @SerialName("producer_artists") val producerArtists: List<GeniusArtist> = emptyList(),
    @SerialName("writer_artists") val writerArtists: List<GeniusArtist> = emptyList(),
    @SerialName("custom_performances") val customPerformances: List<GeniusCustomPerformance> = emptyList(),
    @SerialName("primary_artist") val primaryArtist: GeniusArtist? = null,
    @SerialName("featured_artists") val featuredArtists: List<GeniusArtist> = emptyList(),
    val album: GeniusAlbum? = null,
    @SerialName("release_date_for_display") val releaseDateForDisplay: String? = null,
    @SerialName("url") val url: String? = null,
)

@Serializable
data class GeniusArtist(
    val id: Long = 0,
    val name: String = "",
    @SerialName("header_image_url") val headerImageUrl: String? = null,
)

@Serializable
data class GeniusAlbum(
    val name: String = "",
)

@Serializable
data class GeniusCustomPerformance(
    val label: String = "",
    val artists: List<GeniusArtist> = emptyList(),
)
