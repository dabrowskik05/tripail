package com.tripex.pose.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.tripex.pose.data.location.DefaultFusedLocationClientProvider
import com.tripex.pose.data.location.FusedLocationClientProvider
import com.tripex.pose.data.location.FusedLocationTracker
import com.tripex.pose.data.location.TrackingDataStoreFactory
import com.tripex.pose.data.location.TrackingIntentDataStore
import com.tripex.pose.data.location.TrackingPrefs
import com.tripex.pose.data.location.TrackingSessionDataStore
import com.tripex.pose.data.location.TrackingSetupDataStore
import com.tripex.pose.domain.location.LocationTracker
import com.tripex.pose.domain.location.TrackingIntentRepository
import com.tripex.pose.domain.location.TrackingSessionRepository
import com.tripex.pose.domain.location.TrackingSetupRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object TrackingPrefsModule {
    @Provides
    @Singleton
    @TrackingPrefs
    fun provideTrackingDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = TrackingDataStoreFactory.create(context)
}

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

    @Binds
    @Singleton
    abstract fun bindTrackingIntentRepository(
        impl: TrackingIntentDataStore,
    ): TrackingIntentRepository

    @Binds
    @Singleton
    abstract fun bindTrackingSessionRepository(
        impl: TrackingSessionDataStore,
    ): TrackingSessionRepository

    @Binds
    @Singleton
    abstract fun bindTrackingSetupRepository(
        impl: TrackingSetupDataStore,
    ): TrackingSetupRepository
}
