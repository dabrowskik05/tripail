package com.tripex.pose.ui.shell

object AppShellContract {

    sealed interface Stage {
        data object Loading : Stage
        data object Map : Stage
    }

    data class State(
        val stage: Stage = Stage.Loading,
        val progress: Float = 0f,
        val isReady: Boolean = false,
    )

    sealed interface Intent {
        data object EnterMapRequested : Intent
    }
}
