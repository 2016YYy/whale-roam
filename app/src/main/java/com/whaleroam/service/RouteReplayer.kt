package com.whaleroam.service

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import com.whaleroam.data.Route
import com.whaleroam.data.RoutePoint
import com.whaleroam.data.SettingsStore

/**
 * 路线回放器（v1.4.0）：
 *
 * 用独立的 HandlerThread 按相邻点的 tOffsetMs 差值 ÷ speedFactor 定时把轨迹点推给
 * [MockLocationEngine]，每帧同步写入该点的 bearing / speed，
 * 让注入的 Location 字段与轨迹本身保持一致。
 *
 * 前提：MockLocationEngine.running == true。未启动模拟定位时不启动回放，
 * 直接回调 [Callback.onFinished]（调用方应先自行提示用户）。
 *
 * 线程安全：本对象是全局单例，start/stop 都可能在主线程调用；
 * stop() 在从未 start 过时调用也安全（MainActivity 停止模拟时会直接调）。
 */
object RouteReplayer {

    @Volatile var playing = false
        private set

    interface Callback {
        /** index 从 0 开始；回调发生在回放线程，UI 侧请自行切主线程 */
        fun onProgress(index: Int, total: Int)

        /** 回放结束 / 被外部条件打断（模拟定位被停止）/ 启动条件不满足 */
        fun onFinished()
    }

    private val lock = Any()

    @Volatile private var points: List<RoutePoint> = emptyList()
    @Volatile private var speedFactor = 1f
    @Volatile private var loop = false
    @Volatile private var accuracyMeters = 10f

    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var callback: Callback? = null
    private var stepRunnable: Runnable? = null
    private var index = 0

    /**
     * 开始回放。[speedFactor] 只压缩/拉长时间轴，不改变路径本身。
     * 已有回放在跑时会先停掉再重启。
     */
    fun start(context: Context, route: Route, speedFactor: Float, loop: Boolean, cb: Callback? = null) {
        stop()
        val pts = route.points
        // 前置条件：模拟定位必须已在运行，否则直接结束并回调
        if (pts.isEmpty() || !MockLocationEngine.running) {
            cb?.onFinished()
            return
        }
        this.speedFactor = speedFactor.coerceIn(0.1f, 10f)
        this.loop = loop
        this.points = pts
        this.index = 0
        // 精度沿用设置页的模拟精度，回放期间保持不变
        this.accuracyMeters = runCatching { SettingsStore(context).accuracyMeters }.getOrDefault(10f)

        val t = HandlerThread("whale-route-replay")
        t.start()
        val h = Handler(t.looper)
        val first = Runnable { step() }
        synchronized(lock) {
            thread = t
            handler = h
            callback = cb
            stepRunnable = first
            playing = true
        }
        h.post(first)
    }

    /** 立即停止并清理线程；未启动时调用安全 */
    fun stop() {
        var h: Handler? = null
        var t: HandlerThread? = null
        synchronized(lock) {
            playing = false
            h = handler
            t = thread
            handler = null
            thread = null
            stepRunnable = null
            callback = null
            index = 0
            points = emptyList()
        }
        h?.removeCallbacksAndMessages(null)
        runCatching { t?.quitSafely() }
    }

    /** 单帧：推点 → 回调进度 → 按相邻点时间差安排下一帧 */
    private fun step() {
        if (!playing) return
        if (!MockLocationEngine.running) {
            finish()
            return
        }
        val list = points
        if (list.isEmpty()) {
            finish()
            return
        }
        val i = index.coerceIn(0, list.size - 1)
        val p = list[i]
        // 先把该点的方向与速度写进引擎，再推坐标，保证同一 tick 注入的字段一致
        MockLocationEngine.currentBearingDeg = p.bearing
        MockLocationEngine.currentSpeedMps = p.speedMps
        MockLocationEngine.push(p.lat, p.lon, accuracyMeters)
        callback?.onProgress(i, list.size)

        if (i >= list.size - 1) {
            if (!loop) {
                finish()
                return
            }
            index = 0 // 循环：回到首点，下一帧立即推送
            val next = stepRunnable ?: return
            handler?.postDelayed(next, 0L)
            return
        }
        val gap = (list[i + 1].tOffsetMs - p.tOffsetMs).coerceAtLeast(0L)
        val delayMs = (gap / speedFactor).toLong().coerceAtLeast(1L)
        index = i + 1
        val next = stepRunnable ?: return
        handler?.postDelayed(next, delayMs)
    }

    /** 正常播完或被中断时的统一收尾；加锁保证 onFinished 只会回调一次 */
    private fun finish() {
        var cb: Callback? = null
        var h: Handler? = null
        var t: HandlerThread? = null
        synchronized(lock) {
            if (!playing) return
            playing = false
            cb = callback
            h = handler
            t = thread
            callback = null
            handler = null
            thread = null
            stepRunnable = null
            index = 0
            points = emptyList()
        }
        h?.removeCallbacksAndMessages(null)
        runCatching { t?.quitSafely() }
        cb?.onFinished()
    }
}