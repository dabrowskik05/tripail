package com.tripex.pose.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * One snapped position on the trail. [cell9] and [parentRes7] exist only for dedup and for the
 * statistics; the map is drawn from [latitude] / [longitude].
 */
@Entity(
    tableName = "trail_point",
    indices = [Index("cell9"), Index("parentRes7"), Index("segmentId")],
)
internal data class TrailPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cell9: Long,
    val latitude: Double,
    val longitude: Double,
    val mode: String,
    val segmentId: Long,
    val parentRes7: Long,
    val discoveredAt: Long,
)

@Dao
internal interface TrailPointDao {

    @Insert
    suspend fun insert(point: TrailPointEntity): Long

    @Insert
    suspend fun insertAll(points: List<TrailPointEntity>)

    @Query("SELECT EXISTS(SELECT 1 FROM trail_point WHERE cell9 = :cell AND (:anyMode OR mode != 'VEHICLE'))")
    suspend fun isKnown(cell: Long, anyMode: Boolean): Boolean

    /** Ordered so consecutive rows of one segment are consecutive on the ground. */
    @Query("SELECT * FROM trail_point ORDER BY segmentId, id")
    fun observePoints(): Flow<List<TrailPointEntity>>

    @Query("SELECT DISTINCT cell9 FROM trail_point ORDER BY cell9")
    fun observeTrail(): Flow<List<Long>>

    @Query("SELECT DISTINCT parentRes7 FROM trail_point")
    fun observeFar(): Flow<List<Long>>

    @Query("SELECT COUNT(DISTINCT cell9) FROM trail_point")
    fun observeCount(): Flow<Int>
}
