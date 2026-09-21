package com.tripex.pose.domain.usecase

import com.tripex.pose.domain.geo.BoundaryMatcher
import com.tripex.pose.domain.geo.GeoBounds
import com.tripex.pose.domain.geo.Place
import com.tripex.pose.domain.geo.PlaceKind
import com.tripex.pose.domain.geo.atlas.AdminLevel
import com.tripex.pose.domain.geo.atlas.BoundaryFeature
import com.tripex.pose.domain.geo.atlas.BoundaryGeometrySource
import com.tripex.pose.domain.geo.atlas.Ring
import com.tripex.pose.domain.repository.GeocodingRepository
import com.tripex.pose.domain.repository.UnlockedPlaceRepository
import com.tripex.pose.domain.repository.UnlockedRegionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PickSearchResultUseCaseTest {

    private val poland = BoundaryFeature(
        level = AdminLevel.Adm0,
        id = "PL",
        name = "Poland",
        namePl = "Polska",
        countryIso2 = "PL",
        continentId = "Europe",
    )
    private val malopolskie = BoundaryFeature(
        level = AdminLevel.Adm1,
        id = "PL-12#3",
        name = "Lesser Poland",
        namePl = "Małopolskie",
        countryIso2 = "PL",
    )

    /** A square ring around (50, 20); only its extent matters here. */
    private val square: Ring = listOf(
        19.0 to 49.0,
        21.0 to 49.0,
        21.0 to 51.0,
        19.0 to 51.0,
        19.0 to 49.0,
    )

    private val places = FakePlaces()
    private val regions = FakeRegions()

    private fun useCase(
        hit: BoundaryFeature?,
        rings: List<Ring> = listOf(square),
    ): PickSearchResultUseCase {
        val boundaries = FakeBoundaries(hit, rings)
        return PickSearchResultUseCase(
            boundaryMatcher = BoundaryMatcher(boundaries),
            boundaries = boundaries,
            unlockRegionUseCase = UnlockRegionUseCase(boundaries, regions),
            unlockPlace = UnlockPlaceUseCase(FakeGeocoding(), places),
        )
    }

    @Test
    fun `a country is entered, not owned`() = runTest {
        val place = Place("Polska", 52.0, 19.0, kind = PlaceKind.Country)

        val outcome = useCase(poland).invoke(place).getOrThrow()

        assertEquals(
            SearchOutcome.OpenCountry(
                iso2 = "PL",
                bounds = GeoBounds(north = 51.0, south = 49.0, east = 21.0, west = 19.0),
                label = "Polska",
                continentId = "Europe",
            ),
            outcome,
        )
        // Entering a country must not unlock anything — that is what walking it is for.
        assertTrue(places.stored.value.isEmpty())
        assertTrue(regions.stored.isEmpty())
    }

    @Test
    fun `a region is claimed by its boundary id, never by cells`() = runTest {
        val place = Place("Małopolskie", 50.0, 20.0, kind = PlaceKind.Region)

        val outcome = useCase(malopolskie).invoke(place).getOrThrow()

        assertEquals(
            SearchOutcome.RegionUnlocked(
                regionId = "PL-12#3",
                countryIso2 = "PL",
                label = "Małopolskie",
            ),
            outcome,
        )
        assertEquals(listOf(AdminLevel.Adm1 to "PL-12#3"), regions.stored)
        assertTrue(places.stored.value.isEmpty())
    }

    @Test
    fun `a city is unlocked as a circle`() = runTest {
        val place = Place(
            displayName = "Kraków",
            latitude = 50.06,
            longitude = 19.94,
            boundingBox = GeoBounds(north = 50.13, south = 49.97, east = 20.09, west = 19.79),
            kind = PlaceKind.City,
            id = "pl-krk",
        )

        val outcome = useCase(null).invoke(place).getOrThrow()

        assertTrue(outcome is SearchOutcome.PlaceUnlocked)
        assertEquals(place, (outcome as SearchOutcome.PlaceUnlocked).place)
        assertEquals(listOf("pl-krk"), places.stored.value.map { it.id })
        assertTrue(regions.stored.isEmpty())
    }

    @Test
    fun `a region the bundle does not know degrades to a circle`() = runTest {
        val place = Place("Prowincja Widmo", 50.0, 20.0, kind = PlaceKind.Region)

        val outcome = useCase(hit = null).invoke(place).getOrThrow()

        assertTrue(outcome is SearchOutcome.PlaceUnlocked)
        assertTrue(regions.stored.isEmpty())
        assertEquals(1, places.stored.value.size)
    }

    @Test
    fun `a country without an outline still does something`() = runTest {
        // No rings means no bbox to frame; the player gets a circle rather than a dead tap.
        val place = Place("Polska", 52.0, 19.0, kind = PlaceKind.Country)

        val outcome = useCase(hit = poland, rings = emptyList()).invoke(place).getOrThrow()

        assertTrue(outcome is SearchOutcome.PlaceUnlocked)
    }

    @Test
    fun `a country falls back to the geocoder bbox when the bundle has no outline`() = runTest {
        val place = Place(
            displayName = "Polska",
            latitude = 52.0,
            longitude = 19.0,
            boundingBox = GeoBounds(north = 54.8, south = 49.0, east = 24.1, west = 14.1),
            kind = PlaceKind.Country,
        )

        val outcome = useCase(hit = poland, rings = emptyList()).invoke(place).getOrThrow()

        assertEquals(
            SearchOutcome.OpenCountry("PL", place.boundingBox!!, "Polska", "Europe"),
            outcome,
        )
    }

    private class FakeBoundaries(
        private val hit: BoundaryFeature?,
        private val rings: List<Ring>,
    ) : BoundaryGeometrySource {
        override suspend fun rings(level: AdminLevel, id: String): Result<List<Ring>> =
            Result.success(rings)

        override suspend fun feature(level: AdminLevel, id: String): BoundaryFeature? = hit

        override suspend fun featureAt(
            level: AdminLevel,
            lat: Double,
            lng: Double,
        ): BoundaryFeature? = hit
    }

    private class FakeGeocoding : GeocodingRepository {
        override suspend fun suggest(query: String): Result<List<Place>> =
            Result.success(emptyList())

        override suspend fun reverseGeocode(lat: Double, lng: Double): Result<Place?> =
            Result.success(null)

        override suspend fun search(query: String): Result<Place> =
            Result.failure(NoSuchElementException())
    }

    private class FakePlaces : UnlockedPlaceRepository {
        val stored = MutableStateFlow<List<UnlockedPlaceRepository.UnlockedPlace>>(emptyList())

        override suspend fun unlock(place: UnlockedPlaceRepository.UnlockedPlace): Boolean {
            stored.value = stored.value + place
            return true
        }

        override suspend fun lock(id: String): Boolean = true

        override fun observeAll(): Flow<List<UnlockedPlaceRepository.UnlockedPlace>> = stored
    }

    private class FakeRegions : UnlockedRegionRepository {
        val stored = mutableListOf<Pair<AdminLevel, String>>()

        override suspend fun unlock(level: AdminLevel, featureId: String): Boolean {
            stored += level to featureId
            return true
        }

        override suspend fun lock(level: AdminLevel, featureId: String): Boolean {
            stored.removeAll { it == level to featureId }
            return true
        }

        override fun observeAll(): Flow<List<UnlockedRegionRepository.UnlockedRegion>> =
            MutableStateFlow(emptyList())

        override suspend fun isUnlocked(level: AdminLevel, featureId: String): Boolean =
            stored.contains(level to featureId)
    }
}
