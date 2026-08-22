package com.youneko.rate.data.musicbrainz

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.youneko.rate.data.discogs.CoverDiscogsProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.math.abs
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
    fun releaseGroupFront1200(releaseGroupMbid: String): String = "https://coverartarchive.org/release-group/$releaseGroupMbid/front-1200"
    fun releaseGroupFront500(releaseGroupMbid: String): String = "https://coverartarchive.org/release-group/$releaseGroupMbid/front-500"
    fun releaseGroupFront250(releaseGroupMbid: String): String = "https://coverartarchive.org/release-group/$releaseGroupMbid/front-250"
    fun releaseFront1200(releaseMbid: String): String = "https://coverartarchive.org/release/$releaseMbid/front-1200"
    fun releaseFront500(releaseMbid: String): String = "https://coverartarchive.org/release/$releaseMbid/front-500"
    fun releaseFront250(releaseMbid: String): String = "https://coverartarchive.org/release/$releaseMbid/front-250"

    fun listCandidates(releaseGroupMbid: String?, releaseMbid: String?): List<String> = buildList {
        releaseGroupMbid?.takeIf(String::isNotBlank)?.let { add(releaseGroupFront1200(it)); add(releaseGroupFront500(it)); add(releaseGroupFront250(it)) }
        releaseMbid?.takeIf(String::isNotBlank)?.let { add(releaseFront1200(it)); add(releaseFront500(it)); add(releaseFront250(it)) }
    }
}

object CoverMatch {
    fun normalize(value: String): String = java.text.Normalizer.normalize(value.lowercase(), java.text.Normalizer.Form.NFD)
        .replace("\\p{M}+".toRegex(), "")
        .replace(Regex("\\(.*?\\)|\\[.*?]"), "")
        .replace(Regex("\\b(feat|ft|with|prod|remastered|version|deluxe)\\b.*"), "")
        .replace(Regex("[^a-z0-9]"), "")
        .trim()

    fun similarity(left: String, right: String): Double {
        val a = normalize(left); val b = normalize(right)
        if (a.isBlank() || b.isBlank()) return 0.0
        if (a == b) return 1.0
        val row = IntArray(b.length + 1) { it }
        for (i in a.indices) {
            var diagonal = row[0]; row[0] = i + 1
            for (j in b.indices) {
                val old = row[j + 1]
                row[j + 1] = minOf(row[j + 1] + 1, row[j] + 1, diagonal + if (a[i] == b[j]) 0 else 1)
                diagonal = old
            }
        }
        return 1.0 - row[b.length].toDouble() / maxOf(a.length, b.length).coerceAtLeast(1)
    }

    fun passes(title: String, artist: String, candidateTitle: String, candidateArtist: String, expectedTracks: Int?, candidateTracks: Int?, expectedYear: Int?, candidateYear: Int?): Boolean {
        if (similarity(title, candidateTitle) < 0.85 || similarity(artist, candidateArtist) < 0.80) return false
        val tracksMatch = expectedTracks != null && candidateTracks != null && kotlin.math.abs(expectedTracks - candidateTracks) <= 1
        val yearMatch = expectedYear != null && candidateYear != null && kotlin.math.abs(expectedYear - candidateYear) <= 1
        return (expectedTracks == null && expectedYear == null) || tracksMatch || yearMatch
    }
}

data class CoverCandidate(
    val url: String,
    val sourceProvider: String,
    val title: String? = null,
    val artistName: String? = null,
    val trackCount: Int? = null,
    val releaseDate: String? = null,
    val matchScore: Double? = null,
    val verified: Boolean = false,
    val widthHint: Int? = null,
)
data class CoverSelection(val localUri: String, val sourceProvider: String, val width: Int)

sealed interface CoverResult {
    data class Success(val localUri: String, val sourceProvider: String = "cover_art_archive", val width: Int? = null) : CoverResult
    data object NotFound : CoverResult
    data class Error(val throwable: Throwable) : CoverResult
}

