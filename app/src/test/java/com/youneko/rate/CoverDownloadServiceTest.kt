package com.youneko.rate

import androidx.test.core.app.ApplicationProvider
import com.youneko.rate.YounekoRateApplication
import com.youneko.rate.data.artwork.CoverDownloadResult
import com.youneko.rate.data.artwork.CoverDownloadService
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okio.Buffer
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = YounekoRateApplication::class, sdk = [35])
class CoverDownloadServiceTest {
    @Test
    fun validImagePreservesOriginalAndWrites1500pxCover() {
        MockWebServer().use { server ->
            val bytes = resourceBytes("cover-large.png")
            server.enqueue(MockResponse().setBody(Buffer().write(bytes)))
            val service = CoverDownloadService(ApplicationProvider.getApplicationContext(), OkHttpClient())
            val result = kotlinx.coroutines.runBlocking {
                service.download("download-test", server.url("/cover.jpg").toString(), "musichoarders:applemusic")
            }
            assertTrue(result is CoverDownloadResult.Success)
            val cover = (result as CoverDownloadResult.Success).cover
            assertEquals(1200, cover.width)
            assertEquals(800, cover.height)
            assertTrue(cover.originalFile.isFile)
            assertTrue(cover.thumbnailFile.isFile)
            val thumbnail = BitmapFactoryCompat.decode(cover.thumbnailFile)
            assertTrue(maxOf(thumbnail.first, thumbnail.second) <= 1500)
            cover.originalFile.delete()
            cover.thumbnailFile.delete()
        }
    }

    @Test
    fun directHotlinkBlockIsReportedAsActionableFailure() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(403).setBody("hotlink blocked"))
            val service = CoverDownloadService(ApplicationProvider.getApplicationContext(), OkHttpClient())
            val result = kotlinx.coroutines.runBlocking {
                service.download("blocked-test", server.url("/cover.jpg").toString(), "discogs")
            }
            assertEquals(CoverDownloadResult.Failure(com.youneko.rate.data.artwork.FailureReason.HOTLINK_BLOCKED), result)
        }
    }

    @Test
    fun imageSmallerThan300IsRejected() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(Buffer().write(resourceBytes("cover-small.png"))))
            val service = CoverDownloadService(ApplicationProvider.getApplicationContext(), OkHttpClient())
            val result = kotlinx.coroutines.runBlocking {
                service.download("small-test", server.url("/cover.jpg").toString(), "musichoarders:spotify")
            }
            assertEquals(CoverDownloadResult.Failure(com.youneko.rate.data.artwork.FailureReason.TOO_SMALL), result)
        }
    }

    private fun resourceBytes(name: String): ByteArray = checkNotNull(javaClass.classLoader?.getResourceAsStream(name)).readBytes()
}

private object BitmapFactoryCompat {
    fun decode(file: java.io.File): Pair<Int, Int> {
        val image = javax.imageio.ImageIO.read(file)
        return image.width to image.height
    }
}
