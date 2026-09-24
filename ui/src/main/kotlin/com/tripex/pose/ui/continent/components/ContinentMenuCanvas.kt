package com.tripex.pose.ui.continent.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.tripex.pose.domain.geo.ContinentId
import com.tripex.pose.domain.geo.atlas.ContinentShape
import com.tripex.pose.domain.geo.projection.EquirectangularProjection
import com.tripex.pose.domain.geo.projection.GeometryOps
import com.tripex.pose.ui.continent.ContinentPalette
import kotlin.math.max

/** Rings are projected once at this width; the fit-to-screen is applied as a draw transform. */
internal const val PATH_WORLD_WIDTH: Double = 4096.0

private val OutlineWidth = 1.5.dp
private val SelectedOutlineWidth = 3.dp

private class ContinentPath(
    val id: ContinentId,
    val shape: ContinentShape,
    val path: Path,
)

/**
 * Stage A — the continent menu (vision §3 and §4).
 *
 * Whole continents in their fixed HEXes over water, and nothing else: no country borders, no
 * basemap, no parchment. Those belong to Stage B, which is a separate screen on purpose.
 *
 * The world **fills** the viewport rather than being letterboxed inside it, and the only gesture
 * is a tap: no zoom and no drag, because this is a menu, not a map. Sideways movement belongs to
 * the slider in the bottom panel alone — a finger drag fighting the slider over one value is what
 * made the world jitter. Vertically the whole latitude band is always on screen, so Antarctica
 * stays reachable without scrolling.
 *
 * Hit-testing runs on geographic coordinates via [GeometryOps.pointInRing], so tapping an island
 * selects its parent continent.
 */
@Composable
internal fun ContinentMenuCanvas(
    shapes: List<ContinentShape>,
    selectedId: ContinentId?,
    dimFraction: Float,
    onTap: (ContinentId?) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Where the world sits horizontally, 0 (west) to 1 (east) — see [WorldPan].
     *
     * Driven only by the slider below the map; the canvas reads it and never writes it.
     */
    panFraction: Float = WorldPan.CENTRE,
) {
    val density = LocalDensity.current
    val paths = remember(shapes) { shapes.map { it.toPath() } }
    val currentOnTap by rememberUpdatedState(onTap)

    /** Size of the last layout pass, needed to turn the fraction into pixels. */
    var canvasSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }

    /** Left edge of the world in pixels, derived from the hoisted fraction. */
    val pan: Float? = if (canvasSize.width > 0f) {
        WorldPan.toPan(panFraction, canvasSize.width, canvasSize.height)
    } else {
        null
    }

    // The tap detector outlives recompositions, so it must read the pan through a state holder —
    // a captured value stays at the first frame's centred world while the slider moves the drawing.
    val currentPan by rememberUpdatedState(pan)

    val outlinePx = with(density) { OutlineWidth.toPx() }
    val selectedOutlinePx = with(density) { SelectedOutlineWidth.toPx() }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(paths) {
                detectTapGestures { tap ->
                    val fit = WorldFit.of(size.width.toFloat(), size.height.toFloat(), currentPan)
                    val (lng, lat) = fit.toLngLat(tap.x, tap.y)
                    currentOnTap(paths.continentAt(lng, lat))
                }
            },
    ) {
        if (paths.isEmpty()) return@Canvas
        canvasSize = size
        val fit = WorldFit.of(size.width, size.height, pan)
        if (fit.scale <= 0f) return@Canvas

        withTransform({
            translate(fit.offsetX, fit.offsetY)
            scale(fit.scale, fit.scale, pivot = Offset.Zero)
        }) {
            for (entry in paths) {
                val dim = if (selectedId != null && entry.id != selectedId) dimFraction else 0f
                val isSelected = entry.id == selectedId
                drawPath(entry.path, ContinentPalette.dimmedFill(entry.id, dim))
                drawPath(
                    path = entry.path,
                    color = ContinentPalette.dimmedOutline(entry.id, dim),
                    style = Stroke(
                        width = (if (isSelected) selectedOutlinePx else outlinePx) / fit.scale,
                    ),
                )
            }
        }
    }
}

