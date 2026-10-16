package com.whaleroam.data

import android.content.Context

class SettingsStore(context: Context) {
    private val sp = context.getSharedPreferences("whale_settings", Context.MODE_PRIVATE)

    var joystickSpeedMps: Float
        get() = sp.getFloat("joystick_speed", 5f)
        set(v) = sp.edit().putFloat("joystick_speed", v.coerceIn(0.5f, 50f)).apply()

    var accuracyMeters: Float
        get() = sp.getFloat("accuracy", 10f)
        set(v) = sp.edit().putFloat("accuracy", v.coerceIn(1f, 100f)).apply()

    /** 免责声明是否已被用户同意（首次启动必须同意才能使用） */
    var disclaimerAccepted: Boolean
        get() = sp.getBoolean("disclaimer_accepted", false)
        set(v) = sp.edit().putBoolean("disclaimer_accepted", v).apply()

    /** 最近一次自动检查更新的时间戳，用于启动时限频（默认 24h 一次） */
    var lastUpdateCheckAt: Long
        get() = sp.getLong("last_update_check_at", 0L)
        set(v) = sp.edit().putLong("last_update_check_at", v).apply()
}
