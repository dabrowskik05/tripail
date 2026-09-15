package com.tripex.pose.data.di

import com.tripex.pose.data.map.MapTilerStyleProvider
import com.tripex.pose.domain.map.MapStyleProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class MapModule {

    @Binds
    @Singleton
    abstract fun bindMapStyleProvider(impl: MapTilerStyleProvider): MapStyleProvider
}
