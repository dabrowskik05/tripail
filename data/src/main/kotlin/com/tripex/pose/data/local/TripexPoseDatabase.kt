package com.tripex.pose.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [UnlockedHexEntity::class],
    version = 1,
    exportSchema = true,
)
internal abstract class TripexPoseDatabase : RoomDatabase() {
    abstract fun unlockedHexDao(): UnlockedHexDao
}
