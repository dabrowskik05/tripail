package com.tripex.pose.domain.geo.atlas

import com.tripex.pose.domain.geo.ContinentId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AreaKeyTest {

    @Test
    fun `round-trip continent nav arg`() {
        val key = AreaKey.Continent(ContinentId.Europe)
        assertEquals(key, AreaKey.fromNavArg(key.toNavArg()))
    }

    @Test
    fun `round-trip country nav arg normalizes iso2`() {
        val key = AreaKey.Country("pl")
        val parsed = AreaKey.fromNavArg(key.toNavArg())
        assertEquals(AreaKey.Country("PL"), parsed)
        assertEquals("country:PL", parsed.toNavArg())
    }

    @Test
    fun `round-trip region and city nav args`() {
        val region = AreaKey.Region("POL-14")
        val city = AreaKey.City("place:warsaw")
        assertEquals(region, AreaKey.fromNavArg(region.toNavArg()))
        assertEquals(city, AreaKey.fromNavArg(city.toNavArg()))
    }

    @Test
    fun `args pair round-trips for every kind`() {
        val keys = listOf(
            AreaKey.Continent(ContinentId.Asia),
            AreaKey.Country("DE"),
            AreaKey.Region("DEU-01"),
            AreaKey.City("maptiler:berlin"),
        )

        for (key in keys) {
            val args = key.toArgs()
            assertEquals(key, AreaKey.fromArgs(args.kind, args.id))
        }
    }

    @Test
    fun `args kinds are the documented constants`() {
        assertEquals(AreaKey.KIND_COUNTRY, AreaKey.Country("PL").toArgs().kind)
        assertEquals("PL", AreaKey.Country("pl").toArgs().id)
    }

    @Test
    fun `unknown kind fails`() {
        assertThrows(IllegalStateException::class.java) {
            AreaKey.fromArgs("planet", "earth")
        }
    }

    @Test
    fun `unknown prefix fails`() {
        assertThrows(IllegalStateException::class.java) {
            AreaKey.fromNavArg("unknown:x")
        }
    }
}
