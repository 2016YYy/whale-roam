package com.whaleroam

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import com.whaleroam.data.SettingsStore
import com.whaleroam.service.AppContext
import com.whaleroam.ui.ThemeMode

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
        // v1.5.0：主题模式要在任何 Activity 创建前生效，
        // 否则冷启动时会先按默认（跟随系统）渲染一帧再切换，出现闪烁。
        // 放在 Application.onCreate 里是最早的时机。
        applyThemeMode()
        createChannels()
    }

    /** 把用户选择的主题模式（跟随系统 / 浅色 / 深色）应用到全局 */
    private fun applyThemeMode() {
        val mode = SettingsStore(this).themeMode
        AppCompatDelegate.setDefaultNightMode(ThemeMode.toNightMode(mode))
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
