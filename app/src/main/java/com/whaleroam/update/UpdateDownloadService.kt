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
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * 更新下载服务（v1.5.0 增加镜像加速与断点续传）。
 *
 * 下载流程：
 *  1. [UpdateMirror.pickFastestMirror] 并发测速所有候选地址，选最快的一条；
 *  2. 下载中失败（超时 / 断流 / 非 2xx）自动切下一个候选地址，
 *     并用 HTTP Range 从「已下载字节数」处续传，最多重试 [MAX_SWITCH] 次；
 *  3. 每次切换 / 命中线路都通过 [UpdateBus.Event.MirrorChanged] 上报，
 *     界面据此显示「当前线路：<域名>」。
 *
 * 原有的取消、进度通知、签名校验、唤起安装等行为保持不变。
 */
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

        /** 下载中途失败时最多切换几个候选地址（不含首个） */
        private const val MAX_SWITCH = 2
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

    /**
     * 带镜像加速与断点续传的下载。
     *
     * [rawUrl] 为 latest.json 给出的原始地址，先并发测速选出最快线路，
     * 失败时依次切换到剩余候选，并用 Range 续传。
     */
    private fun download(rawUrl: String, versionName: String) {
        thread(name="whale-apk-download") {
            val outDir = File(getExternalFilesDir(null), "updates").apply { mkdirs() }
            val outFile = File(outDir, "whale-roam-latest.apk")
            val candidates = UpdateMirror.apkCandidates(rawUrl)
            if (candidates.isEmpty()) {
                fail(getString(R.string.mirror_no_candidate))
                return@thread
            }

            // 1) 并发测速：选最快的线路。同步等待，失败时回退到第一个候选（直连）
            val chosen = java.util.concurrent.atomic.AtomicReference<String?>(null)
            val latch = java.util.concurrent.CountDownLatch(1)
            // 注意：ProbeCallback 是普通接口（非 fun interface），
            // 不能用 lambda 简写，必须显式 object，否则 Kotlin 类型推断会失败
            UpdateMirror.pickFastestMirror(candidates, object : UpdateMirror.ProbeCallback {
                override fun onResult(url: String, elapsedMs: Long) {
                    chosen.set(url)
                    latch.countDown()
                }
            })
            latch.await()
            val order = ArrayList<String>()
            val first = chosen.get() ?: candidates[0]
            order.add(first)
            // 其余候选按原顺序补在后面，作为失败时的切换目标
            candidates.filter { it != first }.forEach { order.add(it) }

            // 2) 依次尝试，失败切换 + Range 续传
            var attempt = 0
            var succeeded = false
            while (attempt <= MAX_SWITCH && !cancelled) {
                val target = order[attempt]
                reportMirror(target)
                val resumeFrom = if (attempt == 0) 0L else outFile.length().coerceAtLeast(0L)
                try {
                    downloadOne(target, outFile, versionName, resumeFrom)
                    succeeded = true
                    break
                } catch (t: Throwable) {
                    if (cancelled) break
                    attempt++
                    // 切下一个候选前把已下载的部分保留，续传要用
                }
            }

            if (cancelled) {
                outFile.delete()
                stopSelf()
                return@thread
            }
            if (succeeded) {
                done(outFile, versionName)
            } else {
                outFile.delete()
                fail(getString(R.string.mirror_all_failed))
            }
        }
    }

    /**
     * 下载单个候选地址到 [outFile]。
     *
     * @param resumeFrom 从该字节偏移处开始写（>0 时带 Range 头续传）。
     *                  **注意**：调用方需保证 outFile 已存在且长度 >= resumeFrom；
     *                  本方法用 RandomAccessFile 定位到 resumeFrom 后追加写。
     * @throws Exception 网络错误 / 非 2xx / 内容异常
     */
    private fun downloadOne(url: String, outFile: File, versionName: String, resumeFrom: Long) {
        if (!outFile.exists()) {
            outFile.parentFile?.mkdirs()
            outFile.createNewFile()
        }
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
                instanceFollowRedirects = true
                if (resumeFrom > 0) setRequestProperty("Range", "bytes=$resumeFrom-")
            }
            val code = conn.responseCode
            if (code != 200 && code != 206) throw java.io.IOException("HTTP $code")

            // 服务端不支持 Range 时会返回 200 且带全量内容，此时必须从头覆盖写
            val appendMode = code == 206 && resumeFrom > 0
            val startBytes = if (appendMode) resumeFrom else 0L

            // 已下载量：续传时把已有部分算进去，进度条才不会倒退
            val baseProgress = startBytes
            val contentLength = if (code == 206) {
                // 206 时 contentLengthLong 是剩余部分的大小
                conn.contentLengthLong + startBytes
            } else {
                conn.contentLengthLong
            }
            val total = contentLength.coerceAtLeast(0L)

            conn.inputStream.use { input ->
                RandomAccessFile(outFile, "rw").use { raf ->
                    if (!appendMode) raf.setLength(0) // 全量模式：清空旧内容
                    raf.seek(startBytes)
                    val buf = ByteArray(8192)
                    var read: Int
                    var progress = startBytes
                    var lastPct = -1
                    while (input.read(buf).also { read = it } != -1) {
                        if (cancelled) throw java.io.IOException("cancelled")
                        raf.write(buf, 0, read)
                        progress += read
                        val pct = if (total > 0) (progress * 100 / total).toInt() else -1
                        if (pct != lastPct && pct >= 0) {
                            lastPct = pct
                            notifyProgress(versionName, pct)
                            UpdateBus.post(UpdateBus.Event.Progress(pct, progress, total))
                        } else if (pct < 0) {
                            // 总大小未知：按 -1 上报，界面走不确定进度
                            notifyProgress(versionName, -1)
                            UpdateBus.post(UpdateBus.Event.Progress(-1, progress, 0L))
                        }
                    }
                }
            }

            // 完整性粗校验：拿到 total 时必须一致，否则视为下载不完整
            if (total > 0 && outFile.length() != total) {
                throw java.io.IOException("size mismatch: got ${'$'}{outFile.length()} expect ${'$'}total")
            }
        } finally {
            conn?.disconnect()
        }
    }

    /** 把当前使用的线路域名广播给界面 */
    private fun reportMirror(url: String) {
        UpdateBus.post(UpdateBus.Event.MirrorChanged(UpdateMirror.hostOf(url)))
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
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
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
            .setContentText("${getString(R.string.service_download_failed)}: $msg")
            .setContentTitle(getString(R.string.app_name))
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
