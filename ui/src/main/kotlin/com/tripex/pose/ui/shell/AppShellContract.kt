package com.tripex.pose.ui.shell

/**
 * The shell owns exactly one thing: whether the app is ready to be entered (M2.2).
 * Navigation state lives in the `NavHost` back stack, not here (M2.4).
 */
object AppShellContract {

    /**
     * Which startup signal is missing. Used for the retry copy, never for a progress bar —
     * there is no fake percentage any more.
     */
    enum class FailedSignal {
        Atlas,
        Boundaries,
        Hexes,
        Style,
        Storage,
    }

    sealed interface Readiness {
        data object Preparing : Readiness
        data object Ready : Readiness

        /**
         * At least one signal failed. [degraded] marks a failure the user can enter past —
         * the atlas only powers the world overview, so the map still works without it.
         */
        data class Failed(
            val signal: FailedSignal,
            val degraded: Boolean,
        ) : Readiness
    }

    data class State(
        val readiness: Readiness = Readiness.Preparing,
        /**
         * True until the player has picked a language (V3.5.2).
         *
         * Starts `false` so a first frame drawn before the preference has been read does not
         * flash the picker at somebody who chose months ago.
         */
        val needsLanguage: Boolean = false,
    ) {
        val canEnter: Boolean
            get() = readiness is Readiness.Ready ||
                (readiness as? Readiness.Failed)?.degraded == true
    }

    sealed interface Intent {
        data object Retry : Intent
    }
}
