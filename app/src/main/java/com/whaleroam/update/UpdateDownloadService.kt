package com.whaleroam.update

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.whaleroam.R
import com.whaleroam.WhaleRoamApp
import com.whaleroam.util.ApkSignatureChecker
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class UpdateDownloadService : Service() {

    companion object {
        const val ACTION_DOWNLOAD = "com.whaleroam.UPDATE_DOWNLOAD"
        const val ACTION_CANCEL = "com.whaleroam.UPDATE_CANCEL"
        const val EXTRA_URL = "url"
        const val EXTRA_VERSION_NAME = "versionName"

        /** 已下载完成的安装包固定路径（供对话框「立即安装」使用） */
        fun downloadedApk(context: Context): File =
            File(File(context.getExternalFilesDir(null), "updates"), "whale-roam-latest.apk")

        /** 从任意界面安装已下载的更新包 */
        fun installApk(context: Context, apk: File) {
            val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(installIntent)
        }
    }

    @Volatile private var cancelled = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                cancelled = true
                UpdateBus.post(UpdateBus.Event.Cancelled)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                val url = intent?.getStringExtra(EXTRA_URL)
                val verName = intent?.getStringExtra(EXTRA_VERSION_NAME) ?: ""
                if (url == null) { stopSelf(); return START_NOT_STICKY }
                cancelled = false
                startForeground(WhaleRoamApp.NOTIF_UPDATE_ID, buildProgressNotification(verName, 0))
                download(url, verName)
            }
        }
        return START_NOT_STICKY
    }

    private fun download(url: String, versionName: String) {
        thread(name="whale-apk-download") {
            var conn: HttpURLConnection? = null
            try {
                val outDir = File(getExternalFilesDir(null), "updates").apply { mkdirs() }
                val outFile = File(outDir, "whale-roam-latest.apk")
                if (outFile.exists()) outFile.delete()

                conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                    instanceFollowRedirects = true
                }
                val code = conn.responseCode
                if (code != 200) { fail("HTTP $code"); return@thread }
                val total = conn.contentLengthLong
                conn.inputStream.use { input ->
                    FileOutputStream(outFile).use { output ->
                        val buf = ByteArray(8192)
                        var read: Int
                        var progress = 0L
                        var lastPct = -1
                        while (input.read(buf).also { read = it } != -1) {
                            if (cancelled) { outFile.delete(); stopSelf(); return@thread }
                            output.write(buf, 0, read)
                            progress += read
                            val pct = if (total > 0) (progress * 100 / total).toInt() else -1
                            if (pct != lastPct) {
                                lastPct = pct
                                notifyProgress(versionName, pct)
                                UpdateBus.post(UpdateBus.Event.Progress(pct, progress, total))
                            }
                        }
                        output.flush()
                    }
                }
                done(outFile, versionName)
            } catch (t: Throwable) {
                fail(t.message ?: "download error")
            } finally {
                conn?.disconnect()
            }
        }
    }

    private fun cancelPendingIntent(): PendingIntent {
        val cancelIntent = Intent(this, UpdateDownloadService::class.java).apply {
            action = ACTION_CANCEL
        }
        return PendingIntent.getService(
            this, 1, cancelIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun notifyProgress(versionName: String, pct: Int) {
        val n = NotificationCompat.Builder(this, WhaleRoamApp.CHANNEL_UPDATE)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.service_downloading, versionName, pct))
            .setSmallIcon(R.drawable.ic_whale_notif)
            .setProgress(100, pct, pct < 0)
            .setOngoing(true)
            .addAction(0, getString(R.string.update_cancel), cancelPendingIntent())
            .build()
        nm().notify(WhaleRoamApp.NOTIF_UPDATE_ID, n)
    }

    private fun buildProgressNotification(versionName: String, pct: Int): Notification {
        return NotificationCompat.Builder(this, WhaleRoamApp.CHANNEL_UPDATE)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.service_downloading, versionName, pct))
            .setSmallIcon(R.drawable.ic_whale_notif)
            .setProgress(100, pct, true)
            .setOngoing(true)
            .addAction(0, getString(R.string.update_cancel), cancelPendingIntent())
            .build()
    }

    private fun done(apk: File, versionName: String) {
        // v1.4.0 修复：取消标志与下载线程存在竞争——用户点取消的瞬间下载可能刚好读完最后一个字节，
        // 于是 Cancelled 与 Done 双双回调，UI 先显示已取消又跳到"下载完成"。
        // 这里在进入完成流程时再检查一次，并对已取消场景补发一次 Cancelled 让 UI 状态收敛。
        if (cancelled) {
            apk.delete()
            UpdateBus.post(UpdateBus.Event.Cancelled)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        // 统一签名校验：安装包签名必须与当前应用一致，否则拒绝安装并提示
        if (!ApkSignatureChecker.matchesInstalledApp(this, apk)) {
            apk.delete()
            fail(getString(R.string.update_signature_mismatch))
            return
        }
        val uri: Uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", apk)
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val pi = PendingIntent.getActivity(this, 0, installIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(this, WhaleRoamApp.CHANNEL_UPDATE)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.service_download_done))
            .setSmallIcon(R.drawable.ic_whale_notif)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setOngoing(false)
            .build()
        nm().notify(WhaleRoamApp.NOTIF_UPDATE_ID, n)
        UpdateBus.post(UpdateBus.Event.Done(apk.absolutePath))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun fail(msg: String) {
        // 已取消的下载不应再报"失败"，否则用户会看到互相矛盾的提示
        if (cancelled) {
            UpdateBus.post(UpdateBus.Event.Cancelled)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        val n = NotificationCompat.Builder(this, WhaleRoamApp.CHANNEL_UPDATE)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("${getString(R.string.service_download_failed)}: $msg")
            .setSmallIcon(R.drawable.ic_whale_notif)
            .setAutoCancel(true)
            .build()
        nm().notify(WhaleRoamApp.NOTIF_UPDATE_ID, n)
        UpdateBus.post(UpdateBus.Event.Failed(msg))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun nm() = getSystemService(NotificationManager::class.java)

    override fun onDestroy() { cancelled = true; super.onDestroy() }
}