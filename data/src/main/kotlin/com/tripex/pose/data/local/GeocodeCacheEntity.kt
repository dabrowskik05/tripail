package com.tripex.pose.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One cached suggestion list, keyed by the normalised query.
 *
 * Typing "Warszawa" letter by letter is eight queries; without this, eight round trips. The
 * payload is the serialized result list — small, and it lets a repeat search answer offline.
 */
@Entity(tableName = "geocode_cache")
internal data class GeocodeCacheEntity(
    @PrimaryKey val query: String,
    val payload: String,
    val cachedAt: Long,
)
