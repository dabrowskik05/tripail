package com.tripex.pose.ui.continent

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tripex.pose.domain.geo.AreaCoverage
import com.tripex.pose.domain.geo.ContinentId
import com.tripex.pose.domain.geo.GeoBounds
import com.tripex.pose.ui.R
import com.tripex.pose.ui.components.ChunkyButton
import com.tripex.pose.ui.continent.components.ContinentMenuCanvas
import com.tripex.pose.ui.theme.LocalCartoonStyle
import kotlinx.coroutines.flow.collectLatest

private const val DIM_FRACTION = 1f
private const val DIM_DURATION_MILLIS = 300
private const val PERCENT = 100f

private val PanelShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

/**
 * Stage A — the continent menu (vision §3).
 *
 * A screen entirely of its own: a Compose canvas of continent shapes over water. It holds **no
 * MapLibre instance and no reference to the map host**, which is the whole point of §3 — and also
 * a hard requirement, because MapLibre's `MapView` is a `SurfaceView` that would paint straight
 * over this canvas if it were composed at the same time.
 */
@Composable
fun ContinentMapRoute(
    onOpenContinent: (ContinentId, GeoBounds) -> Unit,
    onBack: () -> Unit,
    viewModel: ContinentMapViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val cartoon = LocalCartoonStyle.current

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is ContinentMapContract.Effect.OpenMap ->
                    onOpenContinent(effect.continentId, effect.bounds)
            }
        }
    }

    val selected = state.selectedContinent

    // With nothing selected this handler stays off, so back falls through to the navigation graph
    // and lands on the splash — which is where the player came from.
    BackHandler(enabled = selected != null) {
        viewModel.onIntent(ContinentMapContract.Intent.ClearSelection)
    }

    val goBack: () -> Unit = {
        if (selected != null) {
            viewModel.onIntent(ContinentMapContract.Intent.ClearSelection)
        } else {
            onBack()
        }
    }

    val dim by animateFloatAsState(
        targetValue = if (selected == null) 0f else DIM_FRACTION,
        animationSpec = tween(DIM_DURATION_MILLIS),
        label = "continent-dim",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(cartoon.oceanBlue),
    ) {
        ContinentMenuCanvas(
            shapes = state.shapes,
            selectedId = selected,
            dimFraction = dim,
            onTap = { viewModel.onIntent(ContinentMapContract.Intent.ContinentClicked(it)) },
            modifier = Modifier.fillMaxSize(),
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .safeDrawingPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(shape = CircleShape, color = cartoon.paperBg, shadowElevation = 4.dp) {
                IconButton(onClick = goBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.area_back_cd),
                        tint = cartoon.inkPrimary,
                    )
                }
            }
            Surface(shape = CircleShape, color = cartoon.paperBg, shadowElevation = 4.dp) {
                Text(
                    text = stringResource(
                        if (selected == null) {
                            R.string.continent_world_title
                        } else {
                            ContinentPalette.label(selected)
                        },
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    color = cartoon.inkPrimary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        }

        // No card over an empty ocean: with nothing picked the hint floats on the water, so the
        // default view is just continents.
        if (selected == null) {
            Text(
                text = stringResource(
                    if (state.atlasUnavailable) {
                        R.string.continent_atlas_unavailable
                    } else {
                        R.string.continent_hint
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = cartoon.inkPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
            )
        } else {
            ContinentMenuPanel(
                state = state,
                onExplore = { viewModel.onIntent(ContinentMapContract.Intent.ExploreSelected) },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/**
 * The bottom menu card, and the app's answer to the system navigation bar.
 *
 * The `Surface` runs all the way to the bottom edge while its content is lifted above the bar,
 * so the strip under the gesture handle is the card's own colour instead of ocean showing through
 * — and nothing interactive ever sits where a system gesture starts.
 */
@Composable
private fun ContinentMenuPanel(
    state: ContinentMapContract.State,
    onExplore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cartoon = LocalCartoonStyle.current
    val selected = state.selectedContinent

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = PanelShape,
        color = ContinentPalette.menuSurface,
        shadowElevation = 12.dp,
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The continent's own name is already in the header; repeating it here would just
            // push the buttons down.
            val hint = when {
                state.atlasUnavailable -> R.string.continent_atlas_unavailable
                selected == null -> R.string.continent_hint
                else -> null
            }
            if (hint != null) {
                Text(
                    text = stringResource(hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = cartoon.inkPrimary,
                    textAlign = TextAlign.Center,
                )
            }

            val coverage = state.coverage
            if (selected != null && coverage is AreaCoverage.Known) {
                Text(
                    text = stringResource(
                        R.string.continent_coverage,
                        (coverage.fraction * PERCENT).toInt(),
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = cartoon.inkPrimary,
                )
            }

            // Going back lives in the top-left arrow, not down here.
            if (selected != null) {
                ChunkyButton(
                    text = stringResource(R.string.continent_explore),
                    onClick = onExplore,
                )
            }
        }
    }
}
