package com.tripex.pose.ui.continent

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tripex.pose.domain.geo.ContinentId
import com.tripex.pose.ui.R
import com.tripex.pose.ui.components.ChunkyButton
import com.tripex.pose.ui.continent.components.SvgRegionCanvas
import com.tripex.pose.ui.theme.LocalCartoonStyle
import com.tripex.pose.ui.theme.TripailTheme
import kotlinx.coroutines.flow.collectLatest

@Composable
fun ContinentMapRoute(
    onOpenMap: (ContinentId) -> Unit,
    viewModel: ContinentMapViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is ContinentMapContract.Effect.OpenMap -> onOpenMap(effect.continentId)
            }
        }
    }

    BackHandler(enabled = state.selectedContinent != null) {
        viewModel.onIntent(ContinentMapContract.Intent.BackFromDetail)
    }

    ContinentMapScreen(
        state = state,
        onIntent = viewModel::onIntent,
    )
}

@Composable
fun ContinentMapScreen(
    state: ContinentMapContract.State,
    onIntent: (ContinentMapContract.Intent) -> Unit,
) {
    val cartoon = LocalCartoonStyle.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val bounceValues = ContinentCatalog.entries.associate { entry ->
        entry.id to rememberIdleBounce(entry.bounceDelayMillis)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(cartoon.paperBg)
            .safeDrawingPadding(),
    ) {
        AnimatedContent(
            targetState = state.selectedContinent,
            transitionSpec = {
                (
                    scaleIn(
                        initialScale = 0.88f,
                        animationSpec = tween(
                            450,
                            easing = CubicBezierEasing(0.2f, 0.8f, 0.3f, 1f),
                        ),
                    ) + fadeIn(tween(450))
                    ) togetherWith fadeOut(tween(200))
            },
            label = "continent-focus",
            modifier = Modifier.fillMaxSize(),
        ) { selected ->
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = stringResource(
                        if (selected == null) {
                            R.string.continent_world_title
                        } else {
                            ContinentCatalog.entry(selected).nameRes
                        },
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                )

                SvgRegionCanvas(
                    entries = if (selected == null) {
                        ContinentCatalog.entries
                    } else {
                        listOf(ContinentCatalog.entry(selected))
                    },
                    selectedId = selected,
                    scale = scale,
                    offset = offset,
                    onScaleOffsetChange = { s, o ->
                        scale = s
                        offset = o
                    },
                    onContinentClick = {
                        onIntent(ContinentMapContract.Intent.ContinentClicked(it))
                    },
                    bounceTranslationY = { id ->
                        val v = bounceValues[id]?.value ?: 0f
                        -4f * v
                    },
                    bounceScale = { id ->
                        val v = bounceValues[id]?.value ?: 0f
                        1f + 0.02f * v
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (selected != null) {
                        val pct = ((state.coverage[selected] ?: 0f) * 100f).toInt()
                        Text(
                            text = stringResource(R.string.continent_coverage, pct),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        ChunkyButton(
                            text = stringResource(R.string.continent_explore),
                            onClick = { onIntent(ContinentMapContract.Intent.ExploreSelected) },
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.continent_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberIdleBounce(delayMillis: Int): State<Float> {
    val transition = rememberInfiniteTransition(label = "idle-bounce")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3_200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(delayMillis),
        ),
        label = "bounce",
    )
}

@Preview(showBackground = true)
@Composable
private fun ContinentMapScreenPreview() {
    TripailTheme {
        ContinentMapScreen(
            state = ContinentMapContract.State(
                coverage = mapOf(ContinentId.Europe to 0.12f),
                selectedContinent = ContinentId.Europe,
            ),
            onIntent = {},
        )
    }
}
