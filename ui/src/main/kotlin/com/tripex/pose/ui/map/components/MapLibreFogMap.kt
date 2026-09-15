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
import com.tripex.pose.ui.theme.LocalFogStyle
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource

private const val FOG_SOURCE_ID = "fog-source"
private const val FOG_LAYER_ID = "fog-layer"
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
    val fogStyle = LocalFogStyle.current
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

    LaunchedEffect(mapLibreMap, styleUri, fogStyle) {
        val map = mapLibreMap ?: return@LaunchedEffect
        styleReady = false
        map.setStyle(Style.Builder().fromUri(styleUri)) { style ->
            ensureFogLayer(
                style = style,
                fogGeoJson = emptyFogGeoJson,
                fillColorArgb = fogStyle.fillColorArgb,
                fillOpacity = fogStyle.fillOpacity,
            )
            styleReady = true
        }
    }

    LaunchedEffect(fogGeoJson, styleReady, mapLibreMap, fogStyle) {
        if (!styleReady) return@LaunchedEffect
        val map = mapLibreMap ?: return@LaunchedEffect
        map.getStyle { style ->
            val source = style.getSourceAs<GeoJsonSource>(FOG_SOURCE_ID)
            if (source != null) {
                source.setGeoJson(fogGeoJson)
            } else {
                ensureFogLayer(
                    style = style,
                    fogGeoJson = fogGeoJson,
                    fillColorArgb = fogStyle.fillColorArgb,
                    fillOpacity = fogStyle.fillOpacity,
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

private fun ensureFogLayer(
    style: Style,
    fogGeoJson: String,
    fillColorArgb: Int,
    fillOpacity: Float,
) {
    if (style.getSource(FOG_SOURCE_ID) == null) {
        style.addSource(GeoJsonSource(FOG_SOURCE_ID, fogGeoJson))
    } else {
        style.getSourceAs<GeoJsonSource>(FOG_SOURCE_ID)?.setGeoJson(fogGeoJson)
    }
    if (style.getLayer(FOG_LAYER_ID) == null) {
        val layer = FillLayer(FOG_LAYER_ID, FOG_SOURCE_ID).withProperties(
            PropertyFactory.fillColor(fillColorArgb),
            PropertyFactory.fillOpacity(fillOpacity),
            PropertyFactory.fillAntialias(false),
        )
        style.addLayer(layer)
    }
}
