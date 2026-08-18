package com.youneko.rate.data.musicbrainz

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface MusicBrainzApi {
    @GET("{entity}")
    suspend fun search(
        @Path("entity") entity: String,
        @Query(value = "query", encoded = true) query: String,
        @Query("fmt") format: String = "json",
        @Query("limit") limit: Int = 25,
        @Query("offset") offset: Int = 0,
    ): MbSearchResponse

    @GET("release/{mbid}")
    suspend fun lookupRelease(
        @Path("mbid") mbid: String,
        @Query("inc") includes: String = "artist-credits+labels+recordings+artist-rels+label-rels+recording-level-rels+work-rels+work-level-rels",
        @Query("fmt") format: String = "json",
    ): MbRelease

    @GET("recording/{mbid}")
    suspend fun lookupRecording(
        @Path("mbid") mbid: String,
        @Query("inc") includes: String = "artist-rels+work-rels",
        @Query("fmt") format: String = "json",
    ): MbRecording

    @GET("work/{mbid}")
    suspend fun lookupWork(
        @Path("mbid") mbid: String,
        @Query("inc") includes: String = "artist-rels",
        @Query("fmt") format: String = "json",
    ): MbWork
}
