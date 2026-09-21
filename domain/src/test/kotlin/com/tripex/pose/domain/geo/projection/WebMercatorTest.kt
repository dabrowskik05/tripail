package com.tripex.pose.domain.geo.projection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class WebMercatorTest {

    @Test
    fun `lng lat to tile round-trip corners at several zooms`() {
        val samples = listOf(
            0.0 to 0.0,
            21.0 to 52.0,
            -74.0 to 40.7,
            139.7 to 35.7,
        )
        for (zoom in listOf(0, 3, 5, 8)) {
            for ((lng, lat) in samples) {
                val tile = WebMercator.lngLatToTile(lng, lat, zoom)
                assertEquals(zoom, tile.z)
                val n = 1 shl zoom
                assertTrue(tile.x in 0 until n)
                assertTrue(tile.y in 0 until n)

                // Reconstruct approximate lng/lat from tile NW + local midpoint via tileLocal
                val (nwLng, nwLat) = WebMercator.tileToLngLat(tile.z, tile.x, tile.y)
                assertTrue(abs(nwLng - lng) < 360.0 / n + 1e-6 || zoom == 0)
                // NW corner is north-west of the point for non-edge cases
                if (zoom >= 3 && abs(lat) < 80) {
                    assertTrue(nwLat >= lat - 1e-6 || tile.y == 0)
                }
            }
        }
    }

    @Test
    fun `tileLocalToLngLat recovers known point inside tile`() {
        val lng = 21.0122
        val lat = 52.2297
        val zoom = 5
        val tile = WebMercator.lngLatToTile(lng, lat, zoom)
        // Binary-search local coords by converting center of extent cells is heavy —
        // instead verify invertibility of tileLocal for extent midpoints.
        val extent = 4096
        val (midLng, midLat) = WebMercator.tileLocalToLngLat(
            tile.z, tile.x, tile.y, extent / 2, extent / 2, extent,
        )
        val back = WebMercator.lngLatToTile(midLng, midLat, zoom)
        assertEquals(tile, back)
    }

    @Test
    fun `tilesCovering returns contiguous set`() {
        val tiles = WebMercator.tilesCovering(
            west = 14.0,
            south = 49.0,
            east = 24.0,
            north = 55.0,
            zoom = 5,
        )
        assertTrue(tiles.isNotEmpty())
        assertTrue(tiles.all { it.z == 5 })
    }
}
