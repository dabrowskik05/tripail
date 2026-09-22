package com.tripex.pose.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripex.pose.domain.settings.AppLanguage
import com.tripex.pose.domain.settings.AppLanguageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The language choice, shared by the first-launch picker and the settings screen (V3.5.1–V3.5.3).
 *
 * Applying it is not this class's job: `AppLanguageApplier` in `:app` watches the same repository
 * and calls the platform. Keeping the write here and the effect there means changing the language
 * from two different screens cannot produce two different behaviours.
 */
@HiltViewModel
class LanguageViewModel @Inject constructor(
    private val repository: AppLanguageRepository,
) : ViewModel() {

    val language: StateFlow<AppLanguage> = repository.observe()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = AppLanguage.DEFAULT,
        )

    fun select(language: AppLanguage) {
        viewModelScope.launch { repository.set(language) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
