package com.youneko.rate

import com.youneko.rate.domain.usecase.CalculateAlbumScoreUseCase
import com.youneko.rate.domain.usecase.ScoreMode
import com.youneko.rate.domain.usecase.TrackScoreInput
import com.youneko.rate.domain.usecase.RatingScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculateAlbumScoreUseCaseTest {
    private val calculate = CalculateAlbumScoreUseCase()

    @Test fun noRatedTracksReturnsNullNotZero() {
        assertNull(calculate(listOf(TrackScoreInput(null, 180_000))))
    }

    @Test fun partialRatingIgnoresUnratedTracks() {
        val result = calculate(listOf(TrackScoreInput(5.0, null), TrackScoreInput(null, null)))
        assertEquals(5.0, result?.average ?: 0.0, 0.0)
        assertEquals(1, result?.ratedCount)
        assertEquals(2, result?.totalCount)
    }

    @Test fun allTracksRatedUsesSimpleAverage() {
        val result = calculate(listOf(TrackScoreInput(4.0, null), TrackScoreInput(5.0, null)))
        assertEquals(4.5, result?.average ?: 0.0, 0.0)
    }

    @Test fun halfStarIsSupported() {
        val result = calculate(listOf(TrackScoreInput(0.5, null)))
        assertEquals(0.5, result?.average ?: 0.0, 0.0)
    }

    @Test fun weightedAverageDiffersFromSimpleAverage() {
        val tracks = listOf(TrackScoreInput(5.0, 300_000), TrackScoreInput(1.0, 60_000))
        val result = calculate(tracks, ScoreMode.WEIGHTED_BY_DURATION)
        assertEquals(4.33, result?.average ?: 0.0, 0.0)
        assertFalse(result?.usedEqualWeightsFallback == true)
    }

    @Test fun missingDurationFallsBackToEqualWeights() {
        val result = calculate(
            listOf(TrackScoreInput(5.0, 300_000), TrackScoreInput(1.0, null)),
            ScoreMode.WEIGHTED_BY_DURATION,
        )
        assertEquals(3.0, result?.average ?: 0.0, 0.0)
        assertTrue(result?.usedEqualWeightsFallback == true)
    }

    @Test fun manualOverrideIsExposedWithoutChangingAverage() {
        val result = calculate(listOf(TrackScoreInput(4.0, null), TrackScoreInput(5.0, null)), manualScoreOverride = 4.5)
        assertEquals(4.5, result?.average ?: 0.0, 0.0)
        assertEquals(4.5, result?.manualOverride ?: 0.0, 0.0)
        assertEquals(4.5, result?.effectiveScore ?: 0.0, 0.0)
    }

    @Test fun averageRoundsHalfUpToTwoDecimals() {
        val result = calculate(listOf(TrackScoreInput(4.0, null), TrackScoreInput(4.25, null)))
        assertEquals(4.13, result?.average ?: 0.0, 0.0)
        assertEquals("4.13", result?.displayAverage)
    }

    @Test fun ratingScaleConvertsLegacyFiveStarsWithoutDataLoss() {
        assertEquals(10.0, RatingScale.TEN_POINT.fromStars(5.0) ?: 0.0, 0.0)
        assertEquals(100.0, RatingScale.HUNDRED_POINT.fromStars(5.0) ?: 0.0, 0.0)
        assertEquals(3.5, RatingScale.TEN_POINT.toStars(7.0) ?: 0.0, 0.0)
        assertEquals(3.5, RatingScale.HUNDRED_POINT.toStars(70.0) ?: 0.0, 0.0)
    }

    @Test fun oneTrackAlbumWorks() {
        val result = calculate(listOf(TrackScoreInput(3.5, 200_000)))
        assertEquals(3.5, result?.average ?: 0.0, 0.0)
        assertEquals(1, result?.ratedCount)
    }
}
