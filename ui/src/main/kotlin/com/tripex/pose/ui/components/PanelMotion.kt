package com.tripex.pose.ui.components

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith

/**
 * How the bottom panels move: up from the bottom edge, back down into it.
 *
 * One panel at a time. A new one waits for the old one to leave — a city after a region, a
 * region after a city, one city after another — so two cards never overlap and the hint under
 * them ("tap a country…") is covered by the card rather than blinking out on its own.
 */
internal object PanelMotion {
    const val EXIT_MILLIS = 180
    const val ENTER_MILLIS = 260

    /** @param afterExit wait for a leaving panel first — true whenever one may be on its way out. */
    fun enter(afterExit: Boolean = true): EnterTransition {
        val delay = if (afterExit) EXIT_MILLIS else 0
        return slideInVertically(tween(ENTER_MILLIS, delayMillis = delay)) { it } +
            fadeIn(tween(ENTER_MILLIS, delayMillis = delay))
    }

    fun exit(): ExitTransition =
        slideOutVertically(tween(EXIT_MILLIS)) { it } + fadeOut(tween(EXIT_MILLIS))

    /** For `AnimatedContent`: the old panel goes down, then the new one comes up. */
    fun <T> swap(): AnimatedContentTransitionScope<T>.() -> ContentTransform = {
        (enter() togetherWith exit()).using(SizeTransform(clip = false))
    }
}
