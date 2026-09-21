package com.tripex.pose.ui.navigation

import com.tripex.pose.domain.geo.ContinentId
import com.tripex.pose.domain.geo.GeoBounds
import com.tripex.pose.domain.geo.atlas.AreaKey
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class RoutesTest {

    private val json = Json

    private val poland = GeoBounds(north = 54.836, south = 49.002, east = 24.146, west = 14.123)

    @Test
    fun `country route round-trips through serialization`() {
        val route = countryRoute("pl", poland)

        val restored = json.decodeFromString<Country>(json.encodeToString(route))

        assertEquals(route, restored)
        assertEquals("PL", restored.iso2)
        assertEquals(poland, restored.bounds())
    }

    @Test
    fun `region route keeps the bbox and the parent country`() {
        val route = regionRoute(regionId = "POL-14", countryIso2 = "pl", bounds = poland)

        val restored = json.decodeFromString<Region>(json.encodeToString(route))

        assertEquals("POL-14", restored.regionId)
        assertEquals("PL", restored.countryIso2)
        assertEquals(poland, restored.bounds())
    }

    @Test
    fun `map view route carries the area key as two primitives`() {
        val route = mapViewRoute(AreaKey.Country("PL"), poland, label = "Polska")

        val restored = json.decodeFromString<MapView>(json.encodeToString(route))

        assertEquals(AreaKey.KIND_COUNTRY, restored.areaKind)
        assertEquals("PL", restored.areaId)
        assertEquals(AreaKey.Country("PL"), restored.areaKey())
        assertEquals(poland, restored.bounds())
        assertEquals("Polska", restored.label)
    }

    @Test
    fun `continent route round-trips its id`() {
        val route = Continent(continentId = ContinentId.Europe.name)

        val restored = json.decodeFromString<Continent>(json.encodeToString(route))

        assertEquals(ContinentId.Europe, ContinentId.valueOf(restored.continentId))
    }

    @Test
    fun `area key survives the nav argument pair`() {
        val keys = listOf(
            AreaKey.Continent(ContinentId.Oceania),
            AreaKey.Country("PL"),
            AreaKey.Region("POL-14"),
            AreaKey.City("maptiler:warsaw"),
        )

        for (key in keys) {
            val args = key.toArgs()
            assertEquals(key, AreaKey.fromArgs(args.kind, args.id))
        }
    }
}
