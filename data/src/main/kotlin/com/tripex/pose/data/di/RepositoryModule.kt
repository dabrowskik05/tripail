package com.tripex.pose.data.di

import com.tripex.pose.data.repository.AreaStatsCacheImpl
import com.tripex.pose.data.repository.MapTilerGeocodingRepository
import com.tripex.pose.data.repository.UnlockedAreaRepositoryImpl
import com.tripex.pose.data.repository.UnlockedPlaceRepositoryImpl
import com.tripex.pose.data.repository.UnlockedRegionRepositoryImpl
import com.tripex.pose.domain.repository.AreaStatsCache
import com.tripex.pose.domain.repository.GeocodingRepository
import com.tripex.pose.domain.repository.UnlockedAreaRepository
import com.tripex.pose.domain.repository.UnlockedPlaceRepository
import com.tripex.pose.domain.repository.UnlockedRegionRepository
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
    abstract fun bindGeocodingRepository(impl: MapTilerGeocodingRepository): GeocodingRepository

    @Binds
    @Singleton
    abstract fun bindAreaStatsCache(impl: AreaStatsCacheImpl): AreaStatsCache

    @Binds
    @Singleton
    abstract fun bindUnlockedRegionRepository(
        impl: UnlockedRegionRepositoryImpl,
    ): UnlockedRegionRepository

    @Binds
    @Singleton
    abstract fun bindUnlockedPlaceRepository(
        impl: UnlockedPlaceRepositoryImpl,
    ): UnlockedPlaceRepository
}
