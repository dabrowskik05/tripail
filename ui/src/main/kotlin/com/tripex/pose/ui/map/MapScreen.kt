package com.tripex.pose.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tripex.pose.domain.location.TrackingState
import com.tripex.pose.ui.R
import com.tripex.pose.ui.map.components.MapLibreFogMap
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

        Card(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            ),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = { onIntent(MapContract.Intent.SearchQueryChanged(it)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        enabled = !state.isSearching,
                        label = { Text(stringResource(R.string.search_label)) },
                        placeholder = { Text(stringResource(R.string.search_placeholder)) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                keyboard?.hide()
                                onIntent(MapContract.Intent.SubmitSearch)
                            },
                        ),
                    )
                    Button(
                        onClick = {
                            keyboard?.hide()
                            onIntent(MapContract.Intent.SubmitSearch)
                        },
                        enabled = !state.isSearching && state.searchQuery.isNotBlank(),
                    ) {
                        Text(
                            if (state.isSearching) {
                                stringResource(R.string.search_working)
                            } else {
                                stringResource(R.string.search_submit)
                            },
                        )
                    }
                }
                state.searchMessage?.let { message ->
                    Text(
                        text = when (message) {
                            is MapContract.SearchMessage.Unlocked -> stringResource(
                                R.string.search_unlocked,
                                message.count,
                                message.placeName,
                            )
                            is MapContract.SearchMessage.Failed ->
                                message.detail
                                    ?: stringResource(R.string.search_not_found)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = stringResource(R.string.search_attribution),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            ),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.tracking_unlocked_count, state.unlockedCount),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = when (state.trackingState) {
                        TrackingState.Idle -> stringResource(R.string.tracking_status_idle)
                        TrackingState.Tracking -> stringResource(R.string.tracking_status_active)
                        TrackingState.PermissionMissing ->
                            stringResource(R.string.tracking_status_permission)
                        TrackingState.LocationDisabled ->
                            stringResource(R.string.tracking_status_gps_off)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.statusMessage?.let { message ->
                    Text(
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
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.trackingState == TrackingState.Tracking) {
                        Button(
                            onClick = { onIntent(MapContract.Intent.StopDiscovery) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.tracking_stop))
                        }
                    } else {
                        Button(
                            onClick = { onIntent(MapContract.Intent.StartDiscovery) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.tracking_start))
                        }
                    }
                    if (state.needsPreciseLocationHint ||
                        state.trackingState == TrackingState.PermissionMissing
                    ) {
                        OutlinedButton(
                            onClick = { onIntent(MapContract.Intent.OpenSettingsRequested) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.tracking_open_settings))
                        }
                    }
                }
            }
        }
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
            ),
            onIntent = {},
        )
    }
}
