package com.tripex.pose.ui.loading.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.util.lerp
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Slingshot launch of the Tripail wordmark (M2.3).
 *
 * [phase] is the flight position: `0` at rest, negative while the logo is pulled back to the
 * left, `> 1` once it has left the screen. [stretch] carries the squash/stretch and is the
 * horizontal scale directly; the vertical scale is derived from it so the logo keeps its volume.
 */
@Stable
internal class SlingshotState {
    val phase = Animatable(0f)
    val stretch = Animatable(NEUTRAL_STRETCH)

    /** Guards against a second tap: the launch runs exactly once. */
    var launched by mutableStateOf(false)
        private set

    val translationFraction: Float get() = phase.value * TRAVEL_FACTOR

    val rotationDeg: Float
        get() = if (phase.value < 0f) {
            (-phase.value / -PULL_PHASE).coerceIn(0f, 1f) * PULL_ROTATION_DEG
        } else {
            -(phase.value / SHOT_ROTATION_PHASE).coerceIn(0f, 1f) * SHOT_ROTATION_DEG
        }

    val alpha: Float
        get() = 1f - ((phase.value - FADE_START_PHASE) / (SHOT_PHASE - FADE_START_PHASE))
            .coerceIn(0f, 1f)

    val scaleX: Float get() = stretch.value

    val scaleY: Float
        get() = if (stretch.value <= NEUTRAL_STRETCH) {
            lerp(
                PULL_SCALE_Y,
                NEUTRAL_STRETCH,
                ((stretch.value - PULL_SCALE_X) / (NEUTRAL_STRETCH - PULL_SCALE_X)).coerceIn(0f, 1f),
            )
        } else {
            lerp(
                NEUTRAL_STRETCH,
                SHOT_SCALE_Y,
                ((stretch.value - NEUTRAL_STRETCH) / (SHOT_SCALE_X - NEUTRAL_STRETCH)).coerceIn(0f, 1f),
            )
        }

    /** Slight upward arc during the flight; [amplitudePx] is its peak height. */
    fun arcOffset(amplitudePx: Float): Float =
        -sin(phase.value.coerceIn(0f, 1f) * PI).toFloat() * amplitudePx

    /**
     * Pulls back, then fires. [onCrossedScreen] runs the moment the wordmark clears the right
     * edge — navigation must not wait for the animation to settle.
     */
    suspend fun launch(
        logoWidthPx: Float,
        screenWidthPx: Float,
        onCrossedScreen: () -> Unit,
    ) {
        if (launched) return
        launched = true

        coroutineScope {
            launch { stretch.animateTo(PULL_SCALE_X, spring(PULL_DAMPING, Spring.StiffnessLow)) }
            phase.animateTo(PULL_PHASE, spring(PULL_DAMPING, Spring.StiffnessLow))
        }

        var crossed = false
        coroutineScope {
            launch { stretch.animateTo(SHOT_SCALE_X, spring(Spring.DampingRatioNoBouncy, Spring.StiffnessHigh)) }
            phase.animateTo(
                targetValue = SHOT_PHASE,
                animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessHigh),
                initialVelocity = SHOT_INITIAL_VELOCITY,
            ) {
                if (!crossed && value * TRAVEL_FACTOR * logoWidthPx >= screenWidthPx) {
                    crossed = true
                    onCrossedScreen()
                }
            }
        }
        if (!crossed) onCrossedScreen()
    }

    private companion object {
        const val NEUTRAL_STRETCH = 1f

        const val PULL_PHASE = -0.18f
        const val PULL_DAMPING = 0.75f
        const val PULL_SCALE_X = 0.82f
        const val PULL_SCALE_Y = 1.06f
        const val PULL_ROTATION_DEG = 3f

        const val SHOT_PHASE = 1.5f
        const val SHOT_SCALE_X = 1.35f
        const val SHOT_SCALE_Y = 0.78f
        const val SHOT_ROTATION_DEG = 8f
        const val SHOT_ROTATION_PHASE = 0.6f
        const val SHOT_INITIAL_VELOCITY = 6f

        const val FADE_START_PHASE = 0.6f
        const val TRAVEL_FACTOR = 1.25f
    }
}

@Composable
internal fun rememberSlingshotState(): SlingshotState = remember { SlingshotState() }
