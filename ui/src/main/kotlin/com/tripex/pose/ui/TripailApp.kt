package com.tripex.pose.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tripex.pose.domain.settings.AppLanguage
import com.tripex.pose.ui.map.host.MapHostState
import com.tripex.pose.ui.map.host.MapHostViewModel
import com.tripex.pose.ui.map.host.TripailMapSurface
import com.tripex.pose.ui.map.components.PlaceDetailSheet
import com.tripex.pose.ui.map.components.SearchSuggestions
import com.tripex.pose.ui.navigation.Continent
import com.tripex.pose.ui.navigation.Country
import com.tripex.pose.ui.navigation.MapView
import com.tripex.pose.ui.navigation.Region
import com.tripex.pose.ui.navigation.TripailNavHost
import com.tripex.pose.ui.navigation.countryRoute
import com.tripex.pose.ui.search.SearchContract
import com.tripex.pose.ui.search.SearchViewModel
import com.tripex.pose.ui.settings.LanguageViewModel
import com.tripex.pose.ui.settings.ProvideAppLanguage
import com.tripex.pose.ui.shell.chrome.AppChromeState
import com.tripex.pose.ui.shell.chrome.TripailTopBar
import kotlinx.coroutines.flow.collectLatest

/**
 * Composition root.
 *
 * Three things live here, above the navigation graph, because all three must outlive every
 * transition and behave identically on every level:
 *
 * - the **map surface**, so the camera survives moving between levels,
 * - the **top bar**, so back, search, settings and community never move or change shape,
 * - **search** and the place panel, because the magnifier is in that bar everywhere and a result
 *   behaves the same wherever it was typed.
 *
 * The map surface is composed **only** on the destinations that are the map. This is not an
 * optimisation: MapLibre's `MapView` is a `SurfaceView`, which draws in its own window layer
 * above all Compose content regardless of z-order. Leaving it composed during the continent menu
 * painted the parchment straight over it.
 */
@Composable
fun TripailApp() {
    val languageViewModel: LanguageViewModel = hiltViewModel()
    val language by languageViewModel.language.collectAsStateWithLifecycle()

    // Outside everything, including the top bar and the nav graph: the language has to be in
    // place before the first string is resolved, or the first frame renders in the other one
    // and corrects itself visibly (V3.5.6).
    ProvideAppLanguage(language) {
        TripailContent(language = language)
    }
}

/**
 * Everything below the language boundary.
 *
 * Split out only so [ProvideAppLanguage] can wrap it: the language is read from a view model,
 * and a composable cannot provide a local to itself.
 */
@Composable
private fun TripailContent(language: AppLanguage) {
    val hostViewModel: MapHostViewModel = hiltViewModel()
    val searchViewModel: SearchViewModel = hiltViewModel()
    val mapHost = remember { MapHostState() }
    val chrome = remember { AppChromeState() }
    val navController = rememberNavController()
    val keyboard = LocalSoftwareKeyboardController.current

    val boundarySourceUri by hostViewModel.boundarySourceUri.collectAsStateWithLifecycle()
    val fogGeoJson by hostViewModel.fogGeoJson.collectAsStateWithLifecycle()
    val searchState by searchViewModel.state.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()

    LaunchedEffect(fogGeoJson) { mapHost.fogGeoJson = fogGeoJson }

    // The camera drives the wash's level of detail; the host owns the camera, the view model
    // owns the pipeline, so the two are joined here rather than knowing about each other.
    LaunchedEffect(mapHost, hostViewModel) {
        mapHost.viewport.collect { hostViewModel.onViewportChanged(it) }
    }

    LaunchedEffect(searchViewModel) {
        searchViewModel.effects.collectLatest { effect ->
            when (effect) {
                is SearchContract.Effect.OpenCountry -> {
                    chrome.closeSearch()
                    navController.navigate(
                        countryRoute(effect.iso2, effect.bounds, effect.continentId),
                    )
                }

                is SearchContract.Effect.FocusCamera -> {
                    chrome.closeSearch()
                    keyboard?.hide()
                    mapHost.flyTo(effect.bounds, animate = true)
                }
            }
        }
    }

    val showsMap = backStackEntry?.destination?.isMapDestination() == true

    // Leaving the map drops the camera fence with it. A limit that outlives the continent that
    // set it would leave the next screen's camera stuck in a box nothing can unlock (V3.3.7).
    LaunchedEffect(showsMap) { if (!showsMap) mapHost.limitTo(null) }

    /**
     * The single back implementation (V3.2.3).
     *
     * The system gesture and the arrow in the bar call the same function with the same context,
     * so they cannot disagree — which is the bug this replaces.
     */
    val onBack: () -> Unit = {
        chrome.onBack(
            panelOpen = searchState.selection != null,
            onClosePanel = { searchViewModel.onIntent(SearchContract.Intent.DismissPanel) },
        )
    }
    BackHandler(enabled = chrome.isVisible, onBack = onBack)

    Box(modifier = Modifier.fillMaxSize()) {
        if (showsMap) {
            TripailMapSurface(
                host = mapHost,
                styleUri = hostViewModel.styleUri,
                boundarySourceUri = boundarySourceUri,
                onFeatureTap = { tap -> mapHost.onTap?.invoke(tap) },
                onPlaceTap = { place ->
                    // A city tapped on the map opens the very same panel a search result does.
                    searchViewModel.onIntent(SearchContract.Intent.PlacePicked(place.toPlace()))
                },
                language = language,
                modifier = Modifier.fillMaxSize(),
            )
        }
        TripailNavHost(navController = navController, mapHost = mapHost, chrome = chrome)

        AnimatedVisibility(
            visible = chrome.isVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .safeDrawingPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                TripailTopBar(
                    title = chrome.title,
                    isSearchOpen = chrome.isSearchOpen,
                    query = searchState.query,
                    isSearching = searchState.isSearching,
                    onBack = onBack,
                    onOpenSearch = { chrome.openSearch() },
                    onQueryChange = {
                        searchViewModel.onIntent(SearchContract.Intent.QueryChanged(it))
                    },
                    onSubmit = {
                        keyboard?.hide()
                        searchViewModel.onIntent(SearchContract.Intent.Submit)
                    },
                    onSettingsClick = { navController.navigate(com.tripex.pose.ui.navigation.Settings) },
                    onCommunityClick = { chrome.openCommunity() },
                )

                if (chrome.isSearchOpen) {
                    SearchSuggestions(
                        suggestions = searchState.suggestions,
                        notFound = searchState.notFound,
                        onPick = { place ->
                            keyboard?.hide()
                            searchViewModel.onIntent(SearchContract.Intent.SuggestionPicked(place))
                        },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }

        searchState.selection?.let { selection ->
            PlaceDetailSheet(
                selection = selection,
                isApplying = searchState.isApplying,
                onReveal = { searchViewModel.onIntent(SearchContract.Intent.Reveal) },
                onCover = { searchViewModel.onIntent(SearchContract.Intent.Cover) },
                onDismiss = { searchViewModel.onIntent(SearchContract.Intent.DismissPanel) },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/** Stage B: the levels that *are* the map. The loading screen and the continent menu are not. */
private fun NavDestination.isMapDestination(): Boolean =
    hasRoute<Continent>() || hasRoute<Country>() || hasRoute<Region>() || hasRoute<MapView>()
