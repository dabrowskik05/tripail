package com.tripex.pose.ui.map.host

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tripex.pose.ui.theme.LocalCartoonStyle

/** Height of the footer line the attribution lives in, above the system navigation bar. */
internal val MapAttributionHeight = 18.dp

private const val TEXT_ALPHA = 0.55f

private const val OPENFREEMAP_URL = "https://openfreemap.org/"
private const val OPENMAPTILES_URL = "https://www.openmaptiles.org/"
private const val OSM_COPYRIGHT_URL = "https://www.openstreetmap.org/copyright"

/**
 * What the map data's licences require on screen, as one quiet footer line:
 * "OpenFreeMap © OpenMapTiles Data from OpenStreetMap" (OpenFreeMap's requested credit; the
 * OpenStreetMap part is required by the ODbL on any provider). It sits in its own strip just
 * above the system navigation bar, under every panel, instead of floating over the map.
 * MapLibre's own logo is not required by its licence and is off.
 */
@Composable
internal fun MapAttribution(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val ink = LocalCartoonStyle.current.inkPrimary.copy(alpha = TEXT_ALPHA)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(MapAttributionHeight)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AttributionLink("OpenFreeMap ", ink) { uriHandler.openUri(OPENFREEMAP_URL) }
        AttributionLink("© OpenMapTiles ", ink) { uriHandler.openUri(OPENMAPTILES_URL) }
        AttributionLink("© OpenStreetMap contributors", ink) { uriHandler.openUri(OSM_COPYRIGHT_URL) }
    }
}

@Composable
private fun AttributionLink(text: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
        color = color,
        maxLines = 1,
        modifier = Modifier.clickable(onClick = onClick),
    )
}
