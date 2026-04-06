package com.carnelia.vpn.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.carnelia.vpn.R
import com.carnelia.vpn.GeoSpoofActivity
import com.carnelia.vpn.utils.AppLogger
import com.carnelia.vpn.utils.PrefsManager
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

class GeoSpoofService : Service() {

    companion object {
        const val CHANNEL_ID = "geo_spoof_channel"
        const val NOTIFICATION_ID = 42

        const val ACTION_START = "com.carnelia.vpn.GEO_START"
        const val ACTION_STOP = "com.carnelia.vpn.GEO_STOP"
        const val ACTION_SET_POINT = "com.carnelia.vpn.GEO_SET_POINT"
        const val ACTION_SET_MOVEMENT = "com.carnelia.vpn.GEO_SET_MOVEMENT"

        const val EXTRA_LAT = "lat"
        const val EXTRA_LON = "lon"
        const val EXTRA_MOVE_ENABLED = "move_enabled"
        const val EXTRA_MOVE_SPEED = "move_speed"     // m/s
        const val EXTRA_MOVE_BEARING = "move_bearing" // degrees 0-360

        // Providers we mock
        private val PROVIDERS = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)

        var isRunning = false
            private set
    }

    private lateinit var locationManager: LocationManager
    private val handler = Handler(Looper.getMainLooper())

    private var currentLat = 0.0
    private var currentLon = 0.0
    private var movementEnabled = false
    private var moveSpeedMs = 1.4f  // ~5 km/h walking default
    private var moveBearing = 0f    // degrees

    private val publishRunnable = object : Runnable {
        override fun run() {
            if (movementEnabled) {
                advancePosition()
            }
            publishLocation(currentLat, currentLon)
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                currentLat = PrefsManager.getGeoLat(this)
                currentLon = PrefsManager.getGeoLon(this)
                movementEnabled = PrefsManager.isGeoMovementEnabled(this)
                moveSpeedMs = PrefsManager.getGeoSpeed(this)
                moveBearing = PrefsManager.getGeoBearing(this)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    try {
                        startForeground(NOTIFICATION_ID, buildNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
                    } catch (e: Exception) {
                        startForeground(NOTIFICATION_ID, buildNotification())
                    }
                } else {
                    startForeground(NOTIFICATION_ID, buildNotification())
                }
                registerProviders()
                handler.removeCallbacks(publishRunnable)
                handler.post(publishRunnable)
                isRunning = true
                AppLogger.log("GeoSpoof: Started at $currentLat, $currentLon")
            }
            ACTION_STOP -> {
                stopSelf()
            }
            ACTION_SET_POINT -> {
                val lat = intent.getDoubleExtra(EXTRA_LAT, currentLat)
                val lon = intent.getDoubleExtra(EXTRA_LON, currentLon)
                currentLat = lat
                currentLon = lon
                PrefsManager.setGeoCoords(this, lat, lon)
                AppLogger.log("GeoSpoof: Point updated to $lat, $lon")
            }
            ACTION_SET_MOVEMENT -> {
                movementEnabled = intent.getBooleanExtra(EXTRA_MOVE_ENABLED, false)
                moveSpeedMs = intent.getFloatExtra(EXTRA_MOVE_SPEED, 1.4f)
                moveBearing = intent.getFloatExtra(EXTRA_MOVE_BEARING, 0f)
                PrefsManager.setGeoMovement(this, movementEnabled, moveSpeedMs, moveBearing)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(publishRunnable)
        unregisterProviders()
        isRunning = false
        AppLogger.log("GeoSpoof: Stopped")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ==================== Location logic ====================

    private fun advancePosition() {
        // Move along bearing by speed * 1s / earth_radius in radians
        val distanceM = moveSpeedMs.toDouble()
        val earthRadius = 6_371_000.0
        val angularDistance = distanceM / earthRadius

        val bearingRad = moveBearing * PI / 180.0
        val latRad = currentLat * PI / 180.0
        val lonRad = currentLon * PI / 180.0

        val newLatRad = Math.asin(
            sin(latRad) * cos(angularDistance) +
            cos(latRad) * sin(angularDistance) * cos(bearingRad)
        )
        val newLonRad = lonRad + Math.atan2(
            sin(bearingRad) * sin(angularDistance) * cos(latRad),
            cos(angularDistance) - sin(latRad) * sin(newLatRad)
        )
        currentLat = newLatRad * 180.0 / PI
        currentLon = newLonRad * 180.0 / PI
    }

    private fun publishLocation(lat: Double, lon: Double) {
        PROVIDERS.forEach { provider ->
            try {
                val loc = Location(provider).apply {
                    latitude = lat
                    longitude = lon
                    altitude = 50.0
                    accuracy = 3.0f
                    time = System.currentTimeMillis()
                    elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                    bearing = moveBearing
                    speed = if (movementEnabled) moveSpeedMs else 0f
                }
                locationManager.setTestProviderLocation(provider, loc)
            } catch (e: Exception) {
                AppLogger.log("GeoSpoof: publish failed for $provider: ${e.message}")
            }
        }
    }

    private fun registerProviders() {
        PROVIDERS.forEach { provider ->
            try {
                locationManager.addTestProvider(
                    provider,
                    false, false, false, false, true, true, true,
                    android.location.Criteria.POWER_LOW,
                    android.location.Criteria.ACCURACY_FINE
                )
                locationManager.setTestProviderEnabled(provider, true)
            } catch (e: Exception) {
                AppLogger.log("GeoSpoof: register $provider failed: ${e.message}")
            }
        }
    }

    private fun unregisterProviders() {
        PROVIDERS.forEach { provider ->
            try {
                locationManager.removeTestProvider(provider)
            } catch (e: Exception) { /* ignore */ }
        }
    }

    // ==================== Notification ====================

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Geo Spoof", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "GPS Spoofing active"
            setShowBadge(false)
        }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, GeoSpoofActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, GeoSpoofService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GPS Spoof — Active")
            .setContentText("Lat: %.5f, Lon: %.5f".format(currentLat, currentLon))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openIntent)
            .addAction(0, "Stop", stopIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}
