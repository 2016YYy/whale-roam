package com.whaleroam.util

import android.content.Context
import android.graphics.drawable.GradientDrawable

/**
 * 各种圆角 / 圆形 Drawable 的工厂（v1.5.0 起支持主题种子色）。
 *
 * 兼容策略：
 * - 不带 seedId 的原签名全部保留，内部走默认深海蓝（seedId = 0），
 *   JoystickService 等老调用方无需改动即可编译通过；
 * - 新增带 seedId 的重载，由调用方把当前主题种子色传进来，
 *   让悬浮摇杆跟随用户选择的 8 套配色。
 *
 * seedId 越界时自动回退到默认深海蓝（见 ThemeSeed.byId）。
 */
object DrawableFactory {

    /** 默认种子色 id：深海蓝，与 ThemeSeed.DEFAULT_SEED_ID 一致 */
    const val DEFAULT_SEED = 0

    /** 锁定态：内圈换成琥珀色，视觉上一眼能看出「松手也会继续走」 */
    private const val LOCKED_COLOR_LIGHT = 0xFFFFB300.toInt()
    private const val LOCKED_COLOR_DARK = 0xFFE65100.toInt()

    // ---------- 种子色取色 ----------

    /** 当前种子色的主色（不透明） */
    fun primaryOf(context: Context, seedId: Int): Int = ThemeSeed.primaryColor(context, seedId)

    /** 当前种子色的深色变体 */
    fun darkOf(context: Context, seedId: Int): Int = ThemeSeed.darkColor(context, seedId)

    /** 当前种子色的强调色 */
    fun accentOf(context: Context, seedId: Int): Int = ThemeSeed.accentColor(context, seedId)

    /** 把不透明色按 alpha（0~255）混成半透明色 */
    fun withAlpha(color: Int, alpha: Int): Int =
        (alpha shl 24) or (color and 0x00FFFFFF)

    // ---------- 悬浮摇杆 ----------

    fun joystickBase(context: Context): GradientDrawable = joystickBase(context, DEFAULT_SEED)

    fun joystickBase(context: Context, seedId: Int): GradientDrawable {
        val primary = primaryOf(context, seedId)
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(withAlpha(primary, 0x55))
            setStroke(4, withAlpha(primary, 0xCC))
        }
    }

    fun joystickKnob(context: Context): GradientDrawable = joystickKnob(context, DEFAULT_SEED)

    fun joystickKnob(context: Context, seedId: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            colors = intArrayOf(accentOf(context, seedId), darkOf(context, seedId))
            orientation = GradientDrawable.Orientation.TL_BR
        }
    }

    /**
     * 摇杆内圈（中心小圆）：可拖动区域。
     * @param locked 锁定态为 true 时换成琥珀色并加白色描边，作为「持续移动」的视觉提示
     */
    fun joystickInner(context: Context, locked: Boolean): GradientDrawable =
        joystickInner(context, locked, DEFAULT_SEED)

    fun joystickInner(context: Context, locked: Boolean, seedId: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            if (locked) {
                colors = intArrayOf(LOCKED_COLOR_LIGHT, LOCKED_COLOR_DARK)
                setStroke(5, 0xFFFFFFFF.toInt())
            } else {
                colors = intArrayOf(accentOf(context, seedId), darkOf(context, seedId))
                setStroke(2, 0x99FFFFFF.toInt())
            }
            orientation = GradientDrawable.Orientation.TL_BR
        }
    }

    /** 顶部拖动把手：圆角小胶囊，仅作视觉提示 */
    fun joystickHandle(context: Context): GradientDrawable = joystickHandle(context, DEFAULT_SEED)

    fun joystickHandle(context: Context, seedId: Int): GradientDrawable {
        val primary = primaryOf(context, seedId)
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 16f
            setColor(withAlpha(primary, 0x99))
            setStroke(2, withAlpha(primary, 0xCC))
        }
    }

    /** 速度档位按钮（走 / 跑 / 骑），锁定态描边高亮 */
    fun joystickTierChip(context: Context, locked: Boolean): GradientDrawable =
        joystickTierChip(context, locked, DEFAULT_SEED)

    fun joystickTierChip(context: Context, locked: Boolean, seedId: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            colors = intArrayOf(accentOf(context, seedId), darkOf(context, seedId))
            orientation = GradientDrawable.Orientation.TL_BR
            setStroke(if (locked) 4 else 1, if (locked) 0xFFFFB300.toInt() else 0x88FFFFFF.toInt())
        }
    }

    /** 「未启动模拟」提示条：半透明红底白字（警示色，不随主题变） */
    fun joystickWarnChip(context: Context): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 14f
            setColor(0xCCD32F2F.toInt())
        }
    }

    /** 锁定角标：琥珀色小圆（锁定态专用色，不随主题变） */
    fun joystickLockBadge(context: Context): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xFFFFB300.toInt())
            setStroke(2, 0xFFFFFFFF.toInt())
        }
    }

    // ---------- 设置页种子色预览块（v1.5.0） ----------

    /**
     * 设置页「外观」分组里的种子色色块。
     * @param selected 选中态：加白色描边，让当前选择一目了然
     */
    fun seedSwatch(context: Context, seedId: Int, selected: Boolean): GradientDrawable {
        val primary = primaryOf(context, seedId)
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(primary)
            setStroke(if (selected) 5 else 2, if (selected) 0xFFFFFFFF.toInt() else withAlpha(primary, 0x66))
        }
    }
}
