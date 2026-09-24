package com.tripex.pose.data.geo

import com.tripex.pose.domain.geo.FogGeometry
import com.tripex.pose.domain.geo.H3Converter
import com.tripex.pose.domain.geo.H3Config
import com.uber.h3core.H3Core
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class H3UtilsTest {

    private lateinit var h3Core: H3Core
    private lateinit var utils: H3Utils

    @Before
    fun setUp() {
        h3Core = H3Core.newInstance()
        utils = H3Utils(h3Core)
    }

    // --- Conversion ---

    @Test
    fun `cellAt for known point returns resolution 11`() {
        val cell = utils.cellAt(WARSAW_LAT, WARSAW_LNG)
        assertEquals(H3Config.WALKING_RESOLUTION, h3Core.getResolution(cell))
        assertTrue(h3Core.isValidCell(cell))
    }

    @Test
    fun `cellAt is deterministic`() {
        val a = utils.cellAt(WARSAW_LAT, WARSAW_LNG)
        val b = utils.cellAt(WARSAW_LAT, WARSAW_LNG)
        assertEquals(a, b)
    }

    @Test
    fun `points 5m apart share a cell, 200m apart do not`() {
        val origin = utils.cellAt(WARSAW_LAT, WARSAW_LNG)
        val near = utils.cellAt(WARSAW_LAT + metersToLatDegrees(5.0), WARSAW_LNG)
        val far = utils.cellAt(WARSAW_LAT + metersToLatDegrees(200.0), WARSAW_LNG)
        assertEquals(origin, near)
        assertNotEquals(origin, far)
    }

    // --- Reveal disk ---

    @Test
    fun `revealDisk k0 returns exactly one cell`() {
        val disk = utils.revealDisk(WARSAW_LAT, WARSAW_LNG, k = 0)
        assertEquals(1, disk.size)
    }

    @Test
    fun `revealDisk k1 returns seven cells over land`() {
        val disk = utils.revealDisk(WARSAW_LAT, WARSAW_LNG, k = 1)
        assertEquals(7, disk.size)
    }

    @Test
    fun `revealDisk k2 returns nineteen cells`() {
        val disk = utils.revealDisk(WARSAW_LAT, WARSAW_LNG, k = 2)
        assertEquals(19, disk.size)
    }

    @Test
    fun `revealDisk cells are valid resolution 11`() {
        val disk = utils.revealDisk(WARSAW_LAT, WARSAW_LNG, k = 1)
        disk.forEach { cell ->
            assertTrue(h3Core.isValidCell(cell))
            assertEquals(H3Config.WALKING_RESOLUTION, h3Core.getResolution(cell))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `revealDisk rejects k outside 0 to 3`() {
        utils.revealDisk(WARSAW_LAT, WARSAW_LNG, k = 4)
    }

    @Test
    fun `a country in several pieces is filled piece by piece, even with an island first`() {
        // A small island listed before the mainland used to become "the country", with the
        // mainland treated as a hole — the root of the Spain / France / Portugal crash.
        val island = listOf(3.0 to 39.5, 3.4 to 39.5, 3.4 to 39.8, 3.0 to 39.8, 3.0 to 39.5)
        val mainland = listOf(-6.0 to 38.0, -1.0 to 38.0, -1.0 to 42.0, -6.0 to 42.0, -6.0 to 38.0)

        val both = utils.cellsForPolygon(listOf(island, mainland), resolution = 5)
        val mainlandOnly = utils.cellsForPolygon(listOf(mainland), resolution = 5)

        assertTrue(mainlandOnly.isNotEmpty())
        assertTrue(both.containsAll(mainlandOnly))
    }

    @Test
    fun `revealAround 5km returns large contiguous disk`() {
        val disk = utils.revealAround(WARSAW_LAT, WARSAW_LNG, radiusMeters = 5_000.0)
        // k ≈ 117 → 3*k*(k+1)+1 cells
        assertTrue(disk.size > 1_000)
        assertTrue(disk.size < 50_000)
        assertTrue(disk.contains(utils.cellAt(WARSAW_LAT, WARSAW_LNG)))
    }

    // --- Bridging ---

    @Test
    fun `bridge same cell returns empty`() {
        val cell = utils.cellAt(WARSAW_LAT, WARSAW_LNG)
        assertTrue(utils.bridge(cell, cell).isEmpty())
    }

    @Test
    fun `bridge over about 100m returns continuous path`() {
        val from = utils.cellAt(WARSAW_LAT, WARSAW_LNG)
        val to = utils.cellAt(WARSAW_LAT + metersToLatDegrees(100.0), WARSAW_LNG)
        val path = utils.bridge(from, to).toList()
        assertTrue(path.contains(from))
        assertTrue(path.contains(to))
        // gridPathCells order is contiguous
        val ordered = h3Core.gridPathCells(from, to)
        for (i in 0 until ordered.lastIndex) {
            assertEquals(1, utils.gridDistance(ordered[i], ordered[i + 1]))
        }
    }

    @Test
    fun `bridge over 5km returns empty anti-teleport`() {
        val from = utils.cellAt(WARSAW_LAT, WARSAW_LNG)
        val to = utils.cellAt(WARSAW_LAT + metersToLatDegrees(5_000.0), WARSAW_LNG)
        assertTrue(utils.bridge(from, to).isEmpty())
    }

    @Test
    fun `gridDistance returns -1 for uncomputable pair without throwing`() {
        // Same cell is distance 0; use a deliberately invalid index for -1 path via runCatching
        val valid = utils.cellAt(WARSAW_LAT, WARSAW_LNG)
        val distance = utils.gridDistance(valid, 0L)
        assertEquals(-1, distance)
    }

    // --- LOD ---

    @Test
    fun `parentOf res9 is shared by disk children`() {
        val disk = utils.revealDisk(WARSAW_LAT, WARSAW_LNG, k = 0)
        val cell = disk.first()
        val parent = utils.parentOf(cell, H3Config.TRAIL_RESOLUTION)
        assertEquals(H3Config.TRAIL_RESOLUTION, h3Core.getResolution(parent))
        assertEquals(parent, utils.parentOf(cell, H3Config.TRAIL_RESOLUTION))
    }

    @Test
    fun `parentOf children under same parent share res9`() {
        val center = utils.cellAt(WARSAW_LAT, WARSAW_LNG)
        val parentMid = utils.parentOf(center, H3Config.TRAIL_RESOLUTION)
        val children = h3Core.cellToChildren(parentMid, H3Config.WALKING_RESOLUTION)
        val parents = children.map { utils.parentOf(it, H3Config.TRAIL_RESOLUTION) }.toSet()
        assertEquals(1, parents.size)
        assertEquals(H3Config.TRAIL_RESOLUTION, h3Core.getResolution(parents.first()))
    }

    // --- Geometry ---

    @Test
    fun `outline empty returns EMPTY`() {
        assertEquals(FogGeometry.EMPTY, utils.outline(emptySet()))
    }

    @Test
    fun `outline single cell is closed hexagon ring`() {
        val cell = utils.cellAt(WARSAW_LAT, WARSAW_LNG)
        val geometry = utils.outline(setOf(cell))
        assertEquals(1, geometry.polygons.size)
        assertEquals(1, geometry.polygons[0].size)
        val ring = geometry.polygons[0][0]
        assertEquals(7, ring.size)
        assertEquals(ring.first(), ring.last())
    }

    @Test
    fun `outline of seven neighbours merges to one polygon`() {
        val disk = utils.revealDisk(WARSAW_LAT, WARSAW_LNG, k = 1)
        assertEquals(7, disk.size)
        val geometry = utils.outline(disk)
        assertEquals(1, geometry.polygons.size)
    }

    @Test
    fun `outline coordinates are lng then lat for Warsaw`() {
        val cell = utils.cellAt(WARSAW_LAT, WARSAW_LNG)
        val ring = utils.outline(setOf(cell)).polygons.first().first()
        val (lng, lat) = ring.first()
        // Warsaw: lng ~21, lat ~52 — first component must be longitude
        assertTrue("expected lng near 21, got $lng", lng in 20.0..22.0)
        assertTrue("expected lat near 52, got $lat", lat in 51.0..53.0)
    }

    @Test
    fun `outline of ring without center has outer and hole rings`() {
        val disk = utils.revealDisk(WARSAW_LAT, WARSAW_LNG, k = 1)
        val center = utils.cellAt(WARSAW_LAT, WARSAW_LNG)
        val ringOnly = disk - center
        assertEquals(6, ringOnly.size)
        val geometry = utils.outline(ringOnly)
        assertEquals(1, geometry.polygons.size)
        assertEquals(2, geometry.polygons[0].size)
    }

    private companion object {
        const val WARSAW_LAT = 52.2297
        const val WARSAW_LNG = 21.0122

        fun metersToLatDegrees(meters: Double): Double = meters / 111_320.0
    }
}
