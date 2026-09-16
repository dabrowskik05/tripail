package com.tripex.pose.ui.loading

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.tripex.pose.ui.R
import com.tripex.pose.ui.components.ChunkyButton
import com.tripex.pose.ui.loading.components.StripedProgressBar
import com.tripex.pose.ui.loading.components.TripexLogo
import com.tripex.pose.ui.loading.components.WavePatternBackground
import com.tripex.pose.ui.theme.LocalCartoonStyle
import com.tripex.pose.ui.theme.TripexPoseTheme
import kotlinx.coroutines.launch

@Composable
fun TripLoadingScreen(
    progress: Float,
    isReady: Boolean,
    onEnterRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cartoon = LocalCartoonStyle.current
    val exit = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(cartoon.skyBrush),
    ) {
        WavePatternBackground(modifier = Modifier.fillMaxSize())

        TripexLogo(
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer {
                    translationX = exit.value * size.width * 1.6f
                    translationY = exit.value * -size.height * 0.30f
                    rotationZ = exit.value * -14f
                    scaleX = lerp(1f, 0.9f, exit.value)
                    scaleY = lerp(1f, 0.9f, exit.value)
                    alpha = 1f - exit.value
                },
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 48.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StripedProgressBar(
                progress = progress,
                modifier = Modifier.fillMaxWidth(),
            )
            ChunkyButton(
                text = stringResource(R.string.loading_enter),
                enabled = isReady && exit.value == 0f,
                onClick = {
                    scope.launch {
                        exit.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(
                                durationMillis = 650,
                                easing = CubicBezierEasing(0.55f, 0f, 0.85f, 0.35f),
                            ),
                        )
                        onEnterRequested()
                    }
                },
            )
        }
    }
}

@Preview(name = "Loading — in progress")
@Composable
private fun TripLoadingScreenInProgressPreview() {
    TripexPoseTheme {
        TripLoadingScreen(
            progress = 0.4f,
            isReady = false,
            onEnterRequested = {},
        )
    }
}

@Preview(name = "Loading — ready")
@Composable
private fun TripLoadingScreenReadyPreview() {
    TripexPoseTheme {
        TripLoadingScreen(
            progress = 1f,
            isReady = true,
            onEnterRequested = {},
        )
    }
}
