package com.youneko.rate.data.musicbrainz

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Response
import okhttp3.ResponseBody

object CoverArtUrls {
    fun releaseGroupFront1200(releaseGroupMbid: String): String =
        "https://coverartarchive.org/release-group/$releaseGroupMbid/front-1200"
    fun releaseGroupFront500(releaseGroupMbid: String): String =
        "https://coverartarchive.org/release-group/$releaseGroupMbid/front-500"
    fun releaseGroupFront250(releaseGroupMbid: String): String =
        "https://coverartarchive.org/release-group/$releaseGroupMbid/front-250"
    fun releaseFront1200(releaseMbid: String): String =
        "https://coverartarchive.org/release/$releaseMbid/front-1200"
    fun releaseFront500(releaseMbid: String): String =
        "https://coverartarchive.org/release/$releaseMbid/front-500"
    fun releaseFront250(releaseMbid: String): String =
        "https://coverartarchive.org/release/$releaseMbid/front-250"

    fun listCandidates(releaseGroupMbid: String?, releaseMbid: String?): List<String> = buildList {
        releaseGroupMbid?.takeIf(String::isNotBlank)?.let {
            add(releaseGroupFront1200(it)); add(releaseGroupFront500(it)); add(releaseGroupFront250(it))
        }
        releaseMbid?.takeIf(String::isNotBlank)?.let {
            add(releaseFront1200(it)); add(releaseFront500(it)); add(releaseFront250(it))
        }
    }
}

data class CoverCandidate(val url: String, val sourceProvider: String)
data class CoverSelection(val localUri: String, val sourceProvider: String, val width: Int)

sealed interface CoverResult {
    data class Success(
        val localUri: String,
        val sourceProvider: String = "cover_art_archive",
        val width: Int? = null,
    ) : CoverResult
    data object NotFound : CoverResult
    data class Error(val throwable: Throwable) : CoverResult
}

