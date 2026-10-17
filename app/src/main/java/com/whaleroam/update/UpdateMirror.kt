package com.whaleroam.update

import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * 镜像源管理（v1.5.0 更新检测与下载加速）
 *
 * 背景：国内直连 raw.githubusercontent.com / github.com 时好时坏，
 * 原来单源串行请求最坏要 10s+ 才能确认失败。这里做两件事：
 *
 *  1. [fetchLatestJsonConcurrently] 并发请求全部 latest.json 源，
 *     谁先成功就用谁，单源超时 6s，全部失败才算失败；
 *  2. [pickFastestMirror] 并发探测候选 APK 地址，返回响应最快的一个，探测超时 3s。
 *
 * 只使用 JDK 自带的 HttpURLConnection（[HttpURLConnection]）与 java.util.concurrent，
 * 不引入任何第三方依赖（v1.5.0 技术约束：离线构建，拉不了新包）。
 */
object UpdateMirror {

    private const val OWNER = "2016YYy"
    private const val REPO = "whale-roam"
    private const val BRANCH = "main"

    /** GitHub 原始文件直连地址（latest.json 被各社区镜像包装的原始 URL） */
    private const val RAW_LATEST_URL =
        "https://raw.githubusercontent.com/$OWNER/$REPO/$BRANCH/latest.json"

    /** APK 直连下载地址前缀：只有命中官方 Releases 的地址才做镜像包装 */
    private const val APK_URL_PREFIX = "https://github.com/$OWNER/$REPO/releases/download/"

    /** 单个 latest.json 源的连接 / 读取超时（6s） */
    private const val JSON_TIMEOUT_MS = 6000

    /** 单个候选地址测速探测的超时（3s） */
    private const val PROBE_TIMEOUT_MS = 3000

    /** 测速探测的 Range 长度：只取前 1KB，够判断连通性与响应速度 */
    private const val PROBE_RANGE_BYTES = 1024

    /** 探测时最多读取的响应体字节数（读到就断开，不浪费流量） */
    private const val PROBE_MAX_READ = 2048

    /** jsDelivr 走的是 @分支/文件路径 形式，不走「前缀包装」，单独列出 */
    private const val JSDELIVR_LATEST_URL =
        "https://cdn.jsdelivr.net/gh/$OWNER/$REPO@$BRANCH/latest.json"

    /**
     * latest.json 源列表：直连 + ghfast.top / gh-proxy.com / ghproxy.net / ghproxy.cc
     * 四种镜像包装 + cdn.jsdelivr.net，共 6 个源。
     * 顺序同时是全部失败时的兜底顺序，后续增删镜像只改这张表。
     *
     * 2026-10-17 实测：原列表中的 moeyy.xyz 及其关联域名（github.moeyy.xyz）
     * 对 latest.json 与 APK 均返回 404 / 连接超时，已替换为实测可用的 ghproxy.cc。
     * 增删镜像前请先用 tools/audit/probe_mirrors.py 实测一次。
     */
    val LATEST_JSON_SOURCES: List<String> = listOf(
        RAW_LATEST_URL,
        "https://ghfast.top/$RAW_LATEST_URL",
        "https://gh-proxy.com/$RAW_LATEST_URL",
        "https://ghproxy.net/$RAW_LATEST_URL",
        "https://ghproxy.cc/$RAW_LATEST_URL",
        JSDELIVR_LATEST_URL
    )

    /**
     * APK 下载候选地址：直连 github.com/releases/download + 同 5 个镜像包装该 URL。
     *
     * @param apkUrl latest.json 里给出的原始下载地址
     */
    fun apkCandidates(apkUrl: String): List<String> {
        val url = apkUrl.trim()
        if (url.isEmpty()) return emptyList()
        // 非官方 Releases 地址（自建源等）不做镜像包装，保持原样
        if (!url.startsWith(APK_URL_PREFIX)) return listOf(url)
        return listOf(url) + listOf(
            "https://ghfast.top/$url",
            "https://gh-proxy.com/$url",
            "https://ghproxy.net/$url",
            "https://ghproxy.cc/$url"
        )
    }

