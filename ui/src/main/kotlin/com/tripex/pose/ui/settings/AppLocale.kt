package com.tripex.pose.ui.settings

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import android.view.ContextThemeWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import com.tripex.pose.domain.settings.AppLanguage
import java.util.Locale

/**
 * Makes the chosen language the one every `stringResource` in the tree reads (V3.5.6).
 *
 * ### Why this exists rather than trusting the platform
 *
 * The language used to be applied only through `AppCompatDelegate.setApplicationLocales`, which
 * is not enough here for three separate reasons:
 *
 * 1. Below API 33 that call needs AppCompat's own `Activity` to install the override — and
 *    `MainActivity` is a plain `ComponentActivity` on a `Theme.Material` theme. On every device
 *    older than Android 13 the choice therefore changed nothing at all.
 * 2. Where it does work, it works by **recreating the Activity**, so the switch is a visible
 *    tear-down rather than a recomposition.
 * 3. It was being called off the main thread, from `applicationScope`, inside a `runCatching`
 *    that swallowed the result.
 *
 * Overriding the composition locals instead makes the language a piece of Compose state: it is
 * correct on the first frame, on every API level, and switching it is a normal recomposition.
 *
 * All three of [LocalContext], [LocalResources] and [LocalConfiguration] have to be provided
 * together. `stringResource` reads [LocalResources]; anything inflating a view or opening a
 * dialog reads [LocalContext]. Providing one and not the others is how half a screen ends up in
 * the other language.
 *
 * The platform call stays in `:app` on top of this, so Android's own per-app language picker
 * agrees with the in-app setting — but nothing on screen depends on it any more.
 */
@Composable
fun ProvideAppLanguage(
    language: AppLanguage,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val localized = remember(context, language) { context.localizedFor(language) }
    CompositionLocalProvider(
        LocalContext provides localized,
        LocalResources provides localized.resources,
        LocalConfiguration provides localized.resources.configuration,
        content = content,
    )
}

/**
 * A context that resolves resources in [language], still rooted at this one.
 *
 * A `ContextThemeWrapper` rather than `createConfigurationContext`, because the wrapper keeps
 * `baseContext` pointing at the Activity. Compose and MapLibre both walk that chain to find the
 * Activity, and a bare configuration context breaks the walk.
 *
 * The override `Configuration` carries nothing but the locale list; every other field is left
 * unset so the display's own density, size and night mode continue to apply.
 */
internal fun Context.localizedFor(language: AppLanguage): Context {
    val override = Configuration().apply {
        setLocales(LocaleList(Locale.forLanguageTag(language.tag)))
    }
    return ContextThemeWrapper(this, 0).apply { applyOverrideConfiguration(override) }
}
