package com.tripex.pose.data.di

import com.tripex.pose.data.repository.GeocodingRepositoryImpl
import com.tripex.pose.data.repository.UnlockedAreaRepositoryImpl
import com.tripex.pose.domain.repository.GeocodingRepository
import com.tripex.pose.domain.repository.UnlockedAreaRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindUnlockedAreaRepository(
        impl: UnlockedAreaRepositoryImpl,
    ): UnlockedAreaRepository

    @Binds
    @Singleton
    abstract fun bindGeocodingRepository(impl: GeocodingRepositoryImpl): GeocodingRepository
}
