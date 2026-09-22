package com.tripex.pose.domain.geo

/**
 * Which resolution the reveal wash is drawn at, and how much of it is queried (V3.1.6).
 *
 * ### The bug this replaces
 *
 * The wash used to be built from `SELECT … ORDER BY discoveredAt DESC LIMIT 20000`. One GPS fix
 * reveals `gridDisk(k = 24)` — **1801 cells** at walking resolution — so the cap held roughly
 * **eleven fixes**, under three minutes of driving. Everything older silently vanished from the
 * map. Photographed on the road: a trail visible at 07:32 was gone by 08:32, while the city
 * circle beside it stayed, because circles live in another table with no such cap.
 *
 * Raising the limit does not fix it. After a week of walking there are millions of cells, and
 * `outline()` over millions of cells locks up the device. The unit has to change, not the number:
 * far away, cells are drawn as their **coarse parents**, of which there are orders of magnitude
 * fewer, and walking resolution is reserved for a camera close enough to tell the difference.
 *
 * A year of walking is roughly 500 000 cells at resolution 11 — but only ~200 distinct parents at
 * resolution 7. That is the whole trick.
 */
enum class FogLod(val resolution: Int) {

    /** Close in: exact cells, limited to what the camera can see. */
    Near(H3Config.WALKING_RESOLUTION),

    /** Mid range: resolution 9 parents (~170 m), still viewport-scoped. */
    Mid(H3Config.LOD_MID_RESOLUTION),

    /**
     * Far out: resolution 7 parents (~1.2 km), **the whole world**.
     *
     * Coarser than the 1 km reveal disk, so the trail looks slightly swollen at this distance.
     * That is the accepted trade: at this zoom a kilometre is a few pixels, and the alternative
     * is discovered ground that disappears with age.
     */
    Far(H3Config.LOD_FAR_RESOLUTION),
    ;

    /** [Far] is global; the others are bounded by what is on screen. */
    val isViewportScoped: Boolean get() = this != Far

    companion object {
        /**
         * Thresholds are deliberately generous: switching late costs a little fidelity, switching
         * early costs a query that can return millions of rows.
         */
        const val NEAR_ZOOM = 11.0
        const val MID_ZOOM = 8.0

        fun forZoom(zoom: Double): FogLod = when {
            zoom >= NEAR_ZOOM -> Near
            zoom >= MID_ZOOM -> Mid
            else -> Far
        }
    }
}
