package com.tripex.pose.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tripex.pose.domain.geo.H3Config

@Entity(
    tableName = "unlocked_hex",
    indices = [
        Index("parentRes9"),
        Index("parentRes7"),
        Index("discoveredAt"),
    ],
)
internal data class UnlockedHexEntity(
    @PrimaryKey val h3Index: Long,
    val parentRes9: Long,
    val parentRes7: Long,
    val discoveredAt: Long,
    val resolution: Int = H3Config.WALKING_RESOLUTION,
)
