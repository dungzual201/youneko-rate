package com.youneko.rate

import com.youneko.rate.data.MediaScanCheckpoint
import com.youneko.rate.data.local.entity.TrackEntity
import com.youneko.rate.data.scan.MediaScanPolicy
import com.youneko.rate.data.scan.MissingTrackPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaScanPolicyTest {
    @Test
    fun unchangedGeneration_skipsWithoutDateModifiedQuery() {
        val checkpoint = MediaScanCheckpoint(100L, 7L, MediaScanPolicy.PROVIDER_VERSION)
        assertTrue(MediaScanPolicy.shouldSkip(false, checkpoint, 7L))
        assertNull(MediaScanPolicy.changedAfter(checkpoint, false, 7L))
    }

    @Test
    fun changedGeneration_queriesAfterLastScanTime() {
        val checkpoint = MediaScanCheckpoint(100L, 7L, MediaScanPolicy.PROVIDER_VERSION)
        assertFalse(MediaScanPolicy.shouldSkip(false, checkpoint, 8L))
        assertEquals(100L, MediaScanPolicy.changedAfter(checkpoint, false, 8L))
    }

    @Test
    fun providerChangeForcesFullScan() {
        val checkpoint = MediaScanCheckpoint(100L, 7L, "old-provider")
        assertTrue(MediaScanPolicy.requiresFull(false, checkpoint, 8L))
        assertNull(MediaScanPolicy.changedAfter(checkpoint, false, 8L))
    }

    @Test
    fun missingPolicyOnlyChangesMissingFields() {
        val original = TrackEntity("id", title = "Bài", stars = 4.0, reviewText = "Nhận xét", isSkip = true, isHighlight = true, createdAt = 1L, updatedAt = 2L)
        val missing = MissingTrackPolicy.markMissing(original, 99L)
        assertEquals(4.0, missing?.stars)
        assertEquals("Nhận xét", missing?.reviewText)
        assertTrue(missing?.isSkip == true)
        assertTrue(missing?.isHighlight == true)
        assertTrue(missing?.isMissing == true)
        assertEquals(99L, missing?.missingSince)
        val present = MissingTrackPolicy.markPresent(missing!!, 100L)
        assertFalse(present.isMissing)
        assertNull(present.missingSince)
    }
}
