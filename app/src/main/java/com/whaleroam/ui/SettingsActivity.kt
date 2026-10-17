package com.whaleroam.ui

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.materialswitch.MaterialSwitch
import com.whaleroam.BuildConfig
import com.whaleroam.R
import com.whaleroam.data.SettingsStore
import com.whaleroam.util.DrawableFactory
import com.whaleroam.util.ThemeSeed

class SettingsActivity : AppCompatActivity() {

    private lateinit var settings: SettingsStore

    /** 种子色色块视图缓存，切换选中态时只改描边，不重建 */
    private val swatches = ArrayList<Pair<View, Int>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // v1.5.0：种子色覆盖层必须在 setContentView 之前应用到主题，
        // 否则本次 inflate 出来的视图用的还是旧色
        ThemeSeed.applyTo(this, SettingsStore(this).themeSeedId)
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
        bindSeekBar(R.id.historyDaysBar, R.id.historyDaysVal,
            init = (settings.historyExpirationDays - 1).coerceIn(0, 364),
            fmt = { "${it + 1} 天" },
            onChange = { p -> settings.historyExpirationDays = (p + 1).coerceIn(1, 365) })

        // ---------- 外观：主题模式 + 8 套种子色（v1.5.0） ----------
        setupAppearance()

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
            // 只读展示，不影响 settings.disclaimerAccepted
            DisclaimerDialog.showReadOnly(this)
        }
        aboutRow.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.title_about)
                .setMessage(getString(R.string.about_body, BuildConfig.VERSION_NAME))
                .setPositiveButton(R.string.ok, null)
                .show()
        }
    }

    /**
     * 外观分组：主题模式单选 + 8 套种子色横向选择器。
     *
     * 模式切换走 AppCompatDelegate.setDefaultNightMode + recreate()，
     * 种子色切换只改主题覆盖层，同样 recreate() 让全界面重新着色。
     */
    private fun setupAppearance() {
        val modeRow = findViewById<TextView>(R.id.themeModeRow)
        val modeValue = findViewById<TextView>(R.id.themeModeValue)
        modeValue.text = getString(modeNameRes(settings.themeMode))
        modeRow.setOnClickListener { showThemeModeDialog(modeValue) }

        val container = findViewById<LinearLayout>(R.id.themeSeedContainer)
        container.removeAllViews()
        swatches.clear()
        val density = resources.displayMetrics.density
        val size = (44 * density).toInt()
        val margin = (8 * density).toInt()
        ThemeSeed.THEMES.forEach { seed ->
            val swatch = View(this).apply {
                background = DrawableFactory.seedSwatch(this@SettingsActivity, seed.id, seed.id == settings.themeSeedId)
                val lp = LinearLayout.LayoutParams(size, size)
                lp.marginEnd = margin
                layoutParams = lp
                contentDescription = getString(seed.nameRes)
                setOnClickListener { onSeedPicked(seed.id) }
            }
            swatches.add(Pair(swatch, seed.id))
            container.addView(swatch)
        }
    }

    /** 点某个种子色块：写设置、刷新描边、recreate 让全局重新着色 */
    private fun onSeedPicked(seedId: Int) {
        if (seedId == settings.themeSeedId) return
        settings.themeSeedId = seedId
        ThemeSeed.applyTo(this, seedId)
        refreshSwatchSelection()
        android.widget.Toast.makeText(
            this,
            getString(R.string.theme_seed_applied, getString(ThemeSeed.byId(seedId).nameRes)),
            android.widget.Toast.LENGTH_SHORT
        ).show()
        // recreate 会让整个界面按新种子色重新 inflate，比逐个视图改色更彻底
        recreate()
    }

    /** 只更新描边选中态，不重建视图 */
    private fun refreshSwatchSelection() {
        swatches.forEach { (view, id) ->
            view.background = DrawableFactory.seedSwatch(this, id, id == settings.themeSeedId)
        }
    }

    /** 主题模式单选对话框 */
    private fun showThemeModeDialog(valueLabel: TextView) {
        val labels = ThemeMode.ALL.map { getString(modeNameRes(it)) }.toTypedArray()
        var checked = ThemeMode.ALL.indexOf(settings.themeMode).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(R.string.theme_mode_title)
            .setSingleChoiceItems(labels, checked) { _, which -> checked = which }
            .setPositiveButton(R.string.ok) { _, _ ->
                val picked = ThemeMode.ALL[checked]
                if (picked != settings.themeMode) {
                    settings.themeMode = picked
                    valueLabel.text = getString(modeNameRes(picked))
                    android.widget.Toast.makeText(
                        this,
                        getString(R.string.theme_mode_applied, getString(modeNameRes(picked))),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    // setDefaultNightMode 会触发所有 Activity 重建，深浅色立即生效
                    AppCompatDelegate.setDefaultNightMode(ThemeMode.toNightMode(picked))
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun modeNameRes(mode: Int): Int = when (mode) {
        ThemeMode.DARK -> R.string.theme_mode_dark
        ThemeMode.LIGHT -> R.string.theme_mode_light
        else -> R.string.theme_mode_follow
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
