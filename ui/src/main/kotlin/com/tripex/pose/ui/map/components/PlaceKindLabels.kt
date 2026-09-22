package com.tripex.pose.ui.map.components

import androidx.annotation.StringRes
import com.tripex.pose.domain.geo.PlaceKind
import com.tripex.pose.ui.R

/**
 * User-facing name for a geocoder category, in the interface language.
 *
 * Shared by the suggestion list and the detail sheet so the same place is never called two
 * different things, and so no enum name ever reaches the screen (vision §4).
 */
@StringRes
internal fun PlaceKind.labelRes(): Int = when (this) {
    PlaceKind.Country -> R.string.place_kind_country
    PlaceKind.Region -> R.string.place_kind_region
    PlaceKind.City -> R.string.place_kind_city
    PlaceKind.Town -> R.string.place_kind_town
    PlaceKind.Municipality -> R.string.place_kind_municipality
    PlaceKind.Village -> R.string.place_kind_village
    PlaceKind.Address -> R.string.place_kind_address
    PlaceKind.Poi, PlaceKind.Unknown -> R.string.place_kind_poi
}
