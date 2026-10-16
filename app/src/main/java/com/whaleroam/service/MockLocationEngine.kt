package com.whaleroam.service

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import android.util.Log

object MockLocationEngine {

    private const val TAG = "WhaleRoamEngine"
    private val PROVIDERS = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)

    @Volatile var running = false; private set
    @Volatile var currentLat = 0.0; private set
    @Volatile var currentLon = 0.0; private set

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
    fun start(context: Context, lat: Double, lon: Double, accuracyMeters: Float) {
        val lm = context.getSystemService(LocationManager::class.java)
        PROVIDERS.forEach { p ->
            runCatching { lm.removeTestProvider(p) }
            lm.addTestProvider(p, false, false, false, false, true, true, true, 1, 1)
            lm.setTestProviderEnabled(p, true)
        }
        running = true
        push(lat, lon, accuracyMeters)
    }

    @SuppressLint("WrongConstant")
    fun push(lat: Double, lon: Double, accuracyMeters: Float) {
        if (!running) return
        currentLat = lat
        currentLon = lon
        val lm = AppContext.get().getSystemService(LocationManager::class.java)
        PROVIDERS.forEach { p ->
            runCatching {
                val loc = Location(p).apply {
                    latitude = lat
                    longitude = lon
                    altitude = 0.0
                    accuracy = accuracyMeters.coerceAtLeast(1f)
                    time = System.currentTimeMillis()
                    elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                }
                lm.setTestProviderLocation(p, loc)
            }.onFailure { Log.w(TAG, "push $p failed: ${it.message}") }
        }
    }

    fun moveBy(dLat: Double, dLon: Double, accuracyMeters: Float) {
        if (!running) return
        push(currentLat + dLat, currentLon + dLon, accuracyMeters)
    }

    fun stop(context: Context) {
        running = false
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
