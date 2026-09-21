package com.tripex.pose.ui.map.host

import android.content.ComponentCallbacks
import android.content.res.Configuration
import android.graphics.RectF
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.savedstate.compose.LocalSavedStateRegistryOwner
import com.tripex.pose.domain.geo.ContinentId
import com.tripex.pose.domain.geo.GeoBounds
import com.tripex.pose.domain.geo.atlas.BoundaryFields
import com.tripex.pose.ui.continent.ContinentPalette
import com.tripex.pose.ui.explore.BoundaryTap
import com.tripex.pose.ui.explore.components.BoundarySurfaceMetrics
import com.tripex.pose.ui.theme.LocalCartoonStyle
import com.tripex.pose.ui.theme.LocalRevealStyle
import java.util.Locale
import kotlin.math.roundToInt
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.VectorSource
import org.maplibre.geojson.Feature

private const val MAP_VIEW_STATE_KEY = "tripail_map_surface_state"
private const val BOUNDARY_SOURCE_ID = "boundaries"
private const val FOG_SOURCE_ID = "reveal-source"

private const val FOG_FILL = "reveal-wash"
private const val FOG_EDGE = "reveal-edge"
private const val BACKGROUND_LAYER = "background"
private const val ADM0_FILL_HIT = "adm0-fill-hit"
private const val ADM0_FILL_SELECTED = "adm0-fill-selected"
private const val ADM0_LINE = "adm0-line"
private const val ADM0_LINE_SELECTED = "adm0-line-selected"
private const val ADM1_FILL_HIT = "adm1-fill-hit"
private const val ADM1_FILL_SELECTED = "adm1-fill-selected"
private const val ADM1_LINE = "adm1-line"
private const val ADM1_LINE_SELECTED = "adm1-line-selected"

/** No real feature id equals this, so it is a filter that matches nothing. */
private const val NO_SELECTION = "\u0000none"

private const val MIN_ZOOM = 1.2
private const val MAX_ZOOM = 16.0

/**
 * Area fills must stay genuinely rendered, not hinted at.
 *
 * `queryRenderedFeatures` will not return a feature from a layer that is effectively invisible,
 * so dropping these to 0.02 to keep the parchment pure is what killed every tap on the map. They
 * carry the parchment's own colour, so they read as parchment while remaining hit-testable, and
 * at this opacity the holes underneath stay clearly legible (vision §2).
 */
private const val AREA_FILL_OPACITY = 0.22f

/**
 * Parchment over the countries the player did not pick, at the country level. They stay
 * hit-testable — switching country is still one tap — but visibly step back.
 */
private const val DIMMED_FILL_OPACITY = 0.5f

/** Only the picked area gets a brighter wash. */
private const val SELECTED_FILL_OPACITY = 0.35f

private const val LINE_OPACITY = 0.85f
private const val SELECTED_LINE_OPACITY = 1f
private const val SELECTED_LINE_WIDTH = 3f
private const val LINE_WIDTH = 1f

/**
 * Vision §4 forbids black and grey borders and allows translucent white. A constant is used on
 * purpose: a `match` expression that fails to evaluate makes MapLibre fall back to the style
 * spec's default `line-color`, which is pure black — that is where the black borders came from.
 */
private val BORDER_COLOR = android.graphics.Color.WHITE

private const val FOG_EDGE_WIDTH = 1.2f
private const val FOG_EDGE_OPACITY = 0.55f

/**
 * Framing margin as a share of the viewport's shorter side, not a fixed dp value.
 *
 * This is what makes the flight land identically on Luxembourg and on Brazil: the country always
 * ends up filling the same proportion of the screen, whatever its size on the globe.
 */
private const val CAMERA_PADDING_FRACTION = 0.13f

/** Used only before the surface has been measured. */
private const val CAMERA_PADDING_DP = 36
private const val HIT_SLOP_DP = 6

/** Deliberately unhurried: the country level is the one place the camera moves on its own. */
private const val FLY_DURATION_MS = 1_600

/** Label layers that name an area rather than a settlement — they only add noise here. */
private val AREA_LABEL_MARKERS = listOf(
    "country", "continent", "state", "ocean", "sea", "water", "marine", "boundary",
)

