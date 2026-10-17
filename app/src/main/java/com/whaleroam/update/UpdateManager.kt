package com.whaleroam.update

import android.content.Context
import com.whaleroam.BuildConfig
import org.json.JSONObject

/**
 * 更新检查（v1.5.0 改为多源并发）
 *
 * 原来单源串行请求 raw.githubusercontent.com，国内网络下最坏 10s+ 才确认失败。
 * 现在并发请求 [UpdateMirror.LATEST_JSON_SOURCES] 里全部 6 个源，谁先成功就用谁，
 * 并把命中的镜像域名记录进 [UpdateInfo.mirror] 供界面展示。
 *
 * 回调可能来自任意线程，调用方需自行切换到主线程。
 */
object UpdateManager {

    interface Callback {
        fun onNeedUpdate(info: UpdateInfo)
        fun onLatest()
        fun onError(msg: String)
    }

    fun check(context: Context, currentCode: Int, cb: Callback) {
        UpdateMirror.fetchLatestJsonConcurrently(object : UpdateMirror.LatestJsonCallback {
            override fun onSuccess(source: String, body: String) {
                try {
                    val json = JSONObject(body)
                    val info = UpdateInfo(
                        versionName = json.getString("versionName"),
                        versionCode = json.getInt("versionCode"),
                        changelog = json.optString("changelog", ""),
                        apkUrl = json.optString("apkUrl", "").ifBlank {
                            // latest.json 没给 apkUrl 时，用这个源反推安装包地址
                            fallbackApkUrl(source)
                        },
                        mirror = UpdateMirror.hostOf(source)
                    )
                    if (info.versionCode > currentCode) cb.onNeedUpdate(info) else cb.onLatest()
                } catch (t: Throwable) {
                    // 某源返回了非预期内容（如 CDN 错误页），按解析失败处理
                    cb.onError(t.message ?: "invalid latest.json")
                }
            }

            override fun onError(msg: String) {
                cb.onError(msg)
            }
        })
    }

    /**
     * latest.json 缺 apkUrl 时的兜底安装包地址。
     * 从源地址里取出版本号不可行（latest.json 不含 tag），这里只能用分支最新构建的保守形式，
     * 实际发布流程始终会写 apkUrl，此分支仅作防御。
     */
    private fun fallbackApkUrl(source: String): String {
        // jsDelivr 的路径带 @main，包装型镜像带完整前缀，统一截出 "owner/repo" 段不可靠，
        // 因此直接返回仓库 Releases 页，交由用户自行下载
        return "https://github.com/2016YYy/whale-roam/releases"
    }
}
