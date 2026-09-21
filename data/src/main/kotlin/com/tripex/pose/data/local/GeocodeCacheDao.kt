package com.tripex.pose.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
internal interface GeocodeCacheDao {

    @Query("SELECT * FROM geocode_cache WHERE query = :query")
    suspend fun find(query: String): GeocodeCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: GeocodeCacheEntity)

    @Query("DELETE FROM geocode_cache WHERE cachedAt < :olderThan")
    suspend fun evictOlderThan(olderThan: Long)
}