/**
 * The one and only MapLibre instance in the app.
 *
 * It is composed **above** the navigation graph, so moving between the world, a continent, a
 * country and the explore screen never tears the map down: the same surface stays on screen and
 * only its filters change. That is what makes the transitions seamless, and it is also why the
 * camera no longer jumps — previously every destination built a fresh `MapView`, which started at
 * MapLibre's default position somewhere off the coast of Africa.
 *
 * Layer order, bottom to top:
 * 1. the live basemap,
 * 2. the parchment wash with unlocked areas punched out,
 * 3. translucent country / region fills and their outlines,
 * 4. the basemap's own place labels.
 */
@Composable
internal fun TripailMapSurface(
    host: MapHostState,
    styleUri: String,
    boundarySourceUri: String,
    onFeatureTap: (BoundaryTap) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val savedStateRegistryOwner = LocalSavedStateRegistryOwner.current
    val cartoon = LocalCartoonStyle.current
    val reveal = LocalRevealStyle.current

    val mapView = remember {
        val restored = savedStateRegistryOwner.savedStateRegistry
            .consumeRestoredStateForKey(MAP_VIEW_STATE_KEY)
        MapView(context).also { it.onCreate(restored) }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }

    val currentOnTap by rememberUpdatedState(onFeatureTap)
    val currentScene by rememberUpdatedState(host.scene)

    val oceanArgb = cartoon.oceanBlue.toArgb()
    DisposableEffect(lifecycleOwner, mapView) {
        val registry = savedStateRegistryOwner.savedStateRegistry
        registry.registerSavedStateProvider(MAP_VIEW_STATE_KEY) {
            Bundle().also { mapView.onSaveInstanceState(it) }
        }
        val lowMemory = object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) = Unit
            override fun onLowMemory() = mapView.onLowMemory()
        }
        context.registerComponentCallbacks(lowMemory)
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
            registry.unregisterSavedStateProvider(MAP_VIEW_STATE_KEY)
            context.unregisterComponentCallbacks(lowMemory)
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)

    // Built once. Nothing below this point ever rebuilds the style or the layers.
    LaunchedEffect(mapView, styleUri, boundarySourceUri) {
        if (boundarySourceUri.isEmpty()) return@LaunchedEffect
        mapView.getMapAsync { libreMap ->
            map = libreMap
            libreMap.uiSettings.apply {
                isRotateGesturesEnabled = false
                isTiltGesturesEnabled = false
                isAttributionEnabled = true
                isLogoEnabled = true
            }
            libreMap.setMinZoomPreference(MIN_ZOOM)
            libreMap.setMaxZoomPreference(MAX_ZOOM)

            val builder = if (styleUri.isNotEmpty()) {
                Style.Builder().fromUri(styleUri)
            } else {
                Style.Builder().fromJson(oceanStyleJson(oceanArgb))
            }
            libreMap.setStyle(builder) { loaded ->
                BoundarySurfaceMetrics.onStyleBuilt()
                applyLabelPolicy(loaded)
                addFogLayers(loaded, reveal.washColorArgb, reveal.edgeColorArgb, reveal.washOpacity)
                loaded.addSource(VectorSource(BOUNDARY_SOURCE_ID, boundarySourceUri))
                addBoundaryLayers(loaded, reveal.washColorArgb)
                BoundarySurfaceMetrics.onLayersBuilt()
                style = loaded
            }
        }
    }

    // Discovering ground rewrites only the wash geometry.
    LaunchedEffect(style, host.fogGeoJson) {
        val loaded = style ?: return@LaunchedEffect
        if (host.fogGeoJson.isEmpty()) return@LaunchedEffect
        loaded.getSourceAs<GeoJsonSource>(FOG_SOURCE_ID)?.setGeoJson(host.fogGeoJson)
    }

    // A scene change is filters and visibility. It never touches the camera.
    LaunchedEffect(style, host.scene) {
        val loaded = style ?: return@LaunchedEffect
        applyScene(loaded, host.scene)
        applyContinentTint(loaded, host.scene.continentId)
        BoundarySurfaceMetrics.onFiltersApplied()
    }

    // The camera moves only when something explicitly asked it to, and then exactly once.
    LaunchedEffect(map, host.cameraRequest) {
        val libreMap = map ?: return@LaunchedEffect
        val request = host.cameraRequest ?: return@LaunchedEffect
        val shorterSide = minOf(mapView.width, mapView.height)
        val padding = if (shorterSide > 0) {
            (shorterSide * CAMERA_PADDING_FRACTION).roundToInt()
        } else {
            with(density) { CAMERA_PADDING_DP.dp.roundToPx() }
        }
        libreMap.frame(request.bounds, padding, request.animate)
        host.onCameraApplied(request.token)
    }

    DisposableEffect(map, style) {
        val libreMap = map ?: return@DisposableEffect onDispose { }
        val slop = with(density) { HIT_SLOP_DP.dp.toPx() }
        val listener = MapLibreMap.OnMapClickListener { point ->
            val screen = libreMap.projection.toScreenLocation(point)
            val box = RectF(screen.x - slop, screen.y - slop, screen.x + slop, screen.y + slop)
            val tap = libreMap.boundaryAt(box, currentScene)
            if (tap != null) currentOnTap(tap)
            tap != null
        }
        libreMap.addOnMapClickListener(listener)
        onDispose { libreMap.removeOnMapClickListener(listener) }
    }
}

