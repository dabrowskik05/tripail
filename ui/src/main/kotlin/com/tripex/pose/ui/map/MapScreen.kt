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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tripex.pose.domain.location.TrackingState
import com.tripex.pose.ui.R
import com.tripex.pose.ui.map.components.BackgroundTrackingDialog
import com.tripex.pose.ui.map.components.CommunityDialog
import com.tripex.pose.ui.theme.TripailTheme

/**
 * The explore level's own content — status, permissions and prompts.
 *
 * The top bar, the search field and the place panel are **not** here: they belong to the app
 * shell and are identical on every level (V3.2.1). This screen starts below them.
 */
@Composable
fun MapScreen(
    state: MapContract.State,
    onIntent: (MapContract.Intent) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .safeDrawingPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .padding(top = CHROME_CLEARANCE),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
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
                TextButton(onClick = { onIntent(MapContract.Intent.OpenSettingsRequested) }) {
                    Text(stringResource(R.string.tracking_open_settings))
                }
            }
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
            CommunityDialog(onDismiss = { onIntent(MapContract.Intent.CloseCommunity) })
        }
    }
}

/** Room for the shared top bar, which is drawn above this screen by the shell. */
private val CHROME_CLEARANCE = 64.dp

@Composable
private fun StatusChip(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface.copy(alpha = STATUS_ALPHA),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

private const val STATUS_ALPHA = 0.92f

@Preview(showBackground = true)
@Composable
private fun MapScreenPreview() {
    TripailTheme {
        MapScreen(state = MapContract.State(trackingState = TrackingState.Idle), onIntent = {})
    }
}
