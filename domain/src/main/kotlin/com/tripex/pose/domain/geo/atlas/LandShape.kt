package com.tripex.pose.domain.geo.atlas

import com.tripex.pose.domain.geo.GeoBounds

/**
 * All land polygons from the overview atlas, used as the land mask for fog.
 */
data class LandShape(
    val rings: List<Ring>,
    val bounds: GeoBounds,
)
