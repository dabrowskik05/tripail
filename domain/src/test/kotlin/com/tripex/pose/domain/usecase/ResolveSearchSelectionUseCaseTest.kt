package com.tripex.pose.domain.usecase

import com.tripex.pose.domain.geo.BoundaryMatcher
import com.tripex.pose.domain.geo.GeoBounds
import com.tripex.pose.domain.geo.Place
import com.tripex.pose.domain.geo.PlaceKind
import com.tripex.pose.domain.geo.atlas.AdminLevel
import com.tripex.pose.domain.geo.atlas.BoundaryFeature
import com.tripex.pose.domain.geo.atlas.BoundaryGeometrySource
import com.tripex.pose.domain.geo.atlas.Ring
import com.tripex.pose.domain.repository.UnlockedPlaceRepository
import com.tripex.pose.domain.settings.AppLanguage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolveSearchSelectionUseCaseTest {

    private val regions = FakeUnlockedRegionRepository()
    private val places = FakeUnlockedPlaceRepository()
    private val appLanguage = FakeAppLanguageRepository()

    private fun useCase(
        boundaries: BoundaryGeometrySource = Bundle(),
        language: AppLanguage = AppLanguage.Polish,
    ) = ResolveSearchSelectionUseCase(
        boundaryMatcher = BoundaryMatcher(boundaries),
        boundaries = boundaries,
        regions = regions,
        places = places,
        appLanguage = appLanguage.also { it.current.value = language },
    )

    private fun place(
        name: String,
        kind: PlaceKind,
        lat: Double = 52.0,
        lng: Double = 21.0,
    ) = Place(
        displayName = name,
        latitude = lat,
        longitude = lng,
        boundingBox = GeoBounds(north = lat + 0.1, south = lat - 0.1, east = lng + 0.1, west = lng - 0.1),
        kind = kind,
        id = "id-$name",
    )

    /**
     * The contract of this stage: picking a suggestion describes a place, it does not claim it.
     * Searching for somewhere to look at must not quietly own it.
     */
    @Test
    fun `resolving writes nothing`() = runTest {
        useCase()(place("Warszawa", PlaceKind.City)).getOrThrow()
        useCase()(place("mazowieckie", PlaceKind.Region)).getOrThrow()
        useCase()(place("Polska", PlaceKind.Country)).getOrThrow()

        assertTrue(places.places.value.isEmpty())
        assertTrue(regions.regions.value.isEmpty())
    }

    @Test
    fun `a city resolves to a circle with a policy radius`() = runTest {
        val selection = useCase()(place("Warszawa", PlaceKind.City)).getOrThrow()

        val target = selection.target as RevealTarget.Circle
        assertTrue(target.radiusMeters > 0.0)
        assertFalse(selection.isRevealed)
        assertFalse("nothing owned yet, so nothing to give back", selection.canCover)
    }

    /** The map shows the city's country's regions, so the selection has to know the country. */
    @Test
    fun `a city knows its country, from the geocoder or else from the bundle`() = runTest {
        val fromGeocoder = useCase()(place("Praga", PlaceKind.City).copy(countryCode = "CZ")).getOrThrow()
        val fromBundle = useCase()(place("Warszawa", PlaceKind.City)).getOrThrow()

        assertEquals("CZ", fromGeocoder.countryIso2)
        assertEquals("PL", fromBundle.countryIso2)
    }

    @Test
    fun `a region resolves to its bundle outline`() = runTest {
        val selection = useCase()(place("mazowieckie", PlaceKind.Region)).getOrThrow()

        val target = selection.target as RevealTarget.Region
        assertEquals("PL-MZ", target.featureId)
        assertEquals(AdminLevel.Adm1, target.level)
    }

    /** A country is somewhere to go. Owning one in a tap would empty the game out. */
    @Test
    fun `a country resolves to navigation, never to a claim`() = runTest {
        val selection = useCase()(place("Polska", PlaceKind.Country)).getOrThrow()

        assertTrue(selection.target is RevealTarget.Country)
        assertFalse(selection.canCover)
    }

    @Test
    fun `an already revealed region can be covered`() = runTest {
        regions.unlock(AdminLevel.Adm1, "PL-MZ")

        val selection = useCase()(place("mazowieckie", PlaceKind.Region)).getOrThrow()

        assertTrue(selection.isRevealed)
        assertTrue(selection.canCover)
    }

    @Test
    fun `a manually revealed city can be covered`() = runTest {
        places.places.value = listOf(
            UnlockedPlaceRepository.UnlockedPlace(
                id = "id-Warszawa",
                name = "Warszawa",
                latitude = 52.0,
                longitude = 21.0,
                radiusMeters = 5_000.0,
                unlockedAt = 0L,
                source = UnlockedPlaceRepository.Source.Manual,
            ),
        )

        val selection = useCase()(place("Warszawa", PlaceKind.City)).getOrThrow()

        assertTrue(selection.isRevealed)
        assertTrue(selection.canCover)
    }

    /**
     * Ground earned by standing in it follows the same rule as the walked trail: it stays.
     * Offering "Cover" here would let a tap undo something the player physically did.
     */
    @Test
    fun `a city earned by being there cannot be covered`() = runTest {
        places.places.value = listOf(
            UnlockedPlaceRepository.UnlockedPlace(
                id = "id-Skierniewice",
                name = "Skierniewice",
                latitude = 51.95,
                longitude = 20.15,
                radiusMeters = 5_000.0,
                unlockedAt = 0L,
                source = UnlockedPlaceRepository.Source.Auto,
            ),
        )

        val selection = useCase()(
            place("Skierniewice", PlaceKind.City, lat = 51.95, lng = 20.15),
        ).getOrThrow()

        assertTrue(selection.isRevealed)
        assertFalse(selection.canCover)
    }

    /**
     * The regression this guards: the bundle name was picked with a hard-coded preference for
     * `name_pl`, so an English interface answered "Polska" and "mazowieckie" (V3.5.6).
     */
    @Test
    fun `labels follow the interface language, not the bundle's preferred name`() = runTest {
        val polish = useCase(language = AppLanguage.Polish)
        assertEquals("Polska", polish(place("Polska", PlaceKind.Country)).getOrThrow().label)
        assertEquals("mazowieckie", polish(place("mazowieckie", PlaceKind.Region)).getOrThrow().label)

        val english = useCase(language = AppLanguage.English)
        assertEquals("Poland", english(place("Polska", PlaceKind.Country)).getOrThrow().label)
        assertEquals("Mazovia", english(place("mazowieckie", PlaceKind.Region)).getOrThrow().label)
    }

    @Test
    fun `a region the bundle does not know degrades to a circle`() = runTest {
        val selection = useCase(EmptyBundle)(place("Nowhere", PlaceKind.Region)).getOrThrow()

        assertTrue(selection.target is RevealTarget.Circle)
    }

    private class Bundle : BoundaryGeometrySource {
        override suspend fun rings(level: AdminLevel, id: String): Result<List<Ring>> =
            Result.success(
                listOf(listOf(20.0 to 51.0, 22.0 to 51.0, 22.0 to 53.0, 20.0 to 53.0, 20.0 to 51.0)),
            )

        override suspend fun feature(level: AdminLevel, id: String): BoundaryFeature? = null

        override suspend fun featureAt(level: AdminLevel, lat: Double, lng: Double) =
            when (level) {
                AdminLevel.Adm0 -> BoundaryFeature(level, "PL", "Poland", "Polska", "PL", "Europe")
                AdminLevel.Adm1 -> BoundaryFeature(level, "PL-MZ", "Mazovia", "mazowieckie", "PL")
            }
    }

    private object EmptyBundle : BoundaryGeometrySource {
        override suspend fun rings(level: AdminLevel, id: String): Result<List<Ring>> =
            Result.failure(IllegalStateException("no bundle"))

        override suspend fun feature(level: AdminLevel, id: String): BoundaryFeature? = null

        override suspend fun featureAt(level: AdminLevel, lat: Double, lng: Double): BoundaryFeature? = null
    }
}
