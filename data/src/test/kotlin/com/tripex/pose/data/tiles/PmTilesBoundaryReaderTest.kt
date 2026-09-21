package com.tripex.pose.data.tiles

import com.tripex.pose.domain.geo.atlas.AdminLevel
import com.tripex.pose.domain.geo.atlas.BoundaryTilesProvider
import com.tripex.pose.domain.geo.projection.GeometryOps
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PmTilesBoundaryReaderTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var reader: PmTilesBoundaryReader
    private lateinit var fixture: File

    @Before
    fun setUp() {
        val url = requireNotNull(javaClass.classLoader?.getResource("tiles/boundaries.pmtiles")) {
            "Missing test fixture tiles/boundaries.pmtiles"
        }
        fixture = File(url.toURI())
        val provider = object : BoundaryTilesProvider {
            override fun styleSourceUri(): String = "pmtiles://file://${fixture.absolutePath}"
            override fun localFilePath(): String = fixture.absolutePath
        }
        reader = PmTilesBoundaryReader(provider, dispatcher)
    }

    @Test
    fun `rings for Poland fall inside expected bbox`() = runTest(dispatcher) {
        val result = reader.rings(AdminLevel.Adm0, "PL")
        assertTrue(result.isSuccess)
        val rings = result.getOrThrow()
        assertTrue(rings.isNotEmpty())
        val bounds = GeometryOps.boundsOf(rings)
        assertTrue(bounds.west in 13.0..16.0 || bounds.west > 14.0)
        assertTrue(bounds.east in 22.0..25.0)
        assertTrue(bounds.south in 48.0..51.0)
        assertTrue(bounds.north in 53.0..56.0)
    }

    @Test
    fun `unknown country id fails`() = runTest(dispatcher) {
        val result = reader.rings(AdminLevel.Adm0, "ZZ")
        assertTrue(result.isFailure)
    }

    @Test
    fun `rings for ADM1 region fall inside Poland`() = runTest(dispatcher) {
        val result = reader.rings(AdminLevel.Adm1, "POL-3146")
        assertTrue(result.isSuccess)
        val bounds = GeometryOps.boundsOf(result.getOrThrow())
        assertTrue(bounds.west > 14.0)
        assertTrue(bounds.east < 25.0)
        assertTrue(bounds.south > 48.0)
        assertTrue(bounds.north < 55.5)
    }

    @Test
    fun `featureAt hits a Polish region near Warsaw`() = runTest(dispatcher) {
        val feature = reader.featureAt(AdminLevel.Adm1, lat = 52.23, lng = 21.01)
        assertTrue(feature != null)
        assertTrue(feature!!.id.startsWith("POL-"))
        assertTrue(feature.countryIso2 == "PL")
    }
}
