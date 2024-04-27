@file:Suppress("UnstableApiUsage")

import com.android.build.gradle.internal.api.ApkVariantOutputImpl
import me.vkryl.task.*
import java.util.*

plugins {
  id("com.android.application")
  id("module-plugin")
  id("cmake-plugin")
}

val generateResourcesAndThemes by tasks.registering(GenerateResourcesAndThemesTask::class) {
  group = "Setup"
  description = "Generates fresh strings, ids, theme resources and utility methods based on current static files"
}
val updateLanguages by tasks.registering(FetchLanguagesTask::class) {
  group = "Setup"
  description = "Generates and updates all strings.xml resources based on translations.telegram.org"
}
val validateApiTokens by tasks.registering(ValidateApiTokensTask::class) {
  group = "Setup"
  description = "Validates some API tokens to make sure they work properly and won't cause problems"
}
val updateExceptions by tasks.registering(UpdateExceptionsTask::class) {
  group = "Setup"
  description = "Updates exception class names with the app or TDLib version number in order to have separate group on Google Play Developer Console"
}
val generatePhoneFormat by tasks.registering(GeneratePhoneFormatTask::class) {
  group = "Setup"
  description = "Generates utility methods for phone formatting, e.g. +12345678901 -> +1 (234) 567 89-01"
}
val checkEmojiKeyboard by tasks.registering(CheckEmojiKeyboardTask::class) {
  group = "Setup"
  description = "Checks that all supported emoji can be entered from the keyboard"
}

val isExperimentalBuild = extra["experimental"] as Boolean? ?: false
val properties = extra["properties"] as Properties
val projectName = extra["app_name"] as String
val versions = extra["versions"] as Properties

data class PullRequest (
  val id: Long,
  val commitShort: String,
  val commitLong: String,
  val commitDate: Long,
  val author: String
) {
  constructor(id: Long, properties: Properties) : this(
    id,
    properties.getOrThrow("pr.$id.commit_short"),
    properties.getOrThrow("pr.$id.commit_long"),
    properties.getLongOrThrow("pr.$id.date"),
    properties.getOrThrow("pr.$id.author")
  )
}

