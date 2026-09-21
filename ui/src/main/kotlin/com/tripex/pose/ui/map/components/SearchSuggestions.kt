package com.tripex.pose.ui.map.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tripex.pose.domain.geo.GeoBounds
import com.tripex.pose.domain.geo.Place
import com.tripex.pose.domain.geo.PlaceKind
import com.tripex.pose.ui.R
import com.tripex.pose.ui.theme.LocalCartoonStyle
import com.tripex.pose.ui.theme.TripailTheme

private val ListShape = RoundedCornerShape(20.dp)
private val MaxListHeight = 320.dp

/**
 * Suggestion list under the search bar (M4.9).
 *
 * Each row shows the parent areas alongside the name, which is what makes four places called
 * "Warszawa" tellable apart — and it costs nothing, because the geocoder returns that context in
 * the same response.
 */
@Composable
internal fun SearchSuggestions(
    suggestions: List<Place>,
    onPick: (Place) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (suggestions.isEmpty()) return
    val cartoon = LocalCartoonStyle.current

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = ListShape,
        color = cartoon.paperBg,
        shadowElevation = 8.dp,
    ) {
        LazyColumn(modifier = Modifier.heightIn(max = MaxListHeight)) {
            items(suggestions, key = { it.id }) { place ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(place) }
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = place.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = cartoon.inkPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val detail = listOfNotNull(
                        place.kindLabel(),
                        place.context.joinToString(", ").takeIf { it.isNotBlank() },
                    ).joinToString(" · ")
                    if (detail.isNotBlank()) {
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = cartoon.inkPrimary.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                HorizontalDivider(color = cartoon.inkPrimary.copy(alpha = 0.08f))
            }
        }
    }
}

@Composable
private fun Place.kindLabel(): String? =
    if (kind == PlaceKind.Unknown) null else stringResource(kind.labelRes())

@Preview(showBackground = true)
@Composable
private fun SearchSuggestionsPreview() {
    TripailTheme {
        SearchSuggestions(
            suggestions = listOf(
                Place(
                    displayName = "Warszawa",
                    latitude = 52.23,
                    longitude = 21.01,
                    boundingBox = GeoBounds(52.37, 52.10, 21.27, 20.85),
                    kind = PlaceKind.City,
                    id = "1",
                    context = listOf("mazowieckie", "Polska"),
                ),
                Place(
                    displayName = "Warszawa",
                    latitude = 51.0,
                    longitude = 22.0,
                    kind = PlaceKind.Village,
                    id = "2",
                    context = listOf("lubelskie", "Polska"),
                ),
            ),
            onPick = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
