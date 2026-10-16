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
}
