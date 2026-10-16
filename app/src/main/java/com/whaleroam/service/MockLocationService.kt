package com.whaleroam.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.whaleroam.MainActivity
import com.whaleroam.R
import com.whaleroam.WhaleRoamApp

class MockLocationService : Service() {

    companion object {
        const val ACTION_START = "com.whaleroam.START"
        const val ACTION_STOP = "com.whaleroam.STOP"
        const val EXTRA_LAT = "lat"
        const val EXTRA_LON = "lon"
        const val EXTRA_ACCURACY = "accuracy"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopMock(); return START_NOT_STICKY }
            else -> {
                val lat = intent?.getDoubleExtra(EXTRA_LAT, 0.0) ?: 0.0
                val lon = intent?.getDoubleExtra(EXTRA_LON, 0.0) ?: 0.0
                val settings = com.whaleroam.data.SettingsStore(this)
                val cfg = MockLocationEngine.InjectConfig(
                    accuracyMeters = settings.accuracyMeters,
                    altitudeMeters = settings.altitudeMeters,
                    randomOffsetEnabled = settings.randomOffsetEnabled,
                    offsetRadiusMeters = settings.offsetRadiusMeters,
                )
                startMock(lat, lon, cfg)
            }
        }
        return START_STICKY
    }

    private fun startMock(lat: Double, lon: Double, cfg: MockLocationEngine.InjectConfig) {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(WhaleRoamApp.NOTIF_MOCK_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(WhaleRoamApp.NOTIF_MOCK_ID, notification)
        }
        runCatching { MockLocationEngine.start(this, lat, lon, cfg) }
            .onFailure { stopSelf() }
    }

    private fun stopMock() {
        MockLocationEngine.stop(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, WhaleRoamApp.CHANNEL_MOCK)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.service_mock_running))
            .setSmallIcon(R.drawable.ic_whale_notif)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
    }

    override fun onDestroy() {
        if (MockLocationEngine.running) MockLocationEngine.stop(this)
        super.onDestroy()
    }
}
