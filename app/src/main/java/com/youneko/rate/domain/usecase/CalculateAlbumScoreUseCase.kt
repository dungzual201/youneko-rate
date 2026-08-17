package com.youneko.rate.domain.usecase

import java.math.BigDecimal
import java.math.RoundingMode

enum class ScoreMode {
    SIMPLE,
    WEIGHTED_BY_DURATION,
}

data class TrackScoreInput(
    val stars: Double?,
    val durationMs: Long?,
)

data class AlbumScoreResult(
    val average: Double,
    val displayAverage: String,
    val ratedCount: Int,
    val totalCount: Int,
    val usedEqualWeightsFallback: Boolean,
    val manualOverride: Double?,
) {
    val effectiveScore: Double
        get() = manualOverride ?: average
}

class CalculateAlbumScoreUseCase {
    operator fun invoke(
        tracks: List<TrackScoreInput>,
        mode: ScoreMode = ScoreMode.SIMPLE,
        manualScoreOverride: Double? = null,
    ): AlbumScoreResult? {
        val rated = tracks.filter { it.stars != null }
        if (rated.isEmpty()) return null

        val missingDuration = mode == ScoreMode.WEIGHTED_BY_DURATION && rated.any {
            it.durationMs == null || it.durationMs <= 0L
        }
        val average = if (mode == ScoreMode.WEIGHTED_BY_DURATION && !missingDuration) {
            val totalDuration = rated.sumOf { it.durationMs!! }
            rated.sumOf { (it.stars!! * it.durationMs!!).toBigDecimal() }
                .toDouble() / totalDuration.toDouble()
        } else {
            rated.sumOf { it.stars!! } / rated.size.toDouble()
        }
        val rounded = BigDecimal.valueOf(average).setScale(2, RoundingMode.HALF_UP).toDouble()
        return AlbumScoreResult(
            average = rounded,
            displayAverage = "%.2f".format(java.util.Locale.US, rounded),
            ratedCount = rated.size,
            totalCount = tracks.size,
            usedEqualWeightsFallback = missingDuration,
            manualOverride = manualScoreOverride,
        )
    }
}
