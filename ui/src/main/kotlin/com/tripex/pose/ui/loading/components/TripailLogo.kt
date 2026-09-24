package com.tripex.pose.ui.loading.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tripex.pose.ui.R

/**
 * Height matched to the old drawn wordmark (displayLarge + pin above the baseline), so the
 * splash layout and slingshot animation keep the same visual weight after switching to the
 * branded PNG. Aspect ratio comes from the asset (3:1).
 */
private val LogoHeight = 144.dp

/**
 * The Tripail wordmark from docs/design — globe + gradient type. Raster, not VectorDrawable:
 * the source SVG uses filters, soft shadows and complex gradients that VD cannot reproduce.
 */
@Composable
internal fun TripailLogo(
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(R.drawable.logo_wordmark),
        contentDescription = stringResource(R.string.logo_content_description),
        contentScale = ContentScale.Fit,
        modifier = modifier.height(LogoHeight),
    )
}
