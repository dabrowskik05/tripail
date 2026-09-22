package com.tripex.pose.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.tripex.pose.ui.map.MapRoute
import com.tripex.pose.ui.map.host.MapHostState
import com.tripex.pose.ui.settings.LanguageRoute
import com.tripex.pose.ui.settings.SettingsRoute
import com.tripex.pose.ui.shell.AppShellContract
import com.tripex.pose.ui.shell.AppShellViewModel
import com.tripex.pose.ui.shell.chrome.AppChromeState

private const val ENTER_MILLIS = 320
private const val EXIT_MILLIS = 240

/**
 * Initial scale of an entering screen (V3.6.1).
 *
 * Barely perceptible on purpose: the continent menu and the map are the same world at two
 * distances, so the transition should read as settling into place rather than as a slide
 * between two unrelated pages.
 */
private const val ENTER_SCALE = 1.03f

/**
 * Navigation graph: Loading → World → Continent → Country → Region → MapView.
 *
 * Back steps exactly one level at every stop, and it does so through the shared chrome rather
 * than through per-screen handlers (V3.2.3). `MapView` is reachable from the country and region
 * levels, which is why it is a leaf rather than part of the chain.
 *
 * Transitions are asymmetric on purpose: the incoming screen fades and settles in **over** the
 * outgoing one, which stays put. A symmetric crossfade left a window where neither screen was
 * opaque and the window background flashed through.
 */
@Composable
fun TripailNavHost(
    navController: NavHostController,
    mapHost: MapHostState,
    chrome: AppChromeState,
    modifier: Modifier = Modifier,
    shellViewModel: AppShellViewModel = hiltViewModel(),
) {
    val shellState by shellViewModel.state.collectAsStateWithLifecycle()

    NavHost(
        navController = navController,
        startDestination = Loading,
        modifier = modifier,
        enterTransition = {
            fadeIn(tween(ENTER_MILLIS)) + scaleIn(tween(ENTER_MILLIS), initialScale = ENTER_SCALE)
        },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { fadeOut(tween(EXIT_MILLIS)) },
    ) {
        composable<Loading> {
            // The splash has no chrome. Hidden in an effect rather than inline: composition can
            // run many times and must stay free of side effects, or the bar would be torn down
            // on every recomposition of the screen underneath it.
            LaunchedEffect(Unit) { chrome.hide() }
            TripLoadingScreen(
                readiness = shellState.readiness,
                canEnter = shellState.canEnter,
                // The splash stays on the back stack on purpose: system back from the continent
                // menu has to land here, not close the app.
                // First launch picks a language before the world appears (V3.5.2); afterwards
                // this is a straight line from the splash into the map.
                onEnterRequested = {
                    navController.navigate(if (shellState.needsLanguage) Language else World)
                },
                onRetry = { shellViewModel.onIntent(AppShellContract.Intent.Retry) },
            )
        }

        composable<World> {
            ContinentMapRoute(
                chrome = chrome,
                onBack = { navController.popBackStack() },
                onOpenContinent = { continentId, bounds ->
                    // The camera is placed, never flown, and the continent fills the screen from
                    // the first frame (V3.3.5).
                    mapHost.flyTo(bounds, animate = false, fill = true)
                    navController.navigate(Continent(continentId = continentId.name))
                },
            )
        }

        composable<Continent> { entry ->
            val continentId = entry.toRoute<Continent>().continentId
            ContinentRoute(
                host = mapHost,
                chrome = chrome,
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
                chrome = chrome,
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
                chrome = chrome,
                onOpenMap = { area, bounds, label ->
                    navController.navigate(mapViewRoute(area, bounds, label))
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable<MapView> { entry ->
            MapRoute(
                host = mapHost,
                chrome = chrome,
                title = entry.toRoute<MapView>().label,
                onBack = { navController.popBackStack() },
            )
        }

        composable<Language> {
            // Nothing to navigate back to yet, and nothing to search — no bar here either.
            LaunchedEffect(Unit) { chrome.hide() }
            LanguageRoute(
                onPicked = {
                    navController.navigate(World) { popUpTo(Language) { inclusive = true } }
                },
            )
        }

        composable<Settings> {
            SettingsRoute(chrome = chrome, onBack = { navController.popBackStack() })
        }
    }
}
