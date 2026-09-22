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

    // There was a `SELECT … ORDER BY discoveredAt DESC LIMIT :limit` here, backing the global
    // wash. One GPS fix is ~1800 cells at walking resolution, so a 20 000 cap held about eleven
    // fixes and everything older silently vanished from the map. It is deleted rather than
    // raised: the wash now draws coarse parents when the camera is far out (`FogLod`), so there
    // is no longer any reason to ask for "all cells" — and no query left to reintroduce the bug.

    @Query("SELECT COUNT(*) FROM unlocked_hex")
    fun observeCount(): Flow<Int>
}
