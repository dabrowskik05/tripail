package com.tripex.pose.domain.location

/**
 * Remembers which one-off setup prompts the player has already been through (V3.7.5).
 *
 * The battery-optimisation and autostart screens are worth showing once, when tracking first
 * starts. Showing them on every start would train the player to dismiss them, which is the one
 * outcome that guarantees the tracking dies on the first OEM that cares.
 */
interface TrackingSetupRepository {

    suspend fun hasSeenReliabilityPrompt(): Boolean

    suspend fun markReliabilityPromptSeen()
}
