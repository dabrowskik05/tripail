package com.tripex.pose.ui.shell.chrome

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * What the one top bar is showing, and what its arrow does (V3.2.1).
 *
 * The bar lives above the navigation graph for the same reason the map does: there is one of it,
 * and it must not be torn down and rebuilt on every transition. Each level registers its title
 * and its back action while it is on screen; nothing else about the bar changes between levels,
 * which is the entire point — the player sees the same controls in the same places everywhere.
 */
@Stable
class AppChromeState {

    /** Name of the scope currently open: "Europa", "Polska", "mazowieckie". */
    var title: String by mutableStateOf("")
        private set

    /** Whether the bar is on screen at all. The loading screen has no chrome. */
    var isVisible: Boolean by mutableStateOf(false)
        private set

    var isSearchOpen: Boolean by mutableStateOf(false)
        private set

    /** The community teaser. Lives here because its button is in the shared bar. */
    var isCommunityOpen: Boolean by mutableStateOf(false)
        private set

    private var levelBack: (() -> Unit)? by mutableStateOf(null)

    fun setLevel(title: String, onBack: () -> Unit) {
        this.title = title
        this.levelBack = onBack
        this.isVisible = true
    }

    fun hide() {
        isVisible = false
        isSearchOpen = false
    }

    fun openSearch() {
        isSearchOpen = true
    }

    fun closeSearch() {
        isSearchOpen = false
    }

    fun openCommunity() {
        isCommunityOpen = true
    }

    fun closeCommunity() {
        isCommunityOpen = false
    }

    /**
     * The one back implementation (V3.2.3).
     *
     * Both the arrow in the bar and the system gesture call this, so they cannot disagree.
     * [panelOpen] and [hasSelection] are passed in by the level, because only the level knows
     * what it has open.
     */
    fun onBack(
        panelOpen: Boolean = false,
        hasSelection: Boolean = false,
        onClosePanel: () -> Unit = {},
        onClearSelection: () -> Unit = {},
    ) {
        val action = BackResolver.resolve(
            BackResolver.Context(
                isSearchOpen = isSearchOpen || isCommunityOpen,
                isPanelOpen = panelOpen,
                hasSelection = hasSelection,
            ),
        )
        when (action) {
            // Whatever overlay is up, the innermost one closes first.
            BackResolver.Action.CloseSearch -> if (isCommunityOpen) closeCommunity() else closeSearch()
            BackResolver.Action.ClosePanel -> onClosePanel()
            BackResolver.Action.ClearSelection -> onClearSelection()
            BackResolver.Action.NavigateUp -> levelBack?.invoke()
        }
    }
}
