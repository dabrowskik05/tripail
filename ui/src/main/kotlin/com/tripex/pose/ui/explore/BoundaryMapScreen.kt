package com.tripex.pose.ui.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tripex.pose.ui.R
import com.tripex.pose.ui.map.host.MapHostState
import com.tripex.pose.ui.map.host.MapScene
import com.tripex.pose.ui.shell.chrome.AppChromeState
import com.tripex.pose.ui.shell.chrome.RegisterChrome
import com.tripex.pose.ui.theme.LocalCartoonStyle

/**
 * One level of the cascade.
 *
 * Draws **no map** and, since V3.2.1, **no chrome** either. Both live above the navigation graph
 * and outlive every transition; a level only declares the scene it wants, registers its name and
 * its back action, and claims the tap handler while it is on screen.
 */
@Composable
internal fun BoundaryMapScreen(
    host: MapHostState,
    chrome: AppChromeState,
    scene: MapScene,
    title: String,
    onBack: () -> Unit,
    onTap: (BoundaryTap) -> Unit,
    modifier: Modifier = Modifier,
    bottomContent: @Composable () -> Unit = {},
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

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
            bottomContent()
        }
    }
}
