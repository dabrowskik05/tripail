package com.tripex.pose.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tripex.pose.ui.theme.LocalCartoonStyle
import com.tripex.pose.ui.theme.TripailTheme

private val ButtonShape = RoundedCornerShape(16.dp)

/**
 * Primary action button.
 *
 * Was a "chunky" cartoon button with a hard offset shadow in pink over a green container, which
 * read as red-on-green. Now a plain filled Material 3 button in the app's teal, with a soft
 * elevation instead of the offset block. The name is kept so call sites stay stable.
 */
@Composable
fun ChunkyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val cartoon = LocalCartoonStyle.current

    Button(
        onClick = onClick,
        enabled = enabled,
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = cartoon.buttonPrimary,
            contentColor = cartoon.buttonOnPrimary,
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 2.dp,
            pressedElevation = 6.dp,
        ),
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 14.dp),
        modifier = modifier,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Preview(name = "Primary button")
@Composable
private fun ChunkyButtonNormalPreview() {
    TripailTheme {
        ChunkyButton(text = "Odkrywaj mapę", onClick = {})
    }
}

@Preview(name = "Primary button — disabled")
@Composable
private fun ChunkyButtonDisabledPreview() {
    TripailTheme {
        ChunkyButton(text = "Odkrywaj mapę", onClick = {}, enabled = false)
    }
}
