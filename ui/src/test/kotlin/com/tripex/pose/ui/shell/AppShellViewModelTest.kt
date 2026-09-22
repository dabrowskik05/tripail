package com.tripex.pose.ui.shell

import app.cash.turbine.test
import com.tripex.pose.domain.geo.GeoBounds
import com.tripex.pose.domain.geo.H3Converter
import com.tripex.pose.domain.geo.atlas.AtlasRepository
import com.tripex.pose.domain.geo.atlas.ContinentShape
import com.tripex.pose.domain.geo.atlas.LandShape
import com.tripex.pose.domain.map.MapStyleProvider
import com.tripex.pose.domain.settings.AppLanguage
import com.tripex.pose.domain.settings.AppLanguageRepository
import com.tripex.pose.domain.tiles.PmTilesBootstrap
import com.tripex.pose.domain.usecase.ObserveUnlockedCountUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
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
    private val h3Converter = mockk<H3Converter>()
    private val atlasRepository = mockk<AtlasRepository>()
    private val pmTilesBootstrap = mockk<PmTilesBootstrap>()
    private val observeUnlockedCount = mockk<ObserveUnlockedCountUseCase>()

    private val land = LandShape(
        rings = listOf(listOf(0.0 to 0.0, 1.0 to 0.0, 1.0 to 1.0, 0.0 to 0.0)),
        bounds = GeoBounds(north = 1.0, south = 0.0, east = 1.0, west = 0.0),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { mapStyleProvider.styleUri() } returns "style://test"
        every { observeUnlockedCount() } returns flowOf(0)
        coEvery { h3Converter.warmUp() } returns Unit
        coEvery { atlasRepository.land() } returns land
        coEvery { atlasRepository.continents() } returns emptyList<ContinentShape>()
        coEvery { pmTilesBootstrap.ensureReady() } returns Result.success("/files/boundaries.pmtiles")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Preparing and cannot be entered`() {
        val viewModel = createViewModel()

        assertEquals(AppShellContract.Readiness.Preparing, viewModel.state.value.readiness)
        assertFalse(viewModel.state.value.canEnter)
    }

    @Test
    fun `stays Preparing while one signal is outstanding`() = runTest(testDispatcher) {
        val slowBootstrap = CompletableDeferred<Result<String>>()
        coEvery { pmTilesBootstrap.ensureReady() } coAnswers { slowBootstrap.await() }

        val viewModel = createViewModel()

        viewModel.state.test {
            runCurrent()
            assertEquals(AppShellContract.Readiness.Preparing, expectMostRecentItem().readiness)

            slowBootstrap.complete(Result.success("/files/boundaries.pmtiles"))
            runCurrent()
            assertEquals(AppShellContract.Readiness.Ready, expectMostRecentItem().readiness)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `becomes Ready once every signal completes`() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.state.test {
            runCurrent()
            val ready = expectMostRecentItem()
            assertEquals(AppShellContract.Readiness.Ready, ready.readiness)
            assertTrue(ready.canEnter)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `atlas failure degrades but still allows entering`() = runTest(testDispatcher) {
        coEvery { atlasRepository.land() } throws IllegalStateException("no atlas asset")

        val viewModel = createViewModel()

        viewModel.state.test {
            runCurrent()
            val state = expectMostRecentItem()
            assertEquals(
                AppShellContract.Readiness.Failed(AppShellContract.FailedSignal.Atlas, degraded = true),
                state.readiness,
            )
            assertTrue(state.canEnter)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `native hex failure blocks entering`() = runTest(testDispatcher) {
        coEvery { h3Converter.warmUp() } throws UnsatisfiedLinkError("libh3-java")

        val viewModel = createViewModel()

        viewModel.state.test {
            runCurrent()
            val state = expectMostRecentItem()
            assertEquals(
                AppShellContract.Readiness.Failed(AppShellContract.FailedSignal.Hexes, degraded = false),
                state.readiness,
            )
            assertFalse(state.canEnter)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `retry re-runs the failed signal`() = runTest(testDispatcher) {
        val failFirst = MutableStateFlow(true)
        coEvery { atlasRepository.land() } coAnswers {
            if (failFirst.value) error("no atlas asset") else land
        }

        val viewModel = createViewModel()

        viewModel.state.test {
            runCurrent()
            assertTrue(expectMostRecentItem().readiness is AppShellContract.Readiness.Failed)

            failFirst.value = false
            viewModel.onIntent(AppShellContract.Intent.Retry)
            runCurrent()
            assertEquals(AppShellContract.Readiness.Ready, expectMostRecentItem().readiness)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun createViewModel() = AppShellViewModel(
        mapStyleProvider = mapStyleProvider,
        h3Converter = h3Converter,
        atlasRepository = atlasRepository,
        pmTilesBootstrap = pmTilesBootstrap,
        observeUnlockedCount = observeUnlockedCount,
        appLanguage = FakeAppLanguage(),
    )

    /** Already chosen, so the language picker stays out of these tests' way. */
    private class FakeAppLanguage(
        private val chosen: AppLanguage? = AppLanguage.Polish,
    ) : AppLanguageRepository {
        override suspend fun selected(): AppLanguage? = chosen

        override fun observe(): Flow<AppLanguage> = flowOf(chosen ?: AppLanguage.DEFAULT)

        override suspend fun set(language: AppLanguage) = Unit
    }
}
