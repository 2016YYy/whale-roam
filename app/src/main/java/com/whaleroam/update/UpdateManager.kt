package com.whaleroam.update

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

object UpdateManager {

    private const val LATEST_URL =
        "https://raw.githubusercontent.com/2016YYy/whale-roam/main/latest.json"

    interface Callback {
        fun onNeedUpdate(info: UpdateInfo)
        fun onLatest()
        fun onError(msg: String)
    }

    fun check(context: Context, currentCode: Int, cb: Callback) {
        thread(name="whale-update-check") {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(LATEST_URL).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10000
                    readTimeout = 10000
                    setRequestProperty("Accept", "application/json")
                }
                if (conn.responseCode != 200) { cb.onError("HTTP ${conn.responseCode}"); return@thread }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val info = UpdateInfo(
                    versionName = json.getString("versionName"),
                    versionCode = json.getInt("versionCode"),
                    changelog = json.optString("changelog", ""),
                    apkUrl = json.getString("apkUrl")
                )
                if (info.versionCode > currentCode) cb.onNeedUpdate(info) else cb.onLatest()
            } catch (t: Throwable) {
                cb.onError(t.message ?: "network error")
            } finally {
                conn?.disconnect()
            }
        }
    }
}
