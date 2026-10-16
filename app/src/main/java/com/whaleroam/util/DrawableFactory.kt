package com.whaleroam.util

import android.content.Context
import android.graphics.drawable.GradientDrawable

object DrawableFactory {
    fun joystickBase(context: Context): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0x551565C0.toInt())
            setStroke(4, 0xCC1565C0.toInt())
        }
    }
    fun joystickKnob(context: Context): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            colors = intArrayOf(0xFF42A5F5.toInt(), 0xFF1565C0.toInt())
            orientation = GradientDrawable.Orientation.TL_BR
        }
    }
}
