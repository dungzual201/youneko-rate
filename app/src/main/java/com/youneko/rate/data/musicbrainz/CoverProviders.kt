package com.youneko.rate.data.musicbrainz

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

@Serializable
data class DeezerSearchResponse(
    val data: List<DeezerAlbum> = emptyList(),
)

@Serializable
data class DeezerAlbum(
    val title: String? = null,
    val artist: DeezerArtist? = null,
    val coverXl: String? = null,
    val nbTracks: Int? = null,
    val releaseDate: String? = null,
)

@Serializable
data class DeezerArtist(
    val name: String? = null,
)

interface DeezerCoverApi {
    @GET("search/album")
    suspend fun searchAlbums(
        @Query("q") query: String,
        @Query("limit") limit: Int = 5,
    ): DeezerSearchResponse
}
