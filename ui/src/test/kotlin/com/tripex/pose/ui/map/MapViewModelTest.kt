package com.tripex.pose.ui.map

import app.cash.turbine.test
import com.tripex.pose.domain.geo.FogGeoJsonBuilder
import com.tripex.pose.domain.location.TrackingController
import com.tripex.pose.domain.location.TrackingState
import com.tripex.pose.domain.location.TrackingStateHolder
import com.tripex.pose.domain.map.MapStyleProvider
import com.tripex.pose.domain.geo.Place
import com.tripex.pose.domain.usecase.ObserveFogGeoJsonUseCase
import com.tripex.pose.domain.usecase.ObserveUnlockedCountUseCase
import com.tripex.pose.domain.usecase.UnlockPlaceResult
import com.tripex.pose.domain.usecase.UnlockPlaceUseCase
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
    private val unlockPlace = mockk<UnlockPlaceUseCase>()
    private val mapStyleProvider = mockk<MapStyleProvider>()
    private val trackingStateHolder = TrackingStateHolder()
    private val observeUnlockedCount = mockk<ObserveUnlockedCountUseCase>()
    private val observeFogGeoJson = mockk<ObserveFogGeoJsonUseCase>()
    private val fogGeoJsonBuilder = FogGeoJsonBuilder()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { mapStyleProvider.styleUri() } returns "style://test"
        every { observeUnlockedCount() } returns flowOf(0)
        every { observeFogGeoJson(any()) } returns flowOf(fogGeoJsonBuilder.emptyWorld())
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
    fun `StartDiscovery without fine location requests permissions`() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.effects.test {
            viewModel.onIntent(MapContract.Intent.StartDiscovery)
            advanceUntilIdle()
            assertEquals(MapContract.Effect.RequestLocationPermissions, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        verify(exactly = 0) { trackingController.startTracking() }
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
    fun `SubmitSearch success sets Unlocked search message`() = runTest(testDispatcher) {
        val place = Place("Warszawa", 52.23, 21.01)
        coEvery { unlockPlace(any()) } returns Result.success(
            UnlockPlaceResult(place = place, newlyUnlocked = 7),
        )
        val viewModel = createViewModel()

        viewModel.state.test {
            skipItems(1)
            viewModel.onIntent(MapContract.Intent.SearchQueryChanged("Warszawa"))
            advanceUntilIdle()
            skipItems(1) // query updated
            viewModel.onIntent(MapContract.Intent.SubmitSearch)
            advanceUntilIdle()
            // StateFlow conflates isSearching=true → false; assert the settled result.
            val done = expectMostRecentItem()
            assertEquals(
                MapContract.SearchMessage.Unlocked(count = 7, placeName = "Warszawa"),
                done.searchMessage,
            )
            assertTrue(done.cameraTarget != null)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun createViewModel() = MapViewModel(
        trackingController = trackingController,
        unlockPlace = unlockPlace,
        fogGeoJsonBuilder = fogGeoJsonBuilder,
        mapStyleProvider = mapStyleProvider,
        trackingStateHolder = trackingStateHolder,
        observeUnlockedCount = observeUnlockedCount,
        observeFogGeoJson = observeFogGeoJson,
    )
}
