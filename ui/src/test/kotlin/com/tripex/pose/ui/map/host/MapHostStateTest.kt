package com.tripex.pose.ui.map.host

import com.tripex.pose.domain.geo.GeoBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapHostStateTest {

    private val europe = GeoBounds(north = 71.0, south = 35.0, east = 40.0, west = -10.0)
    private val player = GeoBounds(north = 54.0, south = 50.0, east = 24.0, west = 18.0)

    /**
     * Entering a continent places it whole and then flies to the player. With one slot the
     * flight could overwrite the placement before the map applied it, so only one happened.
     */
    @Test
    fun `camera requests are applied in order, none replaced`() {
        val host = MapHostState()

        host.flyTo(europe, animate = false, fill = true)
        host.flyTo(player, animate = true)

        val first = host.cameraRequest!!
        assertEquals(europe, first.bounds)
        assertFalse(first.animate)

        host.onCameraApplied(first.token)
        val second = host.cameraRequest!!
        assertEquals(player, second.bounds)
        assertTrue(second.animate)

        host.onCameraApplied(second.token)
        assertNull(host.cameraRequest)
    }

    @Test
    fun `a stale token does not skip the request in front`() {
        val host = MapHostState()
        host.flyTo(europe)
        val head = host.cameraRequest!!

        host.onCameraApplied(head.token + 100)

        assertEquals(head, host.cameraRequest)
    }
}
