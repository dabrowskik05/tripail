package com.tripex.pose.di

import com.tripex.pose.domain.location.TrackingController
import com.tripex.pose.service.TrackingServiceController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TrackingModule {

    @Binds
    @Singleton
    abstract fun bindTrackingController(
        impl: TrackingServiceController,
    ): TrackingController
}
