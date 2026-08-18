package com.youneko.rate.data.musicbrainz

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import retrofit2.Response

object CoverArtUrls {
    fun releaseGroupFront1200(releaseGroupMbid: String): String =
        "https://coverartarchive.org/release-group/$releaseGroupMbid/front-1200"

    fun releaseFront1200(releaseMbid: String): String =
        "https://coverartarchive.org/release/$releaseMbid/front-1200"

    fun releaseGroupFront250(releaseGroupMbid: String): String =
        "https://coverartarchive.org/release-group/$releaseGroupMbid/front-250"

    fun releaseFront250(releaseMbid: String): String =
        "https://coverartarchive.org/release/$releaseMbid/front-250"

    fun releaseGroupFront500(releaseGroupMbid: String): String =
        "https://coverartarchive.org/release-group/$releaseGroupMbid/front-500"

    fun releaseFront500(releaseMbid: String): String =
        "https://coverartarchive.org/release/$releaseMbid/front-500"

    fun listCandidates(releaseGroupMbid: String?, releaseMbid: String?): List<String> = buildList {
        releaseGroupMbid?.takeIf(String::isNotBlank)?.let {
            add(releaseGroupFront1200(it)); add(releaseGroupFront500(it)); add(releaseGroupFront250(it))
        }
        releaseMbid?.takeIf(String::isNotBlank)?.let {
            add(releaseFront1200(it)); add(releaseFront500(it)); add(releaseFront250(it))
        }
    }
}

sealed interface CoverResult {
    data class Success(val localUri: String) : CoverResult
    data object NotFound : CoverResult
    data class Error(val throwable: Throwable) : CoverResult
}

@Singleton
class CoverArtService @Inject constructor(
    private val api: CoverArtApi,
    @ApplicationContext private val context: Context,
) {
    suspend fun downloadForAlbum(
        albumId: String,
        releaseGroupMbid: String?,
        releaseMbid: String?,
    ): CoverResult = downloadToFile(
        releaseGroupMbid = releaseGroupMbid,
        releaseMbid = releaseMbid,
        fileName = "$albumId.jpg",
    )

    fun promoteToAlbumFile(localUri: String, albumId: String): String? {
        val source = Uri.parse(localUri).path?.let(::File) ?: return null
        if (!source.exists()) return null
        val destination = File(context.filesDir, "covers/$albumId.jpg")
        return runCatching {
            source.copyTo(destination, overwrite = true)
            if (source != destination) source.delete()
            destination.toURI().toString()
        }.getOrNull()
    }

    suspend fun downloadToFile(
        releaseGroupMbid: String?,
        releaseMbid: String?,
        fileName: String,
    ): CoverResult = withContext(Dispatchers.IO) {
        val requests = buildList<suspend () -> Response<ResponseBody>> {
                releaseGroupMbid?.takeIf(String::isNotBlank)?.let { mbid ->
                add { api.groupFront1200(mbid) }
                add { api.groupFront500(mbid) }
                add { api.groupFront250(mbid) }
            }
            releaseMbid?.takeIf(String::isNotBlank)?.let { mbid ->
                add { api.front1200(mbid) }
                add { api.front500(mbid) }
                add { api.front250(mbid) }
            }
        }
        if (requests.isEmpty()) return@withContext CoverResult.NotFound
        var sawNotFound = false
        var lastError: Throwable? = null
        val directory = File(context.filesDir, "covers").apply { mkdirs() }
        val destination = File(directory, fileName)
        for (request in requests) {
            val response = try {
                request()
            } catch (error: Throwable) {
                lastError = error
                continue
            }
            if (response.code() == 404) {
                sawNotFound = true
                continue
            }
            if (!response.isSuccessful || response.body() == null) continue
            return@withContext runCatching {
                val bytes = response.body()!!.bytes()
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: error("Cover Art Archive image could not be decoded")
                try {
                    destination.outputStream().use { output ->
                        check(bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, output)) { "Could not encode cover JPEG" }
                    }
                } finally {
                    bitmap.recycle()
                }
                CoverResult.Success(destination.toURI().toString())
            }.getOrElse { CoverResult.Error(it) }
        }
        if (sawNotFound && lastError == null) CoverResult.NotFound
        else CoverResult.Error(lastError ?: IllegalStateException("Cover Art Archive returned no image"))
    }
}
