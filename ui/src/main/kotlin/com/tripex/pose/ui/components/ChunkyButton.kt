package com.tripex.pose.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tripex.pose.ui.theme.LocalCartoonStyle
import com.tripex.pose.ui.theme.TripailTheme
import com.tripex.pose.ui.theme.chunkyShadow

@Composable
fun ChunkyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val cartoon = LocalCartoonStyle.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val shadowOffset by animateDpAsState(
        targetValue = if (pressed) 2.dp else cartoon.chunkyShadowOffset,
        animationSpec = tween(durationMillis = 80),
        label = "chunky-shadow-offset",
    )

    Button(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        interactionSource = interactionSource,
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
        ),
        contentPadding = PaddingValues(horizontal = 44.dp, vertical = 14.dp),
        modifier = modifier.chunkyShadow(
            color = cartoon.accentPinkShadow,
            offset = shadowOffset,
        ),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Preview(name = "ChunkyButton — normal")
@Composable
private fun ChunkyButtonNormalPreview() {
    TripailTheme {
        ChunkyButton(text = "Załaduj", onClick = {})
    }
}

@Preview(name = "ChunkyButton — disabled")
@Composable
private fun ChunkyButtonDisabledPreview() {
    TripailTheme {
        ChunkyButton(text = "Załaduj", onClick = {}, enabled = false)
    }
}
