package com.tripex.pose.domain.location

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide tracking status shared by the Foreground Service and UI (no binding).
 */
@Singleton
class TrackingStateHolder
    @Inject
    constructor() {
        private val _state = MutableStateFlow(TrackingState.Idle)
        val state: StateFlow<TrackingState> = _state.asStateFlow()

        fun update(new: TrackingState) {
            _state.value = new
        }
    }
