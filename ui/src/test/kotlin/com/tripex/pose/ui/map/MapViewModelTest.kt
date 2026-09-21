package com.tripex.pose.ui.map

import app.cash.turbine.test
import com.tripex.pose.domain.geo.Place
import com.tripex.pose.domain.geo.PlaceKind
import com.tripex.pose.domain.location.TrackingController
import com.tripex.pose.domain.location.TrackingState
import com.tripex.pose.domain.location.TrackingSetupRepository
import com.tripex.pose.domain.location.TrackingStateHolder
import com.tripex.pose.domain.usecase.ObserveUnlockedCountUseCase
import com.tripex.pose.domain.repository.GeocodingRepository
import com.tripex.pose.domain.usecase.ObserveSearchSuggestionsUseCase
import com.tripex.pose.domain.usecase.PickSearchResultUseCase
import com.tripex.pose.domain.usecase.SearchOutcome
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val trackingController = mockk<TrackingController>(relaxed = true)
    private val pickSearchResult = mockk<PickSearchResultUseCase>()
    private val trackingSetup = FakeTrackingSetup()

    /**
     * Submitting takes the first suggestion, so the fake has to produce one. It echoes the query
     * back as a place rather than matching a fixture, which keeps every test's query valid.
     */
    private val geocoding = object : GeocodingRepository {
        override suspend fun suggest(query: String): Result<List<Place>> = Result.success(
            if (query.isBlank()) emptyList() else listOf(Place(query, 52.23, 21.01)),
        )

        override suspend fun reverseGeocode(lat: Double, lng: Double): Result<Place?> =
            Result.success(null)

        override suspend fun search(query: String): Result<Place> =
            Result.failure(NoSuchElementException())
    }
    private val trackingStateHolder = TrackingStateHolder()
    private val observeUnlockedCount = mockk<ObserveUnlockedCountUseCase>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { observeUnlockedCount() } returns flowOf(0)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `OpenSettingsRequested emits OpenAppSettings effect`() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.effects.test {
            viewModel.onIntent(MapContract.Intent.OpenSettingsRequested)
            advanceUntilIdle()
            assertEquals(MapContract.Effect.OpenAppSettings, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `precise location hint maps to PreciseLocationRequired status`() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        // WhileSubscribed only updates while collected — subscribe first.
        viewModel.state.test {
            skipItems(1)
            viewModel.onIntent(
                MapContract.Intent.PermissionsUpdated(
                    fineGranted = false,
                    coarseOnly = true,
                ),
            )
            advanceUntilIdle()
            assertEquals(
                MapContract.StatusMessage.PreciseLocationRequired,
                awaitItem().statusMessage,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `tracking state maps to TrackingActive status`() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.state.test {
            skipItems(1)
            trackingStateHolder.update(TrackingState.Tracking)
            advanceUntilIdle()
            assertEquals(MapContract.StatusMessage.TrackingActive, awaitItem().statusMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `SubmitSearch success opens PlaceDetail sheet`() = runTest(testDispatcher) {
        val place = Place("Warszawa", 52.23, 21.01)
        coEvery { pickSearchResult(any()) } returns Result.success(
            SearchOutcome.PlaceUnlocked(place = place, radiusMeters = 8_000.0),
        )
        val viewModel = createViewModel()

        viewModel.state.test {
            skipItems(1)
            viewModel.onIntent(MapContract.Intent.SearchQueryChanged("Warszawa"))
            advanceUntilIdle()
            skipItems(1) // query updated
            viewModel.onIntent(MapContract.Intent.SubmitSearch)
            advanceUntilIdle()
            val done = expectMostRecentItem()
            assertEquals(
                MapContract.PlaceDetail(
                    name = "Warszawa",
                    kind = PlaceKind.Unknown,
                ),
                done.placeDetail,
            )
            assertEquals(null, done.searchMessage)
            assertTrue(done.cameraTarget != null)
            cancelAndIgnoreRemainingEvents()
        }
    }


    @Test
    fun `OpenSettings asks for a Coming soon message`() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.onIntent(MapContract.Intent.OpenSettings)
        advanceUntilIdle()

        viewModel.effects.test {
            assertEquals(MapContract.Effect.ShowComingSoon, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `OpenCommunity shows dialog`() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.state.test {
            skipItems(1)
            viewModel.onIntent(MapContract.Intent.OpenCommunity)
            advanceUntilIdle()
            assertTrue(awaitItem().communityVisible)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `TogglePlaceDetailExpanded flips expanded flag`() = runTest(testDispatcher) {
        val place = Place("Kraków", 50.06, 19.94)
        coEvery { pickSearchResult(any()) } returns Result.success(
            SearchOutcome.PlaceUnlocked(place = place, radiusMeters = 8_000.0),
        )
        val viewModel = createViewModel()

        viewModel.state.test {
            skipItems(1)
            viewModel.onIntent(MapContract.Intent.SearchQueryChanged("Kraków"))
            advanceUntilIdle()
            skipItems(1)
            viewModel.onIntent(MapContract.Intent.SubmitSearch)
            advanceUntilIdle()
            val opened = expectMostRecentItem()
            assertTrue(opened.placeDetail != null)

            viewModel.onIntent(MapContract.Intent.TogglePlaceDetailExpanded)
            advanceUntilIdle()
            assertTrue(expectMostRecentItem().placeDetail!!.expanded)

            viewModel.onIntent(MapContract.Intent.DismissPlaceDetail)
            advanceUntilIdle()
            assertEquals(null, expectMostRecentItem().placeDetail)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `granting foreground location alone asks for background location`() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()

            viewModel.state.test {
                skipItems(1)
                viewModel.onIntent(
                    MapContract.Intent.PermissionsUpdated(
                        fineGranted = true,
                        coarseOnly = false,
                        backgroundGranted = false,
                    ),
                )
                advanceUntilIdle()

                assertEquals(
                    MapContract.BackgroundPrompt.LocationAlways,
                    expectMostRecentItem().backgroundPrompt,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `with background granted the reliability prompt comes up instead`() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()

            viewModel.state.test {
                skipItems(1)
                viewModel.onIntent(
                    MapContract.Intent.PermissionsUpdated(
                        fineGranted = true,
                        coarseOnly = false,
                        backgroundGranted = true,
                    ),
                )
                advanceUntilIdle()

                assertEquals(
                    MapContract.BackgroundPrompt.Reliability,
                    expectMostRecentItem().backgroundPrompt,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the reliability prompt is never shown twice`() =
        runTest(testDispatcher) {
            trackingSetup.seen = true
            val viewModel = createViewModel()

            viewModel.state.test {
                skipItems(1)
                viewModel.onIntent(
                    MapContract.Intent.PermissionsUpdated(
                        fineGranted = true,
                        coarseOnly = false,
                        backgroundGranted = true,
                    ),
                )
                advanceUntilIdle()

                assertEquals(null, expectMostRecentItem().backgroundPrompt)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `dismissing the reliability prompt still counts as having seen it`() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()

            viewModel.state.test {
                skipItems(1)
                viewModel.onIntent(
                    MapContract.Intent.PermissionsUpdated(
                        fineGranted = true,
                        coarseOnly = false,
                        backgroundGranted = true,
                    ),
                )
                advanceUntilIdle()
                viewModel.onIntent(MapContract.Intent.BackgroundPromptDismissed)
                advanceUntilIdle()

                assertEquals(null, expectMostRecentItem().backgroundPrompt)
                assertTrue(trackingSetup.seen)
                cancelAndIgnoreRemainingEvents()
            }
        }

    private fun createViewModel() = MapViewModel(
        trackingController = trackingController,
        pickSearchResult = pickSearchResult,
        trackingSetup = trackingSetup,
        observeSearchSuggestions = ObserveSearchSuggestionsUseCase(geocoding),
        trackingStateHolder = trackingStateHolder,
    )

    private class FakeTrackingSetup(
        var seen: Boolean = false,
    ) : TrackingSetupRepository {
        override suspend fun hasSeenReliabilityPrompt(): Boolean = seen

        override suspend fun markReliabilityPromptSeen() {
            seen = true
        }
    }
}
