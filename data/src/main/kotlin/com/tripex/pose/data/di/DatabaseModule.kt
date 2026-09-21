package com.tripex.pose.data.di

import android.content.Context
import androidx.room.Room
import com.tripex.pose.data.local.AreaStatsDao
import com.tripex.pose.data.local.GeocodeCacheDao
import com.tripex.pose.data.local.Migrations
import com.tripex.pose.data.local.TripexPoseDatabase
import com.tripex.pose.data.local.UnlockedHexDao
import com.tripex.pose.data.local.UnlockedPlaceDao
import com.tripex.pose.data.local.UnlockedRegionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): TripexPoseDatabase =
        Room.databaseBuilder(
            context,
            TripexPoseDatabase::class.java,
            "tripex_pose.db",
        ).addMigrations(*Migrations.ALL)
            .build()

    @Provides
    fun provideUnlockedHexDao(database: TripexPoseDatabase): UnlockedHexDao =
        database.unlockedHexDao()

    @Provides
    fun provideAreaStatsDao(database: TripexPoseDatabase): AreaStatsDao =
        database.areaStatsDao()

    @Provides
    fun provideUnlockedRegionDao(database: TripexPoseDatabase): UnlockedRegionDao =
        database.unlockedRegionDao()

    @Provides
    fun provideUnlockedPlaceDao(database: TripexPoseDatabase): UnlockedPlaceDao =
        database.unlockedPlaceDao()

    @Provides
    fun provideGeocodeCacheDao(database: TripexPoseDatabase): GeocodeCacheDao =
        database.geocodeCacheDao()
}
