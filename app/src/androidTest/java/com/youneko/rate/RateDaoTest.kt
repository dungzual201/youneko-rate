package com.youneko.rate

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.youneko.rate.data.AlbumDraft
import com.youneko.rate.data.RateRepository
import com.youneko.rate.data.TrackDraft
import com.youneko.rate.data.local.YounekoDatabase
import com.youneko.rate.data.local.entity.AlbumEntity
import com.youneko.rate.data.local.entity.ArtistEntity
import com.youneko.rate.data.local.entity.LibrarySearchFtsEntity
import com.youneko.rate.data.local.entity.TrackEntity
import com.youneko.rate.domain.usecase.CalculateAlbumScoreUseCase
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RateDaoTest {
    private lateinit var database: YounekoDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, YounekoDatabase::class.java).build()
    }

    @After
    fun tearDown() { database.close() }

    @Test
    fun ftsSearchFindsAlbumAndReviewText() = runBlocking {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val artist = ArtistEntity(UUID.randomUUID().toString(), "Ado", createdAt = now, updatedAt = now)
        database.artistDao().upsert(artist)
        database.albumDao().upsert(AlbumEntity(id, "Kyougen", artist.id, reviewText = "vocals and energy", createdAt = now, updatedAt = now))
        database.librarySearchFtsDao().upsert(LibrarySearchFtsEntity(id, "album", "Kyougen Ado vocals energy"))
        val result = database.librarySearchFtsDao().search("vocals")
        assertEquals(1, result.size)
        assertEquals(id, result.first().entityId)
    }

    @Test
    fun deletingAlbumCascadesTracks() = runBlocking {
        val now = System.currentTimeMillis()
        val artistId = UUID.randomUUID().toString()
        val albumId = UUID.randomUUID().toString()
        val trackId = UUID.randomUUID().toString()
        database.artistDao().upsert(ArtistEntity(artistId, "Artist", createdAt = now, updatedAt = now))
        database.albumDao().upsert(AlbumEntity(albumId, "Album", artistId, createdAt = now, updatedAt = now))
        database.trackDao().upsert(TrackEntity(trackId, albumId, "Track", trackNumber = 1, createdAt = now, updatedAt = now))
        assertNotNull(database.trackDao().findById(trackId))
        database.albumDao().deleteById(albumId)
        assertTrue(database.trackDao().findById(trackId) == null)
    }

    @Test
    fun repositorySaveQueriesAllTracksAndDeleteEmitsNull() = runBlocking {
        val repository = RateRepository(
            database,
            database.albumDao(),
            database.artistDao(),
            database.trackDao(),
            database.librarySearchFtsDao(),
            CalculateAlbumScoreUseCase(),
        )
        val albumId = repository.saveAlbum(
            AlbumDraft(
                title = "Album",
                artistName = "Artist",
                releaseYear = null,
                albumType = "ALBUM",
                genreTags = emptyList(),
                listenedDate = null,
                coverUri = null,
                tracks = listOf(TrackDraft("Track 1"), TrackDraft("Track 2"), TrackDraft("Track 3")),
            ),
        )

        assertEquals(3, database.trackDao().findForAlbum(albumId).size)
        assertNotNull(database.albumDao().observeById(albumId).first { it != null })

        repository.deleteAlbum(albumId)

        val deleted = withTimeout(1_000) { database.albumDao().observeById(albumId).first { it == null } }
        assertTrue(deleted == null)
        assertTrue(database.trackDao().findForAlbum(albumId).isEmpty())
    }

    @Test
    fun albumInsertQueriesAllTracksAndDeleteEmitsNull() = runBlocking {
        val now = System.currentTimeMillis()
        val artistId = UUID.randomUUID().toString()
        val albumId = UUID.randomUUID().toString()
        val tracks = (1..3).map { number ->
            TrackEntity(
                id = UUID.randomUUID().toString(),
                albumId = albumId,
                title = "Track $number",
                trackNumber = number,
                createdAt = now,
                updatedAt = now,
            )
        }
        database.artistDao().upsert(ArtistEntity(artistId, "Artist", createdAt = now, updatedAt = now))
        database.albumDao().upsert(AlbumEntity(albumId, "Album", artistId, createdAt = now, updatedAt = now))
        database.trackDao().upsertAll(tracks)

        assertEquals(tracks.map { it.id }, database.trackDao().findForAlbum(albumId).map { it.id })
        assertNotNull(database.albumDao().observeById(albumId).first { it != null })

        database.albumDao().deleteById(albumId)

        val deleted = withTimeout(1_000) { database.albumDao().observeById(albumId).first { it == null } }
        assertTrue(deleted == null)
        assertTrue(database.trackDao().findForAlbum(albumId).isEmpty())
    }
}
