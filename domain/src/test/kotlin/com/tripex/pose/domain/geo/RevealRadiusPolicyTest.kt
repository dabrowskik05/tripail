package com.tripex.pose.domain.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RevealRadiusPolicyTest {

    private fun place(
        bounds: GeoBounds? = null,
        kind: PlaceKind = PlaceKind.Unknown,
    ) = Place(
        displayName = "test",
        latitude = 52.23,
        longitude = 21.01,
        boundingBox = bounds,
        kind = kind,
    )

    @Test
    fun `a metropolis bbox yields a radius that covers the whole city`() {
        // Warsaw is roughly 0.30° x 0.26° — about 26 km across.
        val warsaw = GeoBounds(north = 52.37, south = 52.10, east = 21.27, west = 20.85)

        val radius = RevealRadiusPolicy.radiusMeters(place(warsaw))

        assertTrue("expected a city-scale radius, got $radius", radius > 12_000.0)
        assertTrue(radius <= RevealRadiusPolicy.MAX_RADIUS_M)
    }

    @Test
    fun `a measured place is padded by a kilometre, never trimmed`() {
        val town = GeoBounds(north = 52.06, south = 51.94, east = 21.06, west = 20.94)

        val radius = RevealRadiusPolicy.radiusMeters(place(town))

        assertEquals(
            RevealRadiusPolicy.halfDiagonalMeters(town) + RevealRadiusPolicy.CITY_PADDING_M,
            radius,
            0.001,
        )
        assertTrue(
            "the padding has to reach past the boundary itself",
            radius > RevealRadiusPolicy.halfDiagonalMeters(town),
        )
    }

    @Test
    fun `a village bbox is the village plus its kilometre of slack`() {
        val village = GeoBounds(north = 52.005, south = 51.995, east = 21.005, west = 20.995)

        val radius = RevealRadiusPolicy.radiusMeters(place(village))

        assertEquals(
            RevealRadiusPolicy.halfDiagonalMeters(village) + RevealRadiusPolicy.CITY_PADDING_M,
            radius,
            0.001,
        )
    }

    @Test
    fun `an enormous bbox is clamped rather than unlocking a continent`() {
        val huge = GeoBounds(north = 60.0, south = 40.0, east = 40.0, west = 0.0)

        assertEquals(RevealRadiusPolicy.MAX_RADIUS_M, RevealRadiusPolicy.radiusMeters(place(huge)), 0.001)
    }

    @Test
    fun `without a bbox the radius comes from the place kind`() {
        assertEquals(8_000.0, RevealRadiusPolicy.radiusMeters(place(kind = PlaceKind.City)), 0.001)
        assertEquals(4_000.0, RevealRadiusPolicy.radiusMeters(place(kind = PlaceKind.Town)), 0.001)
        assertEquals(3_000.0, RevealRadiusPolicy.radiusMeters(place(kind = PlaceKind.Municipality)), 0.001)
        assertEquals(2_000.0, RevealRadiusPolicy.radiusMeters(place(kind = PlaceKind.Village)), 0.001)
        assertEquals(3_000.0, RevealRadiusPolicy.radiusMeters(place(kind = PlaceKind.Unknown)), 0.001)
    }

    @Test
    fun `a single address is floored at the minimum, not left at 800 m`() {
        // The fallback is 800 m, but no unlock is allowed to be smaller than MIN_RADIUS_M.
        assertEquals(
            RevealRadiusPolicy.MIN_RADIUS_M,
            RevealRadiusPolicy.radiusMeters(place(kind = PlaceKind.Address)),
            0.001,
        )
    }

    @Test
    fun `half diagonal is measured at the box's own latitude`() {
        // One degree of longitude shrinks with latitude; the same box is narrower up north.
        val equator = GeoBounds(north = 0.1, south = -0.1, east = 0.1, west = -0.1)
        val arctic = GeoBounds(north = 70.1, south = 69.9, east = 0.1, west = -0.1)

        assertTrue(
            RevealRadiusPolicy.halfDiagonalMeters(equator) >
                RevealRadiusPolicy.halfDiagonalMeters(arctic),
        )
    }
}
