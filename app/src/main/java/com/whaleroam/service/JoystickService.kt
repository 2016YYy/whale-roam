package com.whaleroam.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import com.whaleroam.data.SettingsStore
import kotlin.math.cos
import kotlin.math.hypot

class JoystickService : Service() {

    private lateinit var wm: WindowManager
    private var rootView: View? = null
    private lateinit var settings: SettingsStore

    private var knobDx = 0f
    private var knobDy = 0f
    private var moved = false
    private val ticker = Handler(Looper.getMainLooper())

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (moved) applyMove()
            ticker.postDelayed(this, TICK_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        settings = SettingsStore(this)
        showOverlay()
        ticker.postDelayed(tickRunnable, TICK_MS)
    }

    private fun showOverlay() {
        val size = (140 * resources.displayMetrics.density).toInt()
        val knobSize = (56 * resources.displayMetrics.density).toInt()

        val wmParams = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 400
        }

        val container = FrameLayout(this).apply {
            background = com.whaleroam.util.DrawableFactory.joystickBase(context)
        }
        val knob = View(this).apply {
            background = com.whaleroam.util.DrawableFactory.joystickKnob(context)
        }
        val knobLp = FrameLayout.LayoutParams(knobSize, knobSize).apply {
            gravity = Gravity.CENTER
        }
        container.addView(knob, knobLp)

        container.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val cx = size / 2f
                    val cy = size / 2f
                    var dx = e.x - cx
                    var dy = e.y - cy
                    val maxR = size / 2f
                    val len = hypot(dx, dy)
                    if (len > maxR) {
                        dx = dx / len * maxR
                        dy = dy / len * maxR
                    }
                    knobDx = dx
                    knobDy = dy
                    knob.translationX = dx
                    knob.translationY = dy
                    moved = len > 20
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    knobDx = 0f
                    knobDy = 0f
                    moved = false
                    knob.animate().translationX(0f).translationY(0f).setDuration(150).start()
                    true
                }
                else -> false
            }
        }

        rootView = container
        wm.addView(container, wmParams)
    }

    private fun applyMove() {
        if (!MockLocationEngine.running) return
        val speed = settings.joystickSpeedMps
        val dt = TICK_MS / 1000.0
        val distanceM = speed * dt
        val lat = MockLocationEngine.currentLat
        val dLat = -knobDy / (140 * resources.displayMetrics.density / 2f) * distanceM / 111320.0
        val dLon = knobDx / (140 * resources.displayMetrics.density / 2f) * distanceM /
                (111320.0 * cos(Math.toRadians(lat)))
        MockLocationEngine.moveBy(dLat, dLon, settings.accuracyMeters)
    }

    override fun onDestroy() {
        ticker.removeCallbacks(tickRunnable)
        rootView?.let { runCatching { wm.removeView(it) } }
        rootView = null
        super.onDestroy()
    }

    companion object { private const val TICK_MS = 100L }
}