@Singleton
class CoverArtService @Inject constructor(
    private val itunesApi: ItunesCoverApi,
    private val deezerApi: DeezerCoverApi,
    @Named("coverArt") private val httpClient: OkHttpClient,
    @ApplicationContext private val context: Context,
) {
    private var legacyCoverArtApi: CoverArtApi? = null

    constructor(api: CoverArtApi, context: Context) : this(
        itunesApi = EmptyItunesApi,
        deezerApi = EmptyDeezerApi,
        httpClient = OkHttpClient.Builder().followRedirects(true).followSslRedirects(true).build(),
        context = context,
    ) {
        legacyCoverArtApi = api
    }

    suspend fun downloadForAlbum(
        albumId: String,
        releaseGroupMbid: String?,
        releaseMbid: String?,
        albumTitle: String? = null,
        artistName: String? = null,
        embeddedPath: String? = null,
    ): CoverResult = downloadMultiSourceToFile(
        albumId, albumTitle, artistName, releaseGroupMbid, releaseMbid, embeddedPath,
    )

    suspend fun searchCandidates(albumTitle: String, artistName: String): List<CoverCandidate> =
        discoverCandidates(albumTitle, artistName, null, null)

    suspend fun cacheCandidate(candidate: CoverCandidate, fileName: String): CoverResult = withContext(Dispatchers.IO) {
        val downloaded = downloadCandidate(candidate) ?: return@withContext CoverResult.NotFound
        if (downloaded.width < MIN_COVER_WIDTH) {
            downloaded.bitmap.recycle()
            return@withContext CoverResult.NotFound
        }
        saveBitmap(downloaded, fileName)
    }

    suspend fun downloadMultiSourceToFile(
        albumId: String,
        albumTitle: String?,
        artistName: String?,
        releaseGroupMbid: String?,
        releaseMbid: String?,
        embeddedPath: String? = null,
    ): CoverResult = withContext(Dispatchers.IO) {
        val candidates = discoverCandidates(albumTitle, artistName, releaseGroupMbid, releaseMbid)
        val embedded = embeddedPath?.let { path ->
            File(path).takeIf { it.isFile }?.let { CoverCandidate("file://$path", "file_tags") }
        }
        val downloaded = coroutineScope {
            (embedded?.let { listOf(async { downloadCandidate(it) }) } ?: emptyList()) +
                candidates.map { async { downloadCandidate(it) } }
        }.awaitAll().filterNotNull()
        val best = downloaded.filter { it.width >= MIN_COVER_WIDTH }.maxByOrNull { it.width }
            ?: return@withContext CoverResult.NotFound
        saveBitmap(best, "$albumId.jpg")
    }

    suspend fun downloadToFile(
        releaseGroupMbid: String?,
        releaseMbid: String?,
        fileName: String,
    ): CoverResult = withContext(Dispatchers.IO) {
        legacyCoverArtApi?.let { return@withContext downloadLegacyToFile(it, releaseGroupMbid, releaseMbid, fileName) }
        val candidates = CoverArtUrls.listCandidates(releaseGroupMbid, releaseMbid)
            .map { CoverCandidate(it, "cover_art_archive") }
        val downloaded = coroutineScope { candidates.map { async { downloadCandidate(it) } }.awaitAll().filterNotNull() }
        val best = downloaded.maxByOrNull { it.width } ?: return@withContext CoverResult.NotFound
        saveBitmap(best, fileName)
    }

    fun promoteToAlbumFile(localUri: String, albumId: String): String? {
        val source = Uri.parse(localUri).path?.let(::File) ?: return null
        if (!source.exists()) return null
        val destination = File(context.filesDir, "covers/$albumId.jpg")
        destination.parentFile?.mkdirs()
        return runCatching {
            if (source.absolutePath != destination.absolutePath) source.copyTo(destination, overwrite = true)
            if (source.absolutePath != destination.absolutePath) source.delete()
            destination.toURI().toString()
        }.getOrNull()
    }

    private suspend fun discoverCandidates(
        albumTitle: String?,
        artistName: String?,
        releaseGroupMbid: String?,
        releaseMbid: String?,
    ): List<CoverCandidate> = coroutineScope {
        val query = listOfNotNull(albumTitle?.trim(), artistName?.trim())
            .filter { it.isNotEmpty() }.joinToString(" ")
        val remote = if (query.isBlank()) emptyList() else listOf(
            async {
                runCatching {
                    val term = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
                    itunesApi.searchAlbums(term).results.mapNotNull { result ->
                        result.artworkUrl100?.let { url ->
                            CoverCandidate(url.replace("100x100bb", "1200x1200bb"), "itunes")
                        }
                    }
                }.getOrDefault(emptyList())
            },
            async {
                runCatching {
                    deezerApi.searchAlbums(query).data.mapNotNull { result ->
                        result.coverXl?.let { url -> CoverCandidate(upscaleDeezer(url), "deezer") }
                    }
                }.getOrDefault(emptyList())
            },
        ).awaitAll().flatten()
        remote + CoverArtUrls.listCandidates(releaseGroupMbid, releaseMbid)
            .map { CoverCandidate(it, "cover_art_archive") }
    }

    private fun upscaleDeezer(url: String): String = url.replace("1000x1000", "1400x1400")

    private fun downloadCandidate(candidate: CoverCandidate): DownloadedCover? {
        val bytes = if (candidate.sourceProvider == "file_tags") {
            candidate.url.removePrefix("file://").let { File(it).takeIf(File::isFile)?.readBytes() }
        } else {
            val request = Request.Builder().url(candidate.url).get().build()
            runCatching {
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) null else response.body?.bytes()
                }
            }.getOrNull()
        } ?: return null
        return decodeBytes(bytes, candidate.sourceProvider)
    }

    private fun decodeBytes(bytes: ByteArray, sourceProvider: String): DownloadedCover? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth < MIN_COVER_WIDTH || bounds.outHeight < MIN_COVER_WIDTH) return null
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        return DownloadedCover(bitmap, maxOf(bounds.outWidth, bounds.outHeight), sourceProvider)
    }

    private suspend fun downloadLegacyToFile(
        api: CoverArtApi,
        releaseGroupMbid: String?,
        releaseMbid: String?,
        fileName: String,
    ): CoverResult {
        val requests = buildList<suspend () -> Response<ResponseBody>> {
            releaseGroupMbid?.takeIf(String::isNotBlank)?.let { mbid ->
                add { api.groupFront1200(mbid) }; add { api.groupFront500(mbid) }; add { api.groupFront250(mbid) }
            }
            releaseMbid?.takeIf(String::isNotBlank)?.let { mbid ->
                add { api.front1200(mbid) }; add { api.front500(mbid) }; add { api.front250(mbid) }
            }
        }
        var sawNotFound = false
        for (request in requests) {
            val response = runCatching { request() }.getOrNull() ?: continue
            if (response.code() == 404) { sawNotFound = true; continue }
            if (!response.isSuccessful) continue
            val bytes = response.body()?.bytes() ?: continue
            val candidate = decodeBytes(bytes, "cover_art_archive") ?: continue
            return saveBitmap(candidate, fileName)
        }
        return if (sawNotFound) CoverResult.NotFound else CoverResult.NotFound
    }

    private fun saveBitmap(candidate: DownloadedCover, fileName: String): CoverResult {
        val destination = File(File(context.filesDir, "covers").apply { mkdirs() }, fileName)
        return runCatching {
            destination.outputStream().use { output ->
                check(candidate.bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)) { "Could not encode cover JPEG" }
            }
            candidate.bitmap.recycle()
            CoverResult.Success(destination.toURI().toString(), candidate.sourceProvider, candidate.width)
        }.getOrElse {
            candidate.bitmap.recycle()
            CoverResult.Error(it)
        }
    }

    private object EmptyItunesApi : ItunesCoverApi {
        override suspend fun searchAlbums(term: String, entity: String, limit: Int) = ItunesSearchResponse()
    }

    private object EmptyDeezerApi : DeezerCoverApi {
        override suspend fun searchAlbums(query: String, limit: Int) = DeezerSearchResponse()
    }

    private data class DownloadedCover(val bitmap: Bitmap, val width: Int, val sourceProvider: String)

    private companion object { const val MIN_COVER_WIDTH = 500 }
}
