package com.tripex.pose.ui.map.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tripex.pose.ui.R
import com.tripex.pose.ui.theme.LocalCartoonStyle
import com.tripex.pose.ui.theme.TripexPoseTheme

@Composable
fun SettingsDialog(
    onDismiss: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cartoon = LocalCartoonStyle.current

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = stringResource(R.string.settings_toggles_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SettingsToggleRow(
                    label = stringResource(R.string.settings_fog_label),
                    checked = true,
                    enabled = false,
                )
                SettingsToggleRow(
                    label = stringResource(R.string.settings_haptic_label),
                    checked = false,
                    enabled = false,
                )
                TextButton(
                    onClick = onOpenSystemSettings,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.tracking_open_settings),
                        color = cartoon.accentPink,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_close))
            }
        },
    )
}

@Composable
private fun SettingsToggleRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val cartoon = LocalCartoonStyle.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = cartoon.grassGreen,
                disabledCheckedTrackColor = cartoon.grassGreen.copy(alpha = 0.4f),
            ),
        )
    }
}

@Preview
@Composable
private fun SettingsDialogPreview() {
    TripexPoseTheme {
        SettingsDialog(onDismiss = {}, onOpenSystemSettings = {})
    }
}
