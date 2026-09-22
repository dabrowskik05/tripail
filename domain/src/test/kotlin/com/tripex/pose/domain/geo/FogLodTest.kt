package com.tripex.pose.domain.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FogLodTest {

    @Test
    fun `close in draws exact cells`() {
        assertEquals(FogLod.Near, FogLod.forZoom(FogLod.NEAR_ZOOM))
        assertEquals(H3Config.WALKING_RESOLUTION, FogLod.Near.resolution)
    }

    @Test
    fun `mid range draws coarser parents`() {
        assertEquals(FogLod.Mid, FogLod.forZoom(FogLod.MID_ZOOM))
        assertEquals(H3Config.LOD_MID_RESOLUTION, FogLod.Mid.resolution)
    }

    @Test
    fun `far out draws the coarsest parents`() {
        assertEquals(FogLod.Far, FogLod.forZoom(2.0))
        assertEquals(H3Config.LOD_FAR_RESOLUTION, FogLod.Far.resolution)
    }

    /**
     * The property that replaces the old `LIMIT 20000`: far out, the query has no viewport and
     * therefore no way to drop discovered ground for being somewhere else — or for being old.
     */
    @Test
    fun `only the far level is global`() {
        assertFalse(FogLod.Near.isViewportScoped.not())
        assertFalse(FogLod.Mid.isViewportScoped.not())
        assertFalse(FogLod.Far.isViewportScoped)
    }

    @Test
    fun `resolution gets coarser as the camera pulls back`() {
        assertTrue(FogLod.Near.resolution > FogLod.Mid.resolution)
        assertTrue(FogLod.Mid.resolution > FogLod.Far.resolution)
    }

    @Test
    fun `the thresholds do not overlap`() {
        assertEquals(FogLod.Near, FogLod.forZoom(FogLod.NEAR_ZOOM + 0.01))
        assertEquals(FogLod.Mid, FogLod.forZoom(FogLod.NEAR_ZOOM - 0.01))
        assertEquals(FogLod.Far, FogLod.forZoom(FogLod.MID_ZOOM - 0.01))
    }
}
