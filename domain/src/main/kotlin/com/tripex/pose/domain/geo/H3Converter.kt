package com.tripex.pose.domain.geo

/**
 * All hexagonal math for Tripail. Stateless, thread-safe, Android-free.
 * Implemented in `:data` as [com.tripex.pose.data.geo.H3Utils].
 *
 * Resolution and ring constants live in [H3Config] — the single public API.
 */
interface H3Converter {
    /** Resolution used for durable unlock storage. */
    val baseResolution: Int

    /** Lat/Lng → single cell at [baseResolution]. */
    fun cellAt(
        lat: Double,
        lng: Double,
    ): Long

    /** Geographic center of a cell as (latitude, longitude). */
    fun cellCenter(cell: Long): Pair<Double, Double>

    /**
     * Area revealed from one GPS fix: disk of radius [k] cells.
     * `k = 1` → 7 cells (6 on a pentagon).
     */
    fun revealDisk(
        lat: Double,
        lng: Double,
        k: Int = H3Config.DEFAULT_RING,
    ): Set<Long>

    /**
     * Manual discovery disk covering roughly [radiusMeters] around a point.
     * Ring size is derived from [H3Config.APPROX_NEIGHBOR_DISTANCE_M] at walking resolution.
     */
    fun revealAround(
        lat: Double,
        lng: Double,
        radiusMeters: Double,
    ): Set<Long>

    /**
     * Cells filling the gap between two consecutive fixes.
     * Empty when distance exceeds [H3Config.MAX_BRIDGE_CELLS] (anti-teleport).
     */
    fun bridge(
        from: Long,
        to: Long,
    ): Set<Long>

    /** Parent cell at a coarser [resolution] — used for LOD. */
    fun parentOf(
        cell: Long,
        resolution: Int,
    ): Long

    /** Grid distance in cells; `-1` when not computable. */
    fun gridDistance(
        from: Long,
        to: Long,
    ): Int

    /** Cell outlines as rings `[lng, lat]` ready for GeoJSON. */
    fun outline(cells: Collection<Long>): FogGeometry

    /** Cells covering a viewport rectangle at [resolution]. */
    fun cellsForBounds(
        bounds: GeoBounds,
        resolution: Int,
    ): Set<Long>

    /** Debug/logging: hex string form of an index. */
    fun toDebugString(cell: Long): String
}
