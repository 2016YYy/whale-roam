package com.whaleroam.util

import android.content.Context
import android.graphics.drawable.GradientDrawable

object DrawableFactory {

    // ---------- 悬浮摇杆配色（与 colors.xml 的 whale_primary / whale_accent 保持一致） ----------
    private const val BASE_FILL = 0x551565C0
    private val BASE_STROKE = 0xCC1565C0.toInt()
    private const val KNOB_COLOR_LIGHT = 0xFF42A5F5.toInt()
    private const val KNOB_COLOR_DARK = 0xFF1565C0.toInt()
    /** 锁定态：内圈换成琥珀色，视觉上一眼能看出「松手也会继续走」 */
    private const val LOCKED_COLOR_LIGHT = 0xFFFFB300.toInt()
    private const val LOCKED_COLOR_DARK = 0xFFE65100.toInt()

    fun joystickBase(context: Context): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(BASE_FILL)
            setStroke(4, BASE_STROKE)
        }
    }

    fun joystickKnob(context: Context): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            colors = intArrayOf(KNOB_COLOR_LIGHT, KNOB_COLOR_DARK)
            orientation = GradientDrawable.Orientation.TL_BR
        }
    }

    /**
     * 摇杆内圈（中心小圆）：可拖动区域。
     * @param locked 锁定态为 true 时换成琥珀色并加白色描边，作为「持续移动」的视觉提示
     */
    fun joystickInner(context: Context, locked: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            if (locked) {
                colors = intArrayOf(LOCKED_COLOR_LIGHT, LOCKED_COLOR_DARK)
                setStroke(5, 0xFFFFFFFF.toInt())
            } else {
                colors = intArrayOf(KNOB_COLOR_LIGHT, KNOB_COLOR_DARK)
                setStroke(2, 0x99FFFFFF.toInt())
            }
            orientation = GradientDrawable.Orientation.TL_BR
        }
    }

    /** 顶部拖动把手：圆角小胶囊，仅作视觉提示 */
    fun joystickHandle(context: Context): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 16f
            setColor(0x991565C0.toInt())
            setStroke(2, 0xCC1565C0.toInt())
        }
    }

    /** 速度档位按钮（走 / 跑 / 骑），锁定态描边高亮 */
    fun joystickTierChip(context: Context, locked: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            colors = intArrayOf(KNOB_COLOR_LIGHT, KNOB_COLOR_DARK)
            orientation = GradientDrawable.Orientation.TL_BR
            setStroke(if (locked) 4 else 1, if (locked) 0xFFFFB300.toInt() else 0x88FFFFFF.toInt())
        }
    }

    /** 「未启动模拟」提示条：半透明红底白字 */
    fun joystickWarnChip(context: Context): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 14f
            setColor(0xCCD32F2F.toInt())
        }
    }

    /** 锁定角标：琥珀色小圆 */
    fun joystickLockBadge(context: Context): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xFFFFB300.toInt())
            setStroke(2, 0xFFFFFFFF.toInt())
        }
    }
}
