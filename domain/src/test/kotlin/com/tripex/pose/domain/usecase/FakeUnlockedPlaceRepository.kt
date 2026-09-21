package com.tripex.pose.domain.usecase

import com.tripex.pose.domain.repository.UnlockedPlaceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory stand-in for circular macro unlocks. */
internal class FakeUnlockedPlaceRepository : UnlockedPlaceRepository {

    val places = MutableStateFlow<List<UnlockedPlaceRepository.UnlockedPlace>>(emptyList())

    override suspend fun unlock(place: UnlockedPlaceRepository.UnlockedPlace): Boolean {
        places.value = places.value.filterNot { it.id == place.id } + place
        return true
    }

    override suspend fun lock(id: String): Boolean {
        val before = places.value.size
        places.value = places.value.filterNot { it.id == id }
        return places.value.size != before
    }

    override fun observeAll(): Flow<List<UnlockedPlaceRepository.UnlockedPlace>> = places
}
