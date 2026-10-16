package com.whaleroam.service

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import kotlin.math.cos
import kotlin.random.Random

/**
 * 模拟定位注入引擎（对标 gogogo ServiceGo 的实现策略）：
 *
 * - GPS + NETWORK 双 Provider 覆盖，避免真实网络定位顶掉模拟值
 * - 1 秒周期循环重注（消费级 App 一般 1s 轮询一次定位，持续刷新保证任何时刻读到的都是模拟值）
 * - 完整字段伪造：accuracy / altitude / speed / bearing / time / elapsedRealtimeNanos，
 *   GPS 额外带 extras("satellites"=7)，增强被检测时的可信度
 * - 可选随机偏移：每次注入时给坐标加半径内的微抖动（防检测）
 */
object MockLocationEngine {

    private const val TAG = "WhaleRoamEngine"
    private val PROVIDERS = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
    private const val TICK_MS = 1000L

    @Volatile var running = false; private set
    @Volatile var currentLat = 0.0; private set
    @Volatile var currentLon = 0.0; private set
    /** 当前模拟速度（m/s），由摇杆档位驱动，供注入的 Location.speed 使用 */
    @Volatile var currentSpeedMps = 0f
    /** 当前模拟方向（度，正北 0 顺时针），由摇杆角度驱动，供 Location.bearing 使用 */
    @Volatile var currentBearingDeg = 0f

    /** 注入配置（随机偏移开关/半径、海拔、精度），启动时从设置读入 */
    data class InjectConfig(
        val accuracyMeters: Float = 10f,
        val altitudeMeters: Double = 55.0,
        val randomOffsetEnabled: Boolean = false,
        val offsetRadiusMeters: Float = 10f,
    )
    @Volatile private var config = InjectConfig()

    private var injectThread: HandlerThread? = null
    private var injectHandler: Handler? = null

    fun canMock(context: Context): Boolean {
        val lm = context.getSystemService(LocationManager::class.java)
        return try {
            lm.addTestProvider(LocationManager.GPS_PROVIDER, false, false, false, false, true, true, true, 1, 1)
            lm.setTestProviderEnabled(LocationManager.GPS_PROVIDER, false)
            lm.removeTestProvider(LocationManager.GPS_PROVIDER)
            true
        } catch (se: SecurityException) {
            Log.w(TAG, "not the mock-location app: ${se.message}")
            false
        } catch (t: Throwable) {
            Log.w(TAG, "canMock error: ${t.message}")
            false
        }
    }

    @SuppressLint("WrongConstant")
    fun start(context: Context, lat: Double, lon: Double, cfg: InjectConfig) {
        val lm = context.getSystemService(LocationManager::class.java)
        PROVIDERS.forEach { p ->
            runCatching { lm.removeTestProvider(p) }
            lm.addTestProvider(p, false, false, false, false, true, true, true, 1, 1)
            lm.setTestProviderEnabled(p, true)
        }
        config = cfg
        currentLat = lat
        currentLon = lon
        running = true
        pushNow()
        startInjectLoop()
    }

    /** 1 秒周期循环重注（对标 ServiceGo 的 HandlerThread 自递归循环） */
    private fun startInjectLoop() {
        stopInjectLoop()
        val thread = HandlerThread("whale-mock-inject", android.os.Process.THREAD_PRIORITY_FOREGROUND)
        thread.start()
        injectThread = thread
        val handler = Handler(thread.looper)
        injectHandler = handler
        val loop = object : Runnable {
            override fun run() {
                if (!running) return
                pushNow()
                handler.postDelayed(this, TICK_MS)
            }
        }
        handler.post(loop)
    }

    private fun stopInjectLoop() {
        injectHandler?.removeCallbacksAndMessages(null)
        injectHandler = null
        injectThread?.quitSafely()
        injectThread = null
    }

    /** 设定新目标点（热切换），立即生效 */
    @SuppressLint("WrongConstant")
    fun push(lat: Double, lon: Double, accuracyMeters: Float) {
        if (!running) return
        currentLat = lat
        currentLon = lon
        pushNow()
    }

    /** 摇杆增量移动，并回写 bearing（由调用方给出当前角度） */
    fun moveBy(dLat: Double, dLon: Double, accuracyMeters: Float, bearingDeg: Float) {
        if (!running) return
        currentBearingDeg = bearingDeg
        push(currentLat + dLat, currentLon + dLon, accuracyMeters)
    }

    /** 当前实际注入一次（带随机偏移抖动） */
    @SuppressLint("WrongConstant")
    private fun pushNow() {
        val lm = AppContext.get().getSystemService(LocationManager::class.java)
        val (lat, lon) = offsetIfEnabled(currentLat, currentLon)
        PROVIDERS.forEach { p ->
            runCatching {
                val loc = Location(p).apply {
                    latitude = lat
                    longitude = lon
                    altitude = config.altitudeMeters
                    accuracy = config.accuracyMeters.coerceAtLeast(1f)
                    speed = currentSpeedMps
                    bearing = currentBearingDeg
                    time = System.currentTimeMillis()
                    // 关键：elapsedRealtime 而非 wall clock，避免时间一致性校验暴露
                    elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                    if (p == LocationManager.GPS_PROVIDER) {
                        // 伪造卫星数，增强真实性
                        extras = Bundle().apply { putInt("satellites", 7) }
                    }
                }
                lm.setTestProviderLocation(p, loc)
            }.onFailure { Log.w(TAG, "push $p failed: ${it.message}") }
        }
    }

    /** 开启随机偏移时，每次注入在半径内抖动一次 */
    private fun offsetIfEnabled(lat: Double, lon: Double): Pair<Double, Double> {
        if (!config.randomOffsetEnabled) return Pair(lat, lon)
        val radius = config.offsetRadiusMeters.toDouble()
        val angle = Random.nextDouble(0.0, 2 * Math.PI)
        val dist = Random.nextDouble(0.0, radius)
        val dLat = dist * Math.sin(angle) / 110574.0
        val dLon = dist * Math.cos(angle) / (111320.0 * cos(Math.toRadians(lat)))
        return Pair(lat + dLat, lon + dLon)
    }

    fun stop(context: Context) {
        running = false
        stopInjectLoop()
        val lm = context.getSystemService(LocationManager::class.java)
        PROVIDERS.forEach { p ->
            runCatching {
                lm.setTestProviderEnabled(p, false)
                lm.removeTestProvider(p)
            }
        }
    }
}

object AppContext {
    private lateinit var ctx: Context
    fun attach(context: Context) { ctx = context.applicationContext }
    fun get(): Context = ctx
}
