package com.youneko.rate.data.musicbrainz

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

interface MusicBrainzReleaseGroupApi {
    @GET("release-group/{mbid}")
    suspend fun lookupReleaseGroup(
        @Path("mbid") mbid: String,
        @Query("inc") includes: String = "artist-credits+releases",
        @Query("fmt") format: String = "json",
    ): MbReleaseGroup
}

interface CoverArtApi {
    @Streaming
    @GET("release-group/{mbid}/front-1200")
    suspend fun groupFront1200(@Path("mbid") mbid: String): Response<ResponseBody>

    @Streaming
    @GET("release-group/{mbid}/front-500")
    suspend fun groupFront500(@Path("mbid") mbid: String): Response<ResponseBody>

    @Streaming
    @GET("release-group/{mbid}/front-250")
    suspend fun groupFront250(@Path("mbid") mbid: String): Response<ResponseBody>

    @Streaming
    @GET("release/{mbid}/front")
    suspend fun front(@Path("mbid") mbid: String): Response<ResponseBody>

    @Streaming
    @GET("release/{mbid}/front-1200")
    suspend fun front1200(@Path("mbid") mbid: String): Response<ResponseBody>

    @Streaming
    @GET("release/{mbid}/front-500")
    suspend fun front500(@Path("mbid") mbid: String): Response<ResponseBody>

    @Streaming
    @GET("release/{mbid}/front-250")
    suspend fun front250(@Path("mbid") mbid: String): Response<ResponseBody>
}
