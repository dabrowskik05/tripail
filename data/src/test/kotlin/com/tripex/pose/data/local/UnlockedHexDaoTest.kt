package com.tripex.pose.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tripex.pose.data.geo.H3Utils
import com.tripex.pose.data.mapper.toUnlockedHexEntity
import com.tripex.pose.domain.geo.H3Config
import com.tripex.pose.domain.geo.H3Converter
import com.uber.h3core.H3Core
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UnlockedHexDaoTest {

    private lateinit var database: TripexPoseDatabase
    private lateinit var dao: UnlockedHexDao
    private lateinit var h3: H3Converter

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, TripexPoseDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.unlockedHexDao()
        h3 = H3Utils(H3Core.newInstance())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `insertAll twice with same rows leaves count unchanged`() = runTest {
        val cells = h3.revealDisk(52.2297, 21.0122, k = 1)
        val entities = cells.map { it.toUnlockedHexEntity(h3, discoveredAt = 1L) }
        dao.insertAll(entities)
        dao.insertAll(entities)
        assertEquals(cells.size, dao.observeCount().first())
    }

    @Test
    fun `observeTrail returns each resolution-9 parent once, sorted`() = runTest {
        val cells = h3.revealAround(52.2297, 21.0122, radiusMeters = 500.0)
        dao.insertAll(cells.map { it.toUnlockedHexEntity(h3, discoveredAt = 1L) })

        val expected = cells.map { h3.parentOf(it, H3Config.TRAIL_RESOLUTION) }.distinct().sorted()
        assertEquals(expected, dao.observeTrail().first())
    }
}
