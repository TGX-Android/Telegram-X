import com.android.build.gradle.internal.api.ApkVariantOutputImpl
import java.util.*

plugins {
    id("com.android.application")
    id("module-plugin")
    id("cmake-plugin")
}

task<me.vkryl.task.GenerateResourcesAndThemesTask>("generateResourcesAndThemes") {
    group = "Setup"
    description = "Generates fresh strings, ids, theme resources and utility methods based on current static files"
}
task<me.vkryl.task.FetchLanguagesTask>("updateLanguages") {
    group = "Setup"
    description = "Generates and updates all strings.xml resources based on translations.telegram.org"
}
task<me.vkryl.task.UpdateExceptionsTask>("updateExceptions") {
    group = "Setup"
    description = "Updates exception class names with the app or TDLib version number in order to have separate group on Google Play Developer Console"
}
task<me.vkryl.task.GeneratePhoneFormatTask>("generatePhoneFormat") {
    group = "Setup"
    description = "Generates utility methods for phone formatting, e.g. +12345678901 -> +1 (234) 567 89-01"
}
task<me.vkryl.task.CheckEmojiKeyboardTask>("checkEmojiKeyboard") {
    group = "Setup"
    description = "Checks that all supported emoji can be entered from the keyboard"
}

android {
    defaultConfig {
        val properties = extra["properties"] as Properties

        applicationId = properties.getOrThrow("app.id")

        // Fields

        val versions = extra["versions"] as Properties
        val jniVersion = versions.getIntOrThrow("version.jni")
        val tdlibVersion = versions.getIntOrThrow("version.tdlib")
        val leveldbVersion = versions.getIntOrThrow("version.leveldb")

        buildConfigInt("SO_VERSION", (jniVersion + tdlibVersion + leveldbVersion))
        buildConfigInt("TDLIB_VERSION", tdlibVersion)

        buildConfigInt("TELEGRAM_API_ID", properties.getIntOrThrow("telegram.api_id"))
        buildConfigString("TELEGRAM_API_HASH", properties.getProperty("telegram.api_hash"))

        buildConfigString("TELEGRAM_RESOURCES_CHANNEL", Telegram.RESOURCES_CHANNEL)
        buildConfigString("TELEGRAM_UPDATES_CHANNEL", Telegram.UPDATES_CHANNEL)

        buildConfigInt("EMOJI_VERSION", Emoji.VERSION)
        buildConfigString("EMOJI_BUILTIN_ID", Emoji.BUILTIN_ID)

        buildConfigString("LANGUAGE_PACK", Telegram.LANGUAGE_PACK)
        buildConfigString("YOUTUBE_API_KEY", properties.getOrThrow("youtube.api_key"))

        buildConfigString("THEME_FILE_EXTENSION", App.THEME_EXTENSION)

        buildConfigString("PROJECT_NAME", properties.getOrThrow("app.name"))
        buildConfigString("MARKET_URL", "https://play.google.com/store/apps/details?id=${applicationId}")

        buildConfigString("DOWNLOAD_URL", properties.getOrThrow("app.download_url"))

        val commitShort = extra["commit_short"] as String
        val commitLong = extra["commit_long"] as String
        val remoteUrl = extra["remote_url"] as String
        val commitUrl = extra["commit_url"] as String
        val commitDate = extra["commit_date"] as Long
        val commitBranch = extra["commit_branch"] as String

        buildConfigString("SOURCES_URL", properties.getProperty("app.sources_url", remoteUrl))
        buildConfigString("REMOTE_URL", remoteUrl)
        buildConfigString("COMMIT_URL", commitUrl)
        buildConfigString("COMMIT_BRANCH", commitBranch)
        buildConfigString("COMMIT_SHORT", commitShort)
        buildConfigString("COMMIT_LONG", commitLong)
        buildConfigLong("COMMIT_DATE", commitDate)
    }

    // TODO: needs performance tests. Must be used once custom icon sets will be available
    // defaultConfig.vectorDrawables.useSupportLibrary = true

    sourceSets.getByName("main") {
        Config.EXOPLAYER_EXTENSIONS.forEach { module ->
            java.srcDirs("../thirdparty/ExoPlayer/extensions/${module}/src/main/java")
        }
    }

    buildTypes {
        getByName("release") {
            Config.EXOPLAYER_EXTENSIONS.forEach { module ->
                val proguardFile = file("../thirdparty/ExoPlayer/extensions/${module}/proguard-rules.txt")
                if (proguardFile.exists()) {
                    project.logger.lifecycle("Applying thirdparty/ExoPlayer/extensions/${module}/proguard-rules.pro")
                    proguardFile(proguardFile)
                }
            }
        }
    }

    flavorDimensions.add("abi")
    productFlavors {
        Abi.VARIANTS.forEach { (abi, variant) ->
            create(variant.flavor) {
                dimension = "abi"
                versionCode = (abi + 1)
                minSdk = variant.minSdkVersion
                buildConfigBool("WEBP_ENABLED", variant.minSdkVersion < 19)
                buildConfigBool("SIDE_LOAD_ONLY", variant.sideLoadOnly)
                ndk.abiFilters.clear()
                ndk.abiFilters.addAll(variant.filters)
                externalNativeBuild.ndkBuild.abiFilters(*variant.filters)
                externalNativeBuild.cmake.abiFilters(*variant.filters)
            }
        }
    }
    applicationVariants.all {
        val variant = this

        val abi = (variant.productFlavors[0].versionCode ?: error("null")) - 1
        val abiVariant = Abi.VARIANTS[abi] ?: error("null")
        val versionCode = defaultConfig.versionCode ?: error("null")

        val versionCodeOverride = versionCode * 1000 + abi * 10
        val versionNameOverride = "${variant.versionName}.${defaultConfig.versionCode}-${abiVariant.displayName}${if (extra["commit_branch"] != "main") "-" + extra["commit_branch"] else ""}${if (variant.buildType.isDebuggable) "-debug" else ""}"
        val fileName = "Telegram-X-${versionNameOverride.replace("-universal(?=-|\$)", "")}"

        variant.buildConfigInt("ORIGINAL_VERSION_CODE", versionCode)
        variant.buildConfigInt("ABI", abi)

        variant.outputs.map { it as ApkVariantOutputImpl }.forEach { output ->
            output.versionCodeOverride = versionCodeOverride
            output.versionNameOverride = versionNameOverride
            output.outputFileName = "${fileName}.apk"
        }

        if (variant.buildType.isMinifyEnabled) {
            variant.assembleProvider!!.configure {
                doLast {
                    variant.mappingFileProvider.get().files.forEach { mappingFile ->
                        mappingFile.renameTo(File(mappingFile.parentFile, "${fileName}.txt"))
                    }
                }
            }
        }
    }

    // Packaging

    packagingOptions {
        Config.SUPPORTED_ABI.forEach { abi ->
            jniLibs.pickFirsts.add("lib/$abi/libc++_shared.so")
            jniLibs.pickFirsts.add("tdlib/src/main/libs/$abi/libtdjni.so")
        }
    }
}

