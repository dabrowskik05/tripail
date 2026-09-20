package com.tripex.pose.ui.map.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tripex.pose.ui.R
import com.tripex.pose.ui.theme.LocalCartoonStyle
import com.tripex.pose.ui.theme.TripailTheme
import com.tripex.pose.ui.theme.chunkyShadow

@Composable
fun FloatingActionMenu(
    expanded: Boolean,
    isTracking: Boolean,
    onToggleExpanded: () -> Unit,
    onStartStopTracking: () -> Unit,
    onCommunityClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cartoon = LocalCartoonStyle.current
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 45f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "fab-rotation",
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(180)) +
                scaleIn(initialScale = 0.8f, animationSpec = tween(180)) +
                slideInVertically(animationSpec = tween(180)) { it / 2 },
            exit = fadeOut(tween(140)) +
                scaleOut(targetScale = 0.8f, animationSpec = tween(140)) +
                slideOutVertically(animationSpec = tween(140)) { it / 2 },
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SmallFloatingActionButton(
                    onClick = onSettingsClick,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = cartoon.inkPrimary,
                    shape = CircleShape,
                    elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp),
                    modifier = Modifier.chunkyShadow(
                        color = cartoon.inkPrimary.copy(alpha = 0.25f),
                        offset = 4.dp,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = stringResource(R.string.map_settings_cd),
                    )
                }
                SmallFloatingActionButton(
                    onClick = onCommunityClick,
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = cartoon.inkPrimary,
                    shape = CircleShape,
                    elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp),
                    modifier = Modifier.chunkyShadow(
                        color = cartoon.sunYellowShadow,
                        offset = 4.dp,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = stringResource(R.string.map_community_cd),
                    )
                }
                SmallFloatingActionButton(
                    onClick = onStartStopTracking,
                    containerColor = if (isTracking) {
                        cartoon.accentPink
                    } else {
                        cartoon.grassGreen
                    },
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape,
                    elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp),
                    modifier = Modifier.chunkyShadow(
                        color = if (isTracking) {
                            cartoon.accentPinkShadow
                        } else {
                            cartoon.grassGreen.copy(alpha = 0.55f)
                        },
                        offset = 4.dp,
                    ),
                ) {
                    Icon(
                        imageVector = if (isTracking) Icons.Filled.Close else Icons.Filled.PlayArrow,
                        contentDescription = stringResource(
                            if (isTracking) R.string.tracking_stop else R.string.tracking_start,
                        ),
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = onToggleExpanded,
            containerColor = MaterialTheme.colorScheme.tertiary,
            contentColor = cartoon.inkPrimary,
            shape = CircleShape,
            elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp),
            modifier = Modifier
                .size(64.dp)
                .chunkyShadow(
                    color = cartoon.sunYellowShadow,
                    offset = cartoon.chunkyShadowOffset,
                ),
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(
                    if (expanded) R.string.map_fab_collapse_cd else R.string.map_fab_expand_cd,
                ),
                modifier = Modifier.graphicsLayer { rotationZ = rotation },
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF8FD4EA)
@Composable
private fun FloatingActionMenuCollapsedPreview() {
    TripailTheme {
        FloatingActionMenu(
            expanded = false,
            isTracking = false,
            onToggleExpanded = {},
            onStartStopTracking = {},
            onCommunityClick = {},
            onSettingsClick = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF8FD4EA)
@Composable
private fun FloatingActionMenuExpandedPreview() {
    TripailTheme {
        FloatingActionMenu(
            expanded = true,
            isTracking = true,
            onToggleExpanded = {},
            onStartStopTracking = {},
            onCommunityClick = {},
            onSettingsClick = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
