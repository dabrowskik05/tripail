package com.tripex.pose.ui.map.host

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.graphics.RectF
import android.os.Bundle
import android.view.View
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.savedstate.compose.LocalSavedStateRegistryOwner
import com.tripex.pose.domain.geo.ContinentId
import com.tripex.pose.domain.geo.GeoBounds
import com.tripex.pose.domain.geo.atlas.AdminLevel
import com.tripex.pose.domain.settings.AppLanguage
import com.tripex.pose.domain.geo.atlas.BoundaryFields
import com.tripex.pose.ui.R
import com.tripex.pose.ui.continent.ContinentPalette
import com.tripex.pose.ui.explore.BoundaryTap
import com.tripex.pose.ui.map.PlaceTap
import com.tripex.pose.ui.explore.components.BoundarySurfaceMetrics
import com.tripex.pose.ui.theme.LocalCartoonStyle
import com.tripex.pose.ui.theme.LocalRevealStyle
import java.util.Locale
import kotlin.math.roundToInt
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.CircleLayer
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

/** MapTiler's settlement layer. Stable across style versions, unlike the layer ids. */
private const val PLACE_SOURCE_LAYER = "place"

private const val PLAYER_SOURCE_ID = "player"
private const val PLAYER_HALO = "player-halo"
private const val PLAYER_PIN = "player-pin"
private const val PLAYER_ICON = "player-marker"
private const val PLAYER_HALO_RADIUS = 16f
private const val PLAYER_HALO_OPACITY = 0.25f
private const val EMPTY_FEATURES = """{"type":"FeatureCollection","features":[]}"""
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
private const val ADM0_LIFT_SHADOW = "adm0-lift-shadow"
private const val ADM0_LIFT_LINE_SHADOW = "adm0-lift-line-shadow"
private const val ADM1_LIFT_SHADOW = "adm1-lift-shadow"
private const val ADM1_LIFT_LINE_SHADOW = "adm1-lift-line-shadow"

/*
 * The "lifted" look, only on what was tapped: the picked country, or the picked region together
 * with its country. MapLibre has no real shadow, so each is a dark copy of the shape pushed
 * down-right — a hard one under the area and a soft, blurred one under its outline. Offsets are in
 * screen pixels, so the effect is the same at every zoom. Every other border stays flat.
 */
private val SHADOW_COLOR = android.graphics.Color.BLACK
private val LIFT_SHADOW_OFFSET = arrayOf(4f, 5f)
private const val LIFT_SHADOW_OPACITY = 0.35f
private val LIFT_LINE_SHADOW_OFFSET = arrayOf(1.5f, 2f)
private const val LIFT_LINE_SHADOW_WIDTH = 2.5f
private const val LIFT_LINE_SHADOW_BLUR = 2.5f
private const val LIFT_LINE_SHADOW_OPACITY = 0.3f
/** The rest of a picked region's country, darkened; opaque enough for the shade to register. */
private const val REST_OF_COUNTRY_OPACITY = 0.45f

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

