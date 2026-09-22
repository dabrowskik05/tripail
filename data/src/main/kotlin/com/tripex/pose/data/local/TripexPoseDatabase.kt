package com.tripex.pose.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        UnlockedHexEntity::class,
        AreaStatsEntity::class,
        UnlockedRegionEntity::class,
        GeocodeCacheEntity::class,
        UnlockedPlaceEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
internal abstract class TripexPoseDatabase : RoomDatabase() {
    abstract fun unlockedHexDao(): UnlockedHexDao

    abstract fun areaStatsDao(): AreaStatsDao

    abstract fun unlockedRegionDao(): UnlockedRegionDao

    abstract fun geocodeCacheDao(): GeocodeCacheDao

    abstract fun unlockedPlaceDao(): UnlockedPlaceDao
}
