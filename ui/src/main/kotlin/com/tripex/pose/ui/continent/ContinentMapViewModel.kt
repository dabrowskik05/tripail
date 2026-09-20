package com.tripex.pose.ui.continent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripex.pose.domain.geo.ContinentId
import com.tripex.pose.domain.usecase.ObserveContinentCoverageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ContinentMapViewModel @Inject constructor(
    observeContinentCoverage: ObserveContinentCoverageUseCase,
) : ViewModel() {

    private val local = MutableStateFlow(ContinentMapContract.State())

    val state: StateFlow<ContinentMapContract.State> = combine(
        local,
        observeContinentCoverage(),
    ) { base, coverage ->
        base.copy(coverage = coverage)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ContinentMapContract.State(),
    )

    private val _effects = Channel<ContinentMapContract.Effect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onIntent(intent: ContinentMapContract.Intent) {
        when (intent) {
            is ContinentMapContract.Intent.ContinentClicked -> {
                val current = local.value.selectedContinent
                if (current == intent.id) {
                    viewModelScope.launch {
                        _effects.send(ContinentMapContract.Effect.OpenMap(intent.id))
                    }
                } else {
                    local.update { it.copy(selectedContinent = intent.id) }
                }
            }
            ContinentMapContract.Intent.BackFromDetail -> {
                local.update { it.copy(selectedContinent = null) }
            }
            ContinentMapContract.Intent.ExploreSelected -> {
                val id = local.value.selectedContinent ?: return
                viewModelScope.launch {
                    _effects.send(ContinentMapContract.Effect.OpenMap(id))
                }
            }
        }
    }
}
