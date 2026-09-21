package com.tripex.pose.data.repository

import com.tripex.pose.core.di.IoDispatcher
import com.tripex.pose.data.local.UnlockedRegionDao
import com.tripex.pose.data.local.UnlockedRegionEntity
import com.tripex.pose.domain.geo.atlas.AdminLevel
import com.tripex.pose.domain.repository.UnlockedRegionRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
internal class UnlockedRegionRepositoryImpl
    @Inject
    constructor(
        private val dao: UnlockedRegionDao,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : UnlockedRegionRepository {

        override suspend fun unlock(level: AdminLevel, featureId: String): Boolean =
            withContext(ioDispatcher) {
                val inserted = dao.insert(
                    UnlockedRegionEntity(
                        adminLevel = level.name,
                        featureId = featureId,
                        unlockedAt = System.currentTimeMillis(),
                    ),
                )
                inserted != IGNORED_INSERT
            }

        override suspend fun lock(level: AdminLevel, featureId: String): Boolean =
            withContext(ioDispatcher) { dao.delete(level.name, featureId) > 0 }

        override fun observeAll(): Flow<List<UnlockedRegionRepository.UnlockedRegion>> =
            dao.observeAll().map { rows -> rows.mapNotNull { it.toDomain() } }

        override suspend fun isUnlocked(level: AdminLevel, featureId: String): Boolean =
            withContext(ioDispatcher) { dao.count(level.name, featureId) > 0 }

        /** Rows written by a future version with an unknown level are skipped, not crashed on. */
        private fun UnlockedRegionEntity.toDomain(): UnlockedRegionRepository.UnlockedRegion? {
            val level = runCatching { AdminLevel.valueOf(adminLevel) }.getOrNull() ?: return null
            return UnlockedRegionRepository.UnlockedRegion(
                level = level,
                featureId = featureId,
                unlockedAt = unlockedAt,
            )
        }

        private companion object {
            /** Room returns -1 from an IGNORE insert that hit an existing row. */
            const val IGNORED_INSERT = -1L
        }
    }
