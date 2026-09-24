package com.tripex.pose.data.network

import com.tripex.pose.data.mapper.toPlace
import com.tripex.pose.domain.geo.PlaceKind
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class MapTilerResponseDecodingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Trimmed from MapTiler's real answer to "Norwegia" (language=pl,en). The landform carries
     * `"place_type_name":[null]`; decoding that as a list of strings threw, and the whole list of
     * suggestions — the country included — was lost.
     */
    @Test
    fun `a landform with a null type name does not sink the whole response`() {
        val body = """
            {"type":"FeatureCollection","features":[
              {"id":"country.204","text":"Norwegia","place_name":"Norwegia",
               "center":[8.79,61.15],"bbox":[-9.68,-54.65,34.68,81.02],"place_type":["country"],
               "properties":{"country_code":"no","place_type_name":["państwo"]},"context":[]},
              {"id":"major_landform.3498138","text":"Norwegian Lake","place_name":"Norwegian Lake",
               "center":[-88.8,48.44],"place_type":["major_landform"],
               "properties":{"place_type_name":[null]},"context":[]}
            ]}
        """.trimIndent()

        val places = json.decodeFromString<MapTilerResponseDto>(body).features.mapNotNull { it.toPlace() }

        assertEquals(2, places.size)
        assertEquals(PlaceKind.Country, places.first().kind)
        assertEquals("NO", places.first().countryCode)
    }
}
