package com.tripex.pose.data.mapper

import com.tripex.pose.data.geo.H3Utils
import com.tripex.pose.domain.geo.H3Converter
import com.tripex.pose.domain.geo.H3Config
import com.uber.h3core.H3Core
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class UnlockedHexMapperTest {

    private lateinit var h3: H3Converter

    @Before
    fun setUp() {
        h3 = H3Utils(H3Core.newInstance())
    }

    @Test
    fun `Long to entity stores parents at lod resolutions`() {
        val index = h3.cellAt(52.2297, 21.0122)
        val discoveredAt = 1_700_000_000_000L
        val entity = index.toUnlockedHexEntity(h3, discoveredAt)
        assertEquals(index, entity.h3Index)
        assertEquals(h3.parentOf(index, H3Config.LOD_MID_RESOLUTION), entity.parentRes9)
        assertEquals(h3.parentOf(index, H3Config.LOD_FAR_RESOLUTION), entity.parentRes7)
        assertEquals(discoveredAt, entity.discoveredAt)
        assertEquals(H3Config.WALKING_RESOLUTION, entity.resolution)
    }
}
