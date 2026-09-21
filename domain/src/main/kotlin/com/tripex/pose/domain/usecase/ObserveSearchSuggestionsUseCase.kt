package com.tripex.pose.domain.usecase

import com.tripex.pose.domain.geo.Place
import com.tripex.pose.domain.repository.GeocodingRepository
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest

/**
 * Turns a stream of keystrokes into a stream of suggestions (M4.9).
 *
 * [DEBOUNCE_MS] is the whole point: without it, typing "Warszawa" is eight requests for one
 * intention. `mapLatest` then drops an in-flight lookup the moment the query moves on, so a slow
 * response for "Wars" can never overwrite the results for "Warszawa".
 */
class ObserveSearchSuggestionsUseCase
    @Inject
    constructor(
        private val repository: GeocodingRepository,
    ) {
        @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
        operator fun invoke(queries: Flow<String>): Flow<Result<List<Place>>> =
            queries
                .map { it.trim() }
                .distinctUntilChanged()
                .debounce(DEBOUNCE_MS)
                .mapLatest { query ->
                    if (query.length < MIN_QUERY_LENGTH) {
                        Result.success(emptyList())
                    } else {
                        repository.suggest(query)
                    }
                }

        companion object {
            const val DEBOUNCE_MS = 300L
            const val MIN_QUERY_LENGTH = 2
        }
    }
