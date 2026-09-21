package com.tripex.pose.domain.tiles

/**
 * Ensures `boundaries.pmtiles` is available under the app files directory
 * for MapLibre byte-range reads (`pmtiles://file://…`).
 */
interface PmTilesBootstrap {
    /**
     * Copy from assets when missing or when [boundariesVersion] differs from the stored one.
     * @return absolute path of the ready file.
     */
    suspend fun ensureReady(): Result<String>
}
