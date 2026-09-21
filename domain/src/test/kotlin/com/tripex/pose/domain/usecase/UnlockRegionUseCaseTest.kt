package com.tripex.pose.domain.usecase

import com.tripex.pose.domain.geo.atlas.AdminLevel
import com.tripex.pose.domain.geo.atlas.BoundaryFeature
import com.tripex.pose.domain.geo.atlas.BoundaryGeometrySource
import com.tripex.pose.domain.geo.atlas.Ring
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnlockRegionUseCaseTest {

    private val square: Ring = listOf(
        20.0 to 52.0,
        21.0 to 52.0,
        21.0 to 53.0,
        20.0 to 53.0,
        20.0 to 52.0,
    )

    @Test
    fun `unlocking a region stores its id, not its geometry`() = runTest {
        val repo = FakeUnlockedRegionRepository()
        val useCase = UnlockRegionUseCase(FakeBoundaries(listOf(square)), repo)

        val result = useCase(AdminLevel.Adm1, "POL-14")

        assertTrue(result.getOrThrow())
        assertEquals(1, repo.regions.value.size)
        assertEquals("POL-14", repo.regions.value.single().featureId)
    }

    @Test
    fun `unlocking the same region twice is a no-op`() = runTest {
        val repo = FakeUnlockedRegionRepository()
        val useCase = UnlockRegionUseCase(FakeBoundaries(listOf(square)), repo)

        useCase(AdminLevel.Adm1, "POL-14").getOrThrow()
        val second = useCase(AdminLevel.Adm1, "POL-14")

        assertFalse("second unlock must report nothing new", second.getOrThrow())
        assertEquals(1, repo.regions.value.size)
    }

    @Test
    fun `a region with no outline in the bundle is refused`() = runTest {
        val repo = FakeUnlockedRegionRepository()
        val useCase = UnlockRegionUseCase(FakeBoundaries(emptyList()), repo)

        assertTrue(useCase(AdminLevel.Adm1, "GHOST-1").isFailure)
        assertTrue("nothing may be stored without geometry", repo.regions.value.isEmpty())
    }

    @Test
    fun `a blank id never reaches the bundle`() = runTest {
        val boundaries = FakeBoundaries(listOf(square))
        val useCase = UnlockRegionUseCase(boundaries, FakeUnlockedRegionRepository())

        assertTrue(useCase(AdminLevel.Adm1, "  ").isFailure)
        assertEquals(0, boundaries.calls)
    }

    private class FakeBoundaries(private val rings: List<Ring>) : BoundaryGeometrySource {
        var calls = 0
            private set

        override suspend fun rings(level: AdminLevel, id: String): Result<List<Ring>> {
            calls++
            return Result.success(rings)
        }

        override suspend fun feature(level: AdminLevel, id: String): BoundaryFeature? = null
        override suspend fun featureAt(level: AdminLevel, lat: Double, lng: Double): BoundaryFeature? = null
    }
}
