package com.example.locationtaskreminder.di

import com.example.locationtaskreminder.data.api.GeoapifyService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    private const val BASE_URL = "https://api.geoapify.com/"

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            // Add increased timeouts for better reliability
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            // Add logging for network failures
            .addInterceptor { chain ->
                try {
                    val request = chain.request()
                    Log.d("OkHttp", "⭐ Making request to: ${request.url}")
                    val response = chain.proceed(request)
                    if (!response.isSuccessful) {
                        Log.e("OkHttp", "⭐ Request failed with code: ${response.code}")
                    }
                    response
                } catch (e: Exception) {
                    Log.e("OkHttp", "⭐ Network error: ${e.message}", e)
                    throw e
                }
            }
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideGeoapifyService(retrofit: Retrofit): GeoapifyService {
        return retrofit.create(GeoapifyService::class.java)
    }
    
    // Provide an ImageLoader configured with our custom OkHttpClient
    @Provides
    @Singleton
    fun provideImageLoader(@ApplicationContext context: Context, okHttpClient: OkHttpClient): coil.ImageLoader {
        return coil.ImageLoader.Builder(context)
            .okHttpClient(okHttpClient)
            .crossfade(true)
            .build()
    }
} 