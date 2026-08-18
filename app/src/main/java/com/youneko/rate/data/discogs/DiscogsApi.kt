package com.youneko.rate.data.discogs

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

interface DiscogsApi {
    @GET("database/search")
    suspend fun searchReleases(
        @Query("artist") artist: String,
        @Query("release_title") title: String,
        @Query("type") type: String = "release",
        @Query("per_page") perPage: Int = 5,
        @Query("page") page: Int = 1,
        @Header("Authorization") authorization: String? = null,
    ): DiscogsSearchResponse

    @GET("releases/{id}")
    suspend fun lookupRelease(
        @Path("id") id: Long,
        @Header("Authorization") authorization: String? = null,
    ): DiscogsRelease
}

@Serializable
data class DiscogsSearchResponse(
    val results: List<DiscogsSearchResult> = emptyList(),
)

@Serializable
data class DiscogsSearchResult(
    val id: Long = 0,
    val title: String = "",
    val year: Int? = null,
    @SerialName("cover_image") val coverImage: String? = null,
    @SerialName("resource_url") val resourceUrl: String? = null,
)

@Serializable
data class DiscogsRelease(
    val id: Long = 0,
    val title: String = "",
    val artists: List<DiscogsNamedCredit> = emptyList(),
    val labels: List<DiscogsNamedCredit> = emptyList(),
    @SerialName("extraartists") val extraArtists: List<DiscogsExtraArtist> = emptyList(),
    val images: List<DiscogsImage> = emptyList(),
)

@Serializable
data class DiscogsNamedCredit(
    val name: String = "",
    @SerialName("resource_url") val resourceUrl: String? = null,
)

@Serializable
data class DiscogsExtraArtist(
    val name: String = "",
    val role: String = "",
    @SerialName("resource_url") val resourceUrl: String? = null,
)

@Serializable
data class DiscogsImage(
    val type: String? = null,
    @SerialName("resource_url") val resourceUrl: String? = null,
    val uri: String? = null,
    @SerialName("uri150") val uri150: String? = null,
)
