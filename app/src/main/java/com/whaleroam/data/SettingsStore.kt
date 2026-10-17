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

    // ---------- 注入仿真（对标 gogogo ServiceGo） ----------

    /** 注入海拔（米），默认 55.0 */
    var altitudeMeters: Double
        get() = sp.getFloat("altitude", 55f).toDouble()
        set(v) = sp.edit().putFloat("altitude", v.toFloat().coerceIn(0f, 9000f)).apply()

    /** 随机偏移开关（防检测微抖动） */
    var randomOffsetEnabled: Boolean
        get() = sp.getBoolean("random_offset", false)
        set(v) = sp.edit().putBoolean("random_offset", v).apply()

    /** 随机偏移半径（米），默认 10 */
    var offsetRadiusMeters: Float
        get() = sp.getFloat("offset_radius", 10f)
        set(v) = sp.edit().putFloat("offset_radius", v.coerceIn(1f, 100f)).apply()

    // ---------- 摇杆三档速度（对标 gogogo 走/跑/骑） ----------

    var walkSpeedMps: Float
        get() = sp.getFloat("speed_walk", 1.2f)
        set(v) = sp.edit().putFloat("speed_walk", v.coerceIn(0.1f, 30f)).apply()

    var runSpeedMps: Float
        get() = sp.getFloat("speed_run", 3.6f)
        set(v) = sp.edit().putFloat("speed_run", v.coerceIn(0.1f, 30f)).apply()

    var bikeSpeedMps: Float
        get() = sp.getFloat("speed_bike", 10f)
        set(v) = sp.edit().putFloat("speed_bike", v.coerceIn(0.1f, 60f)).apply()

    /** 当前速度档位：0 走 / 1 跑 / 2 骑 */
    var speedTier: Int
        get() = sp.getInt("speed_tier", 0)
        set(v) = sp.edit().putInt("speed_tier", v.coerceIn(0, 2)).apply()

    /** 当前档位对应的速度 */
    val currentSpeedMps: Float
        get() = when (speedTier) {
            1 -> runSpeedMps
            2 -> bikeSpeedMps
            else -> walkSpeedMps
        }

    /** 历史记录有效期（天），默认 7 */
    var historyExpirationDays: Int
        get() = sp.getInt("history_expiration_days", 7)
        set(v) = sp.edit().putInt("history_expiration_days", v.coerceIn(1, 365)).apply()

    // ---------- 悬浮摇杆（v1.4.0） ----------

    /** 摇杆锁定态：锁定后松手仍按最后方向持续移动 */
    var joystickLocked: Boolean
        get() = sp.getBoolean("joystick_locked", false)
        set(v) = sp.edit().putBoolean("joystick_locked", v).apply()

    /** 悬浮摇杆窗口位置 X（屏幕像素），下次开启沿用 */
    var joystickPosX: Int
        get() = sp.getInt("joystick_pos_x", 100)
        set(v) = sp.edit().putInt("joystick_pos_x", v).apply()

    /** 悬浮摇杆窗口位置 Y（屏幕像素），下次开启沿用 */
    var joystickPosY: Int
        get() = sp.getInt("joystick_pos_y", 400)
        set(v) = sp.edit().putInt("joystick_pos_y", v).apply()

    // ---------- 路线回放（v1.4.0） ----------

    /** 回放倍速（0.5x ~ 4.0x），只影响时间间隔，不影响路径 */
    var replaySpeedFactor: Float
        get() = sp.getFloat("replay_speed_factor", 1.0f)
        set(v) = sp.edit().putFloat("replay_speed_factor", v.coerceIn(0.5f, 4.0f)).apply()

    /** 回放是否循环 */
    var replayLoop: Boolean
        get() = sp.getBoolean("replay_loop", false)
        set(v) = sp.edit().putBoolean("replay_loop", v).apply()
}
