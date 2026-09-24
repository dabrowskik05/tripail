package com.tripex.pose.data.mapper

import com.tripex.pose.data.network.MapTilerContextDto
import com.tripex.pose.data.network.MapTilerFeatureDto
import com.tripex.pose.data.network.MapTilerPropertiesDto
import com.tripex.pose.domain.geo.PlaceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MapTilerMapperTest {

    private fun feature(
        types: List<String> = emptyList(),
        bbox: List<Double>? = null,
        center: List<Double>? = listOf(21.01, 52.23),
        context: List<String> = emptyList(),
        countryCode: String? = null,
    ) = MapTilerFeatureDto(
        id = "place.1",
        placeName = "Warszawa, Polska",
        text = "Warszawa",
        bbox = bbox,
        center = center,
        placeType = types,
        properties = MapTilerPropertiesDto(countryCode = countryCode),
        context = context.map { MapTilerContextDto(text = it) },
    )

    @Test
    fun `country code is carried over in upper case`() {
        assertEquals("PH", feature(types = listOf("country"), countryCode = "ph").toPlace()!!.countryCode)
        assertNull(feature(countryCode = " ").toPlace()!!.countryCode)
    }

    @Test
    fun `centre is read in GeoJSON order, not lat-lng`() {
        val place = feature().toPlace()

        assertNotNull(place)
        assertEquals(52.23, place!!.latitude, 0.0001)
        assertEquals(21.01, place.longitude, 0.0001)
    }

    @Test
    fun `bbox becomes the bounds the reveal radius is measured from`() {
        val place = feature(bbox = listOf(20.85, 52.10, 21.27, 52.37)).toPlace()!!

        val bounds = place.boundingBox!!
        assertEquals(20.85, bounds.west, 0.0001)
        assertEquals(52.10, bounds.south, 0.0001)
        assertEquals(21.27, bounds.east, 0.0001)
        assertEquals(52.37, bounds.north, 0.0001)
    }

    @Test
    fun `place types map to the categories the radius policy understands`() {
        assertEquals(PlaceKind.City, feature(types = listOf("city")).toPlace()!!.kind)
        assertEquals(PlaceKind.Town, feature(types = listOf("town")).toPlace()!!.kind)
        assertEquals(PlaceKind.Village, feature(types = listOf("village")).toPlace()!!.kind)
        assertEquals(PlaceKind.Region, feature(types = listOf("region")).toPlace()!!.kind)
        assertEquals(PlaceKind.Country, feature(types = listOf("country")).toPlace()!!.kind)
        assertEquals(PlaceKind.Unknown, feature(types = listOf("something-new")).toPlace()!!.kind)
    }

    @Test
    fun `MapTiler's plain place type is a settlement, not an unknown`() {
        // What reverse geocoding actually returns for a town; if this drops to Unknown the
        // automatic city unlock never fires.
        assertEquals(PlaceKind.City, feature(types = listOf("place")).toPlace()!!.kind)
        assertEquals(PlaceKind.Municipality, feature(types = listOf("municipality")).toPlace()!!.kind)
        assertEquals(
            PlaceKind.Municipality,
            feature(types = listOf("municipal_district")).toPlace()!!.kind,
        )
    }

    @Test
    fun `context is kept so homonyms can be told apart`() {
        val place = feature(context = listOf("mazowieckie", "Polska")).toPlace()!!

        assertEquals(listOf("mazowieckie", "Polska"), place.context)
        assertEquals("Warszawa · mazowieckie, Polska", place.qualifiedName)
    }

    @Test
    fun `a feature without a centre is dropped rather than placed at null island`() {
        assertNull(feature(center = null).toPlace())
        assertNull(feature(center = listOf(21.0)).toPlace())
    }
}