@Singleton
class CoverArtService @Inject constructor(
    private val itunesApi: ItunesCoverApi,
    private val deezerApi: DeezerCoverApi,
    private val discogsService: CoverDiscogsProvider,
    @Named("coverArt") private val httpClient: OkHttpClient,
    @ApplicationContext private val context: Context,
) {
    private var legacyCoverArtApi: CoverArtApi? = null

    constructor(api: CoverArtApi, context: Context) : this(
        itunesApi = EmptyItunesApi,
        deezerApi = EmptyDeezerApi,
        discogsService = EmptyDiscogsProvider,
        httpClient = OkHttpClient.Builder().followRedirects(true).followSslRedirects(true).build(),
        context = context,
    ) { legacyCoverArtApi = api }

    suspend fun downloadForAlbum(
        albumId: String,
        releaseGroupMbid: String?,
        releaseMbid: String?,
        albumTitle: String? = null,
        artistName: String? = null,
        embeddedPath: String? = null,
        trackCount: Int? = null,
        releaseYear: Int? = null,
    ): CoverResult = downloadMultiSourceToFile(albumId, albumTitle, artistName, releaseGroupMbid, releaseMbid, embeddedPath, trackCount, releaseYear)

    suspend fun searchCandidates(albumTitle: String, artistName: String): List<CoverCandidate> = discoverCandidates(null, albumTitle, artistName, null, null, null, null)

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
        trackCount: Int? = null,
        releaseYear: Int? = null,
    ): CoverResult = withContext(Dispatchers.IO) {
        val candidates = discoverCandidates(albumId, albumTitle, artistName, releaseGroupMbid, releaseMbid, trackCount, releaseYear)
        val embedded = embeddedPath?.let { path -> File(path).takeIf { it.isFile }?.let { CoverCandidate("file://$path", "file_tags", verified = true) } }
        val downloaded = coroutineScope {
            (embedded?.let { listOf(async { downloadCandidate(it) }) } ?: emptyList()) + candidates.map { async { downloadCandidate(it) } }
        }.awaitAll().filterNotNull()
        val best = downloaded.filter { it.width >= MIN_COVER_WIDTH }.maxByOrNull { it.width } ?: return@withContext CoverResult.NotFound
        saveBitmap(best, "$albumId.jpg")
    }

    suspend fun downloadToFile(releaseGroupMbid: String?, releaseMbid: String?, fileName: String): CoverResult = withContext(Dispatchers.IO) {
        legacyCoverArtApi?.let { return@withContext downloadLegacyToFile(it, releaseGroupMbid, releaseMbid, fileName) }
        val candidates = CoverArtUrls.listCandidates(releaseGroupMbid, releaseMbid).map { CoverCandidate(it, "cover_art_archive", verified = true, widthHint = 1200) }
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
        albumId: String?,
        albumTitle: String?,
        artistName: String?,
        releaseGroupMbid: String?,
        releaseMbid: String?,
        trackCount: Int?,
        releaseYear: Int?,
    ): List<CoverCandidate> = coroutineScope {
        val query = listOfNotNull(albumTitle?.trim(), artistName?.trim()).filter { it.isNotEmpty() }.joinToString(" ")
        val remote = if (query.isBlank()) emptyList() else listOf(
            async {
                runCatching {
                    val term = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
                    itunesApi.searchAlbums(term).results.flatMap { result ->
                        val score = matchScore(albumTitle, artistName, result.collectionName, result.artistName)
                        if (!metadataMatches(score, trackCount, result.trackCount, releaseYear, result.releaseDate, allowMissingMetadata = trackCount == null && releaseYear == null)) emptyList()
                        else listOfNotNull(
                            result.artworkUrl100?.let { url -> CoverCandidate(url.replace("100x100bb", "3000x3000bb"), "itunes", result.collectionName, result.artistName, result.trackCount, result.releaseDate, score, score != null, 3000) },
                            result.artworkUrl100?.let { url -> CoverCandidate(url.replace("100x100bb", "1200x1200bb"), "itunes", result.collectionName, result.artistName, result.trackCount, result.releaseDate, score, score != null, 1200) },
                        )
                    }
                }.getOrDefault(emptyList())
            },
            async {
                runCatching {
                    deezerApi.searchAlbums(query).data.flatMap { result ->
                        val score = matchScore(albumTitle, artistName, result.title, result.artist?.name)
                        if (!metadataMatches(score, trackCount, result.nbTracks, releaseYear, result.releaseDate, allowMissingMetadata = trackCount == null && releaseYear == null)) emptyList()
                        else listOfNotNull(
                            result.coverXl?.let { url -> CoverCandidate(upscaleDeezer(url), "deezer", result.title, result.artist?.name, result.nbTracks, result.releaseDate, score, score != null, 1400) },
                            result.coverXl?.let { url -> CoverCandidate(url, "deezer", result.title, result.artist?.name, result.nbTracks, result.releaseDate, score, score != null, 1000) },
                        )
                    }
                }.getOrDefault(emptyList())
            },
            async {
                if (albumId == null) emptyList() else runCatching {
                    discogsService.loadCover(albumId, albumTitle.orEmpty(), artistName.orEmpty()).let { url ->
                        url?.let { listOf(CoverCandidate(it, "discogs", albumTitle, artistName, trackCount, releaseYear?.toString(), 1.0, true, 600)) } ?: emptyList()
                    }
                }.getOrDefault(emptyList())
            },
        ).awaitAll().flatten()
        val archive = CoverArtUrls.listCandidates(releaseGroupMbid, releaseMbid).map { CoverCandidate(it, "cover_art_archive", verified = true, widthHint = 1200, matchScore = 1.0) }
        remote + archive
    }

    private fun metadataMatches(score: Double?, expectedTracks: Int?, candidateTracks: Int?, expectedYear: Int?, releaseDate: String?, allowMissingMetadata: Boolean): Boolean {
        if (score == null) return false
        val trackMatches = expectedTracks != null && candidateTracks != null && abs(expectedTracks - candidateTracks) <= 1
        val year = releaseDate?.take(4)?.toIntOrNull()
        val yearMatches = expectedYear != null && year != null && abs(expectedYear - year) <= 1
        return allowMissingMetadata || trackMatches || yearMatches
    }

    private fun matchScore(expectedTitle: String?, expectedArtist: String?, candidateTitle: String?, candidateArtist: String?): Double? {
        val title = similarity(expectedTitle.orEmpty(), candidateTitle.orEmpty())
        val artist = similarity(expectedArtist.orEmpty(), candidateArtist.orEmpty())
        return (title to artist).takeIf { title >= 0.85 && artist >= 0.80 }?.let { minOf(title, artist) }
    }

    private fun similarity(a: String, b: String): Double = CoverMatch.similarity(a, b)

    private fun normalize(value: String): String = CoverMatch.normalize(value)

    private fun upscaleDeezer(url: String): String = url.replace("1000x1000", "1400x1400")

    private fun downloadCandidate(candidate: CoverCandidate): DownloadedCover? {
        val bytes = if (candidate.sourceProvider == "file_tags") candidate.url.removePrefix("file://").let { File(it).takeIf(File::isFile)?.readBytes() }
        else {
            val request = Request.Builder().url(candidate.url).get().build()
            runCatching { httpClient.newCall(request).execute().use { response -> if (!response.isSuccessful) null else response.body?.bytes() } }.getOrNull()
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

    private suspend fun downloadLegacyToFile(api: CoverArtApi, releaseGroupMbid: String?, releaseMbid: String?, fileName: String): CoverResult {
        val requests = buildList<suspend () -> Response<ResponseBody>> {
            releaseGroupMbid?.takeIf(String::isNotBlank)?.let { add { api.groupFront1200(it) }; add { api.groupFront500(it) }; add { api.groupFront250(it) } }
            releaseMbid?.takeIf(String::isNotBlank)?.let { add { api.front1200(it) }; add { api.front500(it) }; add { api.front250(it) } }
        }
        for (request in requests) {
            val response = runCatching { request() }.getOrNull() ?: continue
            if (!response.isSuccessful) continue
            val bytes = response.body()?.bytes() ?: continue
            val candidate = decodeBytes(bytes, "cover_art_archive") ?: continue
            return saveBitmap(candidate, fileName)
        }
        return CoverResult.NotFound
    }

    private fun saveBitmap(candidate: DownloadedCover, fileName: String): CoverResult {
        val destination = File(File(context.filesDir, "covers").apply { mkdirs() }, fileName)
        return runCatching {
            destination.outputStream().use { output -> check(candidate.bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)) { "Could not encode cover JPEG" } }
            candidate.bitmap.recycle()
            CoverResult.Success(destination.toURI().toString(), candidate.sourceProvider, candidate.width)
        }.getOrElse { candidate.bitmap.recycle(); CoverResult.Error(it) }
    }

    private object EmptyItunesApi : ItunesCoverApi { override suspend fun searchAlbums(term: String, entity: String, limit: Int, country: String) = ItunesSearchResponse() }
    private object EmptyDeezerApi : DeezerCoverApi { override suspend fun searchAlbums(query: String, limit: Int) = DeezerSearchResponse() }
    private object EmptyDiscogsProvider : CoverDiscogsProvider { override suspend fun loadCover(albumId: String, title: String, artist: String): String? = null }
    private data class DownloadedCover(val bitmap: Bitmap, val width: Int, val sourceProvider: String)
    private companion object { const val MIN_COVER_WIDTH = 500 }
}
