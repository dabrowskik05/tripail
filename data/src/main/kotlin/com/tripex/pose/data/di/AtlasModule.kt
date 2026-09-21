package com.tripex.pose.data.di

import android.content.Context
import android.content.res.AssetManager
import com.tripex.pose.data.atlas.AtlasRepositoryImpl
import com.tripex.pose.data.atlas.GeoJsonAtlasParser
import com.tripex.pose.domain.geo.atlas.AtlasRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object AtlasProvidesModule {
    @Provides
    @Singleton
    fun provideAssetManager(@ApplicationContext context: Context): AssetManager = context.assets

    @Provides
    @Singleton
    fun provideGeoJsonAtlasParser(): GeoJsonAtlasParser = GeoJsonAtlasParser()
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class AtlasModule {
    @Binds
    @Singleton
    abstract fun bindAtlasRepository(impl: AtlasRepositoryImpl): AtlasRepository
}