/** The picked area: opaque enough that its lighter shade of the continent colour reads clearly. */
private const val SELECTED_FILL_OPACITY = 0.75f

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
 * Framing margins live on [CameraRequest]: a share of the viewport's shorter side, never a fixed
 * dp value. That is what makes a flight land identically on Luxembourg and on Brazil — the shape
 * fills the same proportion of the screen whatever its size on the globe.
 */
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
    onPlaceTap: (PlaceTap) -> Unit,
    language: AppLanguage,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    /** `(latitude, longitude)` of the player, or `null` when unknown — then no marker. */
    playerLocation: Pair<Double, Double>? = null,
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
        // TextureView rather than the default SurfaceView: a SurfaceView ignores alpha, so the
        // map could only be cut in and out, never faded, between the menu and the map levels.
        val options = MapLibreMapOptions.createFromAttributes(context).textureMode(true)
        MapView(context, options).also { it.onCreate(restored) }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }

    val currentOnTap by rememberUpdatedState(onFeatureTap)
    val currentOnPlaceTap by rememberUpdatedState(onPlaceTap)
    val currentScene by rememberUpdatedState(host.renderedScene)

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

    // The camera cannot be framed against a view that has not been measured — `fitBounds` on a
    // zero-sized surface produces a nonsense zoom, which is what made entering a continent land
    // far too far out. Tracking the size lets the pending request apply the moment it is real.
    var surfaceSize by remember { mutableStateOf(IntSize.Zero) }
    AndroidView(
        factory = { mapView },
        modifier = modifier.onSizeChanged { surfaceSize = it },
        // Hidden, not removed: an invisible view takes no touches and draws nothing, while the
        // one MapLibre instance and its loaded style survive for the next time it is shown.
        update = { it.visibility = if (visible) View.VISIBLE else View.INVISIBLE },
    )

    // Built once. Nothing below this point ever rebuilds the style or the layers.
    LaunchedEffect(mapView, styleUri, boundarySourceUri) {
        if (boundarySourceUri.isEmpty()) return@LaunchedEffect
        mapView.getMapAsync { libreMap ->
            map = libreMap
            libreMap.uiSettings.apply {
                isRotateGesturesEnabled = false
                isTiltGesturesEnabled = false
                // Drawn by the app instead (`MapAttribution`): MapLibre's logo is not required,
                // and the "i" button hid the text the data licences require to be visible.
                isAttributionEnabled = false
                isLogoEnabled = false
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
                applyLabelPolicy(loaded, language)
                addFogLayers(loaded, reveal.washColorArgb, reveal.edgeColorArgb, reveal.washOpacity)
                loaded.addSource(VectorSource(BOUNDARY_SOURCE_ID, boundarySourceUri))
                addBoundaryLayers(loaded, reveal.washColorArgb)
                addPlayerLayers(loaded, context, cartoon.buttonPrimary.toArgb())
                BoundarySurfaceMetrics.onLayersBuilt()
                style = loaded
            }
        }
    }

    // Switching language renames the map in place — no style reload, no camera jump (V3.5.4).
    LaunchedEffect(style, language) {
        val loaded = style ?: return@LaunchedEffect
        applyLabelPolicy(loaded, language)
    }

    // Discovering ground rewrites only the wash geometry.
    LaunchedEffect(style, host.fogGeoJson) {
        val loaded = style ?: return@LaunchedEffect
        if (host.fogGeoJson.isEmpty()) return@LaunchedEffect
        loaded.getSourceAs<GeoJsonSource>(FOG_SOURCE_ID)?.setGeoJson(host.fogGeoJson)
    }

    // The player's own position — the one thing on the map that is about them right now.
    LaunchedEffect(style, playerLocation) {
        val loaded = style ?: return@LaunchedEffect
        val json = playerLocation?.let { (lat, lng) ->
            Feature.fromGeometry(org.maplibre.geojson.Point.fromLngLat(lng, lat)).toJson()
        } ?: EMPTY_FEATURES
        loaded.getSourceAs<GeoJsonSource>(PLAYER_SOURCE_ID)?.setGeoJson(json)
    }

    // A scene change is filters and visibility. It never touches the camera.
    LaunchedEffect(style, host.renderedScene) {
        val loaded = style ?: return@LaunchedEffect
        applyScene(loaded, host.renderedScene)
        applyContinentTint(loaded, host.renderedScene)
        BoundarySurfaceMetrics.onFiltersApplied()
    }

    // The camera moves only when something explicitly asked it to, and then exactly once.
    // Keyed on the measured size too, so a request issued before layout is honoured afterwards
    // rather than being applied against a zero-sized viewport and silently consumed.
    LaunchedEffect(map, host.cameraRequest, surfaceSize) {
        val libreMap = map ?: return@LaunchedEffect
        val request = host.cameraRequest ?: return@LaunchedEffect
        val shorterSide = minOf(surfaceSize.width, surfaceSize.height)
        if (shorterSide <= 0) return@LaunchedEffect
        libreMap.frame(
            bounds = request.bounds,
            paddingPx = (shorterSide * request.paddingFraction).roundToInt(),
            animate = request.animate,
        )
        host.onCameraApplied(request.token)
    }

    // Ready = styled and placed. Until then the map is a blank or misplaced surface, and the
    // shell keeps it faded out and the panels back rather than show that.
    LaunchedEffect(style, host.cameraRequest) {
        if (style != null && host.cameraRequest == null) host.markReady()
    }

    // Keeps the camera on the continent being explored (V3.3.7). Null means the whole world.
    LaunchedEffect(map, host.maxBounds) {
        val libreMap = map ?: return@LaunchedEffect
        val limit = host.maxBounds
        runCatching {
            libreMap.setLatLngBoundsForCameraTarget(
                limit?.let {
                    LatLngBounds.from(
                        it.north.coerceAtMost(MAX_LATITUDE),
                        it.east.coerceAtMost(MAX_LONGITUDE),
                        it.south.coerceAtLeast(-MAX_LATITUDE),
                        it.west.coerceAtLeast(-MAX_LONGITUDE),
                    )
                },
            )
        }
    }

    DisposableEffect(map, style) {
        val libreMap = map ?: return@DisposableEffect onDispose { }
        val slop = with(density) { HIT_SLOP_DP.dp.toPx() }
        val listener = MapLibreMap.OnMapClickListener { point ->
            val screen = libreMap.projection.toScreenLocation(point)
            val box = RectF(screen.x - slop, screen.y - slop, screen.x + slop, screen.y + slop)

            // Settlements win over the area they sit in: a label is a smaller, more deliberate
            // target than the country behind it (V3.3.4).
            // Only settlements on the continent being explored: its countries are the only ones
            // rendered in the hit layer, so no country under the finger means another continent.
            val place = libreMap.placeAt(box)?.takeIf {
                currentScene.continentId == null || libreMap.countryAt(box) != null
            }
            if (place != null) {
                currentOnPlaceTap(place)
                return@OnMapClickListener true
            }
            val tap = libreMap.boundaryAt(box, currentScene)
            if (tap != null) currentOnTap(tap)
            tap != null
        }
        libreMap.addOnMapClickListener(listener)
        onDispose { libreMap.removeOnMapClickListener(listener) }
    }
}

