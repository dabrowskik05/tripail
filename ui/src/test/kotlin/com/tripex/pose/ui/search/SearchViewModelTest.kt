package com.tripex.pose.ui.search

import app.cash.turbine.test
import com.tripex.pose.domain.geo.BoundaryMatcher
import com.tripex.pose.domain.geo.GeoBounds
import com.tripex.pose.domain.geo.Place
import com.tripex.pose.domain.geo.PlaceKind
import com.tripex.pose.domain.geo.atlas.AdminLevel
import com.tripex.pose.domain.geo.atlas.BoundaryFeature
import com.tripex.pose.domain.geo.atlas.BoundaryGeometrySource
import com.tripex.pose.domain.geo.atlas.Ring
import com.tripex.pose.domain.repository.GeocodingRepository
import com.tripex.pose.domain.repository.UnlockedPlaceRepository
import com.tripex.pose.domain.repository.UnlockedRegionRepository
import com.tripex.pose.domain.settings.AppLanguage
import com.tripex.pose.domain.settings.AppLanguageRepository
import com.tripex.pose.domain.usecase.ObserveSearchSuggestionsUseCase
import com.tripex.pose.domain.usecase.ResolveMapPlaceUseCase
import com.tripex.pose.domain.usecase.ResolveSearchSelectionUseCase
import com.tripex.pose.domain.usecase.RevealTarget
import com.tripex.pose.domain.usecase.ToggleRevealUseCase
import com.tripex.pose.domain.usecase.UnlockPlaceUseCase
import com.tripex.pose.domain.usecase.UnlockRegionUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val geocoding = FakeGeocoding()
    private val places = FakePlaces()
    private val regions = FakeRegions()

    @Before
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val warsaw = Place(
        displayName = "Warszawa",
        latitude = 52.23,
        longitude = 21.01,
        boundingBox = GeoBounds(north = 52.37, south = 52.10, east = 21.27, west = 20.85),
        kind = PlaceKind.City,
        id = "pl-waw",
    )

    /**
     * The correction this stage exists for (V3.4.4). Searching for somewhere to look at must not
     * claim it — the camera goes there and a panel asks.
     */
    @Test
    fun `picking a suggestion reveals nothing`() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.onIntent(SearchContract.Intent.SuggestionPicked(warsaw))
        advanceUntilIdle()

        assertTrue("the database must be untouched", places.stored.value.isEmpty())
        assertNotNull("the panel should be open", viewModel.state.value.selection)
        assertFalse(viewModel.state.value.selection!!.isRevealed)
    }

    @Test
    fun `picking a suggestion centres the camera`() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.effects.test {
            viewModel.onIntent(SearchContract.Intent.SuggestionPicked(warsaw))
            advanceUntilIdle()

            assertTrue(awaitItem() is SearchContract.Effect.FocusCamera)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * Enter used to take `suggestions.first()` and return silently when the list was empty —
     * which is exactly when the player presses it. Typing a full name and hitting enter did
     * nothing at all (V3.4.3).
     */
    @Test
    fun `enter with no suggestions still performs a search`() = runTest(testDispatcher) {
        geocoding.searchResult = Result.success(warsaw)
        val viewModel = createViewModel()

        viewModel.onIntent(SearchContract.Intent.QueryChanged("norwegia"))
        viewModel.onIntent(SearchContract.Intent.Submit)
        advanceUntilIdle()

        assertEquals(listOf("norwegia"), geocoding.searched)
        assertNotNull(viewModel.state.value.selection)
    }

    @Test
    fun `a search that finds nothing says so instead of going quiet`() = runTest(testDispatcher) {
        geocoding.searchResult = Result.failure(NoSuchElementException())
        val viewModel = createViewModel()

        viewModel.onIntent(SearchContract.Intent.QueryChanged("qqqq"))
        viewModel.onIntent(SearchContract.Intent.Submit)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.notFound)
    }

    @Test
    fun `revealing from the panel writes, and the panel flips to cover`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        viewModel.onIntent(SearchContract.Intent.SuggestionPicked(warsaw))
        advanceUntilIdle()

        viewModel.onIntent(SearchContract.Intent.Reveal)
        advanceUntilIdle()

        assertEquals(1, places.stored.value.size)
        val selection = viewModel.state.value.selection!!
        assertTrue("the panel stays open so the change is visible", selection.isRevealed)
        assertTrue(selection.canCover)
    }

    @Test
    fun `covering gives the place back`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        viewModel.onIntent(SearchContract.Intent.SuggestionPicked(warsaw))
        advanceUntilIdle()
        viewModel.onIntent(SearchContract.Intent.Reveal)
        advanceUntilIdle()

        viewModel.onIntent(SearchContract.Intent.Cover)
        advanceUntilIdle()

        assertTrue(places.stored.value.isEmpty())
        assertFalse(viewModel.state.value.selection!!.isRevealed)
    }

    @Test
    fun `a country is entered rather than claimed`() = runTest(testDispatcher) {
        val poland = warsaw.copy(displayName = "Polska", kind = PlaceKind.Country, id = "pl")
        val viewModel = createViewModel()

        viewModel.effects.test {
            viewModel.onIntent(SearchContract.Intent.SuggestionPicked(poland))
            advanceUntilIdle()

            assertTrue(awaitItem() is SearchContract.Effect.OpenCountry)
            assertTrue(places.stored.value.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a city tapped on the map opens the same panel as a search result`() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.onIntent(SearchContract.Intent.PlacePicked(warsaw))
        advanceUntilIdle()

        val selection = viewModel.state.value.selection
        assertNotNull(selection)
        assertTrue(selection!!.target is RevealTarget.Circle)
    }

    /** The panel's labels are language-dependent; these tests assert behaviour, not wording. */
    private val language = object : AppLanguageRepository {
        override suspend fun selected() = AppLanguage.Polish

        override fun observeSelected(): Flow<AppLanguage?> = flowOf(AppLanguage.Polish)

        override fun observe(): Flow<AppLanguage> = flowOf(AppLanguage.Polish)

        override suspend fun set(language: AppLanguage) = Unit
    }

    private fun createViewModel(): SearchViewModel {
        val unlockPlace = UnlockPlaceUseCase(places)
        return SearchViewModel(
            geocoding = geocoding,
            resolveMapPlace = ResolveMapPlaceUseCase(geocoding),
            resolveSelection = ResolveSearchSelectionUseCase(
                boundaryMatcher = BoundaryMatcher(Bundle),
                boundaries = Bundle,
                regions = regions,
                places = places,
                appLanguage = language,
            ),
            toggleReveal = ToggleRevealUseCase(
                unlockRegion = UnlockRegionUseCase(Bundle, regions),
                regions = regions,
                unlockPlace = unlockPlace,
            ),
            observeSearchSuggestions = ObserveSearchSuggestionsUseCase(geocoding),
        )
    }

    private class FakeGeocoding : GeocodingRepository {
        var searchResult: Result<Place> = Result.failure(NoSuchElementException())
        val searched = mutableListOf<String>()

        override suspend fun suggest(query: String): Result<List<Place>> = Result.success(emptyList())

        override suspend fun reverseGeocode(lat: Double, lng: Double): Result<Place?> =
            Result.success(null)

        override suspend fun search(query: String): Result<Place> {
            searched += query
            return searchResult
        }
    }

    private class FakePlaces : UnlockedPlaceRepository {
        val stored = MutableStateFlow<List<UnlockedPlaceRepository.UnlockedPlace>>(emptyList())

        override suspend fun unlock(place: UnlockedPlaceRepository.UnlockedPlace): Boolean {
            stored.value = stored.value.filterNot { it.id == place.id } + place
            return true
        }

        override suspend fun lock(id: String): Boolean {
            val before = stored.value.size
            stored.value = stored.value.filterNot {
                it.id == id && it.source == UnlockedPlaceRepository.Source.Manual
            }
            return stored.value.size != before
        }

        override suspend fun find(id: String) = stored.value.firstOrNull { it.id == id }

        override fun observeAll(): Flow<List<UnlockedPlaceRepository.UnlockedPlace>> = stored
    }

    private class FakeRegions : UnlockedRegionRepository {
        val stored = MutableStateFlow<List<UnlockedRegionRepository.UnlockedRegion>>(emptyList())

        override suspend fun unlock(level: AdminLevel, featureId: String): Boolean {
            stored.value = stored.value + UnlockedRegionRepository.UnlockedRegion(level, featureId, 0L)
            return true
        }

        override suspend fun lock(level: AdminLevel, featureId: String): Boolean {
            stored.value = stored.value.filterNot { it.featureId == featureId }
            return true
        }

        override fun observeAll(): Flow<List<UnlockedRegionRepository.UnlockedRegion>> = stored

        override suspend fun isUnlocked(level: AdminLevel, featureId: String): Boolean =
            stored.value.any { it.featureId == featureId }
    }

    private object Bundle : BoundaryGeometrySource {
        override suspend fun rings(level: AdminLevel, id: String): Result<List<Ring>> =
            Result.success(
                listOf(listOf(20.0 to 51.0, 22.0 to 51.0, 22.0 to 53.0, 20.0 to 53.0, 20.0 to 51.0)),
            )

        override suspend fun feature(level: AdminLevel, id: String): BoundaryFeature? = null

        override suspend fun featureAt(level: AdminLevel, lat: Double, lng: Double) = when (level) {
            AdminLevel.Adm0 -> BoundaryFeature(level, "PL", "Poland", "Polska", "PL", "Europe")
            AdminLevel.Adm1 -> BoundaryFeature(level, "PL-MZ", "Mazovia", "mazowieckie", "PL")
        }
    }
}
