package com.tripex.pose.ui.explore.components

import java.util.concurrent.atomic.AtomicInteger

/**
 * Evidence that changing the selection does **not** rebuild the boundary surface (M3.1 pt 3).
 *
 * The rule is easy to state and easy to break by accident, so it is measured rather than
 * asserted in prose: switching between countries must raise [filterApplications] and leave
 * [styleBuilds] and [layerBuilds] alone. The counters are logged on every selection change, so a
 * `adb logcat -s BoundarySurface` during a drill-down session either proves the property or
 * exposes the regression immediately.
 */
internal object BoundarySurfaceMetrics {

    const val TAG = "BoundarySurface"

    private val styles = AtomicInteger()
    private val layers = AtomicInteger()
    private val filters = AtomicInteger()

    data class Snapshot(
        val styleBuilds: Int,
        val layerBuilds: Int,
        val filterApplications: Int,
    ) {
        override fun toString(): String =
            "styleBuilds=$styleBuilds layerBuilds=$layerBuilds filterApplications=$filterApplications"
    }

    fun onStyleBuilt() { styles.incrementAndGet() }

    fun onLayersBuilt() { layers.incrementAndGet() }

    fun onFiltersApplied() { filters.incrementAndGet() }

    fun snapshot(): Snapshot = Snapshot(
        styleBuilds = styles.get(),
        layerBuilds = layers.get(),
        filterApplications = filters.get(),
    )

    /** Test seam — the counters are process-wide. */
    fun reset() {
        styles.set(0)
        layers.set(0)
        filters.set(0)
    }
}
