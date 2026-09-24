package com.tripex.pose.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tripex.pose.domain.settings.AppLanguage
import com.tripex.pose.domain.usecase.RevealTarget
import com.tripex.pose.ui.components.PanelMotion
import com.tripex.pose.ui.continent.ContinentPalette
import com.tripex.pose.ui.map.components.CommunityDialog
import com.tripex.pose.ui.map.host.BottomPanel
import com.tripex.pose.ui.map.host.MapAttribution
import com.tripex.pose.ui.map.host.MapAttributionHeight
import com.tripex.pose.ui.map.host.MapHostState
import com.tripex.pose.ui.map.host.MapHostViewModel
import com.tripex.pose.ui.map.host.TripailMapSurface
import com.tripex.pose.ui.map.components.PlaceDetailSheet
import com.tripex.pose.ui.map.components.SearchSuggestions
import com.tripex.pose.ui.navigation.Continent
import com.tripex.pose.ui.navigation.Country
import com.tripex.pose.ui.navigation.MapView
import com.tripex.pose.ui.navigation.Region
import com.tripex.pose.ui.navigation.Settings
import com.tripex.pose.ui.navigation.TripailNavHost
import com.tripex.pose.ui.navigation.countryRoute
import com.tripex.pose.ui.search.SearchContract
import com.tripex.pose.ui.search.SearchViewModel
import com.tripex.pose.ui.settings.LanguageViewModel
import com.tripex.pose.ui.settings.ProvideAppLanguage
import com.tripex.pose.ui.shell.chrome.AppChromeState
import com.tripex.pose.ui.shell.chrome.TripailSideActions
import com.tripex.pose.ui.shell.chrome.TripailTopBar
import com.tripex.pose.ui.theme.LocalCartoonStyle
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
    val playerLocation by hostViewModel.playerLocation.collectAsStateWithLifecycle()
    val searchState by searchViewModel.state.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()

    LaunchedEffect(fogGeoJson) { mapHost.fogGeoJson = fogGeoJson }

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

    // Leaving search, by any route, starts the next search from an empty field.
    LaunchedEffect(chrome.isSearchOpen) {
        if (!chrome.isSearchOpen) searchViewModel.onIntent(SearchContract.Intent.Reset)
    }

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
    /**
     * The map is created the first time a map level opens and then **kept**. It used to be
     * removed with `if (showsMap)`, which destroyed the MapLibre instance on every trip back to
     * the continent menu and built a new one — style, sources and GL context — on the way in.
     * Now it only fades out and hides.
     */
    var mapCreated by remember { mutableStateOf(false) }
    LaunchedEffect(showsMap) { if (showsMap) mapCreated = true }
    // Faded in only once styled and placed: fading in a blank or misplaced surface is what made
    // the transition look like a cut.
    val mapAlpha by animateFloatAsState(
        targetValue = if (showsMap && mapHost.isReady) 1f else 0f,
        animationSpec = tween(MAP_FADE_MILLIS),
        label = "map-fade",
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // The same ocean the continent menu is drawn on, so the moment before the map is ready
        // reads as the menu's sea rather than as an empty screen.
        if (showsMap) {
            Box(modifier = Modifier.fillMaxSize().background(LocalCartoonStyle.current.oceanBlue))
        }
        if (mapCreated) {
            TripailMapSurface(
                host = mapHost,
                styleUri = hostViewModel.styleUri,
                boundarySourceUri = boundarySourceUri,
                // Selections replace each other: a region or country picked on the map takes over
                // from a city picked a moment ago, instead of leaving the city's panel behind.
                onFeatureTap = { tap ->
                    searchViewModel.onIntent(SearchContract.Intent.DismissPanel)
                    mapHost.onTap?.invoke(tap)
                },
                onPlaceTap = { place ->
                    // A city tapped on the map opens the very same panel a search result does.
                    searchViewModel.onIntent(SearchContract.Intent.PlacePicked(place.toPlace()))
                },
                language = language,
                visible = showsMap || mapAlpha > 0f,
                playerLocation = playerLocation,
                // The map stops above the footer: under Samsung's buttons nothing on it could be
                // read or tapped, and the attribution gets a line of its own instead of a pill
                // floating over the map.
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                    .padding(bottom = MapAttributionHeight)
                    .graphicsLayer { alpha = mapAlpha },
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .graphicsLayer { alpha = mapAlpha }
                    .background(ContinentPalette.menuSurface),
            ) {
                if (showsMap) MapAttribution()
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
        TripailNavHost(navController = navController, mapHost = mapHost, chrome = chrome)

        // Registered after the NavHost on purpose: the most recently added callback wins, and
        // with it first the NavHost's own back handling took the gesture — so the system gesture
        // popped the stack by itself while the arrow went through [onBack] (V3.2.3).
        BackHandler(enabled = chrome.isVisible, onBack = onBack)

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
                    query = searchViewModel.fieldText,
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
                )

                if (!chrome.isSearchOpen) {
                    TripailSideActions(
                        // A toggle: pressed on the settings screen it closes it, instead of
                        // stacking another copy that needs its own Back.
                        onSettingsClick = {
                            if (backStackEntry?.destination?.hasRoute<Settings>() == true) {
                                navController.popBackStack()
                            } else {
                                navController.navigate(Settings) { launchSingleTop = true }
                            }
                        },
                        onCommunityClick = { chrome.openCommunity() },
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 12.dp),
                    )
                }

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

        // Exactly one card at the bottom: the place sheet when a place is picked, otherwise the
        // current level's card — only once the map is ready, so "tap a country" does not arrive
        // before there is a country to tap. Every change is the old card down, the new one up.
        val selection = searchState.selection
        // A picked city brings its country's regions onto the map, instead of the region picked
        // before it staying lit behind the city's sheet.
        val placeCountry = selection?.takeIf { it.target is RevealTarget.Circle }?.countryIso2
        LaunchedEffect(placeCountry) { mapHost.focusPlaceCountry(placeCountry) }
        val bottomPanel: BottomPanel? = when {
            selection != null -> BottomPanel(key = Triple(selection.label, selection.latitude, selection.longitude)) {
                PlaceDetailSheet(
                    selection = selection,
                    isApplying = searchState.isApplying,
                    onReveal = { searchViewModel.onIntent(SearchContract.Intent.Reveal) },
                    onCover = { searchViewModel.onIntent(SearchContract.Intent.Cover) },
                )
            }
            showsMap && mapHost.isReady -> mapHost.levelPanel
            else -> null
        }
        AnimatedContent(
            targetState = bottomPanel,
            contentKey = { it?.key },
            transitionSpec = PanelMotion.swap(),
            contentAlignment = Alignment.BottomCenter,
            label = "bottom-panel",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                // On the map every card stands on the attribution footer.
                .then(
                    if (showsMap) {
                        Modifier
                            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                            .padding(bottom = MapAttributionHeight)
                    } else {
                        Modifier
                    },
                ),
        ) { panel -> panel?.content?.invoke() }

        // The bar's community button only flips this flag; without a reader it did nothing.
        if (chrome.isCommunityOpen) {
            CommunityDialog(onDismiss = { chrome.closeCommunity() })
        }
    }
}

private const val MAP_FADE_MILLIS = 320

/** Stage B: the levels that *are* the map. The loading screen and the continent menu are not. */
private fun NavDestination.isMapDestination(): Boolean =
    hasRoute<Continent>() || hasRoute<Country>() || hasRoute<Region>() || hasRoute<MapView>()
