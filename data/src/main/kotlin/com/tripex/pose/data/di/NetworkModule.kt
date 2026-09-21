package com.tripex.pose.data.di

import com.tripex.pose.data.BuildConfig
import com.tripex.pose.data.network.MapTilerGeocodingApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Module
@InstallIn(SingletonComponent::class)
internal object NetworkModule {

    private const val MAPTILER_BASE_URL = "https://api.maptiler.com/"

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val userAgentValue =
            "Tripail/${BuildConfig.VERSION_NAME} (Android; portfolio MVP; contact: tripex.pose@gmail.com)"
        val userAgent = Interceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", userAgentValue)
                    .header("Accept", "application/json")
                    .build(),
            )
        }
        val builder = OkHttpClient.Builder()
            .addInterceptor(userAgent)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                },
            )
        }
        return builder.build()
    }

    @Provides
    @Singleton
    fun provideMapTilerGeocodingApi(
        client: OkHttpClient,
        json: Json,
    ): MapTilerGeocodingApi {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(MAPTILER_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(MapTilerGeocodingApi::class.java)
    }

}
