package com.tripex.pose.settings

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import android.view.ContextThemeWrapper
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.tripex.pose.core.logging.Logger
import com.tripex.pose.domain.settings.AppLanguage
import com.tripex.pose.domain.settings.AppLanguageRepository
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Applies the stored language everywhere Compose cannot reach (V3.5.1, corrected in V3.5.6).
 *
 * The screens get their language from `ProvideAppLanguage` in `:ui`, which overrides the
 * composition locals directly and therefore works on every API level. This class covers the
 * three things that live outside the composition:
 *
 * - **`Locale.getDefault()`** — the ICU country names behind `AreaLabels`, and every number and
 *   date format, are resolved in view models with no `Context` in sight.
 * - **The platform per-app locale** — so Android 13+ shows the same choice in system settings,
 *   and a process started for the foreground service inherits it.
 * - **[localize]** — a context the tracking notification can resolve its strings against below
 *   API 33, where the platform call has nothing to install the override on.
 *
 * Two details that were bugs before:
 *
 * - `AppCompatDelegate.setApplicationLocales` is a main-thread API. It was being called from
 *   `applicationScope`, which runs on `Dispatchers.Default`, inside a `runCatching` that turned
 *   the resulting failure into a log line nobody read — so the language silently never applied.
 * - [current] is updated first and unconditionally, so a failing platform call cannot leave the
 *   notification speaking a different language from the screens.
 */
@Singleton
class AppLanguageApplier
    @Inject
    constructor(
        private val repository: AppLanguageRepository,
        private val logger: Logger,
    ) {
        /** The language in force right now, readable from any thread without suspending. */
        @Volatile
        var current: AppLanguage = AppLanguage.DEFAULT
            private set

        fun start(scope: CoroutineScope) {
            // Main, immediate: the platform call below requires it, and on the first emission
            // `immediate` means the language is set before the first frame rather than after it.
            scope.launch(Dispatchers.Main.immediate) {
                repository.observe()
                    .distinctUntilChanged()
                    .collect { language -> apply(language) }
            }
        }

        /**
         * Wraps [context] so `getString` answers in the chosen language.
         *
         * For the foreground service, which resolves its notification text with no Activity and
         * no composition anywhere in the process.
         */
        fun localize(context: Context): Context {
            val override = Configuration().apply {
                setLocales(LocaleList(Locale.forLanguageTag(current.tag)))
            }
            return ContextThemeWrapper(context, 0).apply { applyOverrideConfiguration(override) }
        }

        private fun apply(language: AppLanguage) {
            current = language
            Locale.setDefault(Locale.forLanguageTag(language.tag))
            runCatching {
                AppCompatDelegate.setApplicationLocales(
                    LocaleListCompat.forLanguageTags(language.tag),
                )
            }.onFailure { logger.e(TAG, "Could not apply language ${language.tag}", it) }
        }

        private companion object {
            const val TAG = "AppLanguage"
        }
    }
