package com.tripex.pose.domain.geo

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FogComposerTest {

    private val composer = FogComposer()

    @Test
    fun `nothing discovered leaves the whole world under fog`() {
        val fog = composer.compose(emptyList(), emptyList(), emptyList())
        assertEquals(composer.empty(), fog)
        assertTrue(fog.clear.endsWith("\"features\":[]}"))
    }

    @Test
    fun `a single point clears a round kilometre`() {
        val fog = composer.compose(listOf(point(52.0, 21.0, TrailPoint.SEGMENT_SINGLE)), emptyList(), emptyList())
        val area = ringAreaKm2(firstRing(fog.clear))
        val expected = PI * 1.0 * 1.0
        // The torn edge moves the outline by up to ~120 m, so the area is close, not exact.
        assertTrue("area was $area", abs(area - expected) / expected < 0.2)
    }

    @Test
    fun `points of one segment join into one corridor`() {
        val points = (0..10).map { point(52.0, 21.0 + it * 0.05, segment = 7) }
        val fog = composer.compose(points, emptyList(), emptyList())
        assertEquals(1, Regex("\"Polygon\"").findAll(fog.clear).count())
    }

    @Test
    fun `stand-alone points far apart stay separate`() {
        val points = listOf(
            point(52.0, 21.0, TrailPoint.SEGMENT_SINGLE),
            point(52.0, 22.0, TrailPoint.SEGMENT_SINGLE),
        )
        val fog = composer.compose(points, emptyList(), emptyList())
        assertEquals(2, Regex("\"Polygon\"").findAll(fog.clear).count())
    }

    @Test
    fun `a small pocket inside a loop is cleared but a big one is not`() {
        fun loop(radiusDeg: Double, segment: Long) = (0..36).map {
            val a = it / 36.0 * 2 * PI
            point(52.0 + radiusDeg * kotlin.math.sin(a), 21.0 + radiusDeg * 1.6 * cos(a), segment)
        }
        // ~1.5 km loop: the pocket in the middle is far below 8 km2.
        val small = composer.compose(loop(0.014, 1), emptyList(), emptyList())
        assertEquals(1, ringCount(small.clear))
        // ~22 km loop: hundreds of km2 stay hidden.
        val big = FogComposer().compose(loop(0.2, 2), emptyList(), emptyList())
        assertEquals(2, ringCount(big.clear))
    }

    @Test
    fun `a city overlapping the trail merges with it`() {
        val points = (0..4).map { point(52.0, 21.0 + it * 0.01, segment = 3) }
        val city = FogComposer.Circle(52.0, 21.02, 3_000.0)
        val fog = composer.compose(points, listOf(city), emptyList())
        assertEquals(1, Regex("\"Polygon\"").findAll(fog.clear).count())
        assertFalse(fog.light.endsWith("\"features\":[]}"))
    }

    @Test
    fun `a long trail builds quickly`() {
        // 50 000 points, ~7 500 km of driving in 100 segments.
        val points = ArrayList<TrailPoint>(50_000)
        for (segment in 1..100) {
            val lat = 45.0 + segment * 0.05
            for (i in 0 until 500) {
                points += point(lat + 0.01 * kotlin.math.sin(i / 20.0), 5.0 + i * 0.002 * segment % 20, segment.toLong())
            }
        }
        val started = System.nanoTime()
        FogComposer().compose(points, emptyList(), emptyList())
        val seconds = (System.nanoTime() - started) / 1e9
        assertTrue("took $seconds s", seconds < 30)
    }

    private fun point(lat: Double, lng: Double, segment: Long) =
        TrailPoint(lat, lng, TravelMode.WALK, segment, discoveredAt = 0L)

    private fun ringCount(json: String): Int = Regex("\\[\\[\\[|\\]\\],\\[\\[").findAll(json).count()

    private fun firstRing(json: String): List<Pair<Double, Double>> {
        val body = json.substringAfter("\"coordinates\":[[").substringBefore("]]")
        return Regex("\\[?(-?[0-9.]+),(-?[0-9.]+)").findAll(body)
            .map { it.groupValues[1].toDouble() to it.groupValues[2].toDouble() }
            .toList()
    }

    private fun ringAreaKm2(ring: List<Pair<Double, Double>>): Double {
        var sum = 0.0
        for (i in ring.indices) {
            val (x1, y1) = ring[i]
            val (x2, y2) = ring[(i + 1) % ring.size]
            sum += x1 * y2 - x2 * y1
        }
        val lat = ring.map { it.second }.average()
        return abs(sum) / 2 * 111.32 * 111.32 * cos(Math.toRadians(lat))
    }
}
