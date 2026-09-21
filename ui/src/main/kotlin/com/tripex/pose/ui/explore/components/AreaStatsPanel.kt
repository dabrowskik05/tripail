package com.tripex.pose.ui.explore.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tripex.pose.domain.geo.AreaCoverage
import com.tripex.pose.ui.R
import com.tripex.pose.ui.components.ChunkyButton
import com.tripex.pose.ui.theme.LocalCartoonStyle
import com.tripex.pose.ui.theme.TripailTheme

private val PanelShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
private val ProgressHeight = 10.dp
private const val PERCENT = 100f

/**
 * Bottom card describing the selected area (M3.2 / M3.3).
 *
 * Rows only appear when there is something true to put in them: an area whose coverage could not
 * be measured shows no bar and no percentage rather than a confident `0%`.
 */
@Composable
internal fun AreaStatsPanel(
    flag: String,
    title: String,
    coverage: AreaCoverage,
    onExplore: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    val cartoon = LocalCartoonStyle.current

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = PanelShape,
        color = cartoon.paperBg,
        shadowElevation = 12.dp,
    ) {
        Column(
            // The Surface itself runs to the bottom edge so the strip under the system bar keeps
            // the panel's colour; only the content is lifted above the bar.
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = flag, style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = cartoon.inkPrimary,
                )
            }

            when (coverage) {
                is AreaCoverage.Known -> CoverageRow(coverage)
                AreaCoverage.Unavailable -> Text(
                    text = stringResource(R.string.area_coverage_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = cartoon.inkPrimary.copy(alpha = 0.7f),
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                ChunkyButton(text = stringResource(R.string.area_open_map), onClick = onExplore)
                if (secondaryLabel != null && onSecondary != null) {
                    TextButton(onClick = onSecondary) { Text(secondaryLabel) }
                }
            }
        }
    }
}

@Composable
private fun CoverageRow(coverage: AreaCoverage.Known) {
    val cartoon = LocalCartoonStyle.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.area_coverage, (coverage.fraction * PERCENT).toInt()),
            style = MaterialTheme.typography.bodyLarge,
            color = cartoon.inkPrimary,
        )
        LinearProgressIndicator(
            progress = { coverage.fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(ProgressHeight)
                .clip(RoundedCornerShape(ProgressHeight / 2))
                .background(cartoon.inkPrimary.copy(alpha = 0.12f)),
            color = cartoon.accentPink,
            trackColor = androidx.compose.ui.graphics.Color.Transparent,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AreaStatsPanelPreview() {
    TripailTheme {
        AreaStatsPanel(
            flag = "🇵🇱",
            title = "Polska",
            coverage = AreaCoverage.Known(
                fraction = 0.17f,
                resolution = 7,
                areaCells = 4_200,
                discoveredCells = 714,
            ),
            onExplore = {},
            secondaryLabel = "Regiony",
            onSecondary = {},
        )
    }
}

@Preview(showBackground = true, name = "Coverage unavailable")
@Composable
private fun AreaStatsPanelUnavailablePreview() {
    TripailTheme {
        AreaStatsPanel(
            flag = "🏳️",
            title = "Obszar sporny",
            coverage = AreaCoverage.Unavailable,
            onExplore = {},
        )
    }
}

/** Centers a short note inside the panel width — used for the region hint. */
@Composable
internal fun PanelHint(text: String, modifier: Modifier = Modifier) {
    val cartoon = LocalCartoonStyle.current
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = cartoon.inkPrimary,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth(),
    )
}
