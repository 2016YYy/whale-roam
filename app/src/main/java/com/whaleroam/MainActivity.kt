package com.whaleroam

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.whaleroam.data.HistoryStore
import com.whaleroam.data.SettingsStore
import com.whaleroam.databinding.ActivityMainBinding
import com.whaleroam.service.JoystickService
import com.whaleroam.service.MockLocationEngine
import com.whaleroam.service.MockLocationService
import com.whaleroam.ui.HistoryActivity
import com.whaleroam.ui.SettingsActivity
import com.whaleroam.update.UpdateDownloadService
import com.whaleroam.update.UpdateInfo
import com.whaleroam.update.UpdateManager
import org.json.JSONArray
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import java.net.URLEncoder
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var settings: SettingsStore
    private lateinit var history: HistoryStore

    private var targetLat = 22.5431
    private var targetLon = 114.0579
    private var mocking = false

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

        setupMap()
        setupActions()
        requestPermissions()
        ensureDisclaimerAccepted()
        maybeAutoCheckUpdate()

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

        binding.mapView.addMapListener(object : org.osmdroid.events.MapListener {
            override fun onScroll(event: org.osmdroid.events.ScrollEvent?): Boolean {
                val c = binding.mapView.mapCenter as GeoPoint
                setTarget(c.latitude, c.longitude, recenter = false)
                return true
            }
            override fun onZoom(event: org.osmdroid.events.ZoomEvent?): Boolean = true
        })

        updateCoordText()
    }

    private fun setupActions() {
        binding.startStopBtn.setOnClickListener {
            if (mocking) stopMock() else startMock()
        }
        binding.joystickBtn.setOnClickListener { toggleJoystick() }
        binding.saveBtn.setOnClickListener {
            history.add("手动收藏", targetLat, targetLon)
            toast("已收藏")
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
        val svc = Intent(this, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_START
            putExtra(MockLocationService.EXTRA_LAT, targetLat)
            putExtra(MockLocationService.EXTRA_LON, targetLon)
            putExtra(MockLocationService.EXTRA_ACCURACY, settings.accuracyMeters)
        }
        ContextCompat.startForegroundService(this, svc)
        mocking = true
        binding.startStopBtn.text = getString(R.string.action_stop)
        binding.statusText.text = getString(R.string.status_running, targetLat, targetLon)
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
        mocking = false
        binding.startStopBtn.text = getString(R.string.action_start)
        binding.statusText.text = getString(R.string.status_idle)
    }

    private fun toggleJoystick() {
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
            binding.joystickBtn.text = getString(R.string.action_joystick)
        } else {
            ContextCompat.startForegroundService(this, svc)
            JoystickToggle.visible = true
            binding.joystickBtn.text = getString(R.string.action_joystick_stop)
        }
    }

    private fun doSearch(q: String) {
        val query = q.trim()
        if (query.isEmpty()) return
        val coord = parseCoord(query)
        if (coord != null) {
            setTarget(coord.first, coord.second)
            return
        }
        thread(name="whale-geocode") {
            try {
                val url = "https://nominatim.openstreetmap.org/search?format=json&limit=1&q=" +
                        URLEncoder.encode(query, "UTF-8")
                val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                    connectTimeout = 10000
                    readTimeout = 10000
                    setRequestProperty("User-Agent", packageName)
                }
                val arr = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
                conn.disconnect()
                runOnUiThread {
                    if (arr.length() > 0) {
                        val o = arr.getJSONObject(0)
                        setTarget(o.getDouble("lat"), o.getDouble("lon"))
                    } else {
                        toast("未找到地点")
                    }
                }
            } catch (t: Throwable) {
                runOnUiThread { toast("搜索失败：${t.message}") }
            }
        }
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

    private fun checkUpdate() {
        toast(getString(R.string.update_checking))
        settings.lastUpdateCheckAt = System.currentTimeMillis()
        UpdateManager.check(this, BuildConfig.VERSION_CODE, object : UpdateManager.Callback {
            override fun onNeedUpdate(info: UpdateInfo) {
                runOnUiThread { showUpdateDialog(info) }
            }
            override fun onLatest() { runOnUiThread { toast(getString(R.string.update_latest)) } }
            override fun onError(msg: String) { runOnUiThread { toast("检查失败：$msg") } }
        })
    }

    /** 启动时自动检查更新：默认 24 小时最多一次，静默进行，发现新版本才弹窗 */
    private fun maybeAutoCheckUpdate() {
        val intervalMs = 24 * 60 * 60 * 1000L
        if (System.currentTimeMillis() - settings.lastUpdateCheckAt < intervalMs) return
        settings.lastUpdateCheckAt = System.currentTimeMillis()
        UpdateManager.check(this, BuildConfig.VERSION_CODE, object : UpdateManager.Callback {
            override fun onNeedUpdate(info: UpdateInfo) {
                runOnUiThread { showUpdateDialog(info) }
            }
            override fun onLatest() {}
            override fun onError(msg: String) {}
        })
    }

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

    private fun showUpdateDialog(info: UpdateInfo) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.update_available, info.versionName))
            .setMessage(info.changelog.ifBlank { getString(R.string.update_no_notes) })
            .setPositiveButton(R.string.update_download) { _, _ ->
                val svc = Intent(this, UpdateDownloadService::class.java).apply {
                    action = UpdateDownloadService.ACTION_DOWNLOAD
                    putExtra(UpdateDownloadService.EXTRA_URL, info.apkUrl)
                    putExtra(UpdateDownloadService.EXTRA_VERSION_NAME, info.versionName)
                }
                ContextCompat.startForegroundService(this, svc)
                toast("开始下载，可在通知栏查看进度")
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_history -> { startActivity(Intent(this, HistoryActivity::class.java)); true }
            R.id.menu_settings -> { startActivity(Intent(this, SettingsActivity::class.java)); true }
            R.id.menu_update -> { checkUpdate(); true }
            R.id.menu_disclaimer -> { showDisclaimer(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showDisclaimer() {
        AlertDialog.Builder(this)
            .setTitle(R.string.disclaimer_title)
            .setMessage(R.string.disclaimer_body)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }
}

object JoystickToggle {
    var visible = false
}
