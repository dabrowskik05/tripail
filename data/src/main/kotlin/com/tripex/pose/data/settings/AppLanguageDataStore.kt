package com.tripex.pose.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.tripex.pose.core.di.IoDispatcher
import com.tripex.pose.core.logging.Logger
import com.tripex.pose.domain.settings.AppLanguage
import com.tripex.pose.domain.settings.AppLanguageRepository
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class SettingsPrefs

internal object SettingsDataStoreFactory {
    private const val NAME = "settings_prefs"

    fun create(context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile(NAME) },
        )
}

@Singleton
internal class AppLanguageDataStore
    @Inject
    constructor(
        @SettingsPrefs private val dataStore: DataStore<Preferences>,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
        private val logger: Logger,
    ) : AppLanguageRepository {

        override suspend fun selected(): AppLanguage? =
            withContext(ioDispatcher) {
                runCatching { dataStore.data.first()[LANGUAGE] }
                    .getOrElse {
                        logger.w(TAG, "Could not read language: ${it.message}")
                        null
                    }
                    ?.let(AppLanguage::fromTag)
            }

        override fun observeSelected(): Flow<AppLanguage?> =
            dataStore.data
                .catch { emit(emptyPreferences()) }
                .map { prefs -> prefs[LANGUAGE]?.let(AppLanguage::fromTag) }

        override fun observe(): Flow<AppLanguage> =
            dataStore.data
                .catch { emit(emptyPreferences()) }
                .map { AppLanguage.fromTag(it[LANGUAGE]) }

        override suspend fun set(language: AppLanguage) {
            withContext(ioDispatcher) {
                runCatching { dataStore.edit { it[LANGUAGE] = language.tag } }
                    .onFailure { logger.e(TAG, "Could not persist language", it) }
            }
        }

        private companion object {
            const val TAG = "AppLanguage"
            val LANGUAGE = stringPreferencesKey("app_language")
        }
    }
