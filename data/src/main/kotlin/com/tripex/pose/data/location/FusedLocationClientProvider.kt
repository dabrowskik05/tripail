package com.tripex.pose.data.location

import android.content.Context
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

internal fun interface FusedLocationClientProvider {
    fun get(): FusedLocationProviderClient
}

@Singleton
internal class DefaultFusedLocationClientProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : FusedLocationClientProvider {
    override fun get(): FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
}