gradle.projectsEvaluated {
    tasks.getByName("preBuild").dependsOn(
      "generateResourcesAndThemes",
      "checkEmojiKeyboard",
      "generatePhoneFormat",
      "updateExceptions"
    )
    Abi.VARIANTS.forEach { (_, variant) ->
        tasks.getByName("pre${variant.flavor[0].toUpperCase() + variant.flavor.substring(1)}ReleaseBuild").dependsOn(
            "updateLanguages"
        )
    }
}

dependencies {
    // TDLib: https://github.com/tdlib/td/blob/master/CHANGELOG.md
    implementation(project(":tdlib"))
    implementation(project(":vkryl:core"))
    implementation(project(":vkryl:leveldb"))
    implementation(project(":vkryl:android"))
    implementation(project(":vkryl:td"))
    // AndroidX: https://developer.android.com/jetpack/androidx/releases/
    implementation("androidx.activity:activity:1.4.0")
    implementation("androidx.palette:palette:1.0.0")
    implementation("androidx.recyclerview:recyclerview:1.2.1")
    implementation("androidx.viewpager:viewpager:1.0.0")
    implementation("androidx.work:work-runtime:2.7.0")
    implementation("androidx.browser:browser:1.3.0")
    implementation("androidx.exifinterface:exifinterface:1.3.3")
    implementation("androidx.collection:collection:1.1.0")
    implementation("androidx.interpolator:interpolator:1.0.0")
    implementation("androidx.gridlayout:gridlayout:1.0.0")
    // CameraX: https://developer.android.com/jetpack/androidx/releases/camera
    implementation("androidx.camera:camera-camera2:1.1.0-alpha10")
    implementation("androidx.camera:camera-lifecycle:1.1.0-alpha10")
    implementation("androidx.camera:camera-view:1.0.0-alpha30")
    // Google Play Services: https://developers.google.com/android/guides/releases
    implementation("com.google.android.gms:play-services-base:17.6.0")
    implementation("com.google.android.gms:play-services-basement:17.6.0")
    implementation("com.google.android.gms:play-services-maps:17.0.1")
    implementation("com.google.android.gms:play-services-location:18.0.0")
    implementation("com.google.android.gms:play-services-mlkit-barcode-scanning:16.2.1")
    // Firebase: https://firebase.google.com/support/release-notes/android
    implementation("com.google.firebase:firebase-messaging:22.0.0") {
        exclude(group = "com.google.firebase", module = "firebase-core")
        exclude(group = "com.google.firebase", module = "firebase-analytics")
        exclude(group = "com.google.firebase", module = "firebase-measurement-connector")
    }
    // Play Core: https://developer.android.com/reference/com/google/android/play/core/release-notes
    implementation("com.google.android.play:core:1.10.2")
    // ExoPlayer: https://github.com/google/ExoPlayer/blob/release-v2/RELEASENOTES.md
    implementation("com.google.android.exoplayer:exoplayer-core:2.16.0")
    // The Checker Framework: https://checkerframework.org/CHANGELOG.md
    compileOnly("org.checkerframework:checker-qual:3.18.1")
    // OkHttp: https://github.com/square/okhttp/blob/master/CHANGELOG.md
    implementation("com.squareup.okhttp3:okhttp:4.9.2")
    // ShortcutBadger: https://github.com/leolin310148/ShortcutBadger
    implementation("me.leolin:ShortcutBadger:1.1.22@aar")
    // ReLinker: https://github.com/KeepSafe/ReLinker/blob/master/CHANGELOG.md
    implementation("com.getkeepsafe.relinker:relinker:1.4.4")
    // Konfetti: https://github.com/DanielMartinus/Konfetti/blob/master/README.md
    implementation("nl.dionsegijn:konfetti:1.2.6")
    // Transcoder: https://github.com/natario1/Transcoder/blob/master/docs/_about/changelog.md
    implementation("com.github.natario1:Transcoder:ba8f098c94")

    // ZXing: https://github.com/zxing/zxing/
    implementation("com.google.zxing:core:3.4.1")

    // YouTube: https://developers.google.com/youtube/android/player/
    implementation(files("thirdparty/YouTubeAndroidPlayerApi.jar"))

    // TODO: upgrade to "com.googlecode.mp4parser:isoparser:1.1.22" or latest
    // mp4parser: https://github.com/sannies/mp4parser/releases
    implementation("com.googlecode.mp4parser:isoparser:1.0.6")
}

apply(plugin = "com.google.gms.google-services")