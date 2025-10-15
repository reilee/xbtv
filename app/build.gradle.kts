plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    // detekt 静态检查
    alias(libs.plugins.detekt)
}

import java.util.Properties
import com.android.build.api.variant.ApplicationAndroidComponentsExtension

// --- 自动递增 versionCode 逻辑开始 ---
// 读取根目录 version.properties 中的 versionCode，并在执行 assemble / bundle 相关任务时 +1 后写回
val versionPropsFile = rootProject.file("version.properties")
val versionProps = Properties()
if (versionPropsFile.exists()) {
    versionPropsFile.inputStream().use { versionProps.load(it) }
}
val currentVersionCode = versionProps.getProperty("versionCode")?.toIntOrNull() ?: 1
val invokedTasks = gradle.startParameter.taskNames
// 判断这次构建是否是实际打包（避免 Gradle Sync / help 等任务无意义自增）
val isPackagingBuild = invokedTasks.any { name ->
    name.contains("assemble", ignoreCase = true) ||
    name.contains("bundle", ignoreCase = true) ||
    name.contains("publish", ignoreCase = true)
}
// 可通过 -PnoAutoVersion 跳过自增（例如想复现某一版本）
val skipAuto = project.hasProperty("noAutoVersion")
val usedVersionCode = if (isPackagingBuild && !skipAuto) currentVersionCode + 1 else currentVersionCode
if (isPackagingBuild && !skipAuto) {
    versionProps["versionCode"] = usedVersionCode.toString()
    versionPropsFile.outputStream().use { versionProps.store(it, "auto increment by build script") }
    println("[versionCode] 自动递增: $currentVersionCode -> $usedVersionCode")
} else {
    println("[versionCode] 使用现有: $usedVersionCode (未触发自增条件或已跳过)")
}
// --- 自动递增 versionCode 逻辑结束 ---

android {
    namespace = "com.xiaobaotv.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.xiaobaotv.app"
        minSdk = 21
        targetSdk = 35
        // versionCode 原先写死为 1，这里改为自动逻辑产出的 usedVersionCode
        versionCode = usedVersionCode
        // 这里保持 versionName 固定；如需也根据版本号生成，可再拓展
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    // 引入 Compose BOM 统一版本
    implementation(platform(libs.androidx.compose.bom))

    // Compose 基础
    implementation(libs.androidx.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.foundation.layout)

    // ExoPlayer - 视频播放
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.ui)

    // 网络请求
    implementation(libs.okhttp)
    implementation(libs.jsoup)

    // 图片加载
    implementation(libs.coil.compose)

    // 协程
    implementation(libs.kotlinx.coroutines.android)

    // 其他
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)

    // Navigation
    implementation(libs.androidx.navigation.compose)
}

// 重新启用 detekt 自定义配置：忽略失败，仅生成报告
detekt {
    config.setFrom(files(rootProject.file("detekt.yml")))
    buildUponDefaultConfig = true
    ignoreFailures = true
}

// ========= 移除失效的 variant API outputFileName 设置 =========
// 说明：当前 AGP 版本下 ApplicationVariantOutput 未暴露 outputFileName 属性，导致编译错误。
// 如需未来使用官方 API，可在 AGP 支持后改为：
// extensions.configure<ApplicationAndroidComponentsExtension>("androidComponents") {
//     onVariants(selector().withBuildType("release")) { variant ->
//         variant.outputs.forEach { output ->
//             // println("Variant: ${variant.name}, output: ${output javaClass}")
//         }
//     }
// }
// 目前使用 finalizeBy 的 Copy 任务实现重命名。
// ============================================================

// 确保生成期望文件名：复制已生成的 release APK（unsigned 或 signed）并重命名
val produceRenamedApk by tasks.registering {
    group = "build"
    description = "复制并重命名 release APK 为 xiaobaotv-release.apk"
    doLast {
        val apkDir = layout.buildDirectory.dir("outputs/apk/release").get().asFile
        println("[produceRenamedApk] 目录: ${apkDir.absolutePath}")
        val candidates = listOf(
            apkDir.resolve("app-release.apk"),
            apkDir.resolve("app-release-unsigned.apk"),
            apkDir.resolve("xiaobaotv-release.apk") // 如果已经存在
        )
        val source = candidates.firstOrNull { it.exists() && it.name != "xiaobaotv-release.apk" }
        val target = apkDir.resolve("xiaobaotv-release.apk")
        if (source == null) {
            if (target.exists()) {
                println("[produceRenamedApk] 目标已存在: ${target.name}")
            } else {
                println("[produceRenamedApk] 未找到可复制的源 APK (app-release*.apk)")
            }
        } else {
            source.copyTo(target, overwrite = true)
            println("[produceRenamedApk] 已生成 ${target.name} (来源: ${source.name})")
        }
    }
}

afterEvaluate {
    tasks.matching { it.name == "assembleRelease" }.configureEach {
        finalizedBy(produceRenamedApk)
    }
}
