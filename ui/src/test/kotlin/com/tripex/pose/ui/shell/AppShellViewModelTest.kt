package com.tripex.pose.ui.shell

import app.cash.turbine.test
import com.tripex.pose.domain.map.MapStyleProvider
import com.tripex.pose.domain.usecase.ObserveUnlockedCountUseCase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppShellViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mapStyleProvider = mockk<MapStyleProvider>()
    private val observeUnlockedCount = mockk<ObserveUnlockedCountUseCase>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { mapStyleProvider.styleUri() } returns "style://test"
        every { observeUnlockedCount() } returns flowOf(0)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Loading with zero progress`() {
        val viewModel = createViewModel()

        assertEquals(AppShellContract.Stage.Loading, viewModel.state.value.stage)
        assertEquals(0f, viewModel.state.value.progress, 0.001f)
        assertFalse(viewModel.state.value.isReady)
    }

    @Test
    fun `after splash delay and data ready becomes fully ready`() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.state.test {
            // style + data bez domknięcia delay splasha
            runCurrent()
            val partial = expectMostRecentItem()
            assertEquals(AppShellContract.Stage.Loading, partial.stage)
            assertFalse(partial.isReady)
            assertEquals(2f / 3f, partial.progress, 0.001f)

            advanceTimeBy(1_200L)
            runCurrent()
            val ready = expectMostRecentItem()
            assertTrue(ready.isReady)
            assertEquals(1f, ready.progress, 0.001f)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `EnterMapRequested switches stage to Map`() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.state.test {
            advanceTimeBy(1_200L)
            runCurrent()
            assertEquals(AppShellContract.Stage.Loading, expectMostRecentItem().stage)

            viewModel.onIntent(AppShellContract.Intent.EnterMapRequested)
            runCurrent()
            assertEquals(AppShellContract.Stage.Map, expectMostRecentItem().stage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun createViewModel() = AppShellViewModel(
        mapStyleProvider = mapStyleProvider,
        observeUnlockedCount = observeUnlockedCount,
    )
}
