package com.tripex.pose.ui.map.host

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tripex.pose.domain.geo.GeoBounds
import com.tripex.pose.domain.geo.MapViewport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bridge between the navigation graph and the single MapLibre instance.
 *
 * Screens describe what they want shown; the surface obeys. Because the surface lives *above*
 * the `NavHost`, moving between levels never destroys or recreates the map — the camera simply
 * stays where it was unless someone explicitly asks it to move.
 */
@Stable
class MapHostState {

    var scene: MapScene by mutableStateOf(MapScene())
        private set

    /** Parchment geometry — world polygon with every unlocked area punched out. */
    var fogGeoJson: String by mutableStateOf("")
        internal set

    var cameraRequest: CameraRequest? by mutableStateOf(null)
        private set

    /**
     * Where the camera settled last.
     *
     * The wash reads this to decide which resolution to draw the trail at (`FogLod`). It is a
     * flow rather than Compose state because its only consumer is a `ViewModel` pipeline, and
     * recomposing the map on every camera idle would be pointless work.
     */
    private val _viewport = MutableStateFlow(MapViewport.WORLD)
    val viewport: StateFlow<MapViewport> = _viewport.asStateFlow()

    /**
     * Box the camera may not leave, or `null` for the whole world (V3.3.7).
     *
     * Set when a continent is being explored, so panning cannot wander off onto neighbouring,
     * still-fogged continents. Cleared whenever there is no continent in context — a camera
     * locked into a box nobody can unlock is worse than no lock at all.
     */
    var maxBounds: GeoBounds? by mutableStateOf(null)
        private set

    /**
     * The screen currently in front. Taps are delivered here rather than through a callback wired
     * at the app level, because only the active destination knows what a tap means.
     */
    var onTap: ((com.tripex.pose.ui.explore.BoundaryTap) -> Unit)? by mutableStateOf(null)
        private set

    private var cameraToken = 0L

    fun setTapHandler(handler: (com.tripex.pose.ui.explore.BoundaryTap) -> Unit) {
        onTap = handler
    }

    /**
     * Releases the handler only if it is still the one that was installed.
     *
     * During a navigation transition both screens are briefly composed, and the outgoing one
     * disposes *after* the incoming one has registered. Clearing unconditionally would wipe the
     * new screen's handler and leave the map silently unclickable.
     */
    /** Used by levels where nothing on the map is selectable. */
    fun clearTapHandler() {
        onTap = null
    }

    fun releaseTapHandler(handler: (com.tripex.pose.ui.explore.BoundaryTap) -> Unit) {
        if (onTap === handler) onTap = null
    }

    fun show(scene: MapScene) {
        this.scene = scene
    }

    internal fun onViewportChanged(value: MapViewport) {
        _viewport.value = value
    }

    fun limitTo(bounds: GeoBounds?) {
        maxBounds = bounds
    }

    /**
     * Asks the camera to frame [bounds]. The only way the camera ever moves on its own — and it
     * is deliberately awkward to reach from a selection handler.
     */
    /**
     * @param fill true when [bounds] is the subject of the screen rather than context, so the
     *   shape should reach the edges (V3.3.5).
     */
    fun flyTo(bounds: GeoBounds, animate: Boolean = true, fill: Boolean = false) {
        cameraToken += 1
        cameraRequest = CameraRequest(
            bounds = bounds,
            animate = animate,
            token = cameraToken,
            paddingFraction = if (fill) CameraRequest.FILL_PADDING else CameraRequest.FRAME_PADDING,
        )
    }

    fun onCameraApplied(token: Long) {
        if (cameraRequest?.token == token) cameraRequest = null
    }
}
