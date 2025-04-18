package com.example.locationtaskreminder.service

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location as AndroidLocation
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import com.example.locationtaskreminder.data.repository.GeoapifyRepository
import com.example.locationtaskreminder.model.Location
import com.google.android.gms.location.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LocationService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    @Inject
    lateinit var fusedLocationClient: FusedLocationProviderClient
    
    @Inject
    lateinit var geoapifyRepository: GeoapifyRepository

    @Inject
    lateinit var geofencingClient: GeofencingClient

    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation = _currentLocation.asStateFlow()

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "location_service_channel"
        const val GEOFENCE_NOTIFICATION_CHANNEL_ID = "geofence_alerts_channel"
        private const val NOTIFICATION_ID = 1
        private const val LOCATION_UPDATE_INTERVAL = 10000L // 10 seconds
        private const val FASTEST_LOCATION_UPDATE_INTERVAL = 5000L // 5 seconds
        private const val TAG = "LocationService"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        
        // Start foreground service immediately to avoid ForegroundServiceDidNotStartInTimeException
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Location Tracking")
            .setContentText("Tracking your location for task reminders")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

        try {
            Log.d(TAG, "⭐ DEBUG: Starting foreground service with location type")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
                Log.d(TAG, "⭐ DEBUG: Foreground service started successfully with type LOCATION")
            } else {
                startForeground(NOTIFICATION_ID, notification)
                Log.d(TAG, "⭐ DEBUG: Foreground service started successfully (pre-Q)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "⭐ DEBUG: Error starting foreground service: ${e.message}")
            e.printStackTrace()
            stopSelf()
            return
        }
        
        // Now check permissions and continue with location updates
        if (hasRequiredPermissions()) {
            startLocationUpdates()
        } else {
            Log.e(TAG, "⭐ DEBUG: Missing required permissions, stopping service")
            // We can't immediately stop the service here because we've already started as foreground
            // Instead, we'll just not start the location updates
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Check if background permission is granted (passed from MainActivity)
        val hasBackgroundPermission = intent?.getBooleanExtra("BACKGROUND_PERMISSION_GRANTED", false) ?: false
        
        if (hasBackgroundPermission) {
            Log.d(TAG, "⭐ DEBUG: Service started with background location permission")
        } else {
            Log.d(TAG, "⭐ DEBUG: Service started without background location permission - geofencing may be limited")
        }
        
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            
            // Create service channel (low importance)
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Location Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            serviceChannel.description = "Used for the ongoing location service notification"
            
            // Create geofence alerts channel (high importance)
            val alertsChannel = NotificationChannel(
                GEOFENCE_NOTIFICATION_CHANNEL_ID,
                "Task Reminders",
                NotificationManager.IMPORTANCE_HIGH
            )
            alertsChannel.description = "Notifications for location-based task reminders"
            alertsChannel.enableVibration(true)
            alertsChannel.enableLights(true)
            alertsChannel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            alertsChannel.setShowBadge(true)
            alertsChannel.setBypassDnd(true) // Important notifications should bypass Do Not Disturb
            
            // Delete existing channels first to ensure settings are updated
            notificationManager.deleteNotificationChannel(NOTIFICATION_CHANNEL_ID)
            notificationManager.deleteNotificationChannel(GEOFENCE_NOTIFICATION_CHANNEL_ID)
            
            // Create channels
            notificationManager.createNotificationChannel(serviceChannel)
            notificationManager.createNotificationChannel(alertsChannel)
            
            Log.d(TAG, "⭐ DEBUG: Notification channels created successfully")
        }
    }

    private fun startLocationUpdates() {
        if (!hasRequiredPermissions()) {
            Log.e(TAG, "⭐ DEBUG: Cannot start location updates - missing permissions")
            return
        }
        
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_UPDATE_INTERVAL)
            .setMinUpdateIntervalMillis(FASTEST_LOCATION_UPDATE_INTERVAL)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                object : LocationCallback() {
                    override fun onLocationResult(locationResult: LocationResult) {
                        super.onLocationResult(locationResult)
                        locationResult.lastLocation?.let { androidLocation ->
                            _currentLocation.value = Location(androidLocation.latitude, androidLocation.longitude)
                            serviceScope.launch {
                                // Use Geoapify for reverse geocoding
                                val response = geoapifyRepository.reverseGeocode(
                                    androidLocation.latitude,
                                    androidLocation.longitude
                                )
                                // Handle the geocoding response as needed
                            }
                        }
                    }
                },
                Looper.getMainLooper()
            )
            Log.d(TAG, "⭐ DEBUG: Location updates requested successfully")
        } catch (e: SecurityException) {
            Log.e(TAG, "⭐ DEBUG: SecurityException requesting location updates: ${e.message}")
            e.printStackTrace()
        } catch (e: Exception) {
            Log.e(TAG, "⭐ DEBUG: Exception requesting location updates: ${e.message}")
            e.printStackTrace()
        }
    }

    fun addGeofence(taskId: Long, location: Location, radius: Float) {
        val geofence = Geofence.Builder()
            .setRequestId(taskId.toString())
            .setCircularRegion(location.latitude, location.longitude, radius)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_DWELL)
            .setNotificationResponsiveness(500) // 500ms for quicker response
            .setLoiteringDelay(10000) // 10 seconds dwell time
            .build()

        val geofencingRequest = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER or GeofencingRequest.INITIAL_TRIGGER_DWELL)
            .addGeofence(geofence)
            .build()

        try {
            if (!hasRequiredPermissions()) {
                Log.e(TAG, "⭐ DEBUG: Cannot add geofence - missing permissions")
                return
            }
            
            geofencingClient.addGeofences(geofencingRequest, getPendingIntent())
                .addOnSuccessListener {
                    Log.d(TAG, "⭐ DEBUG: Geofence added successfully for task $taskId")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "⭐ DEBUG: Failed to add geofence: ${e.message}")
                    e.printStackTrace()
                }
        } catch (e: SecurityException) {
            Log.e(TAG, "⭐ DEBUG: Security exception: ${e.message}")
            e.printStackTrace()
        } catch (e: Exception) {
            Log.e(TAG, "⭐ DEBUG: Exception: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun getPendingIntent(): PendingIntent {
        val intent = Intent(this, GeofenceBroadcastReceiver::class.java)
        return PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    private fun hasRequiredPermissions(): Boolean {
        // Check for location permission
        val hasLocationPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || 
        ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        // Check for foreground service location permission on Android 14+
        val hasForegroundServiceLocationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.FOREGROUND_SERVICE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Not required for older Android versions
        }
        
        // Background permission is required for geofencing in the background
        val hasBackgroundPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Not required for Android 9 and below
        }
        
        val result = hasLocationPermission && hasForegroundServiceLocationPermission
        
        if (!result) {
            Log.e(TAG, "⭐ DEBUG: Missing permissions - Location: $hasLocationPermission, FGS Location: $hasForegroundServiceLocationPermission")
        }
        
        // Log background permission status separately
        if (!hasBackgroundPermission) {
            Log.w(TAG, "⭐ DEBUG: Missing background location permission - geofencing will only work when app is in foreground")
        }
        
        return result
    }
} 