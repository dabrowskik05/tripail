package com.tripex.pose.data.di

import com.tripex.pose.data.geo.H3Utils
import com.tripex.pose.domain.geo.H3Converter
import com.uber.h3core.H3Core
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class H3Module {

    @Binds
    @Singleton
    abstract fun bindH3Converter(impl: H3Utils): H3Converter

    companion object {
        @Provides
        @Singleton
        fun provideH3Core(): H3Core = H3Core.newInstance()
    }
}
