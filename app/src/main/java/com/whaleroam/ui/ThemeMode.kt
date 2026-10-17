package com.whaleroam.ui

import androidx.appcompat.app.AppCompatDelegate

/**
 * 主题模式（v1.5.0）：跟随系统 / 浅色 / 深色。
 *
 * 与 [com.whaleroam.data.SettingsStore.themeMode] 的整型值互相映射，
 * 避免把 AppCompatDelegate 的常量直接落库（常量语义可能随支持库变动）。
 */
object ThemeMode {

    const val FOLLOW_SYSTEM = 0
    const val LIGHT = 1
    const val DARK = 2

    /** 落库值 → AppCompatDelegate 夜间模式常量 */
    fun toNightMode(stored: Int): Int = when (stored) {
        LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
        DARK -> AppCompatDelegate.MODE_NIGHT_YES
        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }

    /** 三个可选值的展示顺序，与设置页单选列表一致 */
    val ALL = listOf(FOLLOW_SYSTEM, LIGHT, DARK)
}

