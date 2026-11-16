package com.tripex.pose.domain.usecase

import com.tripex.pose.domain.geo.FogGeometry
import com.tripex.pose.domain.geo.H3Converter
import com.tripex.pose.domain.geo.TrailPoint
import com.tripex.pose.domain.geo.TravelMode
import com.tripex.pose.domain.geo.atlas.Ring
import com.tripex.pose.domain.repository.UnlockedAreaRepository
import kotlin.math.roundToLong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * A square 0.001° grid standing in for H3: cell ids encode the grid position, every resolution
 * is the same grid. Enough for the trail logic, which only compares cells.
 */
internal class FakeGridH3 : H3Converter {
    override val baseResolution: Int = 11

    override suspend fun warmUp() = Unit

    override fun cellAt(lat: Double, lng: Double): Long =
        ((lat * STEPS).roundToLong() + OFFSET) * SPAN + ((lng * STEPS).roundToLong() + OFFSET)

    override fun cellCenter(cell: Long): Pair<Double, Double> =
        ((cell / SPAN) - OFFSET).toDouble() / STEPS to ((cell % SPAN) - OFFSET).toDouble() / STEPS

    override fun revealDisk(lat: Double, lng: Double, k: Int): Set<Long> = setOf(cellAt(lat, lng))

    override fun revealAround(lat: Double, lng: Double, radiusMeters: Double): Set<Long> = setOf(cellAt(lat, lng))

    override fun bridge(from: Long, to: Long): Set<Long> = emptySet()

    override fun parentOf(cell: Long, resolution: Int): Long = cell

    override fun gridDistance(from: Long, to: Long): Int = -1

    override fun outline(cells: Collection<Long>): FogGeometry = FogGeometry.EMPTY

    override fun cellsForPolygon(rings: List<Ring>, resolution: Int): Set<Long> = emptySet()

    override fun toDebugString(cell: Long): String = cell.toString()

    private companion object {
        const val STEPS = 1_000.0
        const val OFFSET = 500_000L
        const val SPAN = 1_000_000L
    }
}

/** In-memory trail with the same "known" rule as the DAO: driven-through ground is not walked ground. */
internal class FakeUnlockedAreaRepository(
    initial: List<Pair<Long, TrailPoint>> = emptyList(),
) : UnlockedAreaRepository {

    val stored = MutableStateFlow(initial)

    val points: List<TrailPoint> get() = stored.value.map { it.second }

    override suspend fun addPoint(cell: Long, point: TrailPoint) {
        stored.value = stored.value + (cell to point)
    }

    override suspend fun addPoints(points: List<Pair<Long, TrailPoint>>) {
        stored.value = stored.value + points
    }

    override suspend fun isKnown(cell: Long, mode: TravelMode): Boolean =
        stored.value.any { (c, p) -> c == cell && (mode == TravelMode.VEHICLE || p.mode != TravelMode.VEHICLE) }

    override fun observePoints(): Flow<List<TrailPoint>> =
        stored.map { rows -> rows.map { it.second }.sortedBy { it.segmentId } }

    override fun observeTrail(): Flow<List<Long>> = stored.map { rows -> rows.map { it.first }.distinct().sorted() }

    override fun observeFar(): Flow<List<Long>> = observeTrail()

    override fun observeCount(): Flow<Int> = stored.map { rows -> rows.map { it.first }.distinct().size }
}
