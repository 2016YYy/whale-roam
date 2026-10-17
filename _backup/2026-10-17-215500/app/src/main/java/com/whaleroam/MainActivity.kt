package com.whaleroam

import android.Manifest
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.whaleroam.data.HistoryStore
import com.whaleroam.data.SettingsStore
import com.whaleroam.databinding.ActivityMainBinding
import com.whaleroam.service.JoystickService
import com.whaleroam.service.MockLocationEngine
import com.whaleroam.service.MockLocationService
import com.whaleroam.ui.HistoryActivity
import com.whaleroam.ui.RouteActivity
import com.whaleroam.ui.SettingsActivity
import com.whaleroam.ui.UpdateDialog
import com.whaleroam.update.UpdateInfo
import com.whaleroam.update.UpdateManager
import com.whaleroam.util.GeoCoordConverter
import com.whaleroam.util.NominatimClient
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var settings: SettingsStore
    private lateinit var history: HistoryStore

    private var targetLat = 22.5431
    private var targetLon = 114.0579
    private var mocking = false

    /** 长按地图查询时的临时标记 */
    private var longPressMarker: Marker? = null

    private val permLocationReq = 101
    private val permNotifReq = 102

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = packageName
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        settings = SettingsStore(this)
        history = HistoryStore(this)

        setupDrawer()
        setupMap()
        setupActions()
        requestPermissions()
        ensureDisclaimerAccepted()
        maybeAutoCheckUpdate()

        handleMapIntent(intent)
    }

    /** 侧边抽屉导航（对标 gogogo 侧边栏）：历史 / 设置 / 检查更新 / 免责声明 / 关于 */
    private fun setupDrawer() {
        val toggle = ActionBarDrawerToggle(
            this, binding.drawerLayout, binding.toolbar,
            R.string.app_name, R.string.app_name
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        binding.navView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_history -> startActivity(Intent(this, HistoryActivity::class.java))
                R.id.nav_settings -> startActivity(Intent(this, SettingsActivity::class.java))
                R.id.nav_routes -> startActivity(Intent(this, RouteActivity::class.java))
                R.id.nav_record -> toggleRecording()
                R.id.nav_update -> checkUpdate()
                R.id.nav_disclaimer -> showDisclaimer()
                R.id.nav_about -> showAbout()
            }
            binding.drawerLayout.closeDrawers()
            true
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleMapIntent(intent)
    }

    private fun handleMapIntent(intent: Intent?) {
        intent?.let {
            val lat = it.getDoubleExtra("extra_lat", Double.NaN)
            val lon = it.getDoubleExtra("extra_lon", Double.NaN)
            if (!lat.isNaN() && !lon.isNaN()) {
                binding.mapView.post { setTarget(lat, lon) }
            }
        }
    }

    private fun setupMap() {
        binding.mapView.setTileSource(TileSourceFactory.MAPNIK)
        binding.mapView.setMultiTouchControls(true)
        binding.mapView.setUseDataConnection(true)
        binding.mapView.controller.setZoom(15.0)
        binding.mapView.controller.setCenter(GeoPoint(targetLat, targetLon))

        // 拖动地图时，以中心点为模拟目标（准星选点模式）
        binding.mapView.addMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                val c = binding.mapView.mapCenter as GeoPoint
                setTarget(c.latitude, c.longitude, recenter = false)
                return true
            }
            override fun onZoom(event: ZoomEvent?): Boolean = true
        })

        // 长按地图：查询该点详细地址（参照 gogogo 长按查询）
        binding.mapView.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean = false
            override fun longPressHelper(p: GeoPoint?): Boolean {
                p?.let { onMapLongPress(it.latitude, it.longitude) }
                return true
            }
        }))

        updateCoordText()
    }

    private fun setupActions() {
        binding.startFab.setOnClickListener {
            if (mocking) stopMock() else startMock()
        }
        binding.joystickBtn.setOnClickListener { toggleJoystick() }
        binding.saveBtn.setOnClickListener {
            history.add("手动收藏", targetLat, targetLon)
            toast(getString(R.string.longpress_saved))
        }
        binding.searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                doSearch(binding.searchInput.text.toString())
                true
            } else false
        }
    }

    private fun requestPermissions() {
        val needLoc = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
        if (needLoc) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                permLocationReq
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), permNotifReq)
            }
        }
    }

    private fun setTarget(lat: Double, lon: Double, recenter: Boolean = true) {
        targetLat = lat
        targetLon = lon
        updateCoordText()
        if (mocking) {
            MockLocationEngine.push(lat, lon, settings.accuracyMeters)
            binding.statusText.text = getString(R.string.status_running, lat, lon)
        }
        if (recenter) {
            binding.mapView.controller.animateTo(GeoPoint(lat, lon))
        }
    }

    private fun updateCoordText() {
        binding.coordText.text = "%.6f, %.6f".format(targetLat, targetLon)
    }

    private fun startMock() {
        // 前置检查链（对标 gogogo doGoLocation）：模拟位置应用 -> GPS 开启 -> WiFi 干扰提醒
        if (!MockLocationEngine.canMock(this)) {
            AlertDialog.Builder(this)
                .setTitle(R.string.disclaimer_title)
                .setMessage(R.string.status_need_mock_app)
                .setPositiveButton("去设置") { _, _ ->
                    startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
            return
        }
        val lm = getSystemService(android.location.LocationManager::class.java)
        if (lm != null && !LocationManagerCompat.isLocationEnabled(lm)) {
            AlertDialog.Builder(this)
                .setMessage("检测到系统定位服务未开启，可能导致模拟定位无法生效。是否去开启？")
                .setPositiveButton("去开启") { _, _ ->
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
            return
        }
        if (isWifiActive()) {
            toast("WiFi 已开启，网络定位可能干扰模拟效果，建议关闭 WiFi 仅用流量")
        }
        val svc = Intent(this, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_START
            putExtra(MockLocationService.EXTRA_LAT, targetLat)
            putExtra(MockLocationService.EXTRA_LON, targetLon)
            putExtra(MockLocationService.EXTRA_ACCURACY, settings.accuracyMeters)
        }
        ContextCompat.startForegroundService(this, svc)
        applyMockingUi(true)

        // 启动模拟时自动记录定位历史（对标 gogogo recordCurrentLocation）：
        // 先以坐标占位入库，再异步逆地理编码回填地址名称
        val place = history.add("", targetLat, targetLon)
        NominatimClient.reverse(targetLat, targetLon, object : NominatimClient.Callback<String> {
            override fun onSuccess(address: String) {
                history.updateLabel(place.id, address)
            }
            override fun onError(msg: String) { /* 失败保持坐标标签 */ }
        })
    }

    /**
     * 路线录制开关（v1.4.0）：
     * 录制必须依附于模拟定位——摇杆每 tick 上报的坐标才是"真实走过"的轨迹。
     * 因此未启动模拟时直接提示，不进入录制态。
     */
    private fun toggleRecording() {
        if (com.whaleroam.service.RouteRecorder.recording) {
            stopRecordingAndSave()
            return
        }
        if (!MockLocationEngine.running) {
            AlertDialog.Builder(this)
                .setTitle(R.string.record_start)
                .setMessage(R.string.record_need_mock)
                .setPositiveButton(R.string.ok, null)
                .show()
            return
        }
        com.whaleroam.service.RouteRecorder.start()
        toast(getString(R.string.record_started))
        invalidateOptionsMenuCompat()
    }

    /** 停止录制并让用户命名保存 */
    private fun stopRecordingAndSave() {
        val count = com.whaleroam.service.RouteRecorder.pointCount
        if (count < 2) {
            com.whaleroam.service.RouteRecorder.cancel()
            toast(getString(R.string.record_too_short))
            invalidateOptionsMenuCompat()
            return
        }
        val input = android.widget.EditText(this).apply {
            hint = getString(R.string.record_name_hint)
            setText(getString(R.string.record_default_name, System.currentTimeMillis() / 1000 % 10000))
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.record_save_title)
            .setMessage(getString(R.string.record_save_msg, count))
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = input.text.toString().ifBlank {
                    getString(R.string.record_default_name, System.currentTimeMillis() / 1000 % 10000)
                }
                val saved = com.whaleroam.service.RouteRecorder.stopAndSave(this, name)
                if (saved != null) {
                    toast(getString(R.string.record_saved, saved.name, saved.points.size))
                } else {
                    toast(getString(R.string.record_too_short))
                }
                invalidateOptionsMenuCompat()
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                com.whaleroam.service.RouteRecorder.cancel()
                invalidateOptionsMenuCompat()
            }
            .setOnCancelListener {
                com.whaleroam.service.RouteRecorder.cancel()
                invalidateOptionsMenuCompat()
            }
            .show()
    }

    /** 按 JoystickToggle 的真实状态刷新摇杆按钮文案 */
    private fun syncJoystickButton() {
        binding.joystickBtn.text = getString(
            if (JoystickToggle.visible) R.string.action_joystick_stop else R.string.action_joystick
        )
    }

    /** 录制状态变化后刷新抽屉菜单标题（NavigationView 需要重建菜单才生效） */
    private fun invalidateOptionsMenuCompat() {
        val title = if (com.whaleroam.service.RouteRecorder.recording) {
            getString(R.string.record_stop)
        } else {
            getString(R.string.record_start)
        }
        binding.navView.menu.findItem(R.id.nav_record)?.title = title
    }

    /** WiFi 是否为当前活动网络（WiFi 扫描会干扰网络定位，导致定位闪回真实位置） */
    private fun isWifiActive(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun stopMock() {
        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_stop_title)
            .setMessage(R.string.confirm_stop_msg)
            .setPositiveButton(R.string.action_stop) { _, _ -> doStopMock() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun doStopMock() {
        val svc = Intent(this, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_STOP
        }
        startService(svc)
        // v1.4.0 修复：停止模拟后摇杆仍在运行，状态不一致且继续占着前台服务
        if (JoystickToggle.visible) {
            stopService(Intent(this, JoystickService::class.java))
            JoystickToggle.visible = false
            syncJoystickButton()
        }
        // 路线回放依附于模拟定位，一并停止
        com.whaleroam.service.RouteReplayer.stop()
        // 录制同理：模拟一停，摇杆不再上报坐标，继续"录制"只会录到一段静止的空轨迹。
        // 这里自动收尾并提示用户，避免录制状态悬挂在后台。
        if (com.whaleroam.service.RouteRecorder.recording) {
            com.whaleroam.service.RouteRecorder.cancel()
            toast(getString(R.string.record_auto_cancelled))
            invalidateOptionsMenuCompat()
        }
        applyMockingUi(false)
    }

    /** 统一刷新「启动/停止模拟」按钮与状态文字（避免各处散落重复代码） */
    private fun applyMockingUi(running: Boolean) {
        mocking = running
        binding.startFab.text = getString(if (running) R.string.action_stop else R.string.action_start)
        binding.startFab.setIconResource(if (running) R.drawable.ic_stop else R.drawable.ic_play)
        binding.statusText.text = if (running) {
            getString(R.string.status_running, targetLat, targetLon)
        } else {
            getString(R.string.status_idle)
        }
    }

    private fun toggleJoystick() {
        // v1.4.0：模拟未启动时摇杆虽然能显示，但拖动不会有任何效果，先给出明确提示
        if (!JoystickToggle.visible && !MockLocationEngine.running) {
            AlertDialog.Builder(this)
                .setTitle(R.string.action_joystick)
                .setMessage(R.string.joystick_need_mock_first)
                .setPositiveButton(R.string.ok, null)
                .show()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this)
                .setMessage(R.string.status_need_overlay)
                .setPositiveButton("去授权") { _, _ ->
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                    )
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
            return
        }
        val svc = Intent(this, JoystickService::class.java)
        if (JoystickToggle.visible) {
            stopService(svc)
            JoystickToggle.visible = false
        } else {
            ContextCompat.startForegroundService(this, svc)
            JoystickToggle.visible = true
        }
        syncJoystickButton()
    }

    // ---------- 搜索 / 坐标输入 ----------

    private fun doSearch(q: String) {
        val query = q.trim()
        if (query.isEmpty()) return
        val coord = parseCoord(query)
        if (coord != null) {
            askCoordinateSystem(coord.first, coord.second)
            return
        }
        toast(getString(R.string.search_searching))
        NominatimClient.search(query, object : NominatimClient.Callback<NominatimClient.SearchResult> {
            override fun onSuccess(result: NominatimClient.SearchResult) {
                runOnUiThread { setTarget(result.lat, result.lon) }
            }
            override fun onError(msg: String) {
                runOnUiThread {
                    toast(if (msg.isBlank()) "未找到地点" else "搜索失败：$msg")
                }
            }
        })
    }

    /** 检测到坐标输入时，让用户选择来源坐标系（WGS-84 / GCJ-02 / BD-09），统一换算为 WGS-84 */
    private fun askCoordinateSystem(lat: Double, lon: Double) {
        val systems = arrayOf(
            getString(R.string.coord_sys_wgs),
            getString(R.string.coord_sys_gcj),
            getString(R.string.coord_sys_bd)
        )
        var checked = 0
        AlertDialog.Builder(this)
            .setTitle(R.string.coord_sys_title)
            .setSingleChoiceItems(systems, checked) { _, which -> checked = which }
            .setPositiveButton(R.string.coord_confirm) { _, _ ->
                val from = GeoCoordConverter.CoordSystem.values()[checked]
                val (wLat, wLon) = GeoCoordConverter.toWgs84(lat, lon, from)
                setTarget(wLat, wLon)
                if (from != GeoCoordConverter.CoordSystem.WGS84) {
                    toast(getString(R.string.coord_converted, wLat, wLon))
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun parseCoord(s: String): Pair<Double, Double>? {
        return try {
            val parts = s.split("[,，\\s]+".toRegex()).filter { it.isNotBlank() }
            if (parts.size >= 2) {
                val lat = parts[0].toDouble()
                val lon = parts[1].toDouble()
                if (lat in -90.0..90.0 && lon in -180.0..180.0) Pair(lat, lon) else null
            } else null
        } catch (t: Throwable) { null }
    }

    // ---------- 长按地图查询地址 ----------

    private fun onMapLongPress(lat: Double, lon: Double) {
        showLongPressMarker(lat, lon)
        val loading = AlertDialog.Builder(this)
            .setTitle(R.string.longpress_title)
            .setMessage(R.string.longpress_locating)
            .setCancelable(true)
            .show()
        NominatimClient.reverse(lat, lon, object : NominatimClient.Callback<String> {
            override fun onSuccess(address: String) {
                runOnUiThread {
                    loading.dismiss()
                    showAddressDialog(lat, lon, address)
                }
            }
            override fun onError(msg: String) {
                runOnUiThread {
                    loading.dismiss()
                    removeLongPressMarker()
                    toast(getString(R.string.longpress_no_address))
                }
            }
        })
    }

    private fun showLongPressMarker(lat: Double, lon: Double) {
        removeLongPressMarker()
        val m = Marker(binding.mapView)
        m.position = GeoPoint(lat, lon)
        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        m.title = "%.6f, %.6f".format(lat, lon)
        longPressMarker = m
        binding.mapView.overlays.add(m)
        binding.mapView.invalidate()
    }

    private fun removeLongPressMarker() {
        longPressMarker?.let {
            binding.mapView.overlays.remove(it)
            binding.mapView.invalidate()
        }
        longPressMarker = null
    }

    private fun showAddressDialog(lat: Double, lon: Double, address: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.longpress_title)
            .setMessage("%.6f, %.6f\n\n%s".format(lat, lon, address))
            .setPositiveButton(R.string.longpress_set_here) { _, _ ->
                removeLongPressMarker()
                setTarget(lat, lon)
            }
            .setNeutralButton(R.string.longpress_copy) { _, _ ->
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("address", address))
                toast(getString(R.string.longpress_copy_done))
            }
            .setNegativeButton(R.string.longpress_save_here) { _, _ ->
                history.add(address.take(24), lat, lon)
                toast(getString(R.string.longpress_saved))
            }
            .setOnCancelListener { removeLongPressMarker() }
            .show()
    }

    // ---------- 更新 ----------

    private fun checkUpdate() {
        UpdateDialog.startCheck(this)
    }

    /** 启动时自动检查更新：默认 24 小时最多一次；有新版本弹窗提示，无更新吐司提示 */
    private fun maybeAutoCheckUpdate() {
        val intervalMs = 24 * 60 * 60 * 1000L
        if (System.currentTimeMillis() - settings.lastUpdateCheckAt < intervalMs) return
        settings.lastUpdateCheckAt = System.currentTimeMillis()
        UpdateManager.check(this, BuildConfig.VERSION_CODE, object : UpdateManager.Callback {
            override fun onNeedUpdate(info: UpdateInfo) {
                runOnUiThread { UpdateDialog.showFound(this@MainActivity, info) }
            }
            override fun onLatest() {
                runOnUiThread { toast(getString(R.string.update_latest)) }
            }
            override fun onError(msg: String) { /* 静默：自动检查失败不打扰用户 */ }
        })
    }

    // ---------- 免责声明 ----------

    /** 首次启动必须阅读并同意免责声明，否则不能使用本软件 */
    private fun ensureDisclaimerAccepted() {
        if (settings.disclaimerAccepted) return
        AlertDialog.Builder(this)
            .setTitle(R.string.disclaimer_title)
            .setMessage(R.string.disclaimer_body)
            .setCancelable(false)
            .setPositiveButton(R.string.disclaimer_agree) { _, _ ->
                settings.disclaimerAccepted = true
            }
            .setNegativeButton(R.string.disclaimer_exit) { _, _ -> finish() }
            .show()
    }

    private fun showDisclaimer() {
        AlertDialog.Builder(this)
            .setTitle(R.string.disclaimer_title)
            .setMessage(R.string.disclaimer_body)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun showAbout() {
        AlertDialog.Builder(this)
            .setTitle(R.string.title_about)
            .setMessage(getString(R.string.about_body, BuildConfig.VERSION_NAME))
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(binding.navView)) {
            binding.drawerLayout.closeDrawers()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
        // v1.4.0：用户可能在通知栏点了「停止模拟」，回到界面时同步真实状态
        if (mocking != MockLocationEngine.running) applyMockingUi(MockLocationEngine.running)
        // 同理：摇杆可能被通知栏「关闭摇杆」关掉，按钮文案要跟着回来
        syncJoystickButton()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }
}

object JoystickToggle {
    var visible = false
}