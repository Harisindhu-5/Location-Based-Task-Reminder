package com.example.locationtaskreminder

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import coil.ImageLoader
import coil.ImageLoaderFactory

@HiltAndroidApp
class LocationTaskReminderApp : Application(), Configuration.Provider, ImageLoaderFactory {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory
    
    @Inject
    lateinit var imageLoader: ImageLoader

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
            
    override fun newImageLoader(): ImageLoader = imageLoader
    
    override fun onCreate() {
        super.onCreate()
        
        // Log API key information for debugging
        val apiKey = com.example.locationtaskreminder.BuildConfig.GEOAPIFY_API_KEY
        android.util.Log.d("App", "⭐ API Key initialized with length: ${apiKey.length}")
        
        // Check network state
        val connectivityManager = getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val networkCapabilities = connectivityManager.activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
        val hasInternet = networkCapabilities?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        
        android.util.Log.d("App", "⭐ Internet connection available: $hasInternet")
    }
} 