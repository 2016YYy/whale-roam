import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) load(FileInputStream(keystorePropsFile))
}

android {
    namespace = "com.whaleroam"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.whaleroam"
        minSdk = 26
        targetSdk = 35
        // 版本号必须随发版提交入库（v1.2/v1.3 曾因漏提交导致源码构建包版本落后于线上）
        versionCode = 6
        versionName = "1.5.0"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        create("release") {
            if (keystorePropsFile.exists()) {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

// ---------------------------------------------------------------------------
// 每次构建自动敏感信息扫描（gitleaks）
//
// - 任何 assembleXxx / bundleXxx 都会先跑 secretScan，避免把密钥打进包或推上去
// - 工具在 tools/gitleaks/gitleaks.exe（不入库）；缺失时打印提示并跳过，不影响无工具环境
// - 命中即让构建失败（不做降级），要看详情请查 build/gitleaks-report.json
// ---------------------------------------------------------------------------
val gitleaksExe = rootProject.file("tools/gitleaks/gitleaks.exe")
val gitleaksReport = rootProject.file("build/gitleaks-report.json")

val secretScan by tasks.registering(Exec::class) {
    group = "verification"
    description = "gitleaks 敏感信息扫描（构建前自动执行）"
    workingDir = rootProject.projectDir
    isIgnoreExitValue = false

    onlyIf {
        if (!gitleaksExe.exists()) {
            logger.warn("[secretScan] 未找到 gitleaks：${gitleaksExe.absolutePath}，已跳过扫描。安装：tools/install-gitleaks.ps1")
            false
        } else if (!rootProject.file(".git").exists()) {
            logger.warn("[secretScan] 非 Git 仓库，跳过历史扫描")
            false
        } else {
            true
        }
    }

    doFirst {
        gitleaksReport.parentFile.mkdirs()
        commandLine(
            gitleaksExe.absolutePath, "detect",
            "--source", ".",
            "--no-banner",
            "--redact",
            "--report-path", gitleaksReport.absolutePath,
            "--report-format", "json"
        )
    }
}

// 挂到所有构建入口之前（debug / release 都会经过 preBuild）
tasks.matching { it.name == "preBuild" || it.name.startsWith("preBuild") }.configureEach {
    dependsOn(secretScan)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.lifecycle:lifecycle-service:2.8.6")
    implementation("org.osmdroid:osmdroid-android:6.1.18")
}