package com.whaleroam.util

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Nominatim（OpenStreetMap）地理编码客户端：
 * - [search]    地名 -> 坐标（正向地理编码）
 * - [reverse]   坐标 -> 详细地址（逆向地理编码，长按地图查询用）
 *
 * 免 API Key，遵循 Nominatim 公共服务使用政策（自定义 UA、低频调用）。
 * 回调均在后台线程触发，调用方需自行切回主线程。
 */
object NominatimClient {

    private const val BASE = "https://nominatim.openstreetmap.org"
    private const val UA = "WhaleRoam/1.2 (open-source mock location tool; https://github.com/2016YYy/whale-roam)"
    private const val TIMEOUT_MS = 10000

    data class SearchResult(val lat: Double, val lon: Double, val displayName: String)

    interface Callback<T> {
        fun onSuccess(result: T)
        fun onError(msg: String)
    }

    /** 地名/地址搜索，返回最匹配的一条 */
    fun search(query: String, cb: Callback<SearchResult>) {
        Thread({
            var conn: HttpURLConnection? = null
            try {
                val url = "$BASE/search?format=json&limit=1&accept-language=zh-CN&q=" +
                        URLEncoder.encode(query, "UTF-8")
                conn = open(url)
                val arr = JSONArray(readBody(conn))
                if (arr.length() == 0) {
                    cb.onError(""); // 空结果用空 msg 区分「未找到」
                } else {
                    val o = arr.getJSONObject(0)
                    cb.onSuccess(
                        SearchResult(
                            lat = o.getDouble("lat"),
                            lon = o.getDouble("lon"),
                            displayName = o.optString("display_name", "")
                        )
                    )
                }
            } catch (t: Throwable) {
                cb.onError(t.message ?: "network error")
            } finally {
                conn?.disconnect()
            }
        }, "whale-geocode").start()
    }

    /** 逆地理编码：经纬度 -> 详细地址 */
    fun reverse(lat: Double, lon: Double, cb: Callback<String>) {
        Thread({
            var conn: HttpURLConnection? = null
            try {
                val url = "$BASE/reverse?format=jsonv2&zoom=18&accept-language=zh-CN&lat=$lat&lon=$lon"
                conn = open(url)
                val o = JSONObject(readBody(conn))
                val name = o.optString("display_name", "")
                if (name.isBlank()) cb.onError("") else cb.onSuccess(name)
            } catch (t: Throwable) {
                cb.onError(t.message ?: "network error")
            } finally {
                conn?.disconnect()
            }
        }, "whale-reverse-geocode").start()
    }

    private fun open(url: String): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("User-Agent", UA)
        }
    }

    private fun readBody(conn: HttpURLConnection): String {
        return conn.inputStream.bufferedReader().use { it.readText() }
    }
}
