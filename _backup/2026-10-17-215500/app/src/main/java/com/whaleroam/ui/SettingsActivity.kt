package com.whaleroam.ui

import android.os.Bundle
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.materialswitch.MaterialSwitch
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

        // ---------- 定位注入仿真 ----------
        bindSeekBar(R.id.accBar, R.id.accVal,
            init = settings.accuracyMeters.toInt(),
            fmt = { "${it.coerceAtLeast(1)} m" },
            onChange = { p -> settings.accuracyMeters = p.coerceAtLeast(1).toFloat() })

        bindSeekBar(R.id.altBar, R.id.altVal,
            init = settings.altitudeMeters.toInt(),
            fmt = { "$it m" },
            onChange = { p -> settings.altitudeMeters = p.toDouble() })

        val offsetSwitch = findViewById<MaterialSwitch>(R.id.randomOffsetSwitch)
        offsetSwitch.isChecked = settings.randomOffsetEnabled
        offsetSwitch.setOnCheckedChangeListener { _, checked -> settings.randomOffsetEnabled = checked }

        bindSeekBar(R.id.offsetBar, R.id.offsetVal,
            init = settings.offsetRadiusMeters.toInt(),
            fmt = { "${it.coerceAtLeast(1)} m" },
            onChange = { p -> settings.offsetRadiusMeters = p.coerceAtLeast(1).toFloat() })

        // ---------- 摇杆三档速度（走 / 跑 / 骑） ----------
        bindSeekBar(R.id.walkBar, R.id.walkVal,
            init = (settings.walkSpeedMps * 10).toInt(),
            fmt = { "${"%.1f".format(it / 10f)} m/s" },
            onChange = { p -> settings.walkSpeedMps = (p / 10f).coerceAtLeast(0.1f) })

        bindSeekBar(R.id.runBar, R.id.runVal,
            init = (settings.runSpeedMps * 10).toInt(),
            fmt = { "${"%.1f".format(it / 10f)} m/s" },
            onChange = { p -> settings.runSpeedMps = (p / 10f).coerceAtLeast(0.1f) })

        bindSeekBar(R.id.bikeBar, R.id.bikeVal,
            init = (settings.bikeSpeedMps * 10).toInt(),
            fmt = { "${"%.1f".format(it / 10f)} m/s" },
            onChange = { p -> settings.bikeSpeedMps = (p / 10f).coerceAtLeast(0.1f) })

        // ---------- 历史记录有效期 ----------
        // v1.4.0 修复：SeekBar 的 max 为 364（即 progress 0~364），对应天数 1~365，
        // 此前 max 只有 30，用户无法把有效期设到 30 天以上，与设置项 1~365 的约束不符
        bindSeekBar(R.id.historyDaysBar, R.id.historyDaysVal,
            init = (settings.historyExpirationDays - 1).coerceIn(0, 364),
            fmt = { "${it + 1} 天" },
            onChange = { p -> settings.historyExpirationDays = (p + 1).coerceIn(1, 365) })

        // ---------- 关于 ----------
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

    /** 通用 SeekBar 绑定：init 初始进度，fmt 格式化显示，onChange 写回设置 */
    private fun bindSeekBar(
        barId: Int, valId: Int,
        init: Int, fmt: (Int) -> String, onChange: (Int) -> Unit
    ) {
        val bar = findViewById<SeekBar>(barId)
        val label = findViewById<TextView>(valId)
        bar.progress = init
        label.text = fmt(init)
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                label.text = fmt(p)
                onChange(p)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }
}