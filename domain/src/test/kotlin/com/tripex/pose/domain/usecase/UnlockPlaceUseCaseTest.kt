package com.tripex.pose.domain.usecase

import com.tripex.pose.domain.geo.GeoBounds
import com.tripex.pose.domain.geo.Place
import com.tripex.pose.domain.repository.UnlockedPlaceRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnlockPlaceUseCaseTest {

    private val warsaw = Place(
        displayName = "Warszawa",
        latitude = 52.23,
        longitude = 21.01,
        boundingBox = GeoBounds(north = 52.37, south = 52.10, east = 21.27, west = 20.85),
        id = "place.warsaw",
    )

    private val places = FakeUnlockedPlaceRepository()
    private val useCase = UnlockPlaceUseCase(places)

    @Test
    fun `a city is stored as one circle, not as cells`() = runTest {
        useCase.unlock(warsaw).getOrThrow()

        val stored = places.places.value.single()
        assertEquals("place.warsaw", stored.id)
        assertEquals("Warszawa", stored.name)
        // Measured off the bounding box, not a constant at the call site.
        assertTrue(stored.radiusMeters > 10_000.0)
    }

    @Test
    fun `a place with no provider id still gets a stable one`() = runTest {
        val nameless = warsaw.copy(id = "")

        useCase.unlock(nameless).getOrThrow()

        val id = places.places.value.single().id
        assertTrue(id.contains("Warszawa"))
        assertEquals(id, useCase.idOf(nameless))
    }

    @Test
    fun `revealing by hand is revocable`() = runTest {
        useCase.unlock(warsaw).getOrThrow()

        assertTrue(useCase.lock(warsaw))
        assertTrue(places.places.value.isEmpty())
    }

    /** Ground earned by standing in it follows the trail's rule: it stays. */
    @Test
    fun `an earned place cannot be given back`() = runTest {
        useCase.unlock(warsaw, source = UnlockedPlaceRepository.Source.Auto).getOrThrow()

        assertFalse(useCase.lock(warsaw))
        assertEquals(1, places.places.value.size)
    }

    @Test
    fun `an explicit radius overrides the policy`() = runTest {
        val result = useCase.unlock(warsaw, radiusMeters = 2_500.0).getOrThrow()

        assertEquals(2_500.0, result.radiusMeters, 1e-9)
        assertEquals(2_500.0, places.places.value.single().radiusMeters, 1e-9)
    }
}
