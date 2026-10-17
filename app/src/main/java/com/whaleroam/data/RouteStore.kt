package com.whaleroam.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 单个轨迹点：tOffsetMs 是相对录制起点的毫秒偏移 */
data class RoutePoint(
    val tOffsetMs: Long,
    val lat: Double,
    val lon: Double,
    val bearing: Float,
    val speedMps: Float,
)

/** 一条录制好的路线 */
data class Route(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val points: List<RoutePoint>,
)

/**
 * 路线持久化（对标 HistoryStore 的写法，JSON 存 filesDir/routes.json）。
 *
 * 容量约束：
 * - 最多保留 [MAX_ROUTES] 条路线，超出后丢弃最旧的；
 * - 单条路线最多 [MAX_POINTS] 个点，超出后按 2 倍抽稀（隔点保留）直到达标。
 *   抽稀后 tOffsetMs 仍是原始时间戳，回放时间轴不变，只是轨迹精度下降。
 *
 * 同名策略：
 * - [save] 采用「同名覆盖」：同名视为同一段路被重新录制，沿用旧 id 并替换点数据，
 *   避免列表里堆出一串同名路线；
 * - [rename] 采用「自动加序号」：重命名撞名时改成「原名 (2)」「原名 (3)」，
 *   因为重命名不该销毁另一条已有路线。
 */
class RouteStore(context: Context) {

    private val file: File by lazy { File(context.filesDir, "routes.json") }
    private val lock = Any()

    /** 读取全部路线，按创建时间倒序（最新在前） */
    fun list(): List<Route> = synchronized(lock) { load() }

    /** 保存一条路线；返回落库后的路线（已按容量规则裁剪） */
    fun save(name: String, points: List<RoutePoint>): Route = synchronized(lock) {
        val list = load().toMutableList()
        val finalName = name.trim().ifBlank { defaultName() }
        val old = list.firstOrNull { it.name == finalName }
        val route = Route(
            id = old?.id ?: nextId(list),
            name = finalName,
            createdAt = System.currentTimeMillis(),
            points = thinIfNeeded(points),
        )
        // 同名覆盖：先移除旧记录，再把新记录插到最前
        if (old != null) list.removeAll { it.name == finalName }
        list.add(0, route)
        if (list.size > MAX_ROUTES) list.subList(MAX_ROUTES, list.size).clear()
        persist(list)
        route
    }

    fun delete(id: Long) {
        synchronized(lock) {
            val list = load().toMutableList()
            if (list.removeAll { it.id == id }) persist(list)
        }
    }

    fun rename(id: Long, name: String) {
        synchronized(lock) {
            val base = name.trim()
            if (base.isEmpty()) return
            val list = load().toMutableList()
            val idx = list.indexOfFirst { it.id == id }
            if (idx < 0) return
            list[idx] = list[idx].copy(name = uniqueName(base, list, id))
            persist(list)
        }
    }

    // ---------- 内部实现 ----------

    private fun load(): MutableList<Route> {
        if (!file.exists()) return mutableListOf()
        return runCatching {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val ptsArr = o.optJSONArray("points") ?: JSONArray()
                val pts = ArrayList<RoutePoint>(ptsArr.length())
                for (j in 0 until ptsArr.length()) {
                    val p = ptsArr.optJSONObject(j) ?: continue
                    pts.add(
                        RoutePoint(
                            tOffsetMs = p.optLong("tOffsetMs"),
                            lat = p.optDouble("lat"),
                            lon = p.optDouble("lon"),
                            bearing = p.optDouble("bearing").toFloat(),
                            speedMps = p.optDouble("speedMps").toFloat(),
                        )
                    )
                }
                Route(o.optLong("id"), o.optString("name"), o.optLong("createdAt"), pts)
            }.sortedByDescending { it.createdAt }.toMutableList()
        }.getOrDefault(mutableListOf())
    }

    private fun persist(list: List<Route>) {
        val arr = JSONArray()
        list.forEach { r ->
            val pts = JSONArray()
            r.points.forEach { p ->
                pts.put(JSONObject().apply {
                    put("tOffsetMs", p.tOffsetMs)
                    put("lat", p.lat)
                    put("lon", p.lon)
                    put("bearing", p.bearing.toDouble())
                    put("speedMps", p.speedMps.toDouble())
                })
            }
            arr.put(JSONObject().apply {
                put("id", r.id)
                put("name", r.name)
                put("createdAt", r.createdAt)
                put("points", pts)
            })
        }
        runCatching { file.writeText(arr.toString()) }
    }

    /** 超出上限时按 2 倍抽稀（保留 0,2,4... 下标），末点必须留下，否则回放会提前结束 */
    private fun thinIfNeeded(points: List<RoutePoint>): List<RoutePoint> {
        if (points.size <= MAX_POINTS) return points
        var cur = points
        while (cur.size > MAX_POINTS) {
            val next = ArrayList<RoutePoint>((cur.size + 1) / 2)
            var i = 0
            while (i < cur.size) {
                next.add(cur[i])
                i += 2
            }
            if (next.last() !== cur.last()) next.add(cur.last())
            cur = next
        }
        return cur
    }

    /** id 用当前毫秒，和现有记录冲突时顺延，保证唯一 */
    private fun nextId(list: List<Route>): Long {
        val maxId = list.maxOfOrNull { it.id } ?: 0L
        return maxOf(System.currentTimeMillis(), maxId + 1)
    }

    private fun uniqueName(base: String, list: List<Route>, exceptId: Long): String {
        if (list.none { it.id != exceptId && it.name == base }) return base
        var n = 2
        while (list.any { it.id != exceptId && it.name == "$base ($n)" }) n++
        return "$base ($n)"
    }

    private fun defaultName(): String =
        "路线 " + SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date())

    companion object {
        private const val MAX_ROUTES = 50
        private const val MAX_POINTS = 20000
    }
}