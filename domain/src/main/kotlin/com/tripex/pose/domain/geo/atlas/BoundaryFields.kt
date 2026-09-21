package com.tripex.pose.domain.geo.atlas

/**
 * Single source of truth for PMTiles / MapLibre boundary feature property names.
 * Literals must not be duplicated elsewhere.
 */
object BoundaryFields {
    const val ADM0_ID = "iso_a2"
    const val ADM0_NAME = "name"
    const val ADM0_NAME_PL = "name_pl"
    const val ADM0_CONTINENT = "continent"
    const val ADM1_ID = "adm1_code"
    const val ADM1_COUNTRY = "iso_a2"
    const val ADM1_NAME = "name"
    const val ADM1_NAME_PL = "name_pl"
    const val LAYER_ADM0 = "adm0"
    const val LAYER_ADM1 = "adm1"
}
