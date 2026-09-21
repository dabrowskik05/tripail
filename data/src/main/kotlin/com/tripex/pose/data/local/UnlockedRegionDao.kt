package com.tripex.pose.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
internal interface UnlockedRegionDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: UnlockedRegionEntity): Long

    @Query("DELETE FROM unlocked_region WHERE adminLevel = :adminLevel AND featureId = :featureId")
    suspend fun delete(adminLevel: String, featureId: String): Int

    @Query("SELECT * FROM unlocked_region ORDER BY unlockedAt")
    fun observeAll(): Flow<List<UnlockedRegionEntity>>

    @Query("SELECT COUNT(*) FROM unlocked_region WHERE adminLevel = :adminLevel AND featureId = :featureId")
    suspend fun count(adminLevel: String, featureId: String): Int
}
