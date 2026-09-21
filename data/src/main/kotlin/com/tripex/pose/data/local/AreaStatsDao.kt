package com.tripex.pose.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
internal interface AreaStatsDao {

    @Query("SELECT * FROM area_stats WHERE areaKey = :areaKey AND boundariesVersion = :boundariesVersion")
    suspend fun find(areaKey: String, boundariesVersion: Int): AreaStatsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AreaStatsEntity)

    /** Drops rows left over from an older boundary bundle. */
    @Query("DELETE FROM area_stats WHERE boundariesVersion != :boundariesVersion")
    suspend fun deleteStaleVersions(boundariesVersion: Int)
}
