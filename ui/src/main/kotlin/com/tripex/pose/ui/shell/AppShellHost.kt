package com.tripex.pose.ui.shell

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tripex.pose.ui.continent.ContinentMapRoute
import com.tripex.pose.ui.loading.TripLoadingScreen
import com.tripex.pose.ui.map.MapRoute

@Composable
fun AppShellHost(
    viewModel: AppShellViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    AnimatedContent(
        targetState = state.stage,
        transitionSpec = {
            fadeIn(tween(durationMillis = 500)) togetherWith
                fadeOut(tween(durationMillis = 250))
        },
        label = "app-shell-stage",
    ) { stage ->
        when (stage) {
            AppShellContract.Stage.Loading -> TripLoadingScreen(
                progress = state.progress,
                isReady = state.isReady,
                onEnterRequested = {
                    viewModel.onIntent(AppShellContract.Intent.EnterContinentsRequested)
                },
            )
            AppShellContract.Stage.Continents -> ContinentMapRoute(
                onOpenMap = { continentId ->
                    viewModel.onIntent(AppShellContract.Intent.OpenMap(continentId))
                },
            )
            AppShellContract.Stage.Map -> MapRoute(
                initialCameraTarget = state.mapCameraTarget,
                onBackToContinents = {
                    viewModel.onIntent(AppShellContract.Intent.BackToContinents)
                },
                onInitialCameraConsumed = {
                    viewModel.onIntent(AppShellContract.Intent.MapCameraConsumed)
                },
            )
        }
    }
}
