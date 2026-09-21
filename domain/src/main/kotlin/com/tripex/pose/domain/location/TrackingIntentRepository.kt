package com.tripex.pose.domain.location

import kotlinx.coroutines.flow.Flow

/**
 * Whether the player has asked to be tracked — persisted, so it outlives the process (V3.7.2).
 *
 * This is deliberately *not* [TrackingState]: that one says what the service is doing right now,
 * this one says what the player wants. The difference matters exactly once, and it is the case
 * this whole stage exists for: when Android kills the process and later revives the service with
 * a `null` Intent, there is nobody left to ask. Without a stored intent the service either
 * resumes tracking somebody who switched it off, or gives up on somebody who did not.
 *
 * Being swiped out of the recents list, having the process killed and rebooting the phone are
 * **not** stopping. Only [set] with `false`, from an explicit user action, is.
 */
interface TrackingIntentRepository {

    suspend fun set(requested: Boolean)

    /** Read once, on a cold start of the service. */
    suspend fun isRequested(): Boolean

    fun observe(): Flow<Boolean>
}
