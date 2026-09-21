package com.tripex.pose.ui.loading

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tripex.pose.ui.R
import com.tripex.pose.ui.components.ChunkyButton
import com.tripex.pose.ui.components.WavePatternBackground
import com.tripex.pose.ui.loading.components.TripailLogo
import com.tripex.pose.ui.loading.components.rememberSlingshotState
import com.tripex.pose.ui.shell.AppShellContract.FailedSignal
import com.tripex.pose.ui.shell.AppShellContract.Readiness
import com.tripex.pose.ui.theme.LocalCartoonStyle
import com.tripex.pose.ui.theme.TripailTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Below this, readiness is treated as instant and no indicator is shown at all (M2.2 anti-flash). */
private const val INDICATOR_DELAY_MILLIS = 250L

/**
 * Once the indicator has appeared it stays for at least this long. Without it, readiness landing
 * just past the anti-flash window produced a blink nobody could read.
 */
private const val INDICATOR_MIN_VISIBLE_MILLIS = 900L

private val ArcAmplitude = 12.dp

@Composable
fun TripLoadingScreen(
    readiness: Readiness,
    canEnter: Boolean,
    onEnterRequested: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cartoon = LocalCartoonStyle.current
    val density = LocalDensity.current
    val slingshot = rememberSlingshotState()
    val scope = rememberCoroutineScope()

    var logoWidthPx by remember { mutableIntStateOf(0) }
    var indicatorVisible by remember { mutableStateOf(false) }

    // Anti-flash: the indicator only appears if preparing outlives the grace window, and once
    // it is on screen it stays long enough to be read.
    LaunchedEffect(readiness) {
        if (readiness is Readiness.Preparing) {
            delay(INDICATOR_DELAY_MILLIS)
            indicatorVisible = true
        } else if (indicatorVisible) {
            delay(INDICATOR_MIN_VISIBLE_MILLIS)
            indicatorVisible = false
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(cartoon.oceanBlue),
    ) {
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val arcAmplitudePx = with(density) { ArcAmplitude.toPx() }

        WavePatternBackground(modifier = Modifier.fillMaxSize())

        TripailLogo(
            modifier = Modifier
                .align(Alignment.Center)
                .onSizeChanged { logoWidthPx = it.width }
                .graphicsLayer {
                    translationX = slingshot.translationFraction * size.width
                    translationY = slingshot.arcOffset(arcAmplitudePx)
                    rotationZ = slingshot.rotationDeg
                    scaleX = slingshot.scaleX
                    scaleY = slingshot.scaleY
                    alpha = slingshot.alpha
                },
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .safeDrawingPadding()
                .padding(horizontal = 32.dp, vertical = 48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Driven by `indicatorVisible`, not by `readiness`, so the minimum-visible window
            // survives readiness flipping to Ready a moment after the indicator appeared.
            if (indicatorVisible) {
                PreparingIndicator()
            }
            if (readiness is Readiness.Failed) {
                FailureNotice(readiness, onRetry)
            }

            // On a hot start the indicator never appears, so the button is there immediately.
            if (canEnter && !indicatorVisible) {
                ChunkyButton(
                    text = stringResource(R.string.loading_enter),
                    enabled = !slingshot.launched,
                    onClick = {
                        scope.launch {
                            slingshot.launch(
                                logoWidthPx = logoWidthPx.toFloat(),
                                screenWidthPx = screenWidthPx,
                                onCrossedScreen = onEnterRequested,
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun PreparingIndicator() {
    val cartoon = LocalCartoonStyle.current
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(36.dp),
            color = cartoon.accentPink,
            trackColor = cartoon.oceanDeep,
            strokeWidth = 5.dp,
        )
        Text(
            text = stringResource(R.string.loading_preparing),
            style = MaterialTheme.typography.bodyMedium,
            color = cartoon.inkPrimary,
        )
    }
}

@Composable
private fun FailureNotice(
    failure: Readiness.Failed,
    onRetry: () -> Unit,
) {
    val cartoon = LocalCartoonStyle.current
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.loading_failed),
            style = MaterialTheme.typography.bodyMedium,
            color = cartoon.inkPrimary,
            textAlign = TextAlign.Center,
        )
        if (failure.signal == FailedSignal.Atlas) {
            Text(
                text = stringResource(R.string.loading_failed_atlas),
                style = MaterialTheme.typography.bodySmall,
                color = cartoon.inkPrimary.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
            )
        }
        TextButton(onClick = onRetry) {
            Text(stringResource(R.string.loading_retry))
        }
    }
}

@Preview(name = "Loading — preparing")
@Composable
private fun TripLoadingScreenPreparingPreview() {
    TripailTheme {
        TripLoadingScreen(
            readiness = Readiness.Preparing,
            canEnter = false,
            onEnterRequested = {},
            onRetry = {},
        )
    }
}

@Preview(name = "Loading — ready")
@Composable
private fun TripLoadingScreenReadyPreview() {
    TripailTheme {
        TripLoadingScreen(
            readiness = Readiness.Ready,
            canEnter = true,
            onEnterRequested = {},
            onRetry = {},
        )
    }
}

@Preview(name = "Loading — atlas failed")
@Composable
private fun TripLoadingScreenFailedPreview() {
    TripailTheme {
        TripLoadingScreen(
            readiness = Readiness.Failed(FailedSignal.Atlas, degraded = true),
            canEnter = true,
            onEnterRequested = {},
            onRetry = {},
        )
    }
}
