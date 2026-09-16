package com.tripex.pose.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tripex.pose.domain.location.TrackingState
import com.tripex.pose.ui.R
import com.tripex.pose.ui.map.components.CommunityDialog
import com.tripex.pose.ui.map.components.FloatingActionMenu
import com.tripex.pose.ui.map.components.MapLibreFogMap
import com.tripex.pose.ui.map.components.MapZoomControls
import com.tripex.pose.ui.map.components.PlaceDetailSheet
import com.tripex.pose.ui.map.components.SearchPill
import com.tripex.pose.ui.map.components.SettingsDialog
import com.tripex.pose.ui.theme.TripexPoseTheme

@Composable
fun MapScreen(
    state: MapContract.State,
    onIntent: (MapContract.Intent) -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current

    Box(modifier = Modifier.fillMaxSize()) {
        if (state.styleUri.isNotEmpty()) {
            MapLibreFogMap(
                styleUri = state.styleUri,
                fogGeoJson = state.fogGeoJson,
                initialViewport = state.initialViewport,
                cameraTarget = state.cameraTarget,
                onCameraIdle = { viewport ->
                    onIntent(MapContract.Intent.CameraIdle(viewport))
                },
                onCameraTargetConsumed = {
                    onIntent(MapContract.Intent.CameraTargetConsumed)
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .safeDrawingPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SearchPill(
                query = state.searchQuery,
                isSearching = state.isSearching,
                onQueryChange = { onIntent(MapContract.Intent.SearchQueryChanged(it)) },
                onSubmit = {
                    keyboard?.hide()
                    onIntent(MapContract.Intent.SubmitSearch)
                },
                onSettingsClick = { onIntent(MapContract.Intent.OpenSettings) },
            )
            Text(
                text = stringResource(R.string.search_attribution),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.searchMessage?.let { message ->
                if (message is MapContract.SearchMessage.Failed) {
                    StatusChip(
                        text = message.detail
                            ?: stringResource(R.string.search_not_found),
                    )
                }
            }
            state.statusMessage?.let { message ->
                StatusChip(
                    text = when (message) {
                        MapContract.StatusMessage.PreciseLocationRequired ->
                            stringResource(R.string.status_precise_location_required)
                        MapContract.StatusMessage.PermissionMissing ->
                            stringResource(R.string.status_permission_missing)
                        MapContract.StatusMessage.LocationDisabled ->
                            stringResource(R.string.status_location_disabled)
                        MapContract.StatusMessage.TrackingActive ->
                            stringResource(R.string.status_tracking_active)
                    },
                )
            }
            if (state.needsPreciseLocationHint ||
                state.trackingState == TrackingState.PermissionMissing
            ) {
                TextButton(
                    onClick = { onIntent(MapContract.Intent.OpenSettingsRequested) },
                ) {
                    Text(stringResource(R.string.tracking_open_settings))
                }
            }
        }

        MapZoomControls(
            onZoomIn = { onIntent(MapContract.Intent.ZoomIn) },
            onZoomOut = { onIntent(MapContract.Intent.ZoomOut) },
            modifier = Modifier
                .align(Alignment.CenterStart)
                .safeDrawingPadding()
                .padding(start = 12.dp),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .safeDrawingPadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            ) {
                Text(
                    text = stringResource(R.string.tracking_unlocked_count, state.unlockedCount),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            FloatingActionMenu(
                expanded = state.fabExpanded,
                isTracking = state.trackingState == TrackingState.Tracking,
                onToggleExpanded = { onIntent(MapContract.Intent.ToggleFabMenu) },
                onStartStopTracking = {
                    if (state.trackingState == TrackingState.Tracking) {
                        onIntent(MapContract.Intent.StopDiscovery)
                    } else {
                        onIntent(MapContract.Intent.StartDiscovery)
                    }
                },
                onCommunityClick = { onIntent(MapContract.Intent.OpenCommunity) },
                onSettingsClick = { onIntent(MapContract.Intent.OpenSettings) },
            )
        }

        state.placeDetail?.let { detail ->
            PlaceDetailSheet(
                detail = detail,
                onDismiss = { onIntent(MapContract.Intent.DismissPlaceDetail) },
                onToggleExpanded = { onIntent(MapContract.Intent.TogglePlaceDetailExpanded) },
            )
        }

        if (state.settingsVisible) {
            SettingsDialog(
                onDismiss = { onIntent(MapContract.Intent.CloseSettings) },
                onOpenSystemSettings = {
                    onIntent(MapContract.Intent.CloseSettings)
                    onIntent(MapContract.Intent.OpenSettingsRequested)
                },
            )
        }

        if (state.communityVisible) {
            CommunityDialog(
                onDismiss = { onIntent(MapContract.Intent.CloseCommunity) },
            )
        }
    }
}

@Composable
private fun StatusChip(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MapScreenPreview() {
    TripexPoseTheme {
        MapScreen(
            state = MapContract.State(
                styleUri = "preview",
                unlockedCount = 42,
                trackingState = TrackingState.Idle,
                searchQuery = "Warszawa",
                fabExpanded = true,
            ),
            onIntent = {},
        )
    }
}
