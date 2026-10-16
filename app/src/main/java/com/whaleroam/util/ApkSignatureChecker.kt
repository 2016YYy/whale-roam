package com.whaleroam.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File

/**
 * 统一签名校验。
 *
 * 所有 Release 包均使用同一把密钥签名，应用内更新下载的 APK 在唤起安装前
 * 必须先通过本校验：只有签名与当前已安装应用完全一致时才允许安装。
 * 这样既保证覆盖升级不丢数据，也能在安装包被篡改/来源不符时提前拦截。
 */
object ApkSignatureChecker {

    /** 校验 apkFile 的签名是否与当前已安装的应用一致 */
    fun matchesInstalledApp(context: Context, apkFile: File): Boolean {
        if (!apkFile.exists() || apkFile.length() <= 0L) return false
        return try {
            val pm = context.packageManager
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            }
            @Suppress("DEPRECATION")
            val installedSigners = pm.getPackageInfo(context.packageName, flags).signingInfo
                ?.apkContentsSigners ?: return false

            val archived = pm.getPackageArchiveInfo(apkFile.absolutePath, flags) ?: return false
            archived.applicationInfo?.let {
                it.sourceDir = apkFile.absolutePath
                it.publicSourceDir = apkFile.absolutePath
            }
            val archivedSigners = archived.signingInfo?.apkContentsSigners ?: return false

            if (installedSigners.size != archivedSigners.size) return false
            installedSigners.contentEquals(archivedSigners)
        } catch (t: Throwable) {
            false
        }
    }
}
