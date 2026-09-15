package com.tripex.pose.data.di

import com.tripex.pose.data.location.DefaultFusedLocationClientProvider
import com.tripex.pose.data.location.FusedLocationClientProvider
import com.tripex.pose.data.location.FusedLocationTracker
import com.tripex.pose.domain.location.LocationTracker
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class LocationModule {

    @Binds
    @Singleton
    abstract fun bindLocationTracker(impl: FusedLocationTracker): LocationTracker

    @Binds
    @Singleton
    abstract fun bindFusedLocationClientProvider(
        impl: DefaultFusedLocationClientProvider,
    ): FusedLocationClientProvider
}
