package com.whaleroam

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.whaleroam.service.AppContext

class WhaleRoamApp : Application() {

    companion object {
        const val CHANNEL_MOCK = "whale_mock"
        const val CHANNEL_UPDATE = "whale_update"
        const val CHANNEL_JOYSTICK = "whale_joystick"
        const val NOTIF_MOCK_ID = 1001
        const val NOTIF_UPDATE_ID = 1002
        const val NOTIF_JOYSTICK_ID = 1003
    }

    override fun onCreate() {
        super.onCreate()
        AppContext.attach(this)
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(
                    CHANNEL_MOCK,
                    getString(R.string.service_mock_channel),
                    NotificationManager.IMPORTANCE_LOW
                ).apply { description = "保持模拟定位服务运行" },
                NotificationChannel(
                    CHANNEL_UPDATE,
                    getString(R.string.service_update_channel),
                    NotificationManager.IMPORTANCE_LOW
                ).apply { description = "下载应用更新" },
                NotificationChannel(
                    CHANNEL_JOYSTICK,
                    getString(R.string.service_joystick_channel),
                    NotificationManager.IMPORTANCE_LOW
                ).apply { description = "保持悬浮摇杆运行" }
            )
        )
    }
}
