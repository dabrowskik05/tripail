package com.tripex.pose.domain.geo.atlas

/**
 * Provides the `pmtiles://file://…` URI for MapLibre boundary tiles.
 * Sole place that constructs this URI — do not assemble paths in `:ui`.
 */
interface BoundaryTilesProvider {
    /**
     * Absolute MapLibre source URI, or empty string when the local bundle is not ready.
     */
    fun styleSourceUri(): String

    /** Absolute filesystem path of the copied PMTiles file, or null if not ready. */
    fun localFilePath(): String?
}
