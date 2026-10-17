package com.whaleroam.ui

import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.whaleroam.R
import com.whaleroam.data.Route
import com.whaleroam.data.RouteStore
import com.whaleroam.data.SettingsStore
import com.whaleroam.service.MockLocationEngine
import com.whaleroam.service.RouteReplayer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 路线回放页（v1.4.0）：
 *
 * - 列表展示已录制的路线（名称 / 点数 / 时长 / 创建时间）；
 * - 点击条目弹出操作框：开始回放（可选循环与倍速）、重命名、删除；
 * - 回放中顶部出现状态条，显示进度并可随时停止。
 *
 * 回放依赖 [MockLocationEngine] 处于运行态，未启动时只提示、不启动。
 */
class RouteActivity : AppCompatActivity() {

    private lateinit var store: RouteStore
    private lateinit var settings: SettingsStore
    private val list = mutableListOf<Route>()
    private lateinit var adapter: RouteAdapter

    private lateinit var listView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var replayBar: View
    private lateinit var replayStatus: TextView

    private val ui = Handler(Looper.getMainLooper())
    private var replayingRouteId = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_routes)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
            .setNavigationOnClickListener { finish() }

        store = RouteStore(this)
        settings = SettingsStore(this)

        listView = findViewById(R.id.list)
        emptyView = findViewById(R.id.empty)
        replayBar = findViewById(R.id.replayBar)
        replayStatus = findViewById(R.id.replayStatus)
        findViewById<View>(R.id.replayStopBtn).setOnClickListener { stopReplay() }

        listView.layoutManager = LinearLayoutManager(this)
        adapter = RouteAdapter()
        listView.adapter = adapter
        reload()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        // 页面销毁时停掉回放，避免 HandlerThread 与回调泄漏到已销毁的 Activity
        if (RouteReplayer.playing) RouteReplayer.stop()
        ui.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    // ---------- 列表 ----------

    private fun reload() {
        list.clear()
        list.addAll(store.list())
        adapter.notifyDataSetChanged()
        emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        emptyView.text = getString(R.string.route_empty) + "\n" + getString(R.string.route_empty_hint)
        listView.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
        refreshReplayBar()
    }

    private fun refreshReplayBar() {
        val playing = RouteReplayer.playing
        replayBar.visibility = if (playing) View.VISIBLE else View.GONE
        if (!playing) replayStatus.text = ""
    }

    /** 路线总时长（最后一个点的时间偏移） */
    private fun durationMs(route: Route): Long =
        route.points.lastOrNull()?.tOffsetMs ?: 0L

    private fun formatDuration(ms: Long): String {
        if (ms < 1000L) return getString(R.string.route_duration_short)
        val sec = ms / 1000L
        return if (sec < 60L) {
            getString(R.string.route_duration_sec, sec.toInt())
        } else {
            getString(R.string.route_duration, (sec / 60).toInt(), (sec % 60).toInt())
        }
    }

    private fun formatTime(ts: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ts))

    inner class RouteAdapter : RecyclerView.Adapter<RouteAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.routeName)
            val meta: TextView = v.findViewById(R.id.routeMeta)
            val time: TextView = v.findViewById(R.id.routeTime)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_route, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val r = list[position]
            holder.name.text = r.name
            holder.meta.text = getString(R.string.route_meta, r.points.size, formatDuration(durationMs(r)))
            holder.time.text = getString(R.string.route_created_at, formatTime(r.createdAt))
            holder.itemView.setOnClickListener { showRouteDialog(r) }
        }

        override fun getItemCount() = list.size
    }

    // ---------- 操作对话框 ----------

    private fun showRouteDialog(route: Route) {
        val options = arrayOf(
            getString(R.string.route_replay),
            getString(R.string.route_rename),
            getString(R.string.delete),
        )
        AlertDialog.Builder(this)
            .setTitle(route.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showReplayDialog(route)
                    1 -> showRenameDialog(route)
                    2 -> showDeleteDialog(route)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** 回放参数：循环勾选框 + 倍速下拉，默认沿用上次的选择 */
    private fun showReplayDialog(route: Route) {
        if (!MockLocationEngine.running) {
            toast(getString(R.string.route_need_mock))
            return
        }
        if (route.points.isEmpty()) {
            toast(getString(R.string.route_replay_empty_points))
            return
        }

        val speeds = floatArrayOf(0.5f, 1f, 2f, 4f)
        val density = resources.displayMetrics.density
        val pad = (20 * density).toInt()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, (8 * density).toInt(), pad, 0)
        }
        val loopBox = CheckBox(this).apply {
            text = getString(R.string.route_replay_loop)
            isChecked = settings.replayLoop
        }
        val speedLabel = TextView(this).apply {
            text = getString(R.string.route_replay_speed)
            setPadding(0, (12 * density).toInt(), 0, (4 * density).toInt())
        }
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@RouteActivity,
                android.R.layout.simple_spinner_dropdown_item,
                speeds.map { this@RouteActivity.getString(R.string.route_speed_format, trimSpeed(it)) }
            )
            // 默认选中与设置中一致的倍速
            val idx = speeds.indexOfFirst { it == settings.replaySpeedFactor }
            setSelection(if (idx >= 0) idx else 1)
        }
        container.addView(loopBox)
        container.addView(speedLabel)
        container.addView(spinner)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.route_replay) + " · " + route.name)
            .setView(container)
            .setPositiveButton(R.string.route_replay) { _, _ ->
                val factor = speeds[spinner.selectedItemPosition.coerceIn(0, speeds.size - 1)]
                val loop = loopBox.isChecked
                // 记住本次选择，下次打开对话框沿用
                settings.replaySpeedFactor = factor
                settings.replayLoop = loop
                startReplay(route, factor, loop)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun trimSpeed(f: Float): String =
        if (f == f.toInt().toFloat()) f.toInt().toString() else f.toString()

    private fun showRenameDialog(route: Route) {
        val input = EditText(this).apply {
            setText(route.name)
            setSelection(route.name.length)
            hint = getString(R.string.route_rename_hint)
        }
        val pad = (20 * resources.displayMetrics.density).toInt()
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, (8 * resources.displayMetrics.density).toInt(), pad, 0)
            addView(input)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.route_rename_title)
            .setView(wrap)
            .setPositiveButton(R.string.save) { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    store.rename(route.id, newName)
                    reload()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showDeleteDialog(route: Route) {
        AlertDialog.Builder(this)
            .setTitle(R.string.route_delete_title)
            .setMessage(getString(R.string.route_delete_msg, route.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                if (RouteReplayer.playing && replayingRouteId == route.id) stopReplay()
                store.delete(route.id)
                reload()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---------- 回放控制 ----------

    private fun startReplay(route: Route, speedFactor: Float, loop: Boolean) {
        if (!MockLocationEngine.running) {
            toast(getString(R.string.route_need_mock))
            return
        }
        if (RouteReplayer.playing) {
            toast(getString(R.string.route_replay_running))
            return
        }
        replayingRouteId = route.id
        replayBar.visibility = View.VISIBLE
        replayStatus.text = getString(R.string.route_replay_started, route.name)
        RouteReplayer.start(this, route, speedFactor, loop, object : RouteReplayer.Callback {
            // 回调发生在回放线程，统一切回主线程再动 UI
            override fun onProgress(index: Int, total: Int) {
                ui.post {
                    if (isFinishing || isDestroyed) return@post
                    replayBar.visibility = View.VISIBLE
                    replayStatus.text = getString(
                        R.string.route_replay_progress,
                        route.name, index + 1, total
                    )
                }
            }

            override fun onFinished() {
                ui.post {
                    if (isFinishing || isDestroyed) return@post
                    replayingRouteId = -1L
                    replayBar.visibility = View.GONE
                    replayStatus.text = ""
                    toast(getString(R.string.route_replay_finished))
                }
            }
        })
        // start 因条件不满足直接回调了 onFinished，这里同步一次状态
        if (!RouteReplayer.playing) refreshReplayBar()
    }

    private fun stopReplay() {
        RouteReplayer.stop()
        replayingRouteId = -1L
        replayBar.visibility = View.GONE
        replayStatus.text = ""
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}