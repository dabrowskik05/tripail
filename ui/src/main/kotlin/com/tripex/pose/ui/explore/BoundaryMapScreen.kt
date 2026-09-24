package com.tripex.pose.ui.explore

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.tripex.pose.ui.map.host.BottomPanel
import com.tripex.pose.ui.map.host.MapHostState
import com.tripex.pose.ui.map.host.MapScene
import com.tripex.pose.ui.shell.chrome.AppChromeState
import com.tripex.pose.ui.shell.chrome.RegisterChrome

/**
 * One level of the cascade.
 *
 * Draws **no map** and, since V3.2.1, **no chrome** either. Both live above the navigation graph
 * and outlive every transition; a level only declares the scene it wants, registers its name and
 * its back action, and claims the tap handler while it is on screen.
 */
@Composable
internal fun <T> BoundaryMapScreen(
    host: MapHostState,
    chrome: AppChromeState,
    scene: MapScene,
    title: String,
    onBack: () -> Unit,
    onTap: (BoundaryTap) -> Unit,
    /**
     * What the bottom panel shows. Animated as a value, so a panel on its way out keeps showing
     * the area it was about instead of switching to the new one mid-slide.
     */
    panel: T,
    /** Identity of [panel]: a new key swaps the card, an update under the same key does not. */
    panelKey: (T) -> Any?,
    bottomContent: @Composable (T) -> Unit = {},
) {
    RegisterChrome(chrome = chrome, title = title, onBack = onBack)

    LaunchedEffect(scene) { host.show(scene) }

    // Keyed on the host, not the lambda: a lambda is a new object on every recomposition, which
    // would tear the handler down and rebuild it constantly.
    val currentOnTap by rememberUpdatedState(onTap)
    DisposableEffect(host) {
        val handler: (BoundaryTap) -> Unit = { currentOnTap(it) }
        host.setTapHandler(handler)
        onDispose { host.releaseTapHandler(handler) }
    }

    // Published, not drawn: the shell draws the one card on screen (see MapHostState.levelPanel).
    val owner = remember { Any() }
    DisposableEffect(host) {
        host.claimPanel(owner)
        onDispose { host.releasePanel(owner) }
    }
    val key = panelKey(panel)
    SideEffect {
        host.updatePanel(owner, BottomPanel(key = key ?: Unit) { bottomContent(panel) })
    }
}
