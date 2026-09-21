package com.tripex.pose.ui.explore.components

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class BoundarySurfaceMetricsTest {

    @Before
    fun setUp() = BoundarySurfaceMetrics.reset()

    @Test
    fun `a drill-down session builds the surface once and then only re-filters`() {
        BoundarySurfaceMetrics.onStyleBuilt()
        BoundarySurfaceMetrics.onLayersBuilt()

        // Four selection changes: continent → PL → DE → FR → back to PL.
        repeat(4) { BoundarySurfaceMetrics.onFiltersApplied() }

        val snapshot = BoundarySurfaceMetrics.snapshot()
        assertEquals(1, snapshot.styleBuilds)
        assertEquals(1, snapshot.layerBuilds)
        assertEquals(4, snapshot.filterApplications)
    }

    @Test
    fun `the snapshot reads back in a form worth logging`() {
        BoundarySurfaceMetrics.onStyleBuilt()
        BoundarySurfaceMetrics.onFiltersApplied()

        assertEquals(
            "styleBuilds=1 layerBuilds=0 filterApplications=1",
            BoundarySurfaceMetrics.snapshot().toString(),
        )
    }
}
