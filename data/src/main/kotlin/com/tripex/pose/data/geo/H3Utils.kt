package com.tripex.pose.data.geo

import com.tripex.pose.domain.geo.FogGeometry
import com.tripex.pose.domain.geo.GeoBounds
import com.tripex.pose.domain.geo.H3Config
import com.tripex.pose.domain.geo.H3Converter
import com.tripex.pose.domain.geo.atlas.Ring
import com.uber.h3core.H3Core
import com.uber.h3core.util.LatLng
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sole `:data` type allowed to import `com.uber.h3core`.
 */
@Singleton
internal class H3Utils @Inject constructor(
    private val h3: H3Core,
) : H3Converter {

    override val baseResolution: Int = H3Config.WALKING_RESOLUTION

    override suspend fun warmUp() {
        // Touches the JNI boundary: throws UnsatisfiedLinkError here rather than mid-walk.
        h3.latLngToCell(0.0, 0.0, baseResolution)
    }

    override fun cellAt(lat: Double, lng: Double): Long =
        h3.latLngToCell(lat, lng, baseResolution)

    override fun cellCenter(cell: Long): Pair<Double, Double> {
        val latLng = h3.cellToLatLng(cell)
        return latLng.lat to latLng.lng
    }

    override fun revealDisk(lat: Double, lng: Double, k: Int): Set<Long> {
        require(k in 0..3) { "k out of sane range: $k" }
        return h3.gridDisk(cellAt(lat, lng), k).toSet()
    }

    override fun revealAround(lat: Double, lng: Double, radiusMeters: Double): Set<Long> {
        require(radiusMeters > 0.0) { "radiusMeters must be > 0" }
        val k = kotlin.math.ceil(radiusMeters / H3Config.APPROX_NEIGHBOR_DISTANCE_M)
            .toInt()
            .coerceIn(0, H3Config.MAX_MANUAL_RING)
        return h3.gridDisk(cellAt(lat, lng), k).toSet()
    }

    override fun bridge(from: Long, to: Long): Set<Long> {
        if (from == to) return emptySet()
        val distance = gridDistance(from, to)
        if (distance < 0 || distance > H3Config.MAX_BRIDGE_CELLS) return emptySet()
        return runCatching { h3.gridPathCells(from, to).toSet() }
            .getOrDefault(emptySet())
    }

    override fun gridDistance(from: Long, to: Long): Int =
        runCatching { h3.gridDistance(from, to).toInt() }.getOrDefault(-1)

    override fun parentOf(cell: Long, resolution: Int): Long =
        h3.cellToParent(cell, resolution)

    override fun outline(cells: Collection<Long>): FogGeometry {
        if (cells.isEmpty()) return FogGeometry.EMPTY
        val polygons: List<List<List<LatLng>>> =
            h3.cellsToMultiPolygon(cells.toSet(), /* geoJson = */ true)
        return FogGeometry(
            polygons.map { polygon ->
                polygon.map { ring ->
                    ring.map { vertex -> vertex.lng to vertex.lat }
                }
            },
        )
    }

    override fun cellsForPolygon(rings: List<Ring>, resolution: Int): Set<Long> {
        if (rings.isEmpty()) return emptySet()
        val exterior = rings.first().toLatLngRing()
        if (exterior.size < MIN_RING_POINTS) return emptySet()
        val holes = rings.drop(1)
            .map { it.toLatLngRing() }
            .filter { it.size >= MIN_RING_POINTS }
        return runCatching { h3.polygonToCells(exterior, holes, resolution).toSet() }
            .getOrDefault(emptySet())
    }

    /** Ring coordinates are `[lng, lat]` (GeoJSON order); H3 wants `LatLng(lat, lng)`. */
    private fun Ring.toLatLngRing(): List<LatLng> = map { (lng, lat) -> LatLng(lat, lng) }

    override fun cellsForBounds(bounds: GeoBounds, resolution: Int): Set<Long> {
        val ring = listOf(
            LatLng(bounds.south, bounds.west),
            LatLng(bounds.north, bounds.west),
            LatLng(bounds.north, bounds.east),
            LatLng(bounds.south, bounds.east),
            LatLng(bounds.south, bounds.west),
        )
        return h3.polygonToCells(ring, emptyList(), resolution).toSet()
    }

    override fun toDebugString(cell: Long): String = h3.h3ToString(cell)

    private companion object {
        const val MIN_RING_POINTS = 4
    }
}
