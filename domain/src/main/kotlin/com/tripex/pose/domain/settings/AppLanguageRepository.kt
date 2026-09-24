package com.tripex.pose.domain.settings

import kotlinx.coroutines.flow.Flow

/**
 * The player's chosen language, or the absence of a choice (V3.5.1).
 *
 * "Not chosen yet" is a real state, not a default: it is what makes the first launch show the
 * language picker exactly once. Collapsing it into [AppLanguage.DEFAULT] would either show the
 * picker forever or never.
 */
interface AppLanguageRepository {

    /** `null` until the player has picked — see the language screen (V3.5.2). */
    suspend fun selected(): AppLanguage?

    /** [selected] as a stream, so "has the player picked yet" updates the moment they do. */
    fun observeSelected(): Flow<AppLanguage?>

    /** Falls back to [AppLanguage.DEFAULT] so callers that only need *a* language stay simple. */
    fun observe(): Flow<AppLanguage>

    suspend fun set(language: AppLanguage)
}
