package com.tripex.pose.data.mapper

import com.tripex.pose.data.network.PhotonFeatureDto
import com.tripex.pose.data.network.PhotonGeometryDto
import com.tripex.pose.data.network.PhotonPropertiesDto
import com.tripex.pose.domain.geo.PlaceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhotonMapperTest {

    private fun feature(
        osmKey: String? = "place",
        osmValue: String? = "city",
        type: String? = "city",
        extent: List<Double>? = null,
        coordinates: List<Double> = listOf(21.01, 52.23),
        name: String? = "Warszawa",
    ) = PhotonFeatureDto(
        geometry = PhotonGeometryDto(coordinates),
        properties = PhotonPropertiesDto(
            osmType = "R",
            osmId = 1,
            osmKey = osmKey,
            osmValue = osmValue,
            type = type,
            name = name,
            county = null,
            state = "mazowieckie",
            country = "Polska",
            countryCode = "pl",
            extent = extent,
        ),
    )

    @Test
    fun `centre is read in GeoJSON order, not lat-lng`() {
        val place = feature().toPlace()!!
        assertEquals(52.23, place.latitude, 0.0)
        assertEquals(21.01, place.longitude, 0.0)
    }

    @Test
    fun `extent is minLon, maxLat, maxLon, minLat`() {
        val box = feature(extent = listOf(20.85, 52.37, 21.27, 52.10)).toPlace()!!.boundingBox!!
        assertEquals(20.85, box.west, 0.0)
        assertEquals(52.37, box.north, 0.0)
        assertEquals(21.27, box.east, 0.0)
        assertEquals(52.10, box.south, 0.0)
    }

    @Test
    fun `settlement size comes from the OSM place tag`() {
        assertEquals(PlaceKind.City, feature(osmValue = "city").toPlace()!!.kind)
        assertEquals(PlaceKind.Town, feature(osmValue = "town").toPlace()!!.kind)
        assertEquals(PlaceKind.Village, feature(osmValue = "village").toPlace()!!.kind)
        assertEquals(PlaceKind.Village, feature(osmValue = "hamlet").toPlace()!!.kind)
        assertEquals(PlaceKind.Country, feature(osmValue = "country", type = "country").toPlace()!!.kind)
        assertEquals(PlaceKind.Region, feature(osmValue = "state", type = "state").toPlace()!!.kind)
    }

    @Test
    fun `shops and streets are not mistaken for settlements`() {
        val shop = feature(osmKey = "shop", osmValue = "clothes", type = "house")
        val road = feature(osmKey = "highway", osmValue = "primary", type = "street")
        assertEquals(PlaceKind.Poi, shop.toPlace()!!.kind)
        assertEquals(PlaceKind.Address, road.toPlace()!!.kind)
    }

    @Test
    fun `country code is upper case and parents become context`() {
        val place = feature().toPlace()!!
        assertEquals("PL", place.countryCode)
        assertEquals(listOf("mazowieckie", "Polska"), place.context)
    }

    @Test
    fun `no name or no coordinates means no place`() {
        assertNull(feature(name = " ").toPlace())
        assertNull(feature(coordinates = emptyList()).toPlace())
    }
}
