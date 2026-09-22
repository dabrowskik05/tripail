package com.tripex.pose.ui.shell.chrome

/**
 * The single definition of what "back" means, anywhere in the app (V3.2.3).
 *
 * ### Why this is a pure function
 *
 * Back used to be implemented twice over: the arrow in the chrome called one lambda, and
 * `BackHandler` was wired separately in four screens, each with its own idea of what to undo.
 * The two disagreed, so the system gesture would collapse a screen the arrow would merely
 * close — the "screen caves in" bug.
 *
 * Making the decision a pure function means the arrow and the gesture cannot drift apart: both
 * ask this, and both act on the answer. It also means the priority order is testable, which is
 * the only way it stays correct after the next screen is added.
 */
object BackResolver {

    /** What is currently open, in the order it should be dismissed. */
    data class Context(
        val isSearchOpen: Boolean = false,
        val isPanelOpen: Boolean = false,
        val hasSelection: Boolean = false,
    )

    enum class Action {
        /** Leave search mode; the level underneath is untouched. */
        CloseSearch,

        /** Dismiss the bottom sheet. */
        ClosePanel,

        /** Drop the highlight, stay on this level. */
        ClearSelection,

        /** Nothing left to undo here — go up one level. */
        NavigateUp,
    }

    /**
     * Innermost thing first. Every step undoes exactly one layer, so repeated back presses walk
     * out the way the player walked in.
     */
    fun resolve(context: Context): Action = when {
        context.isSearchOpen -> Action.CloseSearch
        context.isPanelOpen -> Action.ClosePanel
        context.hasSelection -> Action.ClearSelection
        else -> Action.NavigateUp
    }
}
