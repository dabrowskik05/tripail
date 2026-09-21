package com.tripex.pose.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tripex.pose.ui.map.host.MapHostState
import com.tripex.pose.ui.map.host.MapHostViewModel
import com.tripex.pose.ui.map.host.TripailMapSurface
import com.tripex.pose.ui.navigation.Continent
import com.tripex.pose.ui.navigation.Country
import com.tripex.pose.ui.navigation.MapView
import com.tripex.pose.ui.navigation.Region
import com.tripex.pose.ui.navigation.TripailNavHost

/**
 * Composition root.
 *
 * The map surface is composed **only** on the destinations that are the map (vision §3, Stage B).
 * This is not an optimisation: MapLibre's `MapView` is a `SurfaceView`, which draws in its own
 * window layer *above* all Compose content regardless of z-order. Leaving it composed during the
 * continent menu painted the parchment straight over Stage A and made it invisible.
 *
 * Within Stage B the surface stays composed across the country and region levels, so the map
 * instance — and with it the camera — survives those transitions untouched.
 */
@Composable
fun TripailApp() {
    val hostViewModel: MapHostViewModel = hiltViewModel()
    val mapHost = remember { MapHostState() }
    val navController = rememberNavController()

    val boundarySourceUri by hostViewModel.boundarySourceUri.collectAsStateWithLifecycle()
    val fogGeoJson by hostViewModel.fogGeoJson.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()

    LaunchedEffect(fogGeoJson) { mapHost.fogGeoJson = fogGeoJson }

    val showsMap = backStackEntry?.destination?.isMapDestination() == true

    Box(modifier = Modifier.fillMaxSize()) {
        if (showsMap) {
            TripailMapSurface(
                host = mapHost,
                styleUri = hostViewModel.styleUri,
                boundarySourceUri = boundarySourceUri,
                onFeatureTap = { tap -> mapHost.onTap?.invoke(tap) },
                modifier = Modifier.fillMaxSize(),
            )
        }
        TripailNavHost(navController = navController, mapHost = mapHost)
    }
}

/** Stage B: the levels that *are* the map. The loading screen and the continent menu are not. */
private fun NavDestination.isMapDestination(): Boolean =
    hasRoute<Continent>() || hasRoute<Country>() || hasRoute<Region>() || hasRoute<MapView>()
