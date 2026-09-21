package com.tripex.pose.domain.usecase

import com.tripex.pose.domain.geo.GeoBounds
import com.tripex.pose.domain.geo.Place
import com.tripex.pose.domain.repository.GeocodingRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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

    @Test
    fun `a city is stored as one circle, not as cells`() = runTest {
        val places = FakeUnlockedPlaceRepository()
        val useCase = UnlockPlaceUseCase(FakeGeocoding(Result.success(warsaw)), places)

        val result = useCase.unlock(warsaw).getOrThrow()

        assertEquals(1, places.places.value.size)
        val stored = places.places.value.single()
        assertEquals("place.warsaw", stored.id)
        assertEquals(52.23, stored.latitude, 0.0001)
        assertEquals(result.radiusMeters, stored.radiusMeters, 0.0001)
    }

    @Test
    fun `a capital no longer fails for being too large`() = runTest {
        val places = FakeUnlockedPlaceRepository()
        val useCase = UnlockPlaceUseCase(FakeGeocoding(Result.success(warsaw)), places)

        // The old model rasterised this to ~770 000 H3 indices and refused outright.
        val result = useCase.unlock(warsaw)

        assertTrue("unlocking a capital must succeed", result.isSuccess)
        assertTrue("radius should cover the city", result.getOrThrow().radiusMeters > 12_000.0)
    }

    @Test
    fun `unlocking the same place twice keeps a single row`() = runTest {
        val places = FakeUnlockedPlaceRepository()
        val useCase = UnlockPlaceUseCase(FakeGeocoding(Result.success(warsaw)), places)

        useCase.unlock(warsaw).getOrThrow()
        useCase.unlock(warsaw).getOrThrow()

        assertEquals(1, places.places.value.size)
    }

    @Test
    fun `empty query fails without calling the geocoder`() = runTest {
        val geo = FakeGeocoding(Result.failure(IllegalStateException("should not call")))
        val useCase = UnlockPlaceUseCase(geo, FakeUnlockedPlaceRepository())

        assertTrue(useCase("   ").isFailure)
        assertEquals(0, geo.calls)
    }

    @Test
    fun `geocoding failure propagates`() = runTest {
        val geo = FakeGeocoding(Result.failure(NoSuchElementException("No results")))
        val useCase = UnlockPlaceUseCase(geo, FakeUnlockedPlaceRepository())

        assertTrue(useCase("Nowhere").isFailure)
    }

    private class FakeGeocoding(private val result: Result<Place>) : GeocodingRepository {
        var calls: Int = 0
            private set

        override suspend fun suggest(query: String): Result<List<Place>> {
            calls++
            return result.map { listOf(it) }
        }

        override suspend fun reverseGeocode(lat: Double, lng: Double): Result<Place?> =
            Result.success(null)

        override suspend fun search(query: String): Result<Place> {
            calls++
            return result
        }
    }
}
