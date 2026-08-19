package com.youneko.rate

import com.youneko.rate.data.lyrics.Lyrics
import com.youneko.rate.data.lyrics.LyricsParser
import com.youneko.rate.data.local.entity.TrackEntity
import com.youneko.rate.data.scan.StableMediaKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FeatureMediaScanLyricsTest {
    @Test
    fun ttmlAppleWordSpans_preserveWhitespaceAndTranslation() {
        val lyrics = LyricsParser.parse(
            """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <body><div><p begin="0s" end="2s">Hello <span>world</span><span role="x-translation">Xin chào</span></p></div></body>
            </tt>
            """.trimIndent(),
            "apple.ttml",
        )
        val timed = lyrics as Lyrics.Timed
        assertEquals("Hello world", timed.lines.single().text)
        assertEquals("Xin chào", timed.lines.single().translation)
    }

    @Test
    fun ttmlDuetAgents_areRetained() {
        val lyrics = LyricsParser.parse(
            """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata"><body><div>
              <p begin="1s" ttm:agent="v1">One</p>
              <p begin="2s" ttm:agent="v2">Two</p>
            </div></body></tt>
            """.trimIndent(),
        )
        val timed = lyrics as Lyrics.Timed
        assertEquals(listOf("v1", "v2"), timed.lines.map { it.agent })
        assertEquals(2_000L, timed.lines.first().endMs)
    }

    @Test
    fun ttmlBackgroundRole_isMarked() {
        val lyrics = LyricsParser.parse(
            "<tt xmlns=\"http://www.w3.org/ns/ttml\"><body><div><p begin=\"1s\"><span role=\"x-bg\">ah</span></p></div></body></tt>",
        )
        val line = (lyrics as Lyrics.Timed).lines.single()
        assertTrue(line.isBackground)
        assertEquals("ah", line.text)
    }

    @Test
    fun ttmlOffsetForms_andInvalidTimeFallback() {
        assertEquals(1_500L, LyricsParser.parseTime("1.5s"))
        assertEquals(250L, LyricsParser.parseTime("250ms"))
        assertEquals(65_000L, LyricsParser.parseTime("1:05"))
        assertEquals(3_500L, LyricsParser.parseTime("00:00:03.500"))
        assertEquals(0L, LyricsParser.parseTime("not-a-time"))
    }

    @Test
    fun malformedTtml_fallsBackToPlainWithoutMarkup() {
        val lyrics = LyricsParser.parse("<tt><p>Broken", "broken.ttml")
        val plain = lyrics as Lyrics.Plain
        assertTrue('<' !in plain.text)
        assertTrue('>' !in plain.text)
    }

    @Test
    fun lrc_isSortedAndTimestampSniffWinsOverExtension() {
        val lyrics = LyricsParser.parse("[00:03.00]last\n[00:01.50]first", "wrong.ttml")
        val timed = lyrics as Lyrics.Timed
        assertEquals(listOf("first", "last"), timed.lines.map { it.text })
        assertEquals(1_500L, timed.lines.first().startMs)
    }

    @Test
    fun plainLyrics_areSupported() {
        val plain = LyricsParser.parse("Dòng một\nDòng hai", "song.txt") as Lyrics.Plain
        assertEquals("Dòng một\nDòng hai", plain.text)
    }

    @Test
    fun stableKey_usesSizeDurationAndFirst64kHash() {
        val key = StableMediaKey.from(100L, 12_345L, "abc")
        assertEquals("100|12|abc", key)
        assertEquals(key, StableMediaKey.from(100L, 12_399L, "abc"))
        assertNotNull(StableMediaKey.from(100L, 12_345L, "abc"))
    }

    @Test
    fun missingTrackCopy_preservesRatingReviewAndManualFlags() {
        val track = TrackEntity(
            id = "track-1",
            title = "Bài có dấu",
            stars = 4.5,
            reviewText = "Giữ nguyên nhận xét",
            isSkip = true,
            isHighlight = true,
            createdAt = 1L,
            updatedAt = 2L,
            mediaStoreId = 22L,
            stableKey = "10|3|hash",
        )
        val marked = track.copy(isMissing = true, missingSince = 99L)
        assertEquals(4.5, marked.stars)
        assertEquals("Giữ nguyên nhận xét", marked.reviewText)
        assertTrue(marked.isSkip)
        assertTrue(marked.isHighlight)
        assertEquals("10|3|hash", marked.stableKey)
    }
}
