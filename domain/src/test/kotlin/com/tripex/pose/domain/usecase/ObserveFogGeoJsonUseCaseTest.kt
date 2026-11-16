package com.tripex.pose.domain.usecase

import com.tripex.pose.domain.geo.FogComposer
import com.tripex.pose.domain.geo.TrailPoint
import com.tripex.pose.domain.geo.TravelMode
import com.tripex.pose.domain.geo.atlas.AdminLevel
import com.tripex.pose.domain.geo.atlas.BoundaryFeature
import com.tripex.pose.domain.geo.atlas.BoundaryGeometrySource
import com.tripex.pose.domain.geo.atlas.Ring
import com.tripex.pose.domain.repository.UnlockedPlaceRepository
import com.tripex.pose.domain.repository.UnlockedRegionRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObserveFogGeoJsonUseCaseTest {

    /** The bundle is unreadable: a region must simply not be drawn, never break the fog. */
    private object BrokenBoundaries : BoundaryGeometrySource {
        override suspend fun rings(level: AdminLevel, id: String): Result<List<Ring>> =
            Result.failure(IllegalStateException("no tiles"))

        override suspend fun feature(level: AdminLevel, id: String): BoundaryFeature? = null

        override suspend fun featureAt(level: AdminLevel, lat: Double, lng: Double): BoundaryFeature? = null
    }

    private fun useCase(
        area: FakeUnlockedAreaRepository,
        places: FakeUnlockedPlaceRepository = FakeUnlockedPlaceRepository(),
        regions: FakeUnlockedRegionRepository = FakeUnlockedRegionRepository(),
        dispatcher: TestDispatcher,
    ) = ObserveFogGeoJsonUseCase(area, FogComposer(), regions, places, BrokenBoundaries, dispatcher)

    @Test
    fun `an empty database is the whole world under fog`() = runTest {
        val subject = useCase(FakeUnlockedAreaRepository(), dispatcher = StandardTestDispatcher(testScheduler))

        assertEquals(subject.emptyWorld(), subject().first())
    }

    @Test
    fun `a trail point and a city clear ground even when a region cannot be read`() = runTest {
        val area = FakeUnlockedAreaRepository(
            listOf(1L to TrailPoint(52.2297, 21.0122, TravelMode.WALK, TrailPoint.SEGMENT_SINGLE, 0L)),
        )
        val places = FakeUnlockedPlaceRepository().apply {
            unlock(UnlockedPlaceRepository.UnlockedPlace("osm:R1", "Kraków", 50.06, 19.94, 5_000.0, 0L))
        }
        val regions = FakeUnlockedRegionRepository(
            listOf(UnlockedRegionRepository.UnlockedRegion(AdminLevel.entries.first(), "X", 0L)),
        )
        val subject = useCase(area, places, regions, StandardTestDispatcher(testScheduler))

        val fog = subject().first()

        val clearPolygons = Regex("\"Polygon\"").findAll(fog.clear).count()
        assertEquals("Warsaw and Kraków are far apart", 2, clearPolygons)
        assertTrue(fog.light.contains("Polygon"))
        assertTrue(fog.dense.contains("\"lod\":\"far\""))
    }
}
