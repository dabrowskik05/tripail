package com.tripex.pose.data.network

import com.tripex.pose.data.mapper.toPlace
import com.tripex.pose.domain.geo.PlaceKind
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PhotonResponseDecodingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /** Trimmed from Photon's real reverse answer for central Warsaw with `layer=city`. */
    @Test
    fun `reverse answer for a city decodes into a city with its extent`() {
        val body = """
            {"type":"FeatureCollection","features":[{"type":"Feature",
              "properties":{"osm_type":"R","osm_id":336075,"osm_key":"place","osm_value":"city",
                "type":"city","name":"Warszawa","state":"województwo mazowieckie","country":"Polska",
                "countrycode":"PL","extra":{"admin_level":"6"},
                "extent":[20.8516882,52.3681531,21.2711512,52.0978497]},
              "geometry":{"type":"Point","coordinates":[21.0067249,52.2319581]}}]}
        """.trimIndent()

        val place = json.decodeFromString<PhotonResponseDto>(body).features.single().toPlace()

        assertNotNull(place)
        assertEquals("Warszawa", place!!.displayName)
        assertEquals(PlaceKind.City, place.kind)
        assertEquals("PL", place.countryCode)
        assertEquals("osm:R336075", place.id)
        assertEquals(52.3681531, place.boundingBox!!.north, 1e-9)
        assertEquals(52.0978497, place.boundingBox!!.south, 1e-9)
    }

    /** A feature without a name (Photon returns some for unnamed objects) is skipped, not fatal. */
    @Test
    fun `unnamed feature is dropped without sinking the rest`() {
        val body = """
            {"features":[
              {"properties":{"osm_key":"highway","type":"street"},"geometry":{"coordinates":[1.0,2.0]}},
              {"properties":{"osm_key":"place","osm_value":"country","type":"country","name":"Norway",
                "countrycode":"NO"},"geometry":{"coordinates":[8.78,61.15]}}
            ]}
        """.trimIndent()

        val places = json.decodeFromString<PhotonResponseDto>(body).features.mapNotNull { it.toPlace() }

        assertEquals(1, places.size)
        assertEquals(PlaceKind.Country, places.single().kind)
    }
}
