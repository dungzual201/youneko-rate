package com.youneko.rate

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import com.youneko.rate.data.artwork.ArtworkStore
import com.youneko.rate.data.importer.AudioTag
import com.youneko.rate.data.importer.ImportGrouping
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ArtworkAndScanSpeedTest {
    @Test
    fun artworkCache_resizesAndUsesExpectedPath() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val bitmap = Bitmap.createBitmap(1600, 1200, Bitmap.Config.ARGB_8888)
        val cached = ArtworkStore(context).persistBitmap("album-art-test", bitmap, "embedded")
        assertTrue(cached != null)
        assertTrue(cached!!.path.endsWith("files/covers/album-art-test.jpg"))
        assertEquals("embedded", cached.source)
        val decoded = BitmapFactory.decodeFile(cached.path)
        assertTrue(decoded != null)
        assertTrue(maxOf(decoded.width, decoded.height) <= 1000)
    }

    @Test
    fun importGrouping_usesRequestedUnknownArtistFallback() {
        val group = ImportGrouping.group(
            listOf(
                AudioTag(
                    uri = "content://audio/1",
                    fileName = "one.mp3",
                    artist = null,
                    albumArtist = null,
                    album = "Album",
                    title = "One",
                    trackNumber = 1,
                    discNumber = 1,
                    year = null,
                    genre = null,
                    durationMs = 1_000L,
                ),
            ),
        ).single()
        assertEquals("Không rõ nghệ sĩ", group.artist)
    }
}
