package com.tripex.pose.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.tripex.pose.data.tiles.BoundariesDataStoreFactory
import com.tripex.pose.data.tiles.BoundaryTilesProviderImpl
import com.tripex.pose.data.tiles.PmTilesBootstrapper
import com.tripex.pose.data.tiles.PmTilesBoundaryReader
import com.tripex.pose.domain.geo.atlas.BoundaryGeometrySource
import com.tripex.pose.domain.geo.atlas.BoundaryTilesProvider
import com.tripex.pose.domain.tiles.PmTilesBootstrap
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object TilesProvidesModule {
    @Provides
    @Singleton
    fun provideBoundariesDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = BoundariesDataStoreFactory.create(context)
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class TilesModule {
    @Binds
    @Singleton
    abstract fun bindPmTilesBootstrap(impl: PmTilesBootstrapper): PmTilesBootstrap

    @Binds
    @Singleton
    abstract fun bindBoundaryTilesProvider(impl: BoundaryTilesProviderImpl): BoundaryTilesProvider

    @Binds
    @Singleton
    abstract fun bindBoundaryGeometrySource(impl: PmTilesBoundaryReader): BoundaryGeometrySource
}
