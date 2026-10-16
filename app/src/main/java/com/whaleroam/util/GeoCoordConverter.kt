package com.whaleroam.util

/**
 * 常用坐标系互相转换工具：WGS-84（GPS 原始）/ GCJ-02（国测局火星坐标）/ BD-09（百度坐标）。
 *
 * 算法为业界通用的公开近似公式（偏差通常在 1~2 米内），仅用于坐标输入换算，
 * 不涉及任何第三方专有代码。
 */
object GeoCoordConverter {

    private const val PI = Math.PI
    private const val X_PI = PI * 3000.0 / 180.0
    private const val A = 6378245.0 // 长半轴
    private const val EE = 0.00669342162296594323 // 偏心率平方

    enum class CoordSystem { WGS84, GCJ02, BD09 }

    /** 将任一坐标系转换为 WGS-84（应用内部与 OSM 地图统一使用 WGS-84） */
    fun toWgs84(lat: Double, lon: Double, from: CoordSystem): Pair<Double, Double> {
        return when (from) {
            CoordSystem.WGS84 -> Pair(lat, lon)
            CoordSystem.GCJ02 -> gcj02ToWgs84(lat, lon)
            CoordSystem.BD09 -> {
                val (gLat, gLon) = bd09ToGcj02(lat, lon)
                gcj02ToWgs84(gLat, gLon)
            }
        }
    }

    // ---------- BD-09 <-> GCJ-02 ----------

    fun bd09ToGcj02(bdLat: Double, bdLon: Double): Pair<Double, Double> {
        val x = bdLon - 0.0065
        val y = bdLat - 0.006
        val z = Math.sqrt(x * x + y * y) - 0.00002 * Math.sin(y * X_PI)
        val theta = Math.atan2(y, x) - 0.000003 * Math.cos(x * X_PI)
        val ggLon = z * Math.cos(theta)
        val ggLat = z * Math.sin(theta)
        return Pair(ggLat, ggLon)
    }

    fun gcj02ToBd09(gcjLat: Double, gcjLon: Double): Pair<Double, Double> {
        val z = Math.sqrt(gcjLon * gcjLon + gcjLat * gcjLat) + 0.00002 * Math.sin(gcjLat * X_PI)
        val theta = Math.atan2(gcjLat, gcjLon) + 0.000003 * Math.cos(gcjLon * X_PI)
        val bdLon = z * Math.cos(theta) + 0.0065
        val bdLat = z * Math.sin(theta) + 0.006
        return Pair(bdLat, bdLon)
    }

    // ---------- GCJ-02 <-> WGS-84 ----------

    fun wgs84ToGcj02(wgsLat: Double, wgsLon: Double): Pair<Double, Double> {
        if (outOfChina(wgsLat, wgsLon)) return Pair(wgsLat, wgsLon)
        val (dLat, dLon) = transformDelta(wgsLat, wgsLon)
        return Pair(wgsLat + dLat, wgsLon + dLon)
    }

    fun gcj02ToWgs84(gcjLat: Double, gcjLon: Double): Pair<Double, Double> {
        if (outOfChina(gcjLat, gcjLon)) return Pair(gcjLat, gcjLon)
        val (dLat, dLon) = transformDelta(gcjLat, gcjLon)
        // 一次迭代即可达到米级精度
        return Pair(gcjLat - dLat, gcjLon - dLon)
    }

    private fun transformDelta(lat: Double, lon: Double): Pair<Double, Double> {
        var dLat = transformLat(lon - 105.0, lat - 35.0)
        var dLon = transformLon(lon - 105.0, lat - 35.0)
        val radLat = lat / 180.0 * PI
        var magic = Math.sin(radLat)
        magic = 1 - EE * magic * magic
        val sqrtMagic = Math.sqrt(magic)
        dLat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * PI)
        dLon = (dLon * 180.0) / (A / sqrtMagic * Math.cos(radLat) * PI)
        return Pair(dLat, dLon)
    }

    private fun transformLat(x: Double, y: Double): Double {
        var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x))
        ret += (20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * Math.sin(y * PI) + 40.0 * Math.sin(y / 3.0 * PI)) * 2.0 / 3.0
        ret += (160.0 * Math.sin(y / 12.0 * PI) + 320.0 * Math.sin(y * PI / 30.0)) * 2.0 / 3.0
        return ret
    }

    private fun transformLon(x: Double, y: Double): Double {
        var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x))
        ret += (20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * Math.sin(x * PI) + 40.0 * Math.sin(x / 3.0 * PI)) * 2.0 / 3.0
        ret += (150.0 * Math.sin(x / 12.0 * PI) + 300.0 * Math.sin(x / 30.0 * PI)) * 2.0 / 3.0
        return ret
    }

    private fun outOfChina(lat: Double, lon: Double): Boolean {
        return lon < 72.004 || lon > 137.8347 || lat < 0.8293 || lat > 55.8271
    }
}
