package com.tripex.pose.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cached denominator of an area coverage ratio (M3.4) — numbers only, never geometry.
 *
 * [boundariesVersion] ties a row to the shipped `boundaries.pmtiles`; a bundle bump changes the
 * polygons, so every cached count is stale by definition and rows are ignored, then overwritten.
 */
@Entity(tableName = "area_stats")
internal data class AreaStatsEntity(
    /** Serialized [com.tripex.pose.domain.geo.atlas.AreaKey], e.g. `country:PL`. */
    @PrimaryKey val areaKey: String,
    val resolution: Int,
    val cellCount: Int,
    val boundariesVersion: Int,
)
