package com.youneko.rate.data.scan

import com.youneko.rate.data.MediaScanCheckpoint

object MediaScanPolicy {
    const val PROVIDER_VERSION = "media-scan-v1"

    fun shouldSkip(forceFull: Boolean, checkpoint: MediaScanCheckpoint, generation: Long?): Boolean =
        !forceFull && generation != null && checkpoint.providerVersion == PROVIDER_VERSION && generation == checkpoint.lastGeneration

    fun requiresFull(forceFull: Boolean, checkpoint: MediaScanCheckpoint, generation: Long?): Boolean =
        forceFull || checkpoint.providerVersion != PROVIDER_VERSION || checkpoint.lastScanTimeMs <= 0L || generation == null

    fun changedAfter(checkpoint: MediaScanCheckpoint, forceFull: Boolean, generation: Long?): Long? =
        if (shouldSkip(forceFull, checkpoint, generation) || requiresFull(forceFull, checkpoint, generation)) null else checkpoint.lastScanTimeMs
}
