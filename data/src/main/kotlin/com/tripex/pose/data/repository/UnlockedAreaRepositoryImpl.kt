package com.tripex.pose.data.repository

import com.tripex.pose.core.di.IoDispatcher
import com.tripex.pose.data.local.UnlockedHexDao
import com.tripex.pose.data.mapper.toUnlockedHexEntity
import com.tripex.pose.domain.geo.H3Converter
import com.tripex.pose.domain.repository.UnlockedAreaRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

@Singleton
internal class UnlockedAreaRepositoryImpl @Inject constructor(
    private val dao: UnlockedHexDao,
    private val h3: H3Converter,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : UnlockedAreaRepository {

    override suspend fun unlock(hexes: Set<Long>): Int {
        if (hexes.isEmpty()) return 0
        return withContext(ioDispatcher) {
            val discoveredAt = System.currentTimeMillis()
            val entities = hexes.map { index -> index.toUnlockedHexEntity(h3, discoveredAt) }
            dao.insertAll(entities).count { rowId -> rowId != -1L }
        }
    }

    override fun observeDetailed(viewportCells: Set<Long>): Flow<List<Long>> =
        dao.observeDetailed(viewportCells)

    override fun observeAllDetailed(limit: Int): Flow<List<Long>> =
        dao.observeAllDetailed(limit)

    override fun observeMid(viewportCells: Set<Long>): Flow<List<Long>> =
        dao.observeMid(viewportCells)

    override fun observeFar(): Flow<List<Long>> = dao.observeFar()

    override fun observeCount(): Flow<Int> = dao.observeCount()
}