private fun oceanStyleJson(oceanColor: Int): String {
    val hex = String.format(Locale.ROOT, "#%06X", 0xFFFFFF and oceanColor)
    return """
        {"version":8,"name":"tripail-ocean","sources":{},
         "layers":[{"id":"background","type":"background","paint":{"background-color":"$hex"}}]}
    """.trimIndent()
}

private fun applyLabelPolicy(style: Style) {
    // Vision §4: the map speaks Polish. MapTiler ships `name:pl` on its label layers, so every
    // surviving symbol layer is pointed at it with the English `name` as fallback.
    val polishName = Expression.coalesce(
        Expression.get("name:pl"),
        Expression.get("name_pl"),
        Expression.get("name"),
    )
    for (layer in style.layers) {
        if (layer !is SymbolLayer) continue
        val id = layer.id.lowercase(Locale.ROOT)
        if (AREA_LABEL_MARKERS.any { id.contains(it) }) {
            layer.setProperties(PropertyFactory.visibility(Property.NONE))
        } else {
            runCatching { layer.setProperties(PropertyFactory.textField(polishName)) }
        }
    }
}

/** Parchment between the basemap and its labels: streets vanish, place names stay readable. */
private fun addFogLayers(style: Style, washColor: Int, edgeColor: Int, washOpacity: Float) {
    if (style.getSource(FOG_SOURCE_ID) != null) return
    style.addSource(GeoJsonSource(FOG_SOURCE_ID, """{"type":"FeatureCollection","features":[]}"""))

    val fill = FillLayer(FOG_FILL, FOG_SOURCE_ID).withProperties(
        PropertyFactory.fillColor(washColor),
        PropertyFactory.fillOpacity(washOpacity),
        PropertyFactory.fillAntialias(true),
    )
    val edge = LineLayer(FOG_EDGE, FOG_SOURCE_ID).withProperties(
        PropertyFactory.lineColor(edgeColor),
        PropertyFactory.lineWidth(FOG_EDGE_WIDTH),
        PropertyFactory.lineOpacity(FOG_EDGE_OPACITY),
    )
    val firstSymbol = style.layers.firstOrNull { it is SymbolLayer }?.id
    if (firstSymbol != null) {
        style.addLayerBelow(fill, firstSymbol)
        style.addLayerBelow(edge, firstSymbol)
    } else {
        style.addLayer(fill)
        style.addLayer(edge)
    }
}

/**
 * Country fills are coloured **by continent**, straight from the tiles, with a `match` expression.
 * That is what lets the world level look like a continent map without a second renderer.
 */
