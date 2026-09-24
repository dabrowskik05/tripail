package com.tripex.pose.domain.usecase

import com.tripex.pose.domain.geo.GeoBounds
import com.tripex.pose.domain.geo.Place
import com.tripex.pose.domain.geo.PlaceKind
import com.tripex.pose.domain.repository.GeocodingRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ResolveMapPlaceUseCaseTest {

    private val tapped = Place("Warsaw", 52.23, 21.01, kind = PlaceKind.City, id = "map:Warsaw@52.23,21.01")

    private val warszawa = Place(
        displayName = "Warszawa",
        latitude = 52.23,
        longitude = 21.01,
        boundingBox = GeoBounds(north = 52.37, south = 52.10, east = 21.27, west = 20.85),
        kind = PlaceKind.Municipality,
        id = "municipal_district.888",
    )

    @Test
    fun `a tapped label becomes the geocoder's own record, with its extent and id`() = runTest {
        val resolved = ResolveMapPlaceUseCase(Geocoder(Result.success(warszawa)))(tapped)

        assertEquals(warszawa, resolved)
    }

    @Test
    fun `offline or unknown, the tapped label is kept`() = runTest {
        assertEquals(tapped, ResolveMapPlaceUseCase(Geocoder(Result.failure(Exception("offline"))))(tapped))
        assertEquals(tapped, ResolveMapPlaceUseCase(Geocoder(Result.success(null)))(tapped))
    }

    private class Geocoder(private val reverse: Result<Place?>) : GeocodingRepository {
        override suspend fun suggest(query: String): Result<List<Place>> = Result.success(emptyList())
        override suspend fun reverseGeocode(lat: Double, lng: Double): Result<Place?> = reverse
        override suspend fun search(query: String): Result<Place> = Result.failure(NotImplementedError())
    }
}
