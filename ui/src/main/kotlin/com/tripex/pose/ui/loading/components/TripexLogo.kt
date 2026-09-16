package com.tripex.pose.ui.loading.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tripex.pose.ui.theme.LocalCartoonStyle

@Composable
internal fun TripexLogo(
    modifier: Modifier = Modifier,
) {
    val cartoon = LocalCartoonStyle.current

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Trip",
            style = MaterialTheme.typography.displayLarge.copy(
                shadow = Shadow(
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.5f),
                    offset = Offset(3f, 3f),
                    blurRadius = 0f,
                ),
            ),
            color = cartoon.inkPrimary,
        )
        Column(
            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = "ex",
                style = MaterialTheme.typography.titleLarge,
                color = cartoon.accentPink,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "pose",
                style = MaterialTheme.typography.titleLarge,
                color = cartoon.inkPrimary.copy(alpha = 0.65f),
                letterSpacing = 2.sp,
                modifier = Modifier.padding(top = (-4).dp),
            )
        }
    }
}
