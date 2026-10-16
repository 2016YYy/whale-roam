package com.whaleroam.ui

import android.os.Bundle
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.whaleroam.BuildConfig
import com.whaleroam.R
import com.whaleroam.data.SettingsStore

class SettingsActivity : AppCompatActivity() {

    private lateinit var settings: SettingsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        settings = SettingsStore(this)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        val speedBar = findViewById<SeekBar>(R.id.speedBar)
        val speedVal = findViewById<TextView>(R.id.speedVal)
        val accBar = findViewById<SeekBar>(R.id.accBar)
        val accVal = findViewById<TextView>(R.id.accVal)
        val disclaimerRow = findViewById<TextView>(R.id.rowDisclaimer)
        val aboutRow = findViewById<TextView>(R.id.rowAbout)
        val checkUpdateRow = findViewById<TextView>(R.id.rowCheckUpdate)
        val versionText = findViewById<TextView>(R.id.versionText)

        versionText.text = getString(R.string.pref_version) + "：" + BuildConfig.VERSION_NAME +
                " (" + BuildConfig.VERSION_CODE + ")"

        // 关于—检查更新：弹出 CloudDrive 风格更新窗口（含下载进度条）
        checkUpdateRow.setOnClickListener {
            UpdateDialog.startCheck(this)
        }

        speedBar.progress = (settings.joystickSpeedMps * 10).toInt()
        speedVal.text = "${settings.joystickSpeedMps} m/s"
        speedBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                val v = p / 10f
                settings.joystickSpeedMps = v
                speedVal.text = "$v m/s"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        accBar.progress = settings.accuracyMeters.toInt()
        accVal.text = "${settings.accuracyMeters.toInt()} m"
        accBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                val v = p.coerceAtLeast(1).toFloat()
                settings.accuracyMeters = v
                accVal.text = "${v.toInt()} m"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        disclaimerRow.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.disclaimer_title)
                .setMessage(R.string.disclaimer_body)
                .setPositiveButton(R.string.ok, null)
                .show()
        }
        aboutRow.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.title_about)
                .setMessage(getString(R.string.about_body, BuildConfig.VERSION_NAME))
                .setPositiveButton(R.string.ok, null)
                .show()
        }
    }
}
