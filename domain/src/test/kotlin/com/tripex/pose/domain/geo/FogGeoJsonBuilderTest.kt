package com.tripex.pose.domain.geo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FogGeoJsonBuilderTest {
    private val builder = FogGeoJsonBuilder()

    @Test
    fun `empty outline yields world polygon only`() {
        val json = builder.build(FogGeometry.EMPTY)
        assertTrue(json.contains("FeatureCollection"))
        assertTrue(json.contains("-180.0"))
        assertTrue(json.contains("85.05112878"))
        // No hole rings beyond the world ring itself in coordinates nesting depth check:
        assertTrue(json.contains("\"type\":\"Polygon\""))
    }

    @Test
    fun `unlocked outer ring becomes a hole in world fog`() {
        val hole =
            listOf(
                21.0 to 52.0,
                21.1 to 52.0,
                21.1 to 52.1,
                21.0 to 52.1,
                21.0 to 52.0,
            )
        val json = builder.build(FogGeometry(listOf(listOf(hole))))
        assertTrue(json.contains("21.0,52.0"))
        assertTrue(json.indexOf("21.0") > json.indexOf("-180.0"))
    }

    @Test
    fun `inner ring becomes a separate fog island feature`() {
        val outer =
            listOf(
                21.0 to 52.0,
                21.2 to 52.0,
                21.2 to 52.2,
                21.0 to 52.2,
                21.0 to 52.0,
            )
        val inner =
            listOf(
                21.05 to 52.05,
                21.15 to 52.05,
                21.15 to 52.15,
                21.05 to 52.15,
                21.05 to 52.05,
            )
        val json = builder.build(FogGeometry(listOf(listOf(outer, inner))))
        // Two features: world+hole and island
        val featureCount = "\"type\":\"Feature\"".toRegex().findAll(json).count()
        assertTrue(featureCount >= 2)
        assertTrue(json.contains("21.05,52.05"))
    }

    @Test
    fun `coordinates stay lng then lat`() {
        val ring = listOf(21.0122 to 52.2297, 21.02 to 52.2297, 21.02 to 52.24, 21.0122 to 52.2297)
        val json = builder.build(FogGeometry(listOf(listOf(ring))))
        assertTrue(json.contains("[21.0122,52.2297]"))
        assertFalse(json.contains("[52.2297,21.0122]"))
    }

    @Test
    fun `whole-region outlines are punched into the same world polygon as H3 cells`() {
        val cellRing = listOf(20.0 to 52.0, 20.1 to 52.0, 20.1 to 52.1, 20.0 to 52.0)
        val regionRing = listOf(14.0 to 49.0, 24.0 to 49.0, 24.0 to 55.0, 14.0 to 49.0)

        val json = FogGeoJsonBuilder().build(
            FogGeometry(listOf(listOf(cellRing))),
            extraHoles = listOf(regionRing),
        )

        // One Feature, one Polygon: world ring + the cell hole + the region hole.
        assertEquals(1, json.split("\"type\":\"Feature\"").size - 1)
        assertTrue("cell hole missing", json.contains("[20.1,52.1]"))
        assertTrue("region hole missing", json.contains("[24.0,55.0]"))
    }
}
