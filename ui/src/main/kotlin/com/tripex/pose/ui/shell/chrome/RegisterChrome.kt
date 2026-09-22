package com.tripex.pose.ui.shell.chrome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

/**
 * Tells the shared top bar what to call this level, and what its arrow does (V3.2.1).
 *
 * Every level registers exactly once. The back lambda is read through [rememberUpdatedState] so
 * a recomposition does not tear the registration down and put it back — the same reason the map
 * surface keys its tap handler on the host rather than on the lambda.
 */
@Composable
internal fun RegisterChrome(
    chrome: AppChromeState,
    title: String,
    onBack: () -> Unit,
) {
    val currentBack by rememberUpdatedState(onBack)
    DisposableEffect(chrome, title) {
        chrome.setLevel(title) { currentBack() }
        onDispose { }
    }
}
