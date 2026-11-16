package com.tripex.pose.domain.location

import com.tripex.pose.domain.geo.TravelMode
import org.junit.Assert.assertEquals
import org.junit.Test

class TravelModeClassifierTest {

    private fun location(lat: Double, lng: Double) =
        DomainLocation(lat, lng, accuracyMeters = 5f, elapsedRealtimeNanos = 0L, isMock = false)

    /** Steps north by [metres] every 10 s and returns the mode after each step. */
    private fun TravelModeClassifier.walk(startLat: Double, startMs: Long, vararg metres: Double): List<TravelMode> {
        var lat = startLat
        var ms = startMs
        return metres.map { step ->
            val previous = TrackingSession.Fix(lat, 21.0, ms)
            lat += step / METRES_PER_DEGREE
            ms += 10_000
            update(previous, location(lat, 21.0), ms)
        }
    }

    @Test
    fun `the first fix is a walk`() {
        assertEquals(TravelMode.WALK, TravelModeClassifier().update(null, location(52.0, 21.0), 0L))
    }

    @Test
    fun `fast movement switches to vehicle at once`() {
        val modes = TravelModeClassifier().walk(52.0, 0L, 400.0)
        assertEquals(listOf(TravelMode.VEHICLE), modes)
    }

    @Test
    fun `walking speed stays a walk`() {
        val modes = TravelModeClassifier().walk(52.0, 0L, 15.0, 15.0, 15.0)
        assertEquals(List(3) { TravelMode.WALK }, modes)
    }

    @Test
    fun `back to walk only after three slow fixes`() {
        val modes = TravelModeClassifier().walk(52.0, 0L, 400.0, 20.0, 20.0, 20.0)
        assertEquals(
            listOf(TravelMode.VEHICLE, TravelMode.VEHICLE, TravelMode.VEHICLE, TravelMode.WALK),
            modes,
        )
    }

    @Test
    fun `a medium-speed fix resets the slow streak`() {
        // 60 m / 10 s = 21.6 km/h: neither slow nor fast — a traffic jam, not a walk.
        val modes = TravelModeClassifier().walk(52.0, 0L, 400.0, 20.0, 20.0, 60.0, 20.0, 20.0)
        assertEquals(TravelMode.VEHICLE, modes.last())
    }

    @Test
    fun `fixes closer than half a second keep the current mode`() {
        val classifier = TravelModeClassifier()
        classifier.walk(52.0, 0L, 400.0)
        val mode = classifier.update(TrackingSession.Fix(52.0, 21.0, 1_000L), location(52.0001, 21.0), 1_200L)
        assertEquals(TravelMode.VEHICLE, mode)
    }

    private companion object {
        const val METRES_PER_DEGREE = 111_195.0
    }
}
