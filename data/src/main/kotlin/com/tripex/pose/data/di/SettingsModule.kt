package com.tripex.pose.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.tripex.pose.data.settings.AppLanguageDataStore
import com.tripex.pose.data.settings.SettingsDataStoreFactory
import com.tripex.pose.data.settings.SettingsPrefs
import com.tripex.pose.domain.settings.AppLanguageRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object SettingsProvidesModule {
    @Provides
    @Singleton
    @SettingsPrefs
    fun provideSettingsDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = SettingsDataStoreFactory.create(context)
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SettingsModule {
    @Binds
    @Singleton
    abstract fun bindAppLanguageRepository(impl: AppLanguageDataStore): AppLanguageRepository
}