    /**
     * 从任意 URL 提取域名（用于「当前线路」展示）。
     * 解析失败时退化为原始字符串，保证 UI 一定有内容可显示。
     */
    fun hostOf(url: String): String {
        if (url.isBlank()) return ""
        val cleaned = url.trim()
        // 手动切分，避免依赖 android.net.Uri（对象层保持纯净，便于单测）
        val noScheme = cleaned.substringAfter("://", cleaned)
        val host = noScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        // 去掉 user:pass@ 形式中的凭据段
        val hostOnly = host.substringAfterLast('@', host)
        return hostOnly.ifBlank { cleaned }
    }

    /** latest.json 并发获取回调（回调可能来自任意线程，UI 方需自行切主线程） */
    interface LatestJsonCallback {
        /**
         * 有源成功返回即回调；[source] 为实际命中的源地址。
         * 与 [onError] 合计只保证回调一次。
         */
        fun onSuccess(source: String, body: String)

        /** 所有源都失败才回调；[msg] 为汇总后的失败原因 */
        fun onError(msg: String)
    }

    /**
     * 并发请求全部 latest.json 源，谁先成功就用谁。
     *
     * 实现要点（v1.5.0 「只回调一次」的关键）：
     *  - [AtomicBoolean.delivered] 是所有成功回调的唯一闸门，用 compareAndSet(false, true)
     *    抢占，只有第一个成功的线程能通过，后到的线程无论多快都直接被丢掉；
     *  - 失败路径不抢闸门：每个源失败只把自己的原因追加进共享列表，等 [latch] 放行
     *    （所有源都已结束）后再由最后一个失败的线程用 getAndSet(true) 领取「全部失败」的
     *    播报权，同样只有一个线程能播报；
     *  - 两条路径用的是同一个 [AtomicBoolean]，互斥必然成立，不会出现成功 + 失败双回调。
     *
     * 抢先成功的源主要以短超时取胜：单个源超时 [JSON_TIMEOUT_MS]（6s）。
     */
    fun fetchLatestJsonConcurrently(cb: LatestJsonCallback) {
        val sources = LATEST_JSON_SOURCES
        val pool = Executors.newFixedThreadPool(sources.size)
        val latch = CountDownLatch(sources.size)
        val delivered = AtomicBoolean(false)
        // 失败原因收集：同步列表 + latch 的 happens-before 保证可见性
        val failureReasons = Collections.synchronizedList(mutableListOf<String>())

        sources.forEach { source ->
            pool.execute {
                if (delivered.get()) return@execute // 已有胜利者，不再白费流量
                try {
                    val body = fetchText(source, JSON_TIMEOUT_MS, JSON_TIMEOUT_MS)
                    if (delivered.compareAndSet(false, true)) {
                        cb.onSuccess(source, body)
                    }
                } catch (t: Throwable) {
                    failureReasons.add(source + " -> " + (t.message ?: t.javaClass.simpleName))
                } finally {
                    latch.countDown()
                }
            }
        }

        // 等全部源结束；成功方早在结束前就回调过了，这里只处理「全军覆没」的情况
        latch.await()
        pool.shutdown()
        if (!delivered.get()) {
            // 极端时序下最后一个源可能在 latch 放行前正好成功，再确认一次
            if (!delivered.compareAndSet(false, true)) return
            val joined = synchronized(failureReasons) { failureReasons.toList() }
                .joinToString("; ")
                .ifBlank { "all sources failed" }
            cb.onError(joined)
        }
    }

