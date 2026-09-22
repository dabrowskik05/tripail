package com.tripex.pose.ui.map.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tripex.pose.domain.geo.PlaceKind
import com.tripex.pose.domain.usecase.RevealTarget
import com.tripex.pose.domain.usecase.SearchSelection
import com.tripex.pose.ui.R
import com.tripex.pose.ui.components.ChunkyButton
import com.tripex.pose.ui.theme.LocalCartoonStyle
import com.tripex.pose.ui.theme.TripailTheme

private val SheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

/** Supporting text sits back from the name without needing a second ink colour. */
private const val SECONDARY_ALPHA = 0.7f

/**
 * What the player selected, and what they can do about it (V3.4.5).
 *
 * One panel, two entry points: a search result and a tap on a city open exactly this. The rule
 * for the buttons is that there is never a choice between revealing and covering — a place is
 * one or the other, so it offers one action and a way out.
 *
 * "Cover" is absent for ground that was earned rather than chosen (a city unlocked by standing
 * in it) and for countries, which are entered rather than owned.
 */
@Composable
internal fun PlaceDetailSheet(
    selection: SearchSelection,
    isApplying: Boolean,
    onReveal: () -> Unit,
    onCover: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cartoon = LocalCartoonStyle.current
    val isCountry = selection.target is RevealTarget.Country

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = SheetShape,
        color = cartoon.paperBg,
        shadowElevation = 16.dp,
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = selection.label,
                style = MaterialTheme.typography.headlineSmall,
                color = cartoon.inkPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(selection.kind.labelRes()),
                style = MaterialTheme.typography.labelLarge,
                color = cartoon.inkPrimary.copy(alpha = SECONDARY_ALPHA),
            )
            if (selection.context.isNotEmpty()) {
                Text(
                    text = selection.context.joinToString(", "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = cartoon.inkPrimary.copy(alpha = SECONDARY_ALPHA),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when {
                    // Entering a country is the action; there is nothing here to claim.
                    isCountry -> Unit

                    selection.isRevealed && selection.canCover -> ChunkyButton(
                        text = stringResource(
                            if (isApplying) R.string.place_working else R.string.place_cover,
                        ),
                        onClick = onCover,
                        enabled = !isApplying,
                    )

                    !selection.isRevealed -> ChunkyButton(
                        text = stringResource(
                            if (isApplying) R.string.place_working else R.string.place_reveal,
                        ),
                        onClick = onReveal,
                        enabled = !isApplying,
                    )

                    // Revealed, but earned rather than chosen: nothing to undo.
                    else -> Text(
                        text = stringResource(R.string.place_earned),
                        style = MaterialTheme.typography.bodyMedium,
                        color = cartoon.inkPrimary.copy(alpha = SECONDARY_ALPHA),
                    )
                }

                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.place_dismiss))
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PlaceDetailSheetPreview() {
    TripailTheme {
        PlaceDetailSheet(
            selection = SearchSelection(
                label = "Skierniewice",
                kind = PlaceKind.City,
                context = listOf("łódzkie", "Polska"),
                latitude = 51.95,
                longitude = 20.15,
                bounds = null,
                target = RevealTarget.Circle(
                    place = com.tripex.pose.domain.geo.Place("Skierniewice", 51.95, 20.15),
                    radiusMeters = 5_000.0,
                ),
                isRevealed = false,
                canCover = false,
            ),
            isApplying = false,
            onReveal = {},
            onCover = {},
            onDismiss = {},
        )
    }
}