private fun addBoundaryLayers(style: Style, parchmentColor: Int) {
    // Everything here goes *below* the first symbol layer, so place labels always render on top
    // of borders. Adding them at the top of the stack let white country outlines cut straight
    // through city names.
    val firstSymbolId = style.layers.firstOrNull { it is SymbolLayer }?.id

    fun add(layer: Layer) {
        if (firstSymbolId != null) style.addLayerBelow(layer, firstSymbolId) else style.addLayer(layer)
    }

    fun areaFill(id: String, layerName: String, color: Int, opacity: Float) =
        FillLayer(id, BOUNDARY_SOURCE_ID).withSourceLayer(layerName).withProperties(
            PropertyFactory.fillColor(color),
            PropertyFactory.fillOpacity(opacity),
            PropertyFactory.fillAntialias(true),
        )

    fun border(id: String, layerName: String, width: Float, opacity: Float) =
        LineLayer(id, BOUNDARY_SOURCE_ID).withSourceLayer(layerName).withProperties(
            PropertyFactory.lineColor(BORDER_COLOR),
            PropertyFactory.lineWidth(width),
            PropertyFactory.lineOpacity(opacity),
        )

    val adm0 = BoundaryFields.LAYER_ADM0
    val adm1 = BoundaryFields.LAYER_ADM1

    add(areaFill(ADM0_FILL_HIT, adm0, parchmentColor, AREA_FILL_OPACITY))
    add(areaFill(ADM0_FILL_SELECTED, adm0, BORDER_COLOR, SELECTED_FILL_OPACITY))
    add(border(ADM0_LINE, adm0, LINE_WIDTH, LINE_OPACITY))
    add(border(ADM0_LINE_SELECTED, adm0, SELECTED_LINE_WIDTH, SELECTED_LINE_OPACITY))

    add(areaFill(ADM1_FILL_HIT, adm1, parchmentColor, AREA_FILL_OPACITY))
    add(areaFill(ADM1_FILL_SELECTED, adm1, BORDER_COLOR, SELECTED_FILL_OPACITY))
    add(border(ADM1_LINE, adm1, LINE_WIDTH, LINE_OPACITY))
    add(border(ADM1_LINE_SELECTED, adm1, SELECTED_LINE_WIDTH, SELECTED_LINE_OPACITY))
}

/**
 * The only thing a scene change is allowed to do. It never touches the camera — at every level
 * but the country one the view is the player's, and even there the flight is issued separately.
 */
private fun applyScene(style: Style, scene: MapScene) {
    val adm0Id = Expression.get(BoundaryFields.ADM0_ID)
    val adm1Id = Expression.get(BoundaryFields.ADM1_ID)
    val sentinel = Expression.literal(NO_SELECTION)
    val selected = Expression.literal(scene.selectedId ?: NO_SELECTION)

    val inContinent = scene.continentId?.let {
        Expression.eq(Expression.get(BoundaryFields.ADM0_CONTINENT), Expression.literal(it))
    } ?: Expression.neq(adm0Id, sentinel)

    style.filter(ADM0_FILL_HIT, inContinent)
    style.filter(ADM0_LINE, inContinent)
    style.opacity(
        ADM0_FILL_HIT,
        if (scene.dimsOtherCountries) DIMMED_FILL_OPACITY else AREA_FILL_OPACITY,
    )
    style.filter(ADM0_FILL_SELECTED, Expression.all(inContinent, Expression.eq(adm0Id, selected)))
    style.filter(ADM0_LINE_SELECTED, Expression.all(inContinent, Expression.eq(adm0Id, selected)))

    val regionCountry = scene.countryIso2
    if (scene.showsRegionContext && regionCountry != null) {
        val inCountry = Expression.eq(
            Expression.get(BoundaryFields.ADM1_COUNTRY),
            Expression.literal(regionCountry),
        )
        style.filter(ADM1_FILL_HIT, inCountry)
        style.filter(ADM1_LINE, inCountry)
        style.filter(ADM1_FILL_SELECTED, Expression.all(inCountry, Expression.eq(adm1Id, selected)))
        style.filter(ADM1_LINE_SELECTED, Expression.all(inCountry, Expression.eq(adm1Id, selected)))
        style.show(ADM1_LINE)
        // At the country level the region outlines are there to be read, not tapped: their fills
        // stay off so a tap still resolves to a country (see `boundaryAt`).
        if (scene.showsRegions) {
            style.show(ADM1_FILL_HIT, ADM1_FILL_SELECTED, ADM1_LINE_SELECTED)
        } else {
            style.hide(ADM1_FILL_HIT, ADM1_FILL_SELECTED, ADM1_LINE_SELECTED)
        }
    } else {
        style.hide(ADM1_FILL_HIT, ADM1_FILL_SELECTED, ADM1_LINE, ADM1_LINE_SELECTED)
    }

    // Country borders stay visible even while exploring — vision §2 keeps them on the parchment.
    style.show(ADM0_FILL_HIT, ADM0_LINE, ADM0_FILL_SELECTED, ADM0_LINE_SELECTED, FOG_FILL, FOG_EDGE)
}

/**
 * Re-tints the map to the continent being explored (vision §2).
 *
 * Paint values only — no source reload and no layer rebuild, so this stays as cheap as a filter
 * change and cannot reintroduce the tile flicker.
 */
