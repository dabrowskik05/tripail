package com.tripex.pose.ui.continent.components

import android.graphics.Region
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.vector.PathParser
import androidx.core.graphics.toRect
import com.tripex.pose.domain.geo.ContinentId
import com.tripex.pose.ui.continent.ContinentCatalog
import com.tripex.pose.ui.continent.ContinentCatalogEntry
import kotlin.math.min

@Composable
internal fun SvgRegionCanvas(
    entries: List<ContinentCatalogEntry>,
    selectedId: ContinentId?,
    scale: Float,
    offset: Offset,
    onScaleOffsetChange: (scale: Float, offset: Offset) -> Unit,
    onContinentClick: (ContinentId) -> Unit,
    bounceTranslationY: (ContinentId) -> Float,
    bounceScale: (ContinentId) -> Float,
    modifier: Modifier = Modifier,
) {
    val parsed = remember(entries) {
        entries.map { entry ->
            val path = PathParser().parsePathString(entry.pathData).toPath()
            entry to path
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(entries, scale, offset) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(0.7f, 2.5f)
                    onScaleOffsetChange(newScale, offset + pan)
                }
            }
            .pointerInput(parsed, scale, offset) {
                detectTapGestures { tap ->
                    val fit = fitScale(size.width.toFloat(), size.height.toFloat())
                    val originX = (size.width - ContinentCatalog.VIEW_BOX_WIDTH * fit) / 2f
                    val originY = (size.height - ContinentCatalog.VIEW_BOX_HEIGHT * fit) / 2f
                    val viewX = (tap.x - offset.x - originX) / (fit * scale)
                    val viewY = (tap.y - offset.y - originY) / (fit * scale)
                    for ((entry, path) in parsed.asReversed()) {
                        if (hitTest(path, viewX, viewY)) {
                            onContinentClick(entry.id)
                            return@detectTapGestures
                        }
                    }
                }
            },
    ) {
        val fit = fitScale(size.width, size.height)
        val originX = (size.width - ContinentCatalog.VIEW_BOX_WIDTH * fit) / 2f
        val originY = (size.height - ContinentCatalog.VIEW_BOX_HEIGHT * fit) / 2f

        withTransform({
            translate(left = offset.x + originX, top = offset.y + originY)
            scale(scaleX = fit * scale, scaleY = fit * scale, pivot = Offset.Zero)
        }) {
            for ((entry, path) in parsed) {
                val bounceY = bounceTranslationY(entry.id)
                val bScale = bounceScale(entry.id)
                withTransform({
                    translate(top = bounceY)
                    scale(bScale, bScale, pivot = pathBoundsCenter(path))
                }) {
                    drawPath(path, entry.color)
                    drawPath(
                        path,
                        Color(0xFF2B2250).copy(alpha = if (entry.id == selectedId) 0.55f else 0.25f),
                        style = Stroke(width = if (entry.id == selectedId) 4f else 2f),
                    )
                }
            }
        }
    }
}

private fun fitScale(width: Float, height: Float): Float =
    min(width / ContinentCatalog.VIEW_BOX_WIDTH, height / ContinentCatalog.VIEW_BOX_HEIGHT)

private fun hitTest(path: Path, x: Float, y: Float): Boolean {
    val androidPath = path.asAndroidPath()
    val bounds = android.graphics.RectF().also { androidPath.computeBounds(it, true) }
    val region = Region().apply {
        setPath(androidPath, Region(bounds.toRect()))
    }
    return region.contains(x.toInt(), y.toInt())
}

private fun pathBoundsCenter(path: Path): Offset {
    val bounds = android.graphics.RectF().also { path.asAndroidPath().computeBounds(it, true) }
    return Offset(bounds.centerX(), bounds.centerY())
}
