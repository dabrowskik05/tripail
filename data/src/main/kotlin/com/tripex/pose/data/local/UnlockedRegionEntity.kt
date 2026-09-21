package com.tripex.pose.data.local

import androidx.room.Entity

/**
 * A whole administrative area the player owns (hybrid model).
 *
 * Deliberately stores an **identifier, not geometry**: the outline already ships in
 * `boundaries.pmtiles`, and rasterising a voivodeship to walking-resolution cells would be
 * ~17 million rows. Composite key, because ADM0 and ADM1 ids live in separate namespaces.
 */
@Entity(tableName = "unlocked_region", primaryKeys = ["adminLevel", "featureId"])
internal data class UnlockedRegionEntity(
    /** [com.tripex.pose.domain.geo.atlas.AdminLevel] name. */
    val adminLevel: String,
    val featureId: String,
    val unlockedAt: Long,
)
