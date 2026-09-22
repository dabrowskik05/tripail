package com.tripex.pose.domain.usecase

import com.tripex.pose.domain.geo.GeoBounds
import com.tripex.pose.domain.geo.Place
import com.tripex.pose.domain.geo.PlaceKind
import com.tripex.pose.domain.repository.GeocodingRepository
import com.tripex.pose.domain.repository.UnlockedPlaceRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AutoUnlockCityUseCaseTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val places = FakePlaces()

    private fun useCase(reverse: Place?) = AutoUnlockCityUseCase(
        geocoding = FakeGeocoding(reverse),
        unlockedPlaces = places,
        unlockPlace = UnlockPlaceUseCase(places),
        defaultDispatcher = dispatcher,
    )

    @Test
    fun `standing in a city unlocks the whole city`() = runTest(dispatcher) {
        val warsaw = Place(
            displayName = "Warszawa",
            latitude = 52.23,
            longitude = 21.01,
            boundingBox = GeoBounds(north = 52.37, south = 52.10, east = 21.27, west = 20.85),
            kind = PlaceKind.City,
            id = "pl-waw",
        )

        val result = useCase(warsaw).invoke(52.23, 21.01)

        assertTrue(result is AutoUnlockCityUseCase.Result.Unlocked)
        assertEquals("Warszawa", (result as AutoUnlockCityUseCase.Result.Unlocked).name)
        assertEquals(listOf("pl-waw"), places.stored.value.map { it.id })
        // Measured off the bbox, not off a constant — vision, level 4.
        assertTrue(places.stored.value.single().radiusMeters > 10_000.0)
    }

    @Test
    fun `a street address is ignored`() = runTest(dispatcher) {
        val address = Place("Marszałkowska 1", 52.23, 21.01, kind = PlaceKind.Address)

        val result = useCase(address).invoke(52.23, 21.01)

        assertEquals(AutoUnlockCityUseCase.Result.NothingHere, result)
        assertTrue(places.stored.value.isEmpty())
    }

    @Test
    fun `a country is ignored so dwelling never unlocks a continent`() = runTest(dispatcher) {
        val country = Place("Polska", 52.0, 19.0, kind = PlaceKind.Country)

        assertEquals(
            AutoUnlockCityUseCase.Result.NothingHere,
            useCase(country).invoke(52.0, 19.0),
        )
    }

    @Test
    fun `nothing under the player is not an error`() = runTest(dispatcher) {
        assertEquals(
            AutoUnlockCityUseCase.Result.NothingHere,
            useCase(null).invoke(0.0, 0.0),
        )
    }

    @Test
    fun `a city already owned is not rewritten`() = runTest(dispatcher) {
        val krakow = Place("Kraków", 50.06, 19.94, kind = PlaceKind.City, id = "pl-krk")
        places.stored.value = listOf(
            UnlockedPlaceRepository.UnlockedPlace(
                id = "pl-krk",
                name = "Kraków",
                latitude = 50.06,
                longitude = 19.94,
                radiusMeters = 8_000.0,
                unlockedAt = 1L,
            ),
        )

        val result = useCase(krakow).invoke(50.06, 19.94)

        assertEquals(AutoUnlockCityUseCase.Result.AlreadyOwned, result)
        assertEquals(1L, places.stored.value.single().unlockedAt)
    }

    private class FakeGeocoding(private val reverse: Place?) : GeocodingRepository {
        override suspend fun suggest(query: String): Result<List<Place>> =
            Result.success(emptyList())

        override suspend fun reverseGeocode(lat: Double, lng: Double): Result<Place?> =
            Result.success(reverse)

        override suspend fun search(query: String): Result<Place> =
            Result.failure(NoSuchElementException())
    }

    /** Standing somewhere earns it; the row must say so, or "Cover" would be offered for it. */
    @Test
    fun `an automatic unlock is marked as earned`() = runTest(dispatcher) {
        val city = Place(
            displayName = "Skierniewice",
            latitude = 51.95,
            longitude = 20.15,
            kind = PlaceKind.City,
            id = "pl-ski",
        )

        useCase(city).invoke(city.latitude, city.longitude)

        assertEquals(
            UnlockedPlaceRepository.Source.Auto,
            places.stored.value.single().source,
        )
    }

    private class FakePlaces : UnlockedPlaceRepository {
        val stored = MutableStateFlow<List<UnlockedPlaceRepository.UnlockedPlace>>(emptyList())

        override suspend fun unlock(place: UnlockedPlaceRepository.UnlockedPlace): Boolean {
            stored.value = stored.value.filterNot { it.id == place.id } + place
            return true
        }

        override suspend fun lock(id: String): Boolean {
            stored.value = stored.value.filterNot { it.id == id }
            return true
        }

        override suspend fun find(id: String): UnlockedPlaceRepository.UnlockedPlace? =
            stored.value.firstOrNull { it.id == id }

        override fun observeAll(): Flow<List<UnlockedPlaceRepository.UnlockedPlace>> = stored
    }
}
