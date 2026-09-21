package com.tripex.pose.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.tripex.pose.ui.continent.ContinentMapRoute
import com.tripex.pose.ui.explore.ContinentRoute
import com.tripex.pose.ui.explore.CountryRoute
import com.tripex.pose.ui.explore.RegionRoute
import com.tripex.pose.ui.loading.TripLoadingScreen
import com.tripex.pose.ui.map.host.MapHostState
import com.tripex.pose.ui.map.MapRoute
import com.tripex.pose.ui.shell.AppShellContract
import com.tripex.pose.ui.shell.AppShellViewModel

private const val ENTER_MILLIS = 280
private const val EXIT_MILLIS = 220

/**
 * Navigation graph: Loading → World → Continent → Country → Region → MapView (M2.4, M3.1–M3.3).
 *
 * Back steps exactly one level at every stop. `MapView` is reachable from the country and region
 * levels, which is why it is a leaf rather than part of the chain.
 *
 * Transitions are asymmetric on purpose: the incoming screen fades in **over** the outgoing one,
 * which stays put. A symmetric crossfade left a window where neither screen was opaque and the
 * window background flashed through between the splash and the world.
 */
@Composable
fun TripailNavHost(
    navController: NavHostController,
    mapHost: MapHostState,
    modifier: Modifier = Modifier,
    shellViewModel: AppShellViewModel = hiltViewModel(),
) {
    val shellState by shellViewModel.state.collectAsStateWithLifecycle()

    NavHost(
        navController = navController,
        startDestination = Loading,
        modifier = modifier,
        enterTransition = { fadeIn(tween(ENTER_MILLIS)) },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { fadeOut(tween(EXIT_MILLIS)) },
    ) {
        composable<Loading> {
            TripLoadingScreen(
                readiness = shellState.readiness,
                canEnter = shellState.canEnter,
                // The splash stays on the back stack on purpose: system back from the continent
                // menu has to land here, not close the app.
                onEnterRequested = { navController.navigate(World) },
                onRetry = { shellViewModel.onIntent(AppShellContract.Intent.Retry) },
            )
        }

        composable<World> {
            ContinentMapRoute(
                onBack = { navController.popBackStack() },
                onOpenContinent = { continentId, bounds ->
                    // Hard cut into Stage B (vision §3): the camera is placed, never flown.
                    mapHost.flyTo(bounds, animate = false)
                    navController.navigate(Continent(continentId = continentId.name))
                },
            )
        }

        composable<Continent> { entry ->
            val continentId = entry.toRoute<Continent>().continentId
            ContinentRoute(
                host = mapHost,
                onOpenCountry = { iso2, bounds ->
                    navController.navigate(countryRoute(iso2, bounds, continentId))
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable<Country> { entry ->
            val continentId = entry.toRoute<Country>().continentId
            CountryRoute(
                host = mapHost,
                onOpenMap = { area, bounds, label ->
                    navController.navigate(mapViewRoute(area, bounds, label))
                },
                onOpenRegion = { regionId, countryIso2, bounds ->
                    navController.navigate(regionRoute(regionId, countryIso2, bounds, continentId))
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable<Region> {
            RegionRoute(
                host = mapHost,
                onOpenMap = { area, bounds, label ->
                    navController.navigate(mapViewRoute(area, bounds, label))
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable<MapView> {
            MapRoute(
                host = mapHost,
                // A searched country enters the hierarchy from the side (M4.10). The camera is
                // deliberately *not* placed here: the country level frames it itself, smoothly,
                // and doing both produced a hard cut followed by a short ease.
                onOpenCountry = { iso2, bounds, continentId ->
                    navController.navigate(countryRoute(iso2, bounds, continentId))
                },
                onBack = { navController.popBackStack() },
            )
        }
    }
}
