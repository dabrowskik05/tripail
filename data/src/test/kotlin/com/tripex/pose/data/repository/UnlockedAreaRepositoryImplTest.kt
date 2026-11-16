package com.tripex.pose.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tripex.pose.data.geo.H3Utils
import com.tripex.pose.data.local.TripexPoseDatabase
import com.tripex.pose.domain.geo.H3Config
import com.tripex.pose.domain.geo.TrailPoint
import com.tripex.pose.domain.geo.TravelMode
import com.uber.h3core.H3Core
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UnlockedAreaRepositoryImplTest {

    private lateinit var database: TripexPoseDatabase
    private lateinit var repository: UnlockedAreaRepositoryImpl
    private val h3 = H3Utils(H3Core.newInstance())

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, TripexPoseDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = UnlockedAreaRepositoryImpl(database.trailPointDao(), h3, Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun cellOf(lat: Double, lng: Double) = h3.parentOf(h3.cellAt(lat, lng), H3Config.TRAIL_RESOLUTION)

    private fun point(lat: Double, lng: Double, mode: TravelMode, segment: Long) =
        TrailPoint(lat, lng, mode, segment, discoveredAt = 1L)

    @Test
    fun `walked ground is known to everyone, driven ground only to drivers`() = runTest {
        val walked = cellOf(52.2297, 21.0122)
        val driven = cellOf(52.30, 21.20)
        repository.addPoint(walked, point(52.2297, 21.0122, TravelMode.WALK, 1L))
        repository.addPoint(driven, point(52.30, 21.20, TravelMode.VEHICLE, 2L))

        assertTrue(repository.isKnown(walked, TravelMode.WALK))
        assertTrue(repository.isKnown(walked, TravelMode.VEHICLE))
        assertFalse(repository.isKnown(driven, TravelMode.WALK))
        assertTrue(repository.isKnown(driven, TravelMode.VEHICLE))
    }

    @Test
    fun `points come back grouped by segment with their mode`() = runTest {
        repository.addPoint(cellOf(50.0, 19.0), point(50.0, 19.0, TravelMode.VEHICLE, 2L))
        repository.addPoint(cellOf(52.0, 21.0), point(52.0, 21.0, TravelMode.WALK, 1L))
        repository.addPoint(cellOf(50.01, 19.0), point(50.01, 19.0, TravelMode.VEHICLE, 2L))

        val points = repository.observePoints().first()

        assertEquals(listOf(1L, 2L, 2L), points.map { it.segmentId })
        assertEquals(listOf(TravelMode.WALK, TravelMode.VEHICLE, TravelMode.VEHICLE), points.map { it.mode })
        assertEquals(listOf(50.0, 50.01), points.drop(1).map { it.latitude })
    }

    @Test
    fun `statistics count each cell once`() = runTest {
        val cell = cellOf(52.0, 21.0)
        repository.addPoints(
            listOf(
                cell to point(52.0, 21.0, TravelMode.WALK, 1L),
                cell to point(52.0, 21.0, TravelMode.VEHICLE, 2L),
            ),
        )

        assertEquals(1, repository.observeCount().first())
        assertEquals(listOf(cell), repository.observeTrail().first())
        assertEquals(listOf(h3.parentOf(cell, H3Config.COARSE_RESOLUTION)), repository.observeFar().first())
    }
}
