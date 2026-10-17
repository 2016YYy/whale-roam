package com.whaleroam.update

/**
 * 更新信息
 *
 * @param versionName 线上版本名（如 1.5.0）
 * @param versionCode 线上版本号，与 BuildConfig.VERSION_CODE 比较判断是否有更新
 * @param changelog 更新说明
 * @param apkUrl 安装包下载地址（latest.json 给出的原始地址）
 * @param mirror 实际提供 latest.json 的镜像域名（v1.5.0 多源并发），
 *               例如 "raw.githubusercontent.com" / "ghfast.top" / "cdn.jsdelivr.net"。
 *               为空字符串表示未记录（如从本地或缓存构造）。
 */
data class UpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val changelog: String,
    val apkUrl: String,
    val mirror: String = ""
)
