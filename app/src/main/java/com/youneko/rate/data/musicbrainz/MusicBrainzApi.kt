package com.youneko.rate.data.musicbrainz

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface MusicBrainzApi {
    @GET("{entity}")
    suspend fun search(
        @Path("entity") entity: String,
        @Query("query") query: String,
        @Query("fmt") format: String = "json",
        @Query("limit") limit: Int = 25,
        @Query("offset") offset: Int = 0,
    ): MbSearchResponse

    @GET("release/{mbid}")
    suspend fun lookupRelease(
        @Path("mbid") mbid: String,
        @Query("inc") includes: String = "artist-credits+labels+recordings+release-groups+media",
        @Query("fmt") format: String = "json",
    ): MbRelease
}
