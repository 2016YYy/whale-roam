package com.whaleroam.service

import android.content.Context
import android.os.SystemClock
import com.whaleroam.data.Route
import com.whaleroam.data.RoutePoint
import com.whaleroam.data.RouteStore
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 路线录制器（v1.4.0）：
 *
 * 摇杆服务每 tick（100ms）调用一次 [addPoint]，这里做二次采样：
 * - 距上一点 ≥0.5m：时间 ≥200ms 即记录（移动中，保证轨迹平滑）；
 * - 距上一点 <0.5m：时间放宽到 ≥1s 才记录（原地停留，避免刷出大量重复点）。
 *
 * 采样缓冲只存在内存里，[stopAndSave] 时才交给 [RouteStore] 落盘。
 */
object RouteRecorder {

    /** 采样间隔下限：200ms（移动中） */
    private const val MIN_INTERVAL_MS = 200L
    /** 静止时的采样间隔：1s。距离未达标时放宽到 1s，避免站着不动也刷出 5 点/秒 */
    private const val IDLE_INTERVAL_MS = 1000L
    /**
     * 采样距离下限：0.5 米。
     * 取 0.5m 是为了让步行（1.2m/s）也能每 500ms 记一个点，
     * 回放时轨迹平滑；若取 1m，步行会退化成每秒一点、1.2m 一跳，看起来一顿一顿。
     */
    private const val MIN_DISTANCE_M = 0.5
    private const val EARTH_RADIUS_M = 6371000.0

    @Volatile var recording = false
        private set

    private val lock = Any()
    private val buffer = ArrayList<RoutePoint>()
    private var startElapsed = 0L
    private var lastElapsed = 0L
    private var lastLat = 0.0
    private var lastLon = 0.0

    /** 已录制的点数（供 UI 显示「录制中 n 点」） */
    val pointCount: Int
        get() = synchronized(lock) { buffer.size }

    /** 开始录制：清空上一次的缓冲 */
    fun start() {
        synchronized(lock) {
            buffer.clear()
            startElapsed = SystemClock.elapsedRealtime()
            lastElapsed = startElapsed
            lastLat = 0.0
            lastLon = 0.0
            recording = true
        }
    }

    /**
     * 摇杆每 tick 调用。
     * 首个点无条件记录；之后按「距离 ≥0.5m 时时间 ≥200ms，否则时间 ≥1s」采样。
     */
    fun addPoint(lat: Double, lon: Double, bearing: Float, speedMps: Float) {
        if (!recording) return
        synchronized(lock) {
            if (!recording) return
            val now = SystemClock.elapsedRealtime()
            if (buffer.isNotEmpty()) {
                val dt = now - lastElapsed
                val dist = distanceMeters(lastLat, lastLon, lat, lon)
                // 距离没到 1m 说明基本在原地：把时间阈值放宽到 1s，
                // 否则静止时每 200ms 也会记一个点（10 秒就是 50 个重复点）
                val interval = if (dist < MIN_DISTANCE_M) IDLE_INTERVAL_MS else MIN_INTERVAL_MS
                if (dt < interval) return
            }
            buffer.add(RoutePoint(now - startElapsed, lat, lon, bearing, speedMps))
            lastElapsed = now
            lastLat = lat
            lastLon = lon
        }
    }

    /**
     * 停止并落库。
     * 点数 < 2（基本等于没动过）时视为无效录制：丢弃缓冲、返回 null。
     */
    fun stopAndSave(context: Context, name: String): Route? {
        val snapshot = synchronized(lock) {
            recording = false
            val copy = buffer.toList()
            buffer.clear()
            copy
        }
        if (snapshot.size < 2) return null
        return runCatching { RouteStore(context).save(name, snapshot) }.getOrNull()
    }

    /** 丢弃当前录制 */
    fun cancel() {
        synchronized(lock) {
            recording = false
            buffer.clear()
        }
    }

    /** 球面近似距离（haversine），用于 1m 采样阈值 */
    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }
}