package com.whaleroam.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.whaleroam.MainActivity
import com.whaleroam.R
import com.whaleroam.WhaleRoamApp
import com.whaleroam.data.SettingsStore
import com.whaleroam.util.DrawableFactory
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min

/**
 * 悬浮摇杆前台服务（v1.4.0）
 *
 * 交互设计（与 gogogo 的三档速度摇杆对齐，另加无级调速）：
 * - 内圈：推杆移动（推杆量归一化 0~1，实际速度 = 当前档位速度 × 推杆量），单击切换锁定态
 * - 外圈：拖动整个悬浮窗（长按 400ms 后进入拖动模式，或直接拖动），松手写回设置
 * - 右上角把手：拖动悬浮窗的显式入口
 *
 * 生命周期：onCreate 里立即 startForeground（Android 8+ 前台服务规范），
 * 通知栏提供「关闭摇杆」Action（action = [ACTION_CLOSE]），收到后 stopSelf()。
 */
class JoystickService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var settings: SettingsStore

    /**
     * 当前主题种子色 id（v1.5.0）：从设置读取，悬浮窗配色跟随用户选择的 8 套主题。
     * 服务存活期间主题不会变，因此只在 showOverlay 时取一次即可。
     */
    private var seedId = 0
    private var rootView: FrameLayout? = null
    private var wmParams: WindowManager.LayoutParams? = null

    // ---------- 摇杆状态 ----------

    /** 摇杆偏移（像素），已按最大半径截断，用于绘制与位移换算 */
    private var knobDx = 0f
    private var knobDy = 0f

    /** 归一化推杆量 r = min(len / maxR, 1.0)，无级调速用；0 表示不移动 */
    private var pushRatio = 0f

    /** 是否处于「有方向输入」状态；锁定态下松手保持 true */
    private var moved = false

    /** 锁定态：锁定后松手不清零偏移、不停止移动 */
    private var locked = false

    // ---------- 视图 ----------

    private var knob: FrameLayout? = null
    private var inner: TextView? = null
    private var tierButton: TextView? = null
    private var warnChip: TextView? = null
    private var lockBadge: TextView? = null

    // ---------- 尺寸 ----------

    private var sizePx = 0
    private var innerHitRadiusPx = 0f
    private var touchSlopPx = 0

    // ---------- 手势 ----------

    /** 本次按下是否落在内圈（推杆/锁定）区域，false 表示外圈（拖动窗口） */
    private var innerZone = false
    private var dragMode = false
    private var downRawX = 0f
    private var downRawY = 0f
    private var downX = 0f
    private var downY = 0f
    private var dragStartX = 0
    private var dragStartY = 0

    private var lastWarnAt = 0L
    private var lastMockRunning: Boolean? = null

    private val ticker = Handler(Looper.getMainLooper())

    private val tierLabels: Array<String> by lazy {
        arrayOf(
            getString(R.string.js_tier_walk),
            getString(R.string.js_tier_run),
            getString(R.string.js_tier_bike)
        )
    }

    /** 长按 400ms 进入拖动模式：外圈按住不放后，即使轻微移动也拖窗口而不是推杆 */
    private val dragLongPressRunnable = Runnable { dragMode = true }

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (moved) applyMove()
            syncMockState()
            ticker.postDelayed(this, TICK_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CLOSE) {
            // 通知栏「关闭摇杆」：停掉自己，onDestroy 负责清理悬浮窗 / ticker / 前台通知
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        // Android 8+ 要求 startForegroundService 启动的服务必须及时调用 startForeground，
        // 否则会抛 ForegroundServiceDidNotStartInTimeException 导致崩溃。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                WhaleRoamApp.NOTIF_JOYSTICK_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(WhaleRoamApp.NOTIF_JOYSTICK_ID, buildNotification())
        }
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        settings = SettingsStore(this)
        // 锁定态不跨服务重启恢复（Lead 决策）：
        // 锁定是"松手后按当前方向继续走"的瞬时模式，服务重启后推杆偏移为 0、方向未知，
        // 若恢复成锁定态会出现「界面显示已锁定但摇杆不动」的矛盾状态，比不恢复更困惑。
        // 因此每次启动摇杆一律从未锁定开始，并把设置同步回去。
        locked = false
        settings.joystickLocked = false
        showOverlay()
        ticker.postDelayed(tickRunnable, TICK_MS)
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        // 「关闭摇杆」Action：PendingIntent 指向服务自身，action = ACTION_CLOSE
        val closeIntent = PendingIntent.getService(
            this, 1,
            Intent(this, JoystickService::class.java).setAction(ACTION_CLOSE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, WhaleRoamApp.CHANNEL_JOYSTICK)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.service_joystick_running))
            .setSmallIcon(R.drawable.ic_whale_notif)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .addAction(R.drawable.ic_stop, getString(R.string.js_notif_close), closeIntent)
            .build()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun showOverlay() {
        // 主题种子色：决定摇杆配色，越界时 ThemeSeed.byId 自动回退深海蓝
        seedId = settings.themeSeedId
        sizePx = dp(140)
        val knobSize = dp(56)
        val innerSize = dp(40)
        // 内圈视觉直径 40dp，命中半径给到 28dp（略放宽，方便点中），外圈仍留足拖动区
        innerHitRadiusPx = dp(28).toFloat()
        touchSlopPx = ViewConfiguration.get(this).scaledTouchSlop

        // 位置从设置读取，实现「松手记住位置，下次开启沿用」
        wmParams = WindowManager.LayoutParams(
            sizePx, sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // 恢复上次位置时做一次钳制：换分辨率/旋转屏幕后旧坐标可能完全在屏幕外
            val metrics = resources.displayMetrics
            val keep = dp(40)
            x = settings.joystickPosX.coerceIn(-sizePx + keep, (metrics.widthPixels - keep).coerceAtLeast(0))
            y = settings.joystickPosY.coerceIn(-sizePx + keep, (metrics.heightPixels - keep).coerceAtLeast(0))
        }

        val container = FrameLayout(this).apply {
            background = DrawableFactory.joystickBase(context, seedId)
        }

        // 摇杆内圈（可推杆 + 单击锁定）：整体作为「旋钮」随手指平移
        val knobView = FrameLayout(this).apply {
            background = DrawableFactory.joystickKnob(context, seedId)
        }
        val innerView = TextView(this).apply {
            text = ""
            textSize = 13f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            isClickable = false
            isFocusable = false
            contentDescription = getString(R.string.js_cd_lock)
            background = DrawableFactory.joystickInner(context, false, seedId)
        }
        knobView.addView(
            innerView,
            FrameLayout.LayoutParams(innerSize, innerSize).apply { gravity = Gravity.CENTER }
        )
        container.addView(
            knobView,
            FrameLayout.LayoutParams(knobSize, knobSize).apply { gravity = Gravity.CENTER }
        )
        knob = knobView
        inner = innerView

        // 速度档位切换按钮（走/跑/骑，对标 gogogo 三档速度键）：置于摇杆顶部
        val tier = TextView(this).apply {
            text = tierLabels[settings.speedTier]
            textSize = 12f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = DrawableFactory.joystickTierChip(context, false, seedId)
            setOnClickListener {
                val next = (settings.speedTier + 1) % 3
                settings.speedTier = next
                text = tierLabels[next]
                // 档位变化立即回写引擎速度（乘上当前推杆量），下一个注入 tick 生效
                MockLocationEngine.currentSpeedMps =
                    if (moved) settings.currentSpeedMps * pushRatio else 0f
            }
        }
        container.addView(
            tier,
            FrameLayout.LayoutParams(dp(36), dp(36)).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = dp(6)
            }
        )
        tierButton = tier

        // 右上角拖动把手：显式入口，避免用户不知道外圈能拖窗口
        val handle = TextView(this).apply {
            text = getString(R.string.js_drag_handle)
            textSize = 11f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = DrawableFactory.joystickHandle(context, seedId)
            contentDescription = getString(R.string.js_cd_drag)
            setOnTouchListener { _, e -> handleDragTouch(e) }
        }
        container.addView(
            handle,
            FrameLayout.LayoutParams(dp(30), dp(18)).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = dp(2)
                marginEnd = dp(2)
            }
        )

        // 「未启动模拟」提示条：模拟未运行时显示，明确告诉用户先启动模拟（修复：开了没反应）
        val warn = TextView(this).apply {
            text = getString(R.string.js_need_mock_short)
            textSize = 8f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(1), dp(4), dp(1))
            background = DrawableFactory.joystickWarnChip(context)
            visibility = if (MockLocationEngine.running) View.GONE else View.VISIBLE
        }
        container.addView(
            warn,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                topMargin = dp(2)
                marginStart = dp(2)
            }
        )
        warnChip = warn

        // 锁定角标（右下角），锁定态更醒目
        val badge = TextView(this).apply {
            text = getString(R.string.js_lock_mark)
            textSize = 9f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = DrawableFactory.joystickLockBadge(context)
            visibility = View.GONE
        }
        container.addView(
            badge,
            FrameLayout.LayoutParams(dp(18), dp(18)).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                bottomMargin = dp(4)
                marginEnd = dp(4)
            }
        )
        lockBadge = badge

        container.setOnTouchListener { _, e -> stickTouch(e) }

        val lp = wmParams ?: return
        // 悬浮窗权限可能在服务运行期间被撤销，addView 会抛 BadTokenException，这里兜住并自停
        try {
            wm.addView(container, lp)
        } catch (t: Throwable) {
            rootView = null
            stopSelf()
            return
        }
        rootView = container
        updateLockVisual()
        syncMockState()
    }

    // ---------- 摇杆手势 ----------

    private fun stickTouch(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = e.rawX
                downRawY = e.rawY
                downX = e.x
                downY = e.y
                dragMode = false
                dragStartX = wmParams?.x ?: 0
                dragStartY = wmParams?.y ?: 0
                innerZone = isInnerZone(e.x, e.y)
                if (innerZone) {
                    // 内圈：准备推杆 / 单击锁定；按下时先不动摇杆，避免轻点就位移
                    if (!MockLocationEngine.running) warnMockNotRunning()
                } else {
                    // 外圈：长按 400ms 进入拖动模式
                    ticker.postDelayed(dragLongPressRunnable, DRAG_LONG_PRESS_MS)
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (dragMode) {
                    moveWindowBy(e.rawX - downRawX, e.rawY - downRawY)
                    return true
                }
                val movedFromDown = hypot(e.x - downX, e.y - downY)
                if (!innerZone) {
                    // 外圈：超过触摸阈值即拖动窗口（与推杆手势区分开）
                    if (movedFromDown > touchSlopPx) {
                        ticker.removeCallbacks(dragLongPressRunnable)
                        dragMode = true
                        moveWindowBy(e.rawX - downRawX, e.rawY - downRawY)
                    }
                } else {
                    pushStick(e.x, e.y)
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                ticker.removeCallbacks(dragLongPressRunnable)
                if (dragMode) {
                    endDrag()
                } else if (innerZone) {
                    val isTap = hypot(e.x - downX, e.y - downY) <= touchSlopPx
                    if (isTap) {
                        // 轻点：先复位推杆状态，避免手指微动被当成推杆方向
                        resetStick(animate = true)
                        toggleLock()
                    } else {
                        releaseStick()
                    }
                }
                dragMode = false
                innerZone = false
                return true
            }
        }
        return false
    }

    private fun handleDragTouch(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = e.rawX
                downRawY = e.rawY
                dragStartX = wmParams?.x ?: 0
                dragStartY = wmParams?.y ?: 0
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                moveWindowBy(e.rawX - downRawX, e.rawY - downRawY)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                endDrag()
                return true
            }
        }
        return false
    }

    private fun isInnerZone(x: Float, y: Float): Boolean {
        // 判定中心跟随旋钮当前位置：锁定后旋钮会停在偏移处，
        // 若仍按容器中心判定，用户点可见的内圈会被误判成外圈拖动。
        val cx = sizePx / 2f + knobDx
        val cy = sizePx / 2f + knobDy
        return hypot(x - cx, y - cy) <= innerHitRadiusPx
    }

    /** 推杆：计算归一化推杆量并平移旋钮 */
    private fun pushStick(x: Float, y: Float) {
        val cx = sizePx / 2f
        val cy = sizePx / 2f
        val rawDx = x - cx
        val rawDy = y - cy
        val maxR = sizePx / 2f
        val len = hypot(rawDx, rawDy)
        val dx: Float
        val dy: Float
        if (len > maxR && len > 0f) {
            dx = rawDx / len * maxR
            dy = rawDy / len * maxR
        } else {
            dx = rawDx
            dy = rawDy
        }
        knobDx = dx
        knobDy = dy
        // 无级调速：r = min(len / maxR, 1.0)，r 为 0 时不移动
        pushRatio = min(len / maxR, 1f)
        moved = pushRatio > MIN_PUSH_RATIO
        // 推杆量低于阈值视为无输入：同步把注入速度归零，
        // 避免出现「位置静止但 speed 字段还留着上一次非零值」的情况
        if (!moved) MockLocationEngine.currentSpeedMps = 0f
        knob?.translationX = dx
        knob?.translationY = dy
    }

    /** 松手：锁定态保持偏移持续移动；未锁定则回中并停止 */
    private fun releaseStick() {
        if (locked) {
            // 锁定：不清零 knobDx/knobDy、不置 moved=false，保持持续移动
            return
        }
        resetStick(animate = true)
    }

    /** 复位推杆状态（回中 + 停止移动 + 速度归零），锁定态下也会无条件复位 */
    private fun resetStick(animate: Boolean) {
        knobDx = 0f
        knobDy = 0f
        pushRatio = 0f
        moved = false
        // 修复：松手时注入速度归零，避免位置静止但 speed 字段残留上一次非零值
        MockLocationEngine.currentSpeedMps = 0f
        if (animate) {
            knob?.animate()?.translationX(0f)?.translationY(0f)?.setDuration(150)?.start()
        } else {
            knob?.translationX = 0f
            knob?.translationY = 0f
        }
    }

    /** 点击内圈：切换锁定态并回写设置 */
    private fun toggleLock() {
        locked = !locked
        settings.joystickLocked = locked
        updateLockVisual()
        if (locked) {
            // 锁定：保留当前偏移，松手后继续按最后方向移动
            Toast.makeText(this, R.string.js_lock_on, Toast.LENGTH_SHORT).show()
        } else {
            // 解锁：回中并停止移动
            resetStick(animate = true)
            Toast.makeText(this, R.string.js_lock_off, Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateLockVisual() {
        inner?.background = DrawableFactory.joystickInner(this, locked, seedId)
        inner?.text = if (locked) getString(R.string.js_lock_mark) else ""
        tierButton?.background = DrawableFactory.joystickTierChip(this, locked, seedId)
        lockBadge?.visibility = if (locked) View.VISIBLE else View.GONE
    }

    // ---------- 窗口拖动 ----------

    private fun moveWindowBy(dx: Float, dy: Float) {
        val lp = wmParams ?: return
        val v = rootView ?: return
        // 边界钳制：至少保留 40dp 可见，避免拖出屏幕后再也找不回摇杆
        val metrics = resources.displayMetrics
        val keep = dp(40)
        val maxX = (metrics.widthPixels - keep).coerceAtLeast(0)
        val maxY = (metrics.heightPixels - keep).coerceAtLeast(0)
        lp.x = (dragStartX + dx.toInt()).coerceIn(-sizePx + keep, maxX)
        lp.y = (dragStartY + dy.toInt()).coerceIn(-sizePx + keep, maxY)
        runCatching { wm.updateViewLayout(v, lp) }
    }

    private fun endDrag() {
        val lp = wmParams ?: return
        // 松手后记住位置，下次开启沿用
        settings.joystickPosX = lp.x
        settings.joystickPosY = lp.y
    }

    // ---------- 模拟状态提示 ----------

    /** 模拟未运行时给视觉提示（半透明摇杆 + 「未启动模拟」小字），让用户知道要先启动模拟 */
    private fun syncMockState() {
        val running = MockLocationEngine.running
        if (running == lastMockRunning) return
        lastMockRunning = running
        warnChip?.visibility = if (running) View.GONE else View.VISIBLE
        knob?.alpha = if (running) 1f else 0.35f
    }

    private fun warnMockNotRunning() {
        val now = System.currentTimeMillis()
        if (now - lastWarnAt < 5000L) return
        lastWarnAt = now
        Toast.makeText(this, R.string.js_need_mock_toast, Toast.LENGTH_SHORT).show()
    }

    // ---------- 移动 ----------

    private fun applyMove() {
        if (!MockLocationEngine.running) return
        // 无级调速：实际速度 = 当前档位速度 × 推杆量
        val ratio = pushRatio.coerceIn(0f, 1f)
        if (ratio <= 0f) return
        val speed = settings.currentSpeedMps * ratio
        MockLocationEngine.currentSpeedMps = speed
        val dt = TICK_MS / 1000.0
        val distanceM = speed * dt
        val lat = MockLocationEngine.currentLat
        val maxR = sizePx / 2f
        val dLat = -knobDy / maxR * distanceM / 111320.0
        val dLon = knobDx / maxR * distanceM / (111320.0 * cos(Math.toRadians(lat)))
        // 屏幕向量转地理方位角：正北 0° 顺时针（对标 gogogo bearing = 90 - angle）
        val bearing =
            ((Math.toDegrees(kotlin.math.atan2(knobDx.toDouble(), -knobDy.toDouble())) + 360.0) % 360.0).toFloat()
        MockLocationEngine.moveBy(dLat, dLon, settings.accuracyMeters, bearing)
        // v1.4.0 路线录制钩子：每 tick 移动后，若正在录制则追加采样点
        // （RouteRecorder 内部按「时间 ≥200ms 或距离 ≥1m」采样，这里直接喂原始点）
        if (RouteRecorder.recording) {
            RouteRecorder.addPoint(
                MockLocationEngine.currentLat,
                MockLocationEngine.currentLon,
                bearing,
                speed
            )
        }
    }

    override fun onDestroy() {
        ticker.removeCallbacks(tickRunnable)
        ticker.removeCallbacks(dragLongPressRunnable)
        // 退出时把注入速度归零，避免摇杆关掉后 speed 字段仍残留
        MockLocationEngine.currentSpeedMps = 0f
        val view = rootView
        rootView = null
        if (view != null && ::wm.isInitialized) {
            runCatching { wm.removeView(view) }
        }
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        // 集成修复：用户可能从通知栏「关闭摇杆」关掉服务，此时 MainActivity 的
        // JoystickToggle.visible 还停在 true，主界面会错误地显示"关闭悬浮摇杆"。
        // 这里在销毁时同步回 false，让主界面下次 onResume 时状态正确。
        com.whaleroam.JoystickToggle.visible = false
        super.onDestroy()
    }

    companion object {
        private const val TICK_MS = 100L

        /** 长按外圈进入拖动模式的判定时长 */
        private const val DRAG_LONG_PRESS_MS = 400L

        /** 小于该推杆量视为无输入（防止手指轻微抖动导致漂移） */
        private const val MIN_PUSH_RATIO = 0.02f

        /** 通知栏「关闭摇杆」Action（契约约定字符串，勿改） */
        const val ACTION_CLOSE = "com.whaleroam.JOYSTICK_CLOSE"
    }
}