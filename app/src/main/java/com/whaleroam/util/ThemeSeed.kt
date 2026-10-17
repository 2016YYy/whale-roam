package com.whaleroam.util

import android.app.Activity
import android.content.Context
import com.whaleroam.R

/**
 * 主题种子色（v1.5.0）。
 *
 * 8 套种子色的定义集中在 [THEMES]，与 res/values/colors.xml 中的
 * seed_<name>_primary / _dark / _accent / _bg 四组色值一一对应；
 * 切换种子色通过 [styleRes] 指定的 ThemeOverlay 应用到 Activity 主题上。
 *
 * 深浅模式不在这里管：由 AppCompatDelegate.setDefaultNightMode 统一处理，
 * values-night/ 下的同名 overlay 与基础色会自动生效。
 */
object ThemeSeed {

    /** 单套种子色的资源索引 */
    data class Seed(
        /** 稳定 id，落库用（见 SettingsStore.themeSeedId） */
        val id: Int,
        /** 展示名资源 */
        val nameRes: Int,
        /** 种子色主色（用于设置页色块预览） */
        val previewColorRes: Int,
        /** 对应的 ThemeOverlay 样式资源 */
        val styleRes: Int,
    )

    /** 8 套种子色，索引 0~7；0 为默认深海蓝（沿用历史配色，升级用户无感知） */
    val THEMES: List<Seed> = listOf(
        Seed(0, R.string.theme_seed_whale, R.color.seed_whale_primary, R.style.Theme_WhaleRoam_Seed_Whale),
        Seed(1, R.string.theme_seed_mint, R.color.seed_mint_primary, R.style.Theme_WhaleRoam_Seed_Mint),
        Seed(2, R.string.theme_seed_sunset, R.color.seed_sunset_primary, R.style.Theme_WhaleRoam_Seed_Sunset),
        Seed(3, R.string.theme_seed_sakura, R.color.seed_sakura_primary, R.style.Theme_WhaleRoam_Seed_Sakura),
        Seed(4, R.string.theme_seed_violet, R.color.seed_violet_primary, R.style.Theme_WhaleRoam_Seed_Violet),
        Seed(5, R.string.theme_seed_teal, R.color.seed_teal_primary, R.style.Theme_WhaleRoam_Seed_Teal),
        Seed(6, R.string.theme_seed_amber, R.color.seed_amber_primary, R.style.Theme_WhaleRoam_Seed_Amber),
        Seed(7, R.string.theme_seed_graphite, R.color.seed_graphite_primary, R.style.Theme_WhaleRoam_Seed_Graphite),
    )

    const val DEFAULT_SEED_ID = 0

    /** 按 id 取种子色；越界时回退到默认深海蓝，保证不会因脏数据崩溃 */
    fun byId(id: Int): Seed = THEMES.firstOrNull { it.id == id } ?: THEMES[DEFAULT_SEED_ID]

    /**
     * 把种子色覆盖层应用到 [activity] 的主题上。
     *
     * 必须在 setContentView 之前调用才能影响本次 inflate 出来的视图；
     * 已创建的视图需要 recreate() 才会重新着色。
     */
    fun applyTo(activity: Activity, seedId: Int) {
        val seed = byId(seedId)
        // ThemeOverlay 只覆盖种子色相关的几个属性，其余仍走基础主题
        activity.theme.applyStyle(seed.styleRes, true)
    }

    /** 取种子色主色，供代码里动态取色（如摇杆、通知） */
    fun primaryColor(context: Context, seedId: Int): Int {
        val seed = byId(seedId)
        return resolveColor(context, seed.previewColorRes)
    }

    /** 取种子色深色变体，供状态栏等使用 */
    fun darkColor(context: Context, seedId: Int): Int {
        val res = when (byId(seedId).id) {
            1 -> R.color.seed_mint_dark
            2 -> R.color.seed_sunset_dark
            3 -> R.color.seed_sakura_dark
            4 -> R.color.seed_violet_dark
            5 -> R.color.seed_teal_dark
            6 -> R.color.seed_amber_dark
            7 -> R.color.seed_graphite_dark
            else -> R.color.seed_whale_dark
        }
        return resolveColor(context, res)
    }

    /** 取种子色强调色，供悬浮窗等使用 */
    fun accentColor(context: Context, seedId: Int): Int {
        val res = when (byId(seedId).id) {
            1 -> R.color.seed_mint_accent
            2 -> R.color.seed_sunset_accent
            3 -> R.color.seed_sakura_accent
            4 -> R.color.seed_violet_accent
            5 -> R.color.seed_teal_accent
            6 -> R.color.seed_amber_accent
            7 -> R.color.seed_graphite_accent
            else -> R.color.seed_whale_accent
        }
        return resolveColor(context, res)
    }

    private fun resolveColor(context: Context, resId: Int): Int {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            context.getColor(resId)
        } else {
            @Suppress("DEPRECATION")
            context.resources.getColor(resId)
        }
    }
}
