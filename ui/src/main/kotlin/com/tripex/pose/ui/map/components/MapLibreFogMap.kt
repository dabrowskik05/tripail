package com.tripex.pose.ui.map.components

import android.content.ComponentCallbacks
import android.content.res.Configuration
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.savedstate.compose.LocalSavedStateRegistryOwner
import com.tripex.pose.domain.geo.FogGeoJsonBuilder
import com.tripex.pose.domain.geo.GeoBounds
import com.tripex.pose.domain.geo.MapViewport
import com.tripex.pose.ui.map.MapContract
import com.tripex.pose.ui.theme.LocalRevealStyle
import com.tripex.pose.ui.theme.RevealStyle
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource

private const val REVEAL_SOURCE_ID = "reveal-source"
private const val REVEAL_FILL_LAYER_ID = "reveal-wash-layer"
private const val REVEAL_EDGE_LAYER_ID = "reveal-edge-layer"
private const val MAP_VIEW_STATE_KEY = "maplibre_fog_map_state"

@Composable
fun MapLibreFogMap(
    styleUri: String,
    fogGeoJson: String,
    initialViewport: MapViewport,
    cameraTarget: MapContract.CameraTarget?,
    onCameraIdle: (MapViewport) -> Unit,
    onCameraTargetConsumed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val savedStateRegistryOwner = LocalSavedStateRegistryOwner.current
    val revealStyle = LocalRevealStyle.current
    val emptyFogGeoJson = remember { FogGeoJsonBuilder().emptyWorld() }
    val mapView = remember {
        val restored = savedStateRegistryOwner.savedStateRegistry
            .consumeRestoredStateForKey(MAP_VIEW_STATE_KEY)
        MapView(context).also { it.onCreate(restored) }
    }
    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleReady by remember { mutableStateOf(false) }
    val currentOnCameraIdle by rememberUpdatedState(onCameraIdle)

    DisposableEffect(lifecycleOwner, mapView) {
        val savedStateRegistry = savedStateRegistryOwner.savedStateRegistry
        savedStateRegistry.registerSavedStateProvider(MAP_VIEW_STATE_KEY) {
            Bundle().also { outState -> mapView.onSaveInstanceState(outState) }
        }

        val lowMemoryCallbacks = object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) = Unit
            override fun onLowMemory() = mapView.onLowMemory()
        }
        context.registerComponentCallbacks(lowMemoryCallbacks)

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            savedStateRegistry.unregisterSavedStateProvider(MAP_VIEW_STATE_KEY)
            context.unregisterComponentCallbacks(lowMemoryCallbacks)
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
    )

    LaunchedEffect(mapView, initialViewport) {
        mapView.getMapAsync { map ->
            mapLibreMap = map
            map.uiSettings.isAttributionEnabled = true
            val centerLat = (initialViewport.bounds.north + initialViewport.bounds.south) / 2.0
            val centerLng = (initialViewport.bounds.east + initialViewport.bounds.west) / 2.0
            map.cameraPosition = CameraPosition.Builder()
                .target(LatLng(centerLat, centerLng))
                .zoom(initialViewport.zoom)
                .build()
        }
    }

    DisposableEffect(mapLibreMap) {
        val map = mapLibreMap ?: return@DisposableEffect onDispose { }
        val listener = MapLibreMap.OnCameraIdleListener {
            val bounds = map.projection.visibleRegion.latLngBounds
            currentOnCameraIdle(
                MapViewport(
                    bounds = GeoBounds(
                        north = bounds.latitudeNorth,
                        south = bounds.latitudeSouth,
                        east = bounds.longitudeEast,
                        west = bounds.longitudeWest,
                    ),
                    zoom = map.cameraPosition.zoom,
                ),
            )
        }
        map.addOnCameraIdleListener(listener)
        onDispose { map.removeOnCameraIdleListener(listener) }
    }

    LaunchedEffect(mapLibreMap, styleUri, revealStyle) {
        val map = mapLibreMap ?: return@LaunchedEffect
        styleReady = false
        map.setStyle(Style.Builder().fromUri(styleUri)) { style ->
            ensureRevealLayers(
                style = style,
                fogGeoJson = emptyFogGeoJson,
                revealStyle = revealStyle,
            )
            styleReady = true
        }
    }

    LaunchedEffect(fogGeoJson, styleReady, mapLibreMap, revealStyle) {
        if (!styleReady) return@LaunchedEffect
        val map = mapLibreMap ?: return@LaunchedEffect
        map.getStyle { style ->
            val source = style.getSourceAs<GeoJsonSource>(REVEAL_SOURCE_ID)
            if (source != null) {
                source.setGeoJson(fogGeoJson)
            } else {
                ensureRevealLayers(
                    style = style,
                    fogGeoJson = fogGeoJson,
                    revealStyle = revealStyle,
                )
            }
        }
    }

    LaunchedEffect(cameraTarget, mapLibreMap) {
        val target = cameraTarget ?: return@LaunchedEffect
        val map = mapLibreMap ?: return@LaunchedEffect
        map.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(LatLng(target.latitude, target.longitude))
                    .zoom(target.zoom)
                    .build(),
            ),
            1_200,
        )
        onCameraTargetConsumed()
    }
}

private fun ensureRevealLayers(
    style: Style,
    fogGeoJson: String,
    revealStyle: RevealStyle,
) {
    if (style.getSource(REVEAL_SOURCE_ID) == null) {
        style.addSource(GeoJsonSource(REVEAL_SOURCE_ID, fogGeoJson))
    } else {
        style.getSourceAs<GeoJsonSource>(REVEAL_SOURCE_ID)?.setGeoJson(fogGeoJson)
    }
    if (style.getLayer(REVEAL_FILL_LAYER_ID) == null) {
        style.addLayer(
            FillLayer(REVEAL_FILL_LAYER_ID, REVEAL_SOURCE_ID).withProperties(
                PropertyFactory.fillColor(revealStyle.washColorArgb),
                PropertyFactory.fillOpacity(revealStyle.washOpacity),
                PropertyFactory.fillAntialias(true),
            ),
        )
    }
    if (style.getLayer(REVEAL_EDGE_LAYER_ID) == null) {
        style.addLayer(
            LineLayer(REVEAL_EDGE_LAYER_ID, REVEAL_SOURCE_ID).withProperties(
                PropertyFactory.lineColor(revealStyle.edgeColorArgb),
                PropertyFactory.lineWidth(revealStyle.edgeWidthDp),
                PropertyFactory.lineOpacity(0.55f),
            ),
        )
    }
}
