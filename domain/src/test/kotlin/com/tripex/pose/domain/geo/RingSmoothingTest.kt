package com.tripex.pose.domain.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RingSmoothingTest {

    private val square = listOf(0.0 to 0.0, 1.0 to 0.0, 1.0 to 1.0, 0.0 to 1.0, 0.0 to 0.0)

    @Test
    fun `result stays closed`() {
        val smoothed = RingSmoothing.smooth(square)
        assertEquals(smoothed.first(), smoothed.last())
    }

    @Test
    fun `each pass doubles the corners`() {
        // 4 corners → 8 → 16, plus the closing point.
        assertEquals(4 * 2 * 2 + 1, RingSmoothing.smooth(square, iterations = 2).size)
    }

    @Test
    fun `corners are cut, never pushed outward`() {
        val smoothed = RingSmoothing.smooth(square)
        assertTrue(smoothed.none { (x, y) -> x == 0.0 && y == 0.0 })
        assertTrue(smoothed.all { (x, y) -> x in 0.0..1.0 && y in 0.0..1.0 })
    }

    @Test
    fun `one pass places points a quarter along each edge`() {
        val smoothed = RingSmoothing.smooth(square, iterations = 1)
        assertEquals(0.25 to 0.0, smoothed[0])
        assertEquals(0.75 to 0.0, smoothed[1])
    }

    @Test
    fun `degenerate ring is returned untouched`() {
        val line = listOf(0.0 to 0.0, 1.0 to 1.0, 0.0 to 0.0)
        assertEquals(line, RingSmoothing.smooth(line))
    }
}
