package com.tripex.pose.domain.usecase

import com.tripex.pose.domain.settings.AppLanguage
import com.tripex.pose.domain.settings.AppLanguageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory stand-in for the language preference. */
internal class FakeAppLanguageRepository(
    language: AppLanguage = AppLanguage.DEFAULT,
) : AppLanguageRepository {

    val current = MutableStateFlow(language)

    override suspend fun selected(): AppLanguage = current.value

    override fun observe(): Flow<AppLanguage> = current

    override suspend fun set(language: AppLanguage) {
        current.value = language
    }
}
