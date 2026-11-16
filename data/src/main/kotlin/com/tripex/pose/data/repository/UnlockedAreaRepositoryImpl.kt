package com.tripex.pose.data.repository

import com.tripex.pose.core.di.IoDispatcher
import com.tripex.pose.data.local.TrailPointDao
import com.tripex.pose.data.local.TrailPointEntity
import com.tripex.pose.domain.geo.H3Config
import com.tripex.pose.domain.geo.H3Converter
import com.tripex.pose.domain.geo.TrailPoint
import com.tripex.pose.domain.geo.TravelMode
import com.tripex.pose.domain.repository.UnlockedAreaRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
internal class UnlockedAreaRepositoryImpl @Inject constructor(
    private val dao: TrailPointDao,
    private val h3: H3Converter,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : UnlockedAreaRepository {

    override suspend fun addPoint(cell: Long, point: TrailPoint) {
        withContext(ioDispatcher) { dao.insert(entityOf(cell, point)) }
    }

    override suspend fun addPoints(points: List<Pair<Long, TrailPoint>>) {
        if (points.isEmpty()) return
        withContext(ioDispatcher) { dao.insertAll(points.map { (cell, point) -> entityOf(cell, point) }) }
    }

    override suspend fun isKnown(cell: Long, mode: TravelMode): Boolean =
        withContext(ioDispatcher) { dao.isKnown(cell, anyMode = mode == TravelMode.VEHICLE) }

    override fun observePoints(): Flow<List<TrailPoint>> = dao.observePoints().map { rows ->
        rows.map { row ->
            TrailPoint(
                latitude = row.latitude,
                longitude = row.longitude,
                mode = runCatching { TravelMode.valueOf(row.mode) }.getOrDefault(TravelMode.UNKNOWN),
                segmentId = row.segmentId,
                discoveredAt = row.discoveredAt,
            )
        }
    }

    override fun observeTrail(): Flow<List<Long>> = dao.observeTrail()

    override fun observeFar(): Flow<List<Long>> = dao.observeFar()

    override fun observeCount(): Flow<Int> = dao.observeCount()

    private fun entityOf(cell: Long, point: TrailPoint) = TrailPointEntity(
        cell9 = cell,
        latitude = point.latitude,
        longitude = point.longitude,
        mode = point.mode.name,
        segmentId = point.segmentId,
        parentRes7 = h3.parentOf(cell, H3Config.COARSE_RESOLUTION),
        discoveredAt = point.discoveredAt,
    )
}
