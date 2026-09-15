package com.tripex.pose.di

import com.tripex.pose.core.logging.Logger
import com.tripex.pose.logging.AndroidLogger
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LoggingModule {
    @Binds
    @Singleton
    abstract fun bindLogger(impl: AndroidLogger): Logger
}
