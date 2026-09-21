package com.tripex.pose.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tripex.pose.ui.theme.LocalCartoonStyle
import com.tripex.pose.ui.theme.TripailTheme
import kotlin.math.ceil
import kotlin.random.Random

private val CellSize = 96.dp
private val MinLength = 24.dp
private val MaxLength = 56.dp
private val MinStroke = 3.dp
private val MaxStroke = 5.dp

private const val LAYOUT_SEED = 20260920
private const val MIN_ALPHA = 0.18f
private const val MAX_ALPHA = 0.45f
private const val MAX_ROTATION_DEG = 10f

/** Fraction of a cell kept free at its edges so dashes never line up on cell borders. */
private const val CELL_INSET = 0.1f

/** Share of dashes drawn as a full S-curve instead of a single arc. */
private const val DOUBLE_SEGMENT_CHANCE = 0.55f

private const val MIN_AMPLITUDE_RATIO = 0.16f
private const val AMPLITUDE_SPREAD = 0.12f

private data class WaveDash(
    val centerX: Float,
    val centerY: Float,
    val length: Float,
    val strokeWidth: Float,
    val rotationDeg: Float,
    val alpha: Float,
    val amplitude: Float,
    val doubleSegment: Boolean,
    val flipped: Boolean,
)

/**
 * Scattered wave dashes over the ocean — shared by the start screen and the world overview,
 * so both read as the same body of water (M2.1).
 *
 * Every dash is an open [Path] of one or two quadratic segments drawn with a round [Stroke] —
 * nothing is filled and nothing tiles. Placement is jittered inside a [CellSize] grid seeded with
 * [LAYOUT_SEED], so the layout looks random yet is identical on every launch. The layout is
 * computed once per size in `remember`, never per frame, and it is intentionally static: these
 * screens are meant to be quiet.
 *
 * [intensity] scales every dash's alpha — the world overview uses a lower value so the waves stay
 * behind the continents rather than competing with them.
 */
@Composable
internal fun WavePatternBackground(
    modifier: Modifier = Modifier,
    intensity: Float = 1f,
) {
    val cartoon = LocalCartoonStyle.current

    BoxWithConstraints(modifier = modifier) {
        val dashes = rememberWaveDashes(maxWidth, maxHeight)
        Canvas(modifier = Modifier.fillMaxSize()) {
            for (dash in dashes) {
                drawWaveDash(dash, cartoon.oceanWave, intensity)
            }
        }
    }
}

@Composable
private fun rememberWaveDashes(width: Dp, height: Dp): List<WaveDash> {
    val density = LocalDensity.current
    return remember(width, height, density) { buildDashes(width, height, density) }
}

private fun buildDashes(width: Dp, height: Dp, density: Density): List<WaveDash> {
    if (width <= 0.dp || height <= 0.dp) return emptyList()

    with(density) {
        val cellPx = CellSize.toPx()
        val widthPx = width.toPx()
        val heightPx = height.toPx()
        val minLengthPx = MinLength.toPx()
        val maxLengthPx = MaxLength.toPx()
        val minStrokePx = MinStroke.toPx()
        val maxStrokePx = MaxStroke.toPx()

        // One extra row/column on each axis: dashes jittered out of the first cell still
        // land on screen, so the edges look no different from the middle.
        val columns = ceil(widthPx / cellPx).toInt() + 1
        val rows = ceil(heightPx / cellPx).toInt() + 1
        val random = Random(LAYOUT_SEED)

        val dashes = ArrayList<WaveDash>(columns * rows)
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                val inset = cellPx * CELL_INSET
                val span = cellPx - 2f * inset
                val length = minLengthPx + random.nextFloat() * (maxLengthPx - minLengthPx)
                dashes += WaveDash(
                    centerX = (column - 0.5f) * cellPx + inset + random.nextFloat() * span,
                    centerY = (row - 0.5f) * cellPx + inset + random.nextFloat() * span,
                    length = length,
                    strokeWidth = minStrokePx + random.nextFloat() * (maxStrokePx - minStrokePx),
                    rotationDeg = (random.nextFloat() * 2f - 1f) * MAX_ROTATION_DEG,
                    alpha = MIN_ALPHA + random.nextFloat() * (MAX_ALPHA - MIN_ALPHA),
                    amplitude = length * (MIN_AMPLITUDE_RATIO + random.nextFloat() * AMPLITUDE_SPREAD),
                    doubleSegment = random.nextFloat() < DOUBLE_SEGMENT_CHANCE,
                    flipped = random.nextBoolean(),
                )
            }
        }
        return dashes
    }
}

private fun DrawScope.drawWaveDash(dash: WaveDash, color: Color, intensity: Float) {
    val halfLength = dash.length / 2f
    val amplitude = if (dash.flipped) -dash.amplitude else dash.amplitude

    val path = Path().apply {
        moveTo(-halfLength, 0f)
        if (dash.doubleSegment) {
            quadraticTo(-halfLength / 2f, -amplitude, 0f, 0f)
            quadraticTo(halfLength / 2f, amplitude, halfLength, 0f)
        } else {
            quadraticTo(0f, -amplitude * 2f, halfLength, 0f)
        }
    }

    withTransform({
        translate(dash.centerX, dash.centerY)
        rotate(dash.rotationDeg, pivot = Offset.Zero)
    }) {
        drawPath(
            path = path,
            color = color.copy(alpha = dash.alpha * intensity.coerceIn(0f, 1f)),
            style = Stroke(width = dash.strokeWidth, cap = StrokeCap.Round),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFBCE3F7)
@Composable
private fun WavePatternBackgroundPreview() {
    TripailTheme {
        WavePatternBackground(modifier = Modifier.fillMaxSize())
    }
}
