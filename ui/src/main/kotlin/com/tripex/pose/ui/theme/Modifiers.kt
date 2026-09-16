package com.tripex.pose.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp

fun Modifier.chunkyShadow(
    color: Color,
    offset: Dp,
    shape: Shape = CircleShape,
): Modifier = this.drawBehind {
    val outline = shape.createOutline(size, layoutDirection, this)
    translate(top = offset.toPx()) {
        when (outline) {
            is Outline.Rounded -> {
                drawPath(
                    Path().apply { addRoundRect(outline.roundRect) },
                    color,
                )
            }
            is Outline.Generic -> drawPath(outline.path, color)
            is Outline.Rectangle -> drawRect(color)
        }
    }
}