/**
 * Marker layers on top of everything: a soft halo and the pin. Nothing else may draw over the
 * player's position — not the wash, not a label.
 */
private fun addPlayerLayers(style: Style, context: Context, haloColor: Int) {
    val pin = ContextCompat.getDrawable(context, R.drawable.ic_player_marker)
        ?: return
    style.addImage(PLAYER_ICON, pin.toBitmap())
    style.addSource(GeoJsonSource(PLAYER_SOURCE_ID, EMPTY_FEATURES))
    style.addLayer(
        CircleLayer(PLAYER_HALO, PLAYER_SOURCE_ID).withProperties(
            PropertyFactory.circleRadius(PLAYER_HALO_RADIUS),
            PropertyFactory.circleColor(haloColor),
            PropertyFactory.circleOpacity(PLAYER_HALO_OPACITY),
        ),
    )
    style.addLayer(
        SymbolLayer(PLAYER_PIN, PLAYER_SOURCE_ID).withProperties(
            PropertyFactory.iconImage(PLAYER_ICON),
            // The pin's tip marks the spot, not its middle.
            PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
        ),
    )
}

private fun oceanStyleJson(oceanColor: Int): String {
    val hex = String.format(Locale.ROOT, "#%06X", 0xFFFFFF and oceanColor)
    return """
        {"version":8,"name":"tripail-ocean","sources":{},
         "layers":[{"id":"background","type":"background","paint":{"background-color":"$hex"}}]}
    """.trimIndent()
}

/**
 * Points every surviving label layer at the chosen language (V3.5.4).
 *
 * Applied as a **property change on existing layers**, never by reloading the style: a reload
 * drops the camera back to the style's default position and flickers every tile, which is a
 * heavy price for renaming a city.
 *
 * The fallback chain matters as much as the first choice. Not every feature carries every
 * translation, and a label that resolves to nothing renders as an empty string — a nameless
 * city is worse than one named in the wrong language.
 */
