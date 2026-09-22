package com.tripex.pose.data.repository

import com.tripex.pose.core.di.IoDispatcher
import com.tripex.pose.data.local.UnlockedPlaceDao
import com.tripex.pose.data.local.UnlockedPlaceEntity
import com.tripex.pose.domain.repository.UnlockedPlaceRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
internal class UnlockedPlaceRepositoryImpl
    @Inject
    constructor(
        private val dao: UnlockedPlaceDao,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : UnlockedPlaceRepository {

        override suspend fun unlock(place: UnlockedPlaceRepository.UnlockedPlace): Boolean =
            withContext(ioDispatcher) {
                dao.upsert(
                    UnlockedPlaceEntity(
                        id = place.id,
                        name = place.name,
                        latitude = place.latitude,
                        longitude = place.longitude,
                        radiusMeters = place.radiusMeters,
                        unlockedAt = place.unlockedAt,
                        source = place.source.name.uppercase(),
                    ),
                )
                true
            }

        /** The DAO refuses `AUTO` rows in SQL, so an earned city cannot be given back. */
        override suspend fun lock(id: String): Boolean =
            withContext(ioDispatcher) { dao.delete(id) > 0 }

        override suspend fun find(id: String): UnlockedPlaceRepository.UnlockedPlace? =
            withContext(ioDispatcher) { dao.find(id)?.toDomain() }

        override fun observeAll(): Flow<List<UnlockedPlaceRepository.UnlockedPlace>> =
            dao.observeAll().map { rows -> rows.map { it.toDomain() } }

        private fun UnlockedPlaceEntity.toDomain() = UnlockedPlaceRepository.UnlockedPlace(
            id = id,
            name = name,
            latitude = latitude,
            longitude = longitude,
            radiusMeters = radiusMeters,
            unlockedAt = unlockedAt,
            // An unreadable value means an unlock we cannot account for; treating it as earned
            // errs towards keeping ground the player may have walked.
            source = runCatching {
                UnlockedPlaceRepository.Source.valueOf(
                    source.lowercase().replaceFirstChar { it.uppercase() },
                )
            }.getOrDefault(UnlockedPlaceRepository.Source.Auto),
        )
    }
