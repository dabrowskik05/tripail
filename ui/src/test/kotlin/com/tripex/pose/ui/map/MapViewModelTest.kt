package com.tripex.pose.ui.map

import app.cash.turbine.test
import com.tripex.pose.domain.location.TrackingController
import com.tripex.pose.domain.location.TrackingSetupRepository
import com.tripex.pose.domain.location.TrackingState
import com.tripex.pose.domain.location.TrackingStateHolder
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The explore level only. Search moved to `SearchViewModel` when the top bar became shared
 * (V3.2.2), so the tests that used to live here moved with it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val trackingController = mockk<TrackingController>(relaxed = true)
    private val trackingStateHolder = TrackingStateHolder()
    private val trackingSetup = FakeTrackingSetup()

    @Before
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `granting fine location starts tracking`() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.onIntent(
            MapContract.Intent.PermissionsUpdated(fineGranted = true, coarseOnly = false),
        )
        advanceUntilIdle()

        verify { trackingController.startTracking() }
    }

    @Test
    fun `refusing location asks again rather than starting`() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.effects.test {
            viewModel.onIntent(
                MapContract.Intent.PermissionsUpdated(fineGranted = false, coarseOnly = false),
            )
            advanceUntilIdle()

            assertEquals(MapContract.Effect.RequestLocationPermissions, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `coarse-only permission is reported as needing precise location`() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.state.test {
            skipItems(1)
            viewModel.onIntent(
                MapContract.Intent.PermissionsUpdated(fineGranted = false, coarseOnly = true),
            )
            advanceUntilIdle()

            assertEquals(
                MapContract.StatusMessage.PreciseLocationRequired,
                expectMostRecentItem().statusMessage,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `active tracking is reported`() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.state.test {
            skipItems(1)
            trackingStateHolder.update(TrackingState.Tracking)
            advanceUntilIdle()

            assertEquals(
                MapContract.StatusMessage.TrackingActive,
                expectMostRecentItem().statusMessage,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `granting foreground location alone asks for background location`() = runTest(testDispatcher) {
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
    fun `with background granted the reliability prompt comes up instead`() = runTest(testDispatcher) {
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
    fun `the reliability prompt is never shown twice`() = runTest(testDispatcher) {
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

            assertNull(expectMostRecentItem().backgroundPrompt)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `dismissing the reliability prompt still counts as having seen it`() = runTest(testDispatcher) {
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

            assertNull(expectMostRecentItem().backgroundPrompt)
            assertTrue(trackingSetup.seen)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun createViewModel() = MapViewModel(
        trackingController = trackingController,
        trackingSetup = trackingSetup,
        trackingStateHolder = trackingStateHolder,
    )

    private class FakeTrackingSetup(var seen: Boolean = false) : TrackingSetupRepository {
        override suspend fun hasSeenReliabilityPrompt(): Boolean = seen

        override suspend fun markReliabilityPromptSeen() {
            seen = true
        }
    }
}
