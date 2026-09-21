package com.tripex.pose.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
internal interface UnlockedPlaceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: UnlockedPlaceEntity)

    @Query("DELETE FROM unlocked_place WHERE id = :id")
    suspend fun delete(id: String): Int

    @Query("SELECT * FROM unlocked_place ORDER BY unlockedAt")
    fun observeAll(): Flow<List<UnlockedPlaceEntity>>
}
