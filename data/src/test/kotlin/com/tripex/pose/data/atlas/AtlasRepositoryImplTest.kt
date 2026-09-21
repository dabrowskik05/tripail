package com.tripex.pose.data.atlas

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tripex.pose.domain.geo.ContinentId
import com.tripex.pose.domain.geo.projection.GeometryOps
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AtlasRepositoryImplTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: AtlasRepositoryImpl

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        repository = AtlasRepositoryImpl(
            assetManager = context.assets,
            parser = GeoJsonAtlasParser(),
            ioDispatcher = dispatcher,
        )
    }

    @Test
    fun `continents have non-empty continent ids including required places`() = runTest(dispatcher) {
        val continents = repository.continents()
        val ids = continents.map { it.id }.toSet()
        assertTrue(ids.containsAll(
            listOf(
                ContinentId.Europe,
                ContinentId.Asia,
                ContinentId.Oceania,
                ContinentId.Antarctica,
                ContinentId.NorthAmerica,
                ContinentId.Africa,
                ContinentId.SouthAmerica,
            ),
        ))
        assertTrue(continents.all { it.rings.isNotEmpty() })
        assertTrue(continents.all { it.bounds.north >= it.bounds.south })
    }

    @Test
    fun `required islands and Antarctica are present with continent tags`() = runTest(dispatcher) {
        val continents = repository.continents()
        fun contains(lng: Double, lat: Double): ContinentId? {
            for (continent in continents) {
                if (continent.rings.any { ring -> GeometryOps.pointInRing(ring, lng, lat) }) {
                    return continent.id
                }
            }
            return null
        }
        assertEquals(ContinentId.NorthAmerica, contains(-42.0, 72.0)) // Greenland
        assertEquals(ContinentId.Europe, contains(-19.0, 65.0)) // Iceland
        assertEquals(ContinentId.Asia, contains(138.0, 36.0)) // Japan
        assertEquals(ContinentId.Oceania, contains(174.78, -41.29)) // New Zealand
        assertEquals(ContinentId.Antarctica, contains(0.0, -80.0))
    }

    /**
     * Regression guard for the atlas build.
     *
     * The island probes above all passed while Africa and Asia were silently tagged `Europe`,
     * because `ne_50m_land` carries Afro-Eurasia as one polygon and the old build script
     * label-joined that whole landmass from a single country. Only mainland probes catch it, so
     * every continent with a mainland gets one here.
     *
     * Russia is split at the Urals and France's overseas departments carry their own continent,
     * because the atlas is built from map subunits rather than whole countries.
     */
    @Test
    fun `mainland points resolve to their own continent`() = runTest(dispatcher) {
        val continents = repository.continents()
        fun contains(lng: Double, lat: Double): ContinentId? = continents.firstOrNull { continent ->
            continent.rings.any { ring -> GeometryOps.pointInRing(ring, lng, lat) }
        }?.id

        assertEquals(ContinentId.Europe, contains(21.0, 52.2)) // Warsaw
        assertEquals(ContinentId.Africa, contains(31.2, 30.0)) // Cairo
        assertEquals(ContinentId.Africa, contains(36.8, -1.3)) // Nairobi
        assertEquals(ContinentId.Asia, contains(116.4, 39.9)) // Beijing
        assertEquals(ContinentId.Europe, contains(37.6, 55.75)) // Moscow — west of the Urals
        assertEquals(ContinentId.Asia, contains(104.3, 52.3)) // Irkutsk — east of the Urals
        assertEquals(ContinentId.Asia, contains(132.0, 43.3)) // Vladivostok
        assertEquals(ContinentId.Asia, contains(77.2, 28.6)) // Delhi
        assertEquals(ContinentId.NorthAmerica, contains(-87.6, 41.9)) // Chicago
        assertEquals(ContinentId.SouthAmerica, contains(-46.6, -23.5)) // Sao Paulo
        // Overseas departments follow geography, not their parent country's continent.
        assertEquals(ContinentId.SouthAmerica, contains(-53.0, 4.0)) // French Guiana
        assertEquals(ContinentId.Oceania, contains(133.0, -24.0)) // central Australia
    }

    /**
     * Europe must not reach into Africa or Siberia. Both historical bugs — the Afro-Eurasian
     * mega-polygon and French Guiana inheriting France's continent — show up here as a bounding
     * box far larger than Europe.
     */
    @Test
    fun `europe bounds stay over Europe`() = runTest(dispatcher) {
        val europe = repository.continents().first { it.id == ContinentId.Europe }

        assertTrue("Europe reaches too far south: ${europe.bounds}", europe.bounds.south > 25.0)
        assertTrue("Europe reaches too far east: ${europe.bounds}", europe.bounds.east < 80.0)
        assertTrue("Europe reaches too far west: ${europe.bounds}", europe.bounds.west > -40.0)
    }

    @Test
    fun `land rings are non-empty and cache avoids second parse`() = runTest(dispatcher) {
        val first = repository.land()
        val second = repository.land()
        assertTrue(first.rings.isNotEmpty())
        assertEquals(first, second)
        repository.clearCache()
        val third = repository.land()
        assertEquals(first.rings.size, third.rings.size)
    }
}
