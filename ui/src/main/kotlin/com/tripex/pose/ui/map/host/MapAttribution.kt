package com.tripex.pose.ui.map.host

import androidx.compose.foundation.Image
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tripex.pose.ui.R
import com.tripex.pose.ui.theme.LocalCartoonStyle

/** Height of the footer line the attribution lives in, above the system navigation bar. */
internal val MapAttributionHeight = 18.dp

private val LogoHeight = 12.dp
private const val TEXT_ALPHA = 0.55f

private const val MAPTILER_URL = "https://www.maptiler.com/"
private const val MAPTILER_COPYRIGHT_URL = "https://www.maptiler.com/copyright/"
private const val OSM_COPYRIGHT_URL = "https://www.openstreetmap.org/copyright"

/**
 * What the map data's licences require on screen, as one quiet footer line.
 *
 * Both parts are required and neither can stand in for the other: the MapTiler logo by the
 * MapTiler free plan, the "© OpenStreetMap contributors" text by the OpenStreetMap licence on
 * any plan. They sit in their own strip just above the system navigation bar, under every panel,
 * instead of floating over the map — the first version, in a white pill above the panels, read
 * louder than the map itself. MapLibre's own logo is not required by its licence and is off.
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
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.maptiler_logo),
            contentDescription = stringResource(R.string.map_maptiler_logo_cd),
            modifier = Modifier
                .height(LogoHeight)
                .clickable { uriHandler.openUri(MAPTILER_URL) },
        )
        Row {
            AttributionLink("© MapTiler ", ink) { uriHandler.openUri(MAPTILER_COPYRIGHT_URL) }
            AttributionLink("© OpenStreetMap contributors", ink) { uriHandler.openUri(OSM_COPYRIGHT_URL) }
        }
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
