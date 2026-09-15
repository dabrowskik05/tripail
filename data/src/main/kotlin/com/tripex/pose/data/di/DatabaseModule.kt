package com.tripex.pose.data.di

import android.content.Context
import androidx.room.Room
import com.tripex.pose.data.local.TripexPoseDatabase
import com.tripex.pose.data.local.UnlockedHexDao
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
        ).build()

    @Provides
    fun provideUnlockedHexDao(database: TripexPoseDatabase): UnlockedHexDao =
        database.unlockedHexDao()
}
