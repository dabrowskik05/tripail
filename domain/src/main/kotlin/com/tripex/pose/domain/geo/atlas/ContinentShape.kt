package com.tripex.pose.domain.geo.atlas

import com.tripex.pose.domain.geo.ContinentId
import com.tripex.pose.domain.geo.GeoBounds

/**
 * Land polygons belonging to a single continent in the overview atlas.
 */
data class ContinentShape(
    val id: ContinentId,
    val rings: List<Ring>,
    val bounds: GeoBounds,
)
