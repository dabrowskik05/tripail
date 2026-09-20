package com.tripex.pose.ui.shell

import com.tripex.pose.domain.geo.ContinentId
import com.tripex.pose.ui.map.MapContract

object AppShellContract {

    sealed interface Stage {
        data object Loading : Stage
        data object Continents : Stage
        data object Map : Stage
    }

    data class State(
        val stage: Stage = Stage.Loading,
        val progress: Float = 0f,
        val isReady: Boolean = false,
        val mapCameraTarget: MapContract.CameraTarget? = null,
    )

    sealed interface Intent {
        data object EnterContinentsRequested : Intent
        data class OpenMap(val continentId: ContinentId) : Intent
        data object BackToContinents : Intent
        data object MapCameraConsumed : Intent
    }
}
