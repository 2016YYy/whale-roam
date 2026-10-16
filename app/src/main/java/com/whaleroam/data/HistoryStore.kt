package com.whaleroam.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Place(
    val id: Long,
    val label: String,
    val lat: Double,
    val lon: Double
)

class HistoryStore(private val context: Context) {

    private val file: File by lazy { File(context.filesDir, "history.json") }
    private val settings by lazy { SettingsStore(context) }

    /** 读取列表：顺带归档过期记录（默认 7 天，可在设置中调整），对标 gogogo recordArchive */
    fun list(): MutableList<Place> {
        if (!file.exists()) return mutableListOf()
        val raw = runCatching {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Place(o.getLong("id"), o.getString("label"), o.getDouble("lat"), o.getDouble("lon"))
            }.toMutableList()
        }.getOrDefault(mutableListOf())
        val cutoff = System.currentTimeMillis() - settings.historyExpirationDays * 24L * 3600L * 1000L
        val (kept, expired) = raw.partition { it.id >= cutoff }
        if (expired.isNotEmpty()) save(kept) // 有过期才回写，避免每次读都落盘
        return kept.toMutableList()
    }

    /** 逆地理编码成功后回填标签（自动记录的定位历史先用坐标占位） */
    fun updateLabel(id: Long, label: String) {
        val list = list()
        val idx = list.indexOfFirst { it.id == id }
        if (idx >= 0) {
            list[idx] = list[idx].copy(label = label)
            save(list)
        }
    }

    fun add(label: String, lat: Double, lon: Double): Place {
        val list = list()
        val id = System.currentTimeMillis()
        val name = label.ifBlank { "${"%.5f".format(lat)}, ${"%.5f".format(lon)}" }
        val place = Place(id, name, lat, lon)
        val existingIdx = list.indexOfFirst { Math.abs(it.lat - lat) < 1e-4 && Math.abs(it.lon - lon) < 1e-4 }
        if (existingIdx >= 0) list[existingIdx] = place else list.add(0, place)
        if (list.size > MAX) list.subList(MAX, list.size).clear()
        save(list)
        return place
    }

    fun delete(id: Long) {
        val list = list()
        list.removeAll { it.id == id }
        save(list)
    }

    private fun save(list: List<Place>) {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(JSONObject().apply {
                put("id", p.id); put("label", p.label); put("lat", p.lat); put("lon", p.lon)
            })
        }
        file.writeText(arr.toString())
    }

    companion object { private const val MAX = 100 }
}
