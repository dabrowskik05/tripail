package com.tripex.pose.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tripex.pose.domain.location.TrackingState
import com.tripex.pose.ui.R
import com.tripex.pose.ui.map.components.BackgroundTrackingDialog
import com.tripex.pose.ui.map.components.CommunityDialog
import com.tripex.pose.ui.map.components.PlaceDetailSheet
import com.tripex.pose.ui.map.components.MapTopBar
import com.tripex.pose.ui.map.components.SearchSuggestions
import com.tripex.pose.ui.theme.TripailTheme

@Composable
fun MapScreen(
    state: MapContract.State,
    onIntent: (MapContract.Intent) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val keyboard = LocalSoftwareKeyboardController.current

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .safeDrawingPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MapTopBar(
                query = state.searchQuery,
                isSearching = state.isSearching,
                onQueryChange = { onIntent(MapContract.Intent.SearchQueryChanged(it)) },
                onSubmit = {
                    keyboard?.hide()
                    onIntent(MapContract.Intent.SubmitSearch)
                },
                onSettingsClick = { onIntent(MapContract.Intent.OpenSettings) },
                onCommunityClick = { onIntent(MapContract.Intent.OpenCommunity) },
            )

            SearchSuggestions(
                suggestions = state.suggestions,
                onPick = { place ->
                    keyboard?.hide()
                    onIntent(MapContract.Intent.SuggestionPicked(place))
                },
            )
            state.searchMessage?.let { message ->
                if (message is MapContract.SearchMessage.Failed) {
                    StatusChip(
                        text = message.detail
                            ?: stringResource(R.string.search_not_found),
                    )
                }
                // Attribution is a licence condition; it rides with the search output rather
                // than hanging over the map forever.
                Text(
                    text = stringResource(R.string.search_attribution),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding(),
        )

        state.placeDetail?.let { detail ->
            PlaceDetailSheet(
                detail = detail,
                onDismiss = { onIntent(MapContract.Intent.DismissPlaceDetail) },
                onToggleExpanded = { onIntent(MapContract.Intent.TogglePlaceDetailExpanded) },
            )
        }

        state.backgroundPrompt?.let { prompt ->
            BackgroundTrackingDialog(
                prompt = prompt,
                onConfirm = { onIntent(MapContract.Intent.BackgroundPromptConfirmed) },
                onDismiss = { onIntent(MapContract.Intent.BackgroundPromptDismissed) },
                onAutostart = { onIntent(MapContract.Intent.OpenAutostartSettings) },
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
    TripailTheme {
        MapScreen(
            state = MapContract.State(
                trackingState = TrackingState.Idle,
                searchQuery = "Warszawa",
            ),
            onIntent = {},
        )
    }
}
