package com.tripex.pose.domain.geo

import com.tripex.pose.domain.geo.atlas.AdminLevel
import com.tripex.pose.domain.geo.atlas.BoundaryFeature
import com.tripex.pose.domain.geo.atlas.BoundaryGeometrySource
import com.tripex.pose.domain.geo.atlas.Ring
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BoundaryMatcherTest {

    private fun matcher(hit: BoundaryFeature?) = BoundaryMatcher(FakeBoundaries(hit))

    private val malopolskie = BoundaryFeature(
        level = AdminLevel.Adm1,
        id = "PL-12",
        name = "Lesser Poland",
        namePl = "Małopolskie",
        countryIso2 = "PL",
    )

    @Test
    fun `matches by point-in-polygon when the name agrees`() = runTest {
        val place = Place("Malopolskie", 50.0, 20.0, kind = PlaceKind.Region)

        assertEquals(malopolskie, matcher(malopolskie).match(AdminLevel.Adm1, place))
    }

    @Test
    fun `diacritics do not break the name check`() = runTest {
        val place = Place("małopolskie", 50.0, 20.0, kind = PlaceKind.Region)

        assertEquals(malopolskie, matcher(malopolskie).match(AdminLevel.Adm1, place))
    }

    @Test
    fun `matches on the English bundle name too`() = runTest {
        val place = Place("Lesser Poland", 50.0, 20.0, kind = PlaceKind.Region)

        assertEquals(malopolskie, matcher(malopolskie).match(AdminLevel.Adm1, place))
    }

    @Test
    fun `no feature under the point means no match`() = runTest {
        val place = Place("Atlantyda", 0.0, -30.0, kind = PlaceKind.Region)

        assertNull(matcher(null).match(AdminLevel.Adm1, place))
    }

    @Test
    fun `an area result keeps the containing feature even when the names differ`() = runTest {
        // The geocoder and Natural Earth disagree on wording all the time; for an area, the
        // polygon the centre falls into is the better evidence.
        val place = Place("Galicja Zachodnia", 50.0, 20.0, kind = PlaceKind.Region)

        assertEquals(malopolskie, matcher(malopolskie).match(AdminLevel.Adm1, place))
    }

    @Test
    fun `a city whose name disagrees is not taken for its region`() = runTest {
        val place = Place("Kraków", 50.06, 19.94, kind = PlaceKind.City)

        assertNull(matcher(malopolskie).match(AdminLevel.Adm1, place))
    }

    private class FakeBoundaries(private val hit: BoundaryFeature?) : BoundaryGeometrySource {
        override suspend fun rings(level: AdminLevel, id: String): Result<List<Ring>> =
            Result.success(emptyList())

        override suspend fun feature(level: AdminLevel, id: String): BoundaryFeature? = hit

        override suspend fun featureAt(
            level: AdminLevel,
            lat: Double,
            lng: Double,
        ): BoundaryFeature? = hit
    }
}
