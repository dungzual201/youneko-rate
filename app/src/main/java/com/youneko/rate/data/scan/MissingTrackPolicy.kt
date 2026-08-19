package com.youneko.rate.data.scan

import com.youneko.rate.data.local.entity.TrackEntity

object MissingTrackPolicy {
    fun markMissing(track: TrackEntity, now: Long): TrackEntity? =
        if (track.isMissing) null else track.copy(isMissing = true, missingSince = now, updatedAt = now)

    fun markPresent(track: TrackEntity, now: Long): TrackEntity =
        track.copy(isMissing = false, missingSince = null, updatedAt = now)
}
