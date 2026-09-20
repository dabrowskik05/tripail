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
        /**
         * Must use [H3Core.newSystemInstance] on Android.
         *
         * [H3Core.newInstance] unpacks `/android-arm64/libh3-java.so` from the JAR via
         * [ClassLoader.getResourceAsStream]. Modern AGP strips those embedded `.so`
         * resources from the APK, which yields:
         * `UnsatisfiedLinkError: No native resource found at /android-arm64/libh3-java.so`.
         *
         * `newSystemInstance` calls `System.loadLibrary("h3-java")`, which loads
         * `lib/arm64-v8a/libh3-java.so` from `:app` jniLibs / the h3-android AAR.
         */
        @Provides
        @Singleton
        fun provideH3Core(): H3Core = H3Core.newSystemInstance()
    }
}
