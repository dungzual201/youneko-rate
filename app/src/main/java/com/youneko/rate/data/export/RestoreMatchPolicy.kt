package com.youneko.rate.data.export

import com.youneko.rate.data.local.entity.TrackEntity
import java.text.Normalizer

fun trackMatchesForRestore(
    backup: TrackSnapshot,
    candidate: TrackEntity,
    backupArtistName: String?,
    candidateArtistName: String?,
): Boolean {
    if (!backup.stableKey.isNullOrBlank() && backup.stableKey == candidate.stableKey) return true
    if (!backup.recordingMbid.isNullOrBlank() && backup.recordingMbid == candidate.recordingMbid) return true
    return !backupArtistName.isNullOrBlank() &&
        normalizeRestoreText(backup.title) == normalizeRestoreText(candidate.title) &&
        normalizeRestoreText(backupArtistName) == normalizeRestoreText(candidateArtistName.orEmpty()) &&
        backup.durationMs != null && candidate.durationMs != null &&
        kotlin.math.abs(backup.durationMs - candidate.durationMs) <= 2_000L
}

fun restoreMissingState(track: TrackEntity, matched: Boolean, nowMs: Long): TrackEntity =
    if (matched) track.copy(isMissing = false, missingSince = null)
    else track.copy(isMissing = true, missingSince = track.missingSince ?: nowMs)

fun normalizeRestoreText(value: String): String = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
    .replace("\\p{Mn}+".toRegex(), "")
    .lowercase()
    .replace("\\s+".toRegex(), " ")