private fun applyLabelPolicy(style: Style, language: AppLanguage) {
    val tags = language.mapNameTags
    val localisedName = Expression.coalesce(*tags.map { Expression.get(it) }.toTypedArray())

    for (layer in style.layers) {
        if (layer !is SymbolLayer) continue
        val id = layer.id.lowercase(Locale.ROOT)
        if (AREA_LABEL_MARKERS.any { id.contains(it) }) {
            layer.setProperties(PropertyFactory.visibility(Property.NONE))
        } else {
            runCatching { layer.setProperties(PropertyFactory.textField(localisedName)) }
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

    fun liftShadow(id: String, layerName: String) =
        FillLayer(id, BOUNDARY_SOURCE_ID).withSourceLayer(layerName).withProperties(
            PropertyFactory.fillColor(SHADOW_COLOR),
            PropertyFactory.fillOpacity(LIFT_SHADOW_OPACITY),
            PropertyFactory.fillTranslate(LIFT_SHADOW_OFFSET),
        )

    fun liftLineShadow(id: String, layerName: String) =
        LineLayer(id, BOUNDARY_SOURCE_ID).withSourceLayer(layerName).withProperties(
            PropertyFactory.lineColor(SHADOW_COLOR),
            PropertyFactory.lineWidth(LIFT_LINE_SHADOW_WIDTH),
            PropertyFactory.lineBlur(LIFT_LINE_SHADOW_BLUR),
            PropertyFactory.lineOpacity(LIFT_LINE_SHADOW_OPACITY),
            PropertyFactory.lineTranslate(LIFT_LINE_SHADOW_OFFSET),
        )

    add(areaFill(ADM0_FILL_HIT, adm0, parchmentColor, AREA_FILL_OPACITY))
    add(liftShadow(ADM0_LIFT_SHADOW, adm0))
    add(areaFill(ADM0_FILL_SELECTED, adm0, BORDER_COLOR, SELECTED_FILL_OPACITY))
    add(liftLineShadow(ADM0_LIFT_LINE_SHADOW, adm0))
    add(border(ADM0_LINE, adm0, LINE_WIDTH, LINE_OPACITY))
    add(border(ADM0_LINE_SELECTED, adm0, SELECTED_LINE_WIDTH, SELECTED_LINE_OPACITY))

    add(areaFill(ADM1_FILL_HIT, adm1, parchmentColor, AREA_FILL_OPACITY))
    add(liftShadow(ADM1_LIFT_SHADOW, adm1))
    add(areaFill(ADM1_FILL_SELECTED, adm1, BORDER_COLOR, SELECTED_FILL_OPACITY))
    add(liftLineShadow(ADM1_LIFT_LINE_SHADOW, adm1))
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

    // Lifted: the picked country — or, once a region is picked, the country it belongs to.
    val liftedCountry = Expression.literal(
        (if (scene.level == MapLevel.Region) scene.countryIso2 else scene.selectedId) ?: NO_SELECTION,
    )
    style.filter(ADM0_LIFT_SHADOW, Expression.all(inContinent, Expression.eq(adm0Id, liftedCountry)))
    style.filter(ADM0_LIFT_LINE_SHADOW, Expression.all(inContinent, Expression.eq(adm0Id, liftedCountry)))

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
        style.filter(ADM1_LIFT_SHADOW, Expression.all(inCountry, Expression.eq(adm1Id, selected)))
        style.filter(ADM1_LIFT_LINE_SHADOW, Expression.all(inCountry, Expression.eq(adm1Id, selected)))
        style.show(ADM1_LINE)
        // Region fills stay on at the country level as well, so a region can be tapped without
        // first pressing anything (V3.3.2). `boundaryAt` queries them before the countries and
        // falls through, which is what keeps the neighbouring country reachable (V3.3.3).
        if (scene.showsRegions) {
            style.show(ADM1_FILL_HIT, ADM1_LIFT_SHADOW, ADM1_FILL_SELECTED, ADM1_LIFT_LINE_SHADOW, ADM1_LINE_SELECTED)
        } else {
            style.hide(ADM1_FILL_HIT, ADM1_LIFT_SHADOW, ADM1_FILL_SELECTED, ADM1_LIFT_LINE_SHADOW, ADM1_LINE_SELECTED)
        }
    } else {
        style.hide(
            ADM1_FILL_HIT, ADM1_LIFT_SHADOW, ADM1_FILL_SELECTED,
            ADM1_LIFT_LINE_SHADOW, ADM1_LINE, ADM1_LINE_SELECTED,
        )
    }

    // Country borders stay visible even while exploring — vision §2 keeps them on the parchment.
    style.show(
        ADM0_FILL_HIT, ADM0_LIFT_SHADOW, ADM0_FILL_SELECTED, ADM0_LIFT_LINE_SHADOW, ADM0_LINE,
        ADM0_LINE_SELECTED, FOG_FILL, FOG_EDGE,
    )
}

/**
 * Re-tints the map to the continent being explored (vision §2).
 *
 * Paint values only — no source reload and no layer rebuild, so this stays as cheap as a filter
 * change and cannot reintroduce the tile flicker.
 */
private fun applyContinentTint(style: Style, scene: MapScene) {
    val id = scene.continentId?.let { runCatching { ContinentId.valueOf(it) }.getOrNull() }
    val tints = ContinentPalette.mapTints(id)

    (style.getLayer(FOG_FILL) as? FillLayer)
        ?.setProperties(PropertyFactory.fillColor(tints.parchment.toArgb()))
    (style.getLayer(ADM0_FILL_HIT) as? FillLayer)
        ?.setProperties(PropertyFactory.fillColor(tints.land.toArgb()))
    // A picked region stands out twice: lighter itself, and the rest of its country darker.
    val regionPicked = scene.level == MapLevel.Region && scene.selectedId != null
    // The picked region itself is left out of the shade, so its lighter colour is not muddied by
    // the darker one showing through underneath.
    val hitColor = if (regionPicked) {
        Expression.switchCase(
            Expression.eq(Expression.get(BoundaryFields.ADM1_ID), Expression.literal(scene.selectedId.orEmpty())),
            Expression.color(tints.land.toArgb()),
            Expression.color(tints.restOfCountry.toArgb()),
        )
    } else {
        Expression.color(tints.land.toArgb())
    }
    (style.getLayer(ADM1_FILL_HIT) as? FillLayer)?.setProperties(
        PropertyFactory.fillColor(hitColor),
        PropertyFactory.fillOpacity(if (regionPicked) REST_OF_COUNTRY_OPACITY else AREA_FILL_OPACITY),
    )
    (style.getLayer(BACKGROUND_LAYER) as? BackgroundLayer)
        ?.setProperties(PropertyFactory.backgroundColor(tints.water.toArgb()))

    val highlight = tints.selected.toArgb()
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
/**
 * Resolves a tap to the **narrowest** thing under the finger (V3.3.2, V3.3.3).
 *
 * Regions are queried first, then countries. That order is the whole behaviour: inside the open
 * country a region wins, and anywhere else the query falls through to the country underneath —
 * so the neighbour across the border is always one tap away, even while a region is selected.
 * The old code picked a single layer set from a mode flag, which is why it could only ever offer
 * one of the two.
 */
private fun MapLibreMap.boundaryAt(box: RectF, scene: MapScene): BoundaryTap? {
    if (scene.showsRegions) {
        regionAt(box)?.let { return it }
    }
    return countryAt(box)
}

private fun MapLibreMap.regionAt(box: RectF): BoundaryTap? {
    val feature = queryRenderedFeatures(box, ADM1_FILL_HIT, ADM1_FILL_SELECTED).firstOrNull()
        ?: return null
    val id = feature.str(BoundaryFields.ADM1_ID) ?: return null
    return BoundaryTap(
        featureId = id,
        name = feature.str(BoundaryFields.ADM1_NAME_PL) ?: feature.str(BoundaryFields.ADM1_NAME) ?: id,
        level = AdminLevel.Adm1,
        countryIso2 = feature.str(BoundaryFields.ADM1_COUNTRY),
    )
}

private fun MapLibreMap.countryAt(box: RectF): BoundaryTap? {
    val feature = queryRenderedFeatures(box, ADM0_FILL_HIT, ADM0_FILL_SELECTED).firstOrNull()
        ?: return null
    val id = feature.str(BoundaryFields.ADM0_ID) ?: return null
    return BoundaryTap(
        featureId = id,
        name = feature.str(BoundaryFields.ADM0_NAME_PL) ?: feature.str(BoundaryFields.ADM0_NAME) ?: id,
        level = AdminLevel.Adm0,
        countryIso2 = id,
        continentId = feature.str(BoundaryFields.ADM0_CONTINENT),
    )
}

/**
 * A settlement label or icon under the finger (V3.3.4).
 *
 * The basemap's own `place` source-layer is queried, not our boundary tiles — which is why the
 * layers are found by their **source layer** rather than by id. MapTiler's style names are not a
 * stable contract and have changed between style versions; `place` is.
 */
private fun MapLibreMap.placeAt(box: RectF): PlaceTap? {
    val style = style ?: return null
    val placeLayers = style.layers
        .filterIsInstance<SymbolLayer>()
        .filter { it.sourceLayer == PLACE_SOURCE_LAYER }
        .map { it.id }
    if (placeLayers.isEmpty()) return null

    val feature = queryRenderedFeatures(box, *placeLayers.toTypedArray()).firstOrNull() ?: return null
    val name = feature.str("name:pl")
        ?: feature.str("name:en")
        ?: feature.str("name")
        ?: return null
    val point = feature.geometry() as? org.maplibre.geojson.Point ?: return null

    return PlaceTap(
        name = name,
        latitude = point.latitude(),
        longitude = point.longitude(),
        // `class` is MapTiler's own categorisation: city, town, village, suburb…
        placeClass = feature.str("class"),
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
private const val MAX_LONGITUDE = 180.0
