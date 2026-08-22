package com.youneko.rate.data.musicbrainz

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

/** Public Cover Art Archive candidate discovered through MusicBrainz release search. */
data class ArchiveCoverCandidate(
    val releaseMbid: String,
    val title: String,
    val artist: String,
    val url: String,
    val widthHint: Int = 1200,
)

@Singleton
class PublicCoverProviders @Inject constructor(
    private val musicBrainzApi: MusicBrainzApi,
    private val coverArtApi: CoverArtApi,
) {
    suspend fun searchCoverArtArchive(artist: String, album: String): List<ArchiveCoverCandidate> = withContext(Dispatchers.IO) {
        val normalizedArtist = artist.trim()
        val normalizedAlbum = album.trim()
        if (normalizedArtist.isBlank() || normalizedAlbum.isBlank()) return@withContext emptyList()
        val query = URLEncoder.encode(
            "artist:\"$normalizedArtist\" AND release:\"$normalizedAlbum\"",
            StandardCharsets.UTF_8.name(),
        )
        val releases = musicBrainzApi.search("release", query = query, limit = 10).releases
        releases.mapNotNull { release ->
            if (release.id.isBlank()) return@mapNotNull null
            val available = coverArtApi.front(release.id).let { response ->
                val result = response.isSuccessful && response.code() != 404
                response.body()?.close()
                if (!result && response.code() != 404) error("Cover Art Archive HTTP ${response.code()}")
                result
            }
            if (!available) return@mapNotNull null
            ArchiveCoverCandidate(
                releaseMbid = release.id,
                title = release.title.ifBlank { normalizedAlbum },
                artist = release.artistCredit.firstOrNull()?.name?.ifBlank { normalizedArtist } ?: normalizedArtist,
                url = "https://coverartarchive.org/release/${release.id}/front",
            )
        }
    }

}
