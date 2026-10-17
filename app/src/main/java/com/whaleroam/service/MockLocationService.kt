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
        /** 通知栏「停止」按钮专用（与 ACTION_STOP 行为一致，单独常量便于区分来源） */
        const val ACTION_STOP_FROM_NOTIF = "com.whaleroam.STOP_FROM_NOTIF"
        const val EXTRA_LAT = "lat"
        const val EXTRA_LON = "lon"
        const val EXTRA_ACCURACY = "accuracy"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP, ACTION_STOP_FROM_NOTIF -> {
                stopMock()
                if (intent?.action == ACTION_STOP_FROM_NOTIF) {
                    // 从通知栏停止：顺带通知 UI 侧同步按钮状态（UI 会在 onResume 时重新读取引擎状态）
                    android.widget.Toast.makeText(
                        this, getString(R.string.service_mock_stopped), android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
                return START_NOT_STICKY
            }
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
            // v1.4.0：通知栏直接停止模拟，不用再回到 App
            .addAction(0, getString(R.string.notif_action_stop), stopPendingIntent())
            .build()
    }

    /** 通知栏「停止模拟」按钮的 PendingIntent */
    private fun stopPendingIntent(): PendingIntent {
        val stopIntent = Intent(this, MockLocationService::class.java).apply {
            action = ACTION_STOP_FROM_NOTIF
        }
        return PendingIntent.getService(
            this, 2, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    override fun onDestroy() {
        if (MockLocationEngine.running) MockLocationEngine.stop(this)
        super.onDestroy()
    }
}