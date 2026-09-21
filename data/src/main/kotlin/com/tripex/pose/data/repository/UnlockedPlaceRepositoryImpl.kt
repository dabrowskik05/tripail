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
                    ),
                )
                true
            }

        override suspend fun lock(id: String): Boolean =
            withContext(ioDispatcher) { dao.delete(id) > 0 }

        override fun observeAll(): Flow<List<UnlockedPlaceRepository.UnlockedPlace>> =
            dao.observeAll().map { rows ->
                rows.map {
                    UnlockedPlaceRepository.UnlockedPlace(
                        id = it.id,
                        name = it.name,
                        latitude = it.latitude,
                        longitude = it.longitude,
                        radiusMeters = it.radiusMeters,
                        unlockedAt = it.unlockedAt,
                    )
                }
            }
    }