android {
  namespace = "org.thunderdog.challegram"

  defaultConfig {
    val jniVersion = versions.getProperty("version.jni")
    val leveldbVersion = versions.getProperty("version.leveldb")

    buildConfigString("JNI_VERSION", jniVersion)
    buildConfigString("LEVELDB_VERSION", leveldbVersion)

    buildConfigString("TDLIB_REMOTE_URL", "https://github.com/tdlib/td")

    buildConfigField("boolean", "EXPERIMENTAL", isExperimentalBuild.toString())

    buildConfigInt("TELEGRAM_API_ID", properties.getIntOrThrow("telegram.api_id"))
    buildConfigString("TELEGRAM_API_HASH", properties.getOrThrow("telegram.api_hash"))

    buildConfigString("TELEGRAM_RESOURCES_CHANNEL", Telegram.RESOURCES_CHANNEL)
    buildConfigString("TELEGRAM_UPDATES_CHANNEL", Telegram.UPDATES_CHANNEL)

    buildConfigInt("EMOJI_VERSION", versions.getIntOrThrow("version.emoji"))
    buildConfigString("EMOJI_BUILTIN_ID", Emoji.BUILTIN_ID)

    buildConfigString("LANGUAGE_PACK", Telegram.LANGUAGE_PACK)

    buildConfigString("THEME_FILE_EXTENSION", App.THEME_EXTENSION)

    // Library versions in BuildConfig.java

    var openSslVersion = ""
    var openSslVersionFull = ""
    val openSslVersionFile = File(project.rootDir.absoluteFile, "tdlib/source/openssl/include/openssl/opensslv.h")
    openSslVersionFile.bufferedReader().use { reader ->
      val regex = Regex("^#\\s*define OPENSSL_VERSION_NUMBER\\s*((?:0x)[0-9a-fAF]+)L?\$")
      while (true) {
        val line = reader.readLine() ?: break
        val result = regex.find(line)
        if (result != null) {
          val rawVersion = result.groupValues[1]
          val version = if (rawVersion.startsWith("0x")) {
            rawVersion.substring(2).toLong(16)
          } else {
            rawVersion.toLong()
          }
          // MNNFFPPS: major minor fix patch status
          val major = ((version shr 28) and 0xf).toInt()
          val minor = ((version shr 20) and 0xff).toInt()
          val fix = ((version shr 12) and 0xff).toInt()
          val patch = ((version shr 4) and 0xff).toInt()
          val status = (version and 0xf).toInt()
          if (status != 0xf) {
            error("Using non-stable OpenSSL version: $rawVersion (status = ${status.toString(16)})")
          }
          openSslVersion = "${major}.${minor}"
          openSslVersionFull = "${major}.${minor}.${fix}${('a'.code - 1 + patch).toChar()}"
          break
        }
      }
    }
    if (openSslVersion.isEmpty()) {
      error("OpenSSL not found!")
    }

    var tdlibVersion = ""
    val tdlibCommit = File(project.rootDir.absoluteFile, "tdlib/version.txt").bufferedReader().readLine().take(7)
    val tdlibVersionFile = File(project.rootDir.absoluteFile, "tdlib/source/td/CMakeLists.txt")
    tdlibVersionFile.bufferedReader().use { reader ->
      val regex = Regex("^project\\(TDLib VERSION (\\d+\\.\\d+\\.\\d+) LANGUAGES CXX C\\)$")
      while (true) {
        val line = reader.readLine() ?: break
        val result = regex.find(line)
        if (result != null) {
          tdlibVersion = "${result.groupValues[1]}-${tdlibCommit}"
          break
        }
      }
    }
    if (tdlibVersion.isEmpty()) {
      error("TDLib not found!")
    }

    val pullRequests: List<PullRequest> = properties.getProperty("pr.ids", "").split(',').filter { it.matches(Regex("^[0-9]+$")) }.map {
      PullRequest(it.toLong(), properties)
    }.sortedBy { it.id }

    buildConfigString("OPENSSL_VERSION", openSslVersion)
    buildConfigString("OPENSSL_VERSION_FULL", openSslVersionFull)
    buildConfigString("TDLIB_VERSION", tdlibVersion)

    val tgxGitVersionProvider = providers.of(GitVersionValueSource::class) {
      parameters.module = layout.projectDirectory
    }
    val tgxGit = tgxGitVersionProvider.get()

    buildConfigString("REMOTE_URL", tgxGit.remoteUrl)
    buildConfigString("COMMIT_URL", tgxGit.commitUrl)
    buildConfigString("COMMIT", tgxGit.commitHashShort)
    buildConfigString("COMMIT_FULL", tgxGit.commitHashLong)
    buildConfigLong("COMMIT_DATE", tgxGit.commitDate)
    buildConfigString("SOURCES_URL", properties.getProperty("app.sources_url", tgxGit.remoteUrl))

    buildConfigField("long[]", "PULL_REQUEST_ID", "{${
      pullRequests.joinToString(", ") { it.id.toString() }
    }}")
    buildConfigField("long[]", "PULL_REQUEST_COMMIT_DATE", "{${
      pullRequests.joinToString(", ") { it.commitDate.toString() }
    }}")
    buildConfigField("String[]", "PULL_REQUEST_COMMIT", "{${
      pullRequests.joinToString(", ") { "\"${it.commitShort}\"" }
    }}")
    buildConfigField("String[]", "PULL_REQUEST_COMMIT_FULL", "{${
      pullRequests.joinToString(", ") { "\"${it.commitLong}\"" }
    }}")
    buildConfigField("String[]", "PULL_REQUEST_URL", "{${
      pullRequests.joinToString(", ") { "\"${tgxGit.remoteUrl}/pull/${it.id}/files/${it.commitLong}\"" }
    }}")
    buildConfigField("String[]", "PULL_REQUEST_AUTHOR", "{${
      pullRequests.joinToString(", ") { "\"${it.author}\"" }
    }}")

    // WebRTC version

    val webrtcGitVersionProvider = providers.of(GitVersionValueSource::class) {
      parameters.module = layout.projectDirectory.dir("jni/third_party/webrtc")
    }
    val webrtcGit = webrtcGitVersionProvider.get()
    buildConfigString("WEBRTC_COMMIT", webrtcGit.commitHashShort)
    buildConfigString("WEBRTC_COMMIT_URL", webrtcGit.commitUrl)

    // tgcalls version

    val tgcallsGitVersionProvider = providers.of(GitVersionValueSource::class) {
      parameters.module = layout.projectDirectory.dir("jni/third_party/tgcalls")
    }
    val tgcallsGit = tgcallsGitVersionProvider.get()
    buildConfigString("TGCALLS_COMMIT", tgcallsGit.commitHashShort)
    buildConfigString("TGCALLS_COMMIT_URL", tgcallsGit.commitUrl)

    // Set application version

    val appVersionOverride = properties.getProperty("app.version", "0").toInt()
    val appVersion = if (appVersionOverride > 0) appVersionOverride else versions.getOrThrow("version.app").toInt()
    val majorVersion = versions.getOrThrow("version.major").toInt()

    val timeZone = TimeZone.getTimeZone("UTC")
    val then = Calendar.getInstance(timeZone)
    then.timeInMillis = versions.getOrThrow("version.creation").toLong()
    val now = Calendar.getInstance(timeZone)
    now.timeInMillis = tgxGit.commitDate * 1000L
    if (now.timeInMillis < then.timeInMillis)
      error("Invalid commit time!")
    val minorVersion = monthYears(now, then)

    versionCode = appVersion
    versionName = "${majorVersion}.${minorVersion}"
  }

  // TODO: needs performance tests. Must be used once custom icon sets will be available
  // defaultConfig.vectorDrawables.useSupportLibrary = true

  sourceSets.getByName("main") {
    java.srcDirs("./src/google/java") // TODO: Huawei & FOSS editions
    java.srcDirs(
      "./jni/third_party/webrtc/rtc_base/java/src",
      "./jni/third_party/webrtc/modules/audio_device/android/java/src",
      "./jni/third_party/webrtc/sdk/android/api",
      "./jni/third_party/webrtc/sdk/android/src/java",
      "../thirdparty/WebRTC/src/java"
    )
    Config.ANDROIDX_MEDIA_EXTENSIONS.forEach { extension ->
      java.srcDirs("../thirdparty/androidx-media/libraries/${extension}/src/main/java")
    }
  }

  lint {
    disable += "MissingTranslation"
    checkDependencies = true
  }

  buildFeatures {
    buildConfig = true
  }

  buildTypes {
    release {
      arrayOf(
        "exoplayer",
        "common",
        "transformer",
        "extractor",
        "muxer",
        "decoder",
        "container",
        "datasource",
        "database",
        "effect"
      ).plus(Config.ANDROIDX_MEDIA_EXTENSIONS).forEach { extension ->
        val proguardFile = file(
          "../thirdparty/androidx-media/libraries/${extension}/proguard-rules.txt"
        )
        if (proguardFile.exists()) {
          project.logger.lifecycle("Applying ${proguardFile.path}")
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
        val ndkVersionKey = if (variant.is64Bit) {
          "version.ndk_primary"
        } else {
          "version.ndk_legacy"
        }
        if (variant.minSdkVersion < Config.PRIMARY_SDK_VERSION) {
          proguardFile("proguard-r8-bug-android-4.x-workaround.pro")
        }
        ndkVersion = versions.getProperty(ndkVersionKey)
        ndkPath = File(sdkDirectory, "ndk/$ndkVersion").absolutePath
        buildConfigString("NDK_VERSION", ndkVersion)
        buildConfigBool("WEBP_ENABLED", true) // variant.minSdkVersion < 19
        ndk.abiFilters.clear()
        ndk.abiFilters.addAll(variant.filters)
        externalNativeBuild.ndkBuild.abiFilters(*variant.filters)
        externalNativeBuild.cmake.abiFilters(*variant.filters)
      }
    }
  }

  applicationVariants.configureEach {
    val abi = (productFlavors[0].versionCode ?: error("null")) - 1
    val abiVariant = Abi.VARIANTS[abi] ?: error("null")
    val versionCode = defaultConfig.versionCode ?: error("null")

    val versionCodeOverride = versionCode * 1000 + abi * 10
    val versionNameOverride = "${versionName}.${defaultConfig.versionCode}${if (extra.has("app_version_suffix")) extra["app_version_suffix"] else ""}-${abiVariant.displayName}${if (extra.has("app_name_suffix")) "-" + extra["app_name_suffix"] else ""}${if (buildType.isDebuggable) "-debug" else ""}"
    val outputFileNamePrefix = properties.getProperty("app.file", projectName.replace(" ", "-").replace("#", ""))
    val fileName = "${outputFileNamePrefix}-${versionNameOverride.replace("-universal(?=-|\$)", "")}"

    buildConfigField("int", "ORIGINAL_VERSION_CODE", versionCode.toString())
    buildConfigField("int", "ABI", abi.toString())
    buildConfigField("String", "ORIGINAL_VERSION_NAME", "\"${versionName}.${defaultConfig.versionCode}\"")

    outputs.map { it as ApkVariantOutputImpl }.forEach { output ->
      output.versionCodeOverride = versionCodeOverride
      output.versionNameOverride = versionNameOverride
      output.outputFileName = "${fileName}.apk"
    }

    if (buildType.isMinifyEnabled) {
      assembleProvider!!.configure {
        doLast {
          mappingFileProvider.get().files.forEach { mappingFile ->
            mappingFile.renameTo(File(mappingFile.parentFile, "${fileName}.txt"))
          }
        }
      }
    }
  }

  // Packaging

  packaging {
    Config.SUPPORTED_ABI.forEach { abi ->
      jniLibs.pickFirsts.let { set ->
        set.add("lib/$abi/libc++_shared.so")
        set.add("tdlib/openssl/$abi/lib/libcryptox.so")
        set.add("tdlib/openssl/$abi/lib/libsslx.so")
        set.add("tdlib/src/main/libs/$abi/libtdjni.so")
      }
    }
  }
}

