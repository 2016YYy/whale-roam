package com.whaleroam.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.whaleroam.BuildConfig
import com.whaleroam.R
import com.whaleroam.update.UpdateBus
import com.whaleroam.update.UpdateDownloadService
import com.whaleroam.update.UpdateInfo
import com.whaleroam.update.UpdateManager
import java.io.File
import java.util.Locale

/**
 * CloudDrive 风格的检查更新对话框：
 *
 * 状态流转：
 *   CHECKING（正在检查）→ FOUND（发现新版本，展示更新说明）
 *   FOUND --立即下载--> DOWNLOADING（内嵌进度条 + 百分比 + 已下载/总量）
 *   DOWNLOADING → DONE（立即安装）/ FAILED（重试）/ CANCELLED（重新下载）
 *
 * 下载由前台 Service 承担：关闭对话框后仍可在通知栏继续后台下载。
 */
class UpdateDialog private constructor(
    private val activity: Activity
) : UpdateBus.Listener {

    enum class State { CHECKING, FOUND, DOWNLOADING, DONE, FAILED, CANCELLED }

    private val view = LayoutInflater.from(activity).inflate(R.layout.dialog_update, null)
    private val title: TextView = view.findViewById(R.id.updateTitle)
    private val body: TextView = view.findViewById(R.id.updateBody)
    private val progressArea: View = view.findViewById(R.id.progressArea)
    private val progress: LinearProgressIndicator = view.findViewById(R.id.updateProgress)
    private val sizeText: TextView = view.findViewById(R.id.updateSizeText)
    private val pctText: TextView = view.findViewById(R.id.updatePctText)
    private val resultText: TextView = view.findViewById(R.id.updateResultText)
    private val negativeBtn: MaterialButton = view.findViewById(R.id.updateNegativeButton)
    private val positiveBtn: MaterialButton = view.findViewById(R.id.updatePositiveButton)

    private var dialog: AlertDialog? = null
    private var state: State = State.CHECKING
    private var info: UpdateInfo? = null
    private var apkFile: File? = null

    companion object {
        /** 手动检查更新入口（设置页 / 菜单） */
        fun startCheck(activity: Activity) {
            val d = UpdateDialog(activity)
            d.showChecking()
            UpdateManager.check(activity, BuildConfig.VERSION_CODE, object : UpdateManager.Callback {
                override fun onNeedUpdate(info: UpdateInfo) {
                    activity.runOnUiThread { d.showFound(info) }
                }
                override fun onLatest() {
                    activity.runOnUiThread {
                        d.dismiss()
                        android.widget.Toast.makeText(
                            activity, R.string.update_latest, android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                override fun onError(msg: String) {
                    activity.runOnUiThread { d.showError(msg) }
                }
            })
        }

        /** 已知有新版本时直接弹出（启动自动检查命中） */
        fun showFound(activity: Activity, info: UpdateInfo) {
            val d = UpdateDialog(activity)
            d.showFound(info)
        }
    }

    // ---------- 状态切换 ----------

    private fun showChecking() {
        state = State.CHECKING
        UpdateBus.register(this)
        dialog = build()
        title.text = activity.getString(R.string.update_check_title)
        body.text = activity.getString(R.string.update_checking_body)
        progressArea.visibility = View.GONE
        negativeBtn.visibility = View.GONE
        positiveBtn.visibility = View.GONE
    }

    private fun showFound(updateInfo: UpdateInfo) {
        info = updateInfo
        if (state != State.CHECKING) UpdateBus.register(this) // 二次进入（如失败重试）可能未注册
        state = State.FOUND
        if (dialog == null) {
            UpdateBus.register(this)
            dialog = build()
        }
        title.text = activity.getString(R.string.update_available, updateInfo.versionName)
        body.text = updateInfo.changelog.ifBlank { activity.getString(R.string.update_no_notes) }
        progressArea.visibility = View.GONE
        resultText.visibility = View.GONE
        negativeBtn.visibility = View.VISIBLE
        negativeBtn.text = activity.getString(R.string.update_dl_later)
        negativeBtn.setOnClickListener { dismiss() }
        positiveBtn.visibility = View.VISIBLE
        positiveBtn.text = activity.getString(R.string.update_dl_start)
        positiveBtn.isEnabled = true
        positiveBtn.setOnClickListener { startDownload() }
    }

    private fun startDownload() {
        val updateInfo = info ?: return
        state = State.DOWNLOADING
        resultText.visibility = View.GONE
        progressArea.visibility = View.VISIBLE
        progress.isIndeterminate = true
        sizeText.text = activity.getString(R.string.update_dl_indeterminate)
        pctText.text = ""
        body.text = activity.getString(R.string.update_dl_doing)
        negativeBtn.text = activity.getString(R.string.update_dl_cancel)
        negativeBtn.visibility = View.VISIBLE
        negativeBtn.setOnClickListener { cancelDownload() }
        positiveBtn.visibility = View.GONE

        val svc = Intent(activity, UpdateDownloadService::class.java).apply {
            action = UpdateDownloadService.ACTION_DOWNLOAD
            putExtra(UpdateDownloadService.EXTRA_URL, updateInfo.apkUrl)
            putExtra(UpdateDownloadService.EXTRA_VERSION_NAME, updateInfo.versionName)
        }
        ContextCompat.startForegroundService(activity, svc)
    }

    private fun cancelDownload() {
        val svc = Intent(activity, UpdateDownloadService::class.java).apply {
            action = UpdateDownloadService.ACTION_CANCEL
        }
        activity.startService(svc)
        // Cancelled 事件会驱动 UI 切换；这里直接恢复 FOUND 状态也行
        backToFound()
    }

    private fun backToFound() {
        val updateInfo = info ?: return dismiss()
        showFound(updateInfo)
    }

    private fun showError(msg: String) {
        if (state == State.CHECKING) {
            // 检查失败：弹错误提示后关闭
            dismiss()
            android.widget.Toast.makeText(
                activity,
                activity.getString(R.string.update_check_failed_toast, msg),
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }
        state = State.FAILED
        progressArea.visibility = View.GONE
        resultText.visibility = View.VISIBLE
        resultText.text = "${activity.getString(R.string.update_dl_failed)}: $msg"
        negativeBtn.visibility = View.VISIBLE
        negativeBtn.text = activity.getString(R.string.cancel)
        negativeBtn.setOnClickListener { dismiss() }
        positiveBtn.visibility = View.VISIBLE
        positiveBtn.text = activity.getString(R.string.update_retry)
        positiveBtn.setOnClickListener { startDownload() }
    }

    private fun build(): AlertDialog {
        return AlertDialog.Builder(activity)
            .setView(view)
            .setCancelable(state == State.FOUND || state == State.CHECKING)
            .create()
            .apply {
                setOnDismissListener {
                    UpdateBus.unregister(this@UpdateDialog)
                }
                show()
            }
    }

    private fun dismiss() {
        UpdateBus.unregister(this)
        dialog?.dismiss()
        dialog = null
    }

    // ---------- 下载事件（可能来自后台线程） ----------

    override fun onEvent(e: UpdateBus.Event) {
        activity.runOnUiThread {
            when (e) {
                is UpdateBus.Event.Progress -> {
                    if (state != State.DOWNLOADING) return@runOnUiThread
                    if (e.pct < 0) {
                        progress.isIndeterminate = true
                        sizeText.text = formatBytes(e.downloaded)
                        pctText.text = ""
                    } else {
                        progress.isIndeterminate = false
                        progress.setProgressCompat(e.pct, true)
                        sizeText.text = activity.getString(
                            R.string.update_downloaded_of, formatBytes(e.downloaded), formatBytes(e.total)
                        )
                        pctText.text = String.format(Locale.CHINA, "%d%%", e.pct)
                    }
                }
                is UpdateBus.Event.Done -> {
                    if (state != State.DOWNLOADING) return@runOnUiThread
                    state = State.DONE
                    apkFile = File(e.apkPath)
                    progress.isIndeterminate = false
                    progress.setProgressCompat(100, true)
                    pctText.text = "100%"
                    resultText.visibility = View.VISIBLE
                    resultText.setTextColor(ContextCompat.getColor(activity, R.color.whale_primary))
                    resultText.text = activity.getString(R.string.service_download_done)
                    negativeBtn.visibility = View.VISIBLE
                    negativeBtn.text = activity.getString(R.string.cancel)
                    negativeBtn.setOnClickListener { dismiss() }
                    positiveBtn.visibility = View.VISIBLE
                    positiveBtn.text = activity.getString(R.string.update_install_now)
                    positiveBtn.setOnClickListener {
                        apkFile?.let { f -> UpdateDownloadService.installApk(activity, f) }
                    }
                }
                is UpdateBus.Event.Failed -> showError(e.msg)
                UpdateBus.Event.Cancelled -> {
                    if (state == State.DOWNLOADING) {
                        state = State.CANCELLED
                        backToFound()
                    }
                }
            }
        }
    }

    private fun formatBytes(b: Long): String {
        return if (b >= 1024 * 1024) String.format(Locale.CHINA, "%.1f MB", b / 1024f / 1024f)
        else String.format(Locale.CHINA, "%.0f KB", b / 1024f)
    }
}
