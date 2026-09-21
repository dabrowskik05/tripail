package com.tripex.pose.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
internal interface UnlockedHexDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(hexes: List<UnlockedHexEntity>): List<Long>

    @Query("SELECT h3Index FROM unlocked_hex WHERE parentRes7 IN (:viewportCells)")
    fun observeDetailed(viewportCells: Set<Long>): Flow<List<Long>>

    @Query("SELECT DISTINCT parentRes9 FROM unlocked_hex WHERE parentRes7 IN (:viewportCells)")
    fun observeMid(viewportCells: Set<Long>): Flow<List<Long>>

    @Query("SELECT DISTINCT parentRes7 FROM unlocked_hex")
    fun observeFar(): Flow<List<Long>>

    /**
     * Every unlocked cell, newest first, capped. Backs the global wash: discovered ground must be
     * visible wherever the camera is, not only inside the current viewport.
     */
    @Query("SELECT h3Index FROM unlocked_hex ORDER BY discoveredAt DESC LIMIT :limit")
    fun observeAllDetailed(limit: Int): Flow<List<Long>>

    @Query("SELECT COUNT(*) FROM unlocked_hex")
    fun observeCount(): Flow<Int>
}
