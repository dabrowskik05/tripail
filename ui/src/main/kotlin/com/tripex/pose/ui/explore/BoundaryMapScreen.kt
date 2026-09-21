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
import com.tripex.pose.ui.theme.LocalCartoonStyle

/**
 * Chrome for one level of the cascade.
 *
 * Draws **no map**. The map lives above the navigation graph and outlives every transition; a
 * level only declares the scene it wants and claims the tap handler while it is on screen.
 */
@Composable
internal fun BoundaryMapScreen(
    host: MapHostState,
    scene: MapScene,
    title: String,
    onBack: () -> Unit,
    onTap: (BoundaryTap) -> Unit,
    modifier: Modifier = Modifier,
    bottomContent: @Composable () -> Unit = {},
) {
    val cartoon = LocalCartoonStyle.current

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
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .safeDrawingPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(shape = CircleShape, color = cartoon.paperBg, shadowElevation = 4.dp) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.area_back_cd),
                        tint = cartoon.inkPrimary,
                    )
                }
            }
            if (title.isNotBlank()) {
                Surface(shape = CircleShape, color = cartoon.paperBg, shadowElevation = 4.dp) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = cartoon.inkPrimary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
            bottomContent()
        }
    }
}
