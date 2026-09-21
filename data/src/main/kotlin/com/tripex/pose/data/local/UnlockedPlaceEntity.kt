package com.tripex.pose.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A searched place owned as a circle — centre and radius, never cells. */
@Entity(tableName = "unlocked_place")
internal data class UnlockedPlaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double,
    val unlockedAt: Long,
)
