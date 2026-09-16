package com.tripex.pose.ui.loading.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tripex.pose.ui.R
import com.tripex.pose.ui.theme.LocalCartoonStyle

private const val STRIPE_PERIOD_PX = 24f

@Composable
internal fun StripedProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val cartoon = LocalCartoonStyle.current
    val progressDescription = stringResource(R.string.loading_progress_desc, (progress * 100).toInt())

    val transition = rememberInfiniteTransition(label = "stripes")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = STRIPE_PERIOD_PX,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "stripe-phase",
    )

    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 300),
        label = "load-progress",
    )

    Box(
        modifier = modifier
            .height(12.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.35f))
            .semantics { contentDescription = progressDescription },
    ) {
        if (animatedProgress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedProgress)
                    .clip(CircleShape)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(cartoon.accentOrange, cartoon.accentPink),
                        ),
                    ),
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.35f),
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.35f),
                                    Color.Transparent,
                                ),
                                start = Offset(phase, 0f),
                                end = Offset(phase + STRIPE_PERIOD_PX, 0f),
                                tileMode = TileMode.Repeated,
                            ),
                        ),
                )
            }
        }
    }
}