private fun applyContinentTint(style: Style, continentId: String?) {
    val id = continentId?.let { runCatching { ContinentId.valueOf(it) }.getOrNull() }
    val tints = ContinentPalette.mapTints(id)

    (style.getLayer(FOG_FILL) as? FillLayer)
        ?.setProperties(PropertyFactory.fillColor(tints.parchment.toArgb()))
    (style.getLayer(ADM0_FILL_HIT) as? FillLayer)
        ?.setProperties(PropertyFactory.fillColor(tints.land.toArgb()))
    (style.getLayer(ADM1_FILL_HIT) as? FillLayer)
        ?.setProperties(PropertyFactory.fillColor(tints.land.toArgb()))
    (style.getLayer(BACKGROUND_LAYER) as? BackgroundLayer)
        ?.setProperties(PropertyFactory.backgroundColor(tints.water.toArgb()))

    val highlight = ContinentPalette.selectedHighlight.toArgb()
    (style.getLayer(ADM0_FILL_SELECTED) as? FillLayer)
        ?.setProperties(PropertyFactory.fillColor(highlight))
    (style.getLayer(ADM1_FILL_SELECTED) as? FillLayer)
        ?.setProperties(PropertyFactory.fillColor(highlight))
}

private fun Style.filter(layerId: String, expression: Expression) {
    when (val layer = getLayer(layerId)) {
        is FillLayer -> layer.setFilter(expression)
        is LineLayer -> layer.setFilter(expression)
        else -> Unit
    }
}

private fun Style.opacity(layerId: String, value: Float) {
    (getLayer(layerId) as? FillLayer)?.setProperties(PropertyFactory.fillOpacity(value))
}

private fun Style.visible(layerId: String, visible: Boolean) {
    getLayer(layerId)?.setProperties(
        PropertyFactory.visibility(if (visible) Property.VISIBLE else Property.NONE),
    )
}

private fun Style.hide(vararg ids: String) = ids.forEach { visible(it, false) }

private fun Style.show(vararg ids: String) = ids.forEach { visible(it, true) }

/** The project's only hit-test against rendered tiles — a pointer, never a geometry source. */
private fun MapLibreMap.boundaryAt(box: RectF, scene: MapScene): BoundaryTap? {
    val layerIds = if (scene.showsRegions) {
        arrayOf(ADM1_FILL_HIT, ADM1_FILL_SELECTED)
    } else {
        arrayOf(ADM0_FILL_HIT, ADM0_FILL_SELECTED)
    }
    val idField = if (scene.showsRegions) BoundaryFields.ADM1_ID else BoundaryFields.ADM0_ID
    val nameField = if (scene.showsRegions) BoundaryFields.ADM1_NAME else BoundaryFields.ADM0_NAME
    val namePlField =
        if (scene.showsRegions) BoundaryFields.ADM1_NAME_PL else BoundaryFields.ADM0_NAME_PL

    val feature = queryRenderedFeatures(box, *layerIds).firstOrNull() ?: return null
    val id = feature.str(idField) ?: return null
    return BoundaryTap(
        featureId = id,
        name = feature.str(namePlField) ?: feature.str(nameField) ?: id,
        countryIso2 = feature.str(BoundaryFields.ADM1_COUNTRY),
        continentId = feature.str(BoundaryFields.ADM0_CONTINENT),
    )
}

private fun Feature.str(key: String): String? =
    runCatching { getStringProperty(key) }.getOrNull()?.takeIf { it.isNotBlank() }

/** Frames [bounds]; an antimeridian-spanning box is skipped rather than crashing MapLibre. */
private fun MapLibreMap.frame(bounds: GeoBounds, paddingPx: Int, animate: Boolean) {
    if (bounds.east <= bounds.west || bounds.north <= bounds.south) return
    val latLng = runCatching {
        LatLngBounds.from(
            bounds.north.coerceAtMost(MAX_LATITUDE),
            bounds.east,
            bounds.south.coerceAtLeast(-MAX_LATITUDE),
            bounds.west,
        )
    }.getOrNull() ?: return
    val update = CameraUpdateFactory.newLatLngBounds(latLng, paddingPx)
    runCatching {
        // `animateCamera` eases in and out; `easeCamera` moves at a constant rate and reads as a
        // lurch at this duration.
        if (animate) animateCamera(update, FLY_DURATION_MS) else moveCamera(update)
    }
}

private const val MAX_LATITUDE = 85.0