gradle.projectsEvaluated {
  tasks.named("preBuild").configure {
    dependsOn(
      generateResourcesAndThemes,
      checkEmojiKeyboard,
      generatePhoneFormat,
      updateExceptions,
    )
  }
  Abi.VARIANTS.forEach { (_, variant) ->
    tasks.named("pre${variant.flavor[0].uppercaseChar() + variant.flavor.substring(1)}ReleaseBuild") {
      dependsOn(updateLanguages)
      if (!isExperimentalBuild) {
        dependsOn(validateApiTokens)
      }
    }
  }
}

dependencies {
  // TDLib: https://github.com/tdlib/td/blob/master/CHANGELOG.md
  implementation(project(":tdlib"))
  implementation(project(":vkryl:core"))
  implementation(project(":vkryl:leveldb"))
  implementation(project(":vkryl:android"))
  implementation(project(":vkryl:td"))
  // AndroidX: https://developer.android.com/jetpack/androidx/versions
  implementation("androidx.activity:activity:1.8.2") // 1.9.0+ requires minSdkVersion 19
  implementation("androidx.palette:palette:1.0.0")
  implementation("androidx.recyclerview:recyclerview:1.3.2")
  implementation("androidx.viewpager:viewpager:1.0.0")
  implementation("androidx.work:work-runtime:2.9.0")
  implementation("androidx.browser:browser:1.5.0") // 1.7.0+ requires minSdkVersion 19
  implementation("androidx.exifinterface:exifinterface:1.3.7")
  implementation("androidx.collection:collection:1.4.0")
  implementation("androidx.interpolator:interpolator:1.0.0")
  implementation("androidx.gridlayout:gridlayout:1.0.0")
  // CameraX: https://developer.android.com/jetpack/androidx/releases/camera
  implementation("androidx.camera:camera-camera2:${LibraryVersions.ANDROIDX_CAMERA}")
  implementation("androidx.camera:camera-video:${LibraryVersions.ANDROIDX_CAMERA}")
  implementation("androidx.camera:camera-lifecycle:${LibraryVersions.ANDROIDX_CAMERA}")
  implementation("androidx.camera:camera-view:${LibraryVersions.ANDROIDX_CAMERA}")
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
  implementation("com.google.firebase:firebase-appcheck-safetynet:16.1.2")
  // Play In-App Updates: https://developer.android.com/reference/com/google/android/play/core/release-notes-in_app_updates
  implementation("com.google.android.play:app-update:2.1.0")
  // AndroidX/media: https://github.com/androidx/media/blob/release/RELEASENOTES.md
  implementation("androidx.media3:media3-exoplayer:${LibraryVersions.ANDROIDX_MEDIA}")
  implementation("androidx.media3:media3-transformer:${LibraryVersions.ANDROIDX_MEDIA}")
  implementation("androidx.media3:media3-effect:${LibraryVersions.ANDROIDX_MEDIA}")
  implementation("androidx.media3:media3-common:${LibraryVersions.ANDROIDX_MEDIA}")
  // 17.x version requires minSdk 19 or higher
  implementation("com.google.mlkit:language-id:16.1.1")
  // The Checker Framework: https://checkerframework.org/CHANGELOG.md
  compileOnly("org.checkerframework:checker-qual:3.42.0")
  // OkHttp: https://github.com/square/okhttp/blob/master/CHANGELOG.md
  implementation("com.squareup.okhttp3:okhttp:4.12.0")
  // ShortcutBadger: https://github.com/leolin310148/ShortcutBadger
  implementation("me.leolin:ShortcutBadger:1.1.22@aar")
  // ReLinker: https://github.com/KeepSafe/ReLinker/blob/master/CHANGELOG.md
  implementation("com.getkeepsafe.relinker:relinker:1.4.5")
  // Konfetti: https://github.com/DanielMartinus/Konfetti/blob/main/README.md
  implementation("nl.dionsegijn:konfetti-xml:2.0.4")
  // Transcoder: https://github.com/natario1/Transcoder/blob/master/docs/_about/changelog.md
  implementation("com.github.natario1:Transcoder:ba8f098c94")
  // https://github.com/mikereedell/sunrisesunsetlib-java
  implementation("com.luckycatlabs:SunriseSunsetCalculator:1.2")

  // ZXing: https://github.com/zxing/zxing/blob/master/CHANGES
  implementation("com.google.zxing:core:3.5.3")

  // subsampling-scale-image-view: https://github.com/davemorrissey/subsampling-scale-image-view
  implementation("com.davemorrissey.labs:subsampling-scale-image-view-androidx:3.10.0")

  // TODO: upgrade to "com.googlecode.mp4parser:isoparser:1.1.22" or latest
  // mp4parser: https://github.com/sannies/mp4parser/releases
  implementation("com.googlecode.mp4parser:isoparser:1.0.6")
}

if (!isExperimentalBuild) {
  apply(plugin = "com.google.gms.google-services")
}