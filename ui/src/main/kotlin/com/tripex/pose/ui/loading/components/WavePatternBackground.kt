package com.tripex.pose.ui.loading.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp

private val TileWidth = 70.dp
private val TileHeight = 32.dp

@Composable
internal fun WavePatternBackground(
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val tileWidthPx = TileWidth.toPx()
        val tileHeightPx = TileHeight.toPx()
        val columns = (size.width / tileWidthPx).toInt() + 2
        val rows = (size.height / tileHeightPx).toInt() + 2

        for (row in 0 until rows) {
            for (column in 0 until columns) {
                val originX = column * tileWidthPx
                val originY = row * tileHeightPx

                withTransform({
                    translate(originX, originY)
                }) {
                    drawWaveTile(
                        width = tileWidthPx,
                        height = tileHeightPx,
                        waveColor = Color.White.copy(alpha = 0.5f),
                        amplitude = tileHeightPx * 0.22f,
                        baseline = tileHeightPx * 0.45f,
                    )
                    drawWaveTile(
                        width = tileWidthPx,
                        height = tileHeightPx,
                        waveColor = Color.White.copy(alpha = 0.3f),
                        amplitude = tileHeightPx * 0.16f,
                        baseline = tileHeightPx * 0.62f,
                    )
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWaveTile(
    width: Float,
    height: Float,
    waveColor: Color,
    amplitude: Float,
    baseline: Float,
) {
    val path = Path().apply {
        moveTo(0f, baseline)
        quadraticTo(
            width * 0.25f,
            baseline - amplitude,
            width * 0.5f,
            baseline,
        )
        quadraticTo(
            width * 0.75f,
            baseline + amplitude,
            width,
            baseline,
        )
        lineTo(width, height)
        lineTo(0f, height)
        close()
    }
    drawPath(path, waveColor)
}