    /** 从 [url] 拉取文本，非 200 一律视为失败 */
    private fun fetchText(url: String, connectMs: Int, readMs: Int): String {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = connectMs
                readTimeout = readMs
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Cache-Control", "no-cache")
            }
            val code = conn.responseCode
            if (code != 200) throw java.io.IOException("HTTP $code")
            // readText() 是 kotlin.io 对 Reader 的扩展，InputStream 没有，
            // 必须先包一层 bufferedReader；同时显式声明类型避开平台类型推断问题
            val input: java.io.InputStream = conn.inputStream
            return input.bufferedReader().use { it.readText() }
        } finally {
            conn?.disconnect()
        }
    }

    /** 并发测速回调（回调可能来自任意线程） */
    interface ProbeCallback {
        /** [url] 为最快候选；全部失败时为首个候选（兜底直连），[elapsedMs] 为 -1 */
        fun onResult(url: String, elapsedMs: Long)
    }

    /**
     * 并发探测候选地址，返回响应最快的一个；全部失败才回退到第一个候选。
     *
     * 单源超时 [PROBE_TIMEOUT_MS]（3s）。策略：
     *  - 每个候选独立探测，谁快谁赢；
     *  - 一旦某个候选的耗时已明显领先且剩余候选不可能更快，可提前返回（[best] 非空即返回，
     *    因为探测本身有 3s 上限，最坏也就等 3s）；为简单可靠，这里等所有探测结束后再取最快，
     *    3s 上限让等待时间可控；
     *  - 单源探测失败只记 null，不影响其它候选。
     */
    fun pickFastestMirror(candidates: List<String>, cb: ProbeCallback) {
        if (candidates.isEmpty()) {
            cb.onResult("", -1)
            return
        }
        if (candidates.size == 1) {
            // 只有一个候选时也实测一次，把真实延迟反馈给上层；失败则回退自身
            val el = probeOnce(candidates[0])
            cb.onResult(candidates[0], el ?: -1)
            return
        }
        val pool = Executors.newFixedThreadPool(candidates.size)
        val latch = CountDownLatch(candidates.size)
        val best = AtomicReference<Pair<String, Long>?>(null)

        candidates.forEach { url ->
            pool.execute {
                try {
                    val el = probeOnce(url)
                    if (el != null) {
                        // 只在严格快于当前最优时替换；时间相等时保留先到者
                        while (true) {
                            val cur = best.get()
                            if (cur == null || el < cur.second) {
                                if (best.compareAndSet(cur, Pair(url, el))) break
                            } else {
                                break
                            }
                        }
                    }
                } catch (_: Throwable) {
                    // 单源探测失败不影响其它候选
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        pool.shutdown()
        val b = best.get()
        if (b != null) cb.onResult(b.first, b.second) else cb.onResult(candidates[0], -1)
    }

    /** 探测单个候选地址；失败返回 null。HEAD 不被支持时自动降级 Range 请求 */
    fun probeOnce(url: String): Long? {
        val head = requestRtt(url, "HEAD", null)
        if (head != null) return head
        return requestRtt(url, "GET", "bytes=0-" + (PROBE_RANGE_BYTES - 1))
    }

    /** 发起一次探测请求并返回耗时；状态码不 acceptable 或抛异常都返回 null */
    private fun requestRtt(url: String, method: String, range: String?): Long? {
        var conn: HttpURLConnection? = null
        val t0 = System.currentTimeMillis()
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = PROBE_TIMEOUT_MS
                readTimeout = PROBE_TIMEOUT_MS
                requestMethod = method
                instanceFollowRedirects = true
                if (range != null) setRequestProperty("Range", range)
            }
            val code = conn.responseCode
            // 200（全量）与 206（部分内容）都说明服务端可用
            if (code == 200 || code == 206) {
                if (method == "GET" && range != null) drainQuietly(conn.inputStream)
                return System.currentTimeMillis() - t0
            }
            return null
        } catch (_: Throwable) {
            return null
        } finally {
            conn?.disconnect()
        }
    }

    /** 探测用的轻量读取：只读前 [PROBE_MAX_READ] 字节，避免把整个 APK 拉下来 */
    private fun drainQuietly(input: InputStream) {
        val buf = ByteArray(2048)
        var total = 0
        try {
            while (total < PROBE_MAX_READ) {
                val n = input.read(buf)
                if (n <= 0) break
                total += n
            }
        } catch (_: Throwable) {
            // 探测读体失败无所谓，连接马上关闭
        }
    }
}
