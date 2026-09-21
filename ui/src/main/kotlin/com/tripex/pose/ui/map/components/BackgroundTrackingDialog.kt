package com.tripex.pose.ui.map.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tripex.pose.ui.R
import com.tripex.pose.ui.map.MapContract
import com.tripex.pose.ui.theme.TripailTheme

/**
 * The two one-off screens that make tracking survive a pocket (V3.7.4 / V3.7.5).
 *
 * Both say what stops working if the player declines, rather than what the app would like. The
 * difference matters: "allow all the time" means nothing on its own, while "tracking stops when
 * you close the app" is a consequence somebody can weigh.
 */
@Composable
internal fun BackgroundTrackingDialog(
    prompt: MapContract.BackgroundPrompt,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onAutostart: () -> Unit,
) {
    val isPermission = prompt == MapContract.BackgroundPrompt.LocationAlways

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (isPermission) {
                        R.string.background_permission_title
                    } else {
                        R.string.background_reliability_title
                    },
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(
                        if (isPermission) {
                            R.string.background_permission_body
                        } else {
                            R.string.background_reliability_body
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (!isPermission) {
                    TextButton(
                        onClick = onAutostart,
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        Text(stringResource(R.string.background_reliability_autostart))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(
                        if (isPermission) {
                            R.string.background_permission_confirm
                        } else {
                            R.string.background_reliability_confirm
                        },
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.background_prompt_later))
            }
        },
    )
}

@Preview
@Composable
private fun BackgroundPermissionPreview() {
    TripailTheme {
        BackgroundTrackingDialog(
            prompt = MapContract.BackgroundPrompt.LocationAlways,
            onConfirm = {},
            onDismiss = {},
            onAutostart = {},
        )
    }
}
