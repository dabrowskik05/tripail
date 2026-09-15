package com.tripex.pose.data.mapper

import com.tripex.pose.data.local.UnlockedHexEntity
import com.tripex.pose.domain.geo.H3Config
import com.tripex.pose.domain.geo.H3Converter

internal fun Long.toUnlockedHexEntity(
    h3: H3Converter,
    discoveredAt: Long,
): UnlockedHexEntity =
    UnlockedHexEntity(
        h3Index = this,
        parentRes9 = h3.parentOf(this, H3Config.LOD_MID_RESOLUTION),
        parentRes7 = h3.parentOf(this, H3Config.LOD_FAR_RESOLUTION),
        discoveredAt = discoveredAt,
        resolution = H3Config.WALKING_RESOLUTION,
    )
