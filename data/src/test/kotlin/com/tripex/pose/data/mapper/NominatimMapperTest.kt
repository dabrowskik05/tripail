package com.tripex.pose.data.mapper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NominatimMapperTest {

    @Test
    fun `maps nominatim dto to place with bounding box`() {
        val dto = com.tripex.pose.data.network.NominatimPlaceDto(
            lat = "52.2319581",
            lon = "21.0067249",
            displayName = "Warszawa, Polska",
            boundingbox = listOf("52.09", "52.36", "20.85", "21.27"),
        )
        val place = dto.toPlace()
        assertEquals(52.2319581, place.latitude, 1e-7)
        assertEquals(21.0067249, place.longitude, 1e-7)
        assertEquals("Warszawa, Polska", place.displayName)
        val box = place.boundingBox!!
        assertEquals(52.09, box.south, 1e-6)
        assertEquals(52.36, box.north, 1e-6)
        assertEquals(20.85, box.west, 1e-6)
        assertEquals(21.27, box.east, 1e-6)
    }

    @Test
    fun `invalid bounding box becomes null`() {
        assertNull(listOf("a", "b").toGeoBoundsOrNull())
        assertNull(emptyList<String>().toGeoBoundsOrNull())
    }
}