/**
 * The world scaled to **cover** the viewport, aspect preserved, slid sideways by the current pan.
 *
 * Covering rather than fitting is what removes the wide empty bands of ocean above and below the
 * continents; the cost is that the world is then wider than the screen, which is exactly what the
 * slider is for.
 *
 * Kept as a value type with its own factory so the placement can be unit-tested — a canvas that
 * silently renders off-screen looks exactly like a canvas with no data.
 */
internal data class WorldFit(val scale: Float, val offsetX: Float, val offsetY: Float) {

    fun toLngLat(x: Float, y: Float): Pair<Double, Double> {
        if (scale <= 0f) return 0.0 to 0.0
        return EquirectangularProjection.xyToLngLat(
            x = ((x - offsetX) / scale).toDouble(),
            y = ((y - offsetY) / scale).toDouble(),
            worldWidth = PATH_WORLD_WIDTH,
        )
    }

    companion object {
        /** Projected world height at [PATH_WORLD_WIDTH], for the +84°…-90° band. */
        val WORLD_HEIGHT: Double = PATH_WORLD_WIDTH *
            (EquirectangularProjection.MAX_LAT - EquirectangularProjection.MIN_LAT) / 360.0

        /**
         * @param pan left edge of the world in pixels; `null` centres it on the prime meridian.
         */
        fun of(width: Float, height: Float, pan: Float? = null): WorldFit {
            if (width <= 0f || height <= 0f) return WorldFit(0f, 0f, 0f)
            val scale = coverScale(width, height)
            return WorldFit(
                scale = scale,
                offsetX = (pan ?: centeredPan(width, height))
                    .coerceIn(panRange(width, height)),
                offsetY = (height - (WORLD_HEIGHT * scale).toFloat()) / 2f,
            )
        }

        /** How far the world may slide: from "right edge flush" up to "left edge flush". */
        fun panRange(width: Float, height: Float): ClosedFloatingPointRange<Float> {
            if (width <= 0f || height <= 0f) return 0f..0f
            val worldWidth = worldWidthPx(width, height)
            // A world narrower than the screen cannot pan; it only has its centred position.
            if (worldWidth <= width) return centeredPan(width, height).let { it..it }
            return (width - worldWidth)..0f
        }

        /** Starting position: the world centred, i.e. the prime meridian in the middle. */
        fun centeredPan(width: Float, height: Float): Float {
            if (width <= 0f || height <= 0f) return 0f
            return (width - worldWidthPx(width, height)) / 2f
        }

        /** Smallest scale at which the world still covers the viewport in both directions. */
        private fun coverScale(width: Float, height: Float): Float =
            max(width / PATH_WORLD_WIDTH, height / WORLD_HEIGHT).toFloat()

        private fun worldWidthPx(width: Float, height: Float): Float =
            (PATH_WORLD_WIDTH * coverScale(width, height)).toFloat()
    }
}

private fun ContinentShape.toPath(): ContinentPath {
    val path = Path()
    for (ring in rings) {
        if (ring.size < 3) continue
        var first = true
        for ((lng, lat) in ring) {
            val (x, y) = EquirectangularProjection.lngLatToXy(lng, lat, PATH_WORLD_WIDTH)
            if (first) {
                path.moveTo(x.toFloat(), y.toFloat())
                first = false
            } else {
                path.lineTo(x.toFloat(), y.toFloat())
            }
        }
        path.close()
    }
    return ContinentPath(id = id, shape = this, path = path)
}

private fun List<ContinentPath>.continentAt(lng: Double, lat: Double): ContinentId? {
    for (entry in this) {
        val bounds = entry.shape.bounds
        if (lat > bounds.north || lat < bounds.south) continue
        if (lng < bounds.west || lng > bounds.east) continue
        if (entry.shape.rings.any { GeometryOps.pointInRing(it, lng, lat) }) return entry.id
    }
    return null
}
