@file:Suppress("UnstableApiUsage")

import com.android.build.gradle.internal.api.ApkVariantOutputImpl
import tgx.gradle.*
import tgx.gradle.task.*
import java.util.*

plugins {
  id(libs.plugins.android.application.get().pluginId)
  alias(libs.plugins.kotlin.android)
  id("tgx-config")
  id("tgx-module")
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

val config = extra["config"] as ApplicationConfig

//noinspection WrongGradleMethod
android {
  namespace = "org.thunderdog.challegram"

  lint {
    disable += "MissingTranslation"
    checkDependencies = true
  }

  externalNativeBuild {
    cmake {
      path("jni/CMakeLists.txt")
    }
  }

  defaultConfig {
    applicationId = config.applicationId
    targetSdk = config.targetSdkVersion
    multiDexEnabled = true

    buildConfigString("PROJECT_NAME", config.applicationName)
    buildConfigBool("SHARED_STL", Config.SHARED_STL)
    buildConfigString("SAFETYNET_API_KEY", config.safetyNetToken)

    buildConfigString("DOWNLOAD_URL", config.appDownloadUrl)
    buildConfigString("GOOGLE_PLAY_URL", config.googlePlayUrl)
    buildConfigString("GALAXY_STORE_URL", config.galaxyStoreUrl)
    buildConfigString("HUAWEI_APPGALLERY_URL", config.huaweiAppGalleryUrl)
    buildConfigString("AMAZON_APPSTORE_URL", config.amazonAppStoreUrl)

    buildConfigString("TGX_EXTENSION", config.extension)

    buildConfigString("JNI_VERSION", config.nativeLibraryVersion)
    buildConfigString("LEVELDB_VERSION", config.leveldbVersion)

    buildConfigString("TDLIB_REMOTE_URL", "https://github.com/tdlib/td")

    buildConfigField("boolean", "EXPERIMENTAL", config.isExperimentalBuild.toString())

    buildConfigInt("TARGET_SDK_INT", config.targetSdkVersion)

    buildConfigInt("TELEGRAM_API_ID", config.telegramApiId)
    buildConfigString("TELEGRAM_API_HASH", config.telegramApiHash)

    buildConfigString("TELEGRAM_RESOURCES_CHANNEL", Telegram.RESOURCES_CHANNEL)
    buildConfigString("TELEGRAM_UPDATES_CHANNEL", Telegram.UPDATES_CHANNEL)

    buildConfigInt("EMOJI_VERSION", config.emojiVersion)
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
            fatal("Using non-stable OpenSSL version: $rawVersion (status = ${status.toString(16)})")
          }
          openSslVersion = "${major}.${minor}"
          openSslVersionFull = "${major}.${minor}.${fix}${('a'.code - 1 + patch).toChar()}"
          break
        }
      }
    }
    if (openSslVersion.isEmpty()) {
      fatal("OpenSSL not found!")
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
      fatal("TDLib not found!")
    }

    buildConfigString("OPENSSL_VERSION", openSslVersion)
    buildConfigString("OPENSSL_VERSION_FULL", openSslVersionFull)
    buildConfigString("TDLIB_VERSION", tdlibVersion)

    val tgxGitVersionProvider = providers.of(GitVersionValueSource::class) {
      parameters.module = layout.projectDirectory
    }
    val tgxGit = tgxGitVersionProvider.get()

    val sourcesUrl = config.sourceCodeUrl.takeIf {
      it.isNotEmpty()
    } ?: tgxGit.remoteUrl
    buildConfigString("REMOTE_URL", tgxGit.remoteUrl)
    buildConfigString("COMMIT_URL", tgxGit.commitUrl)
    buildConfigString("COMMIT", tgxGit.commitHashShort)
    buildConfigString("COMMIT_FULL", tgxGit.commitHashLong)
    buildConfigLong("COMMIT_DATE", tgxGit.commitDate)
    buildConfigString("SOURCES_URL", sourcesUrl)

    buildConfigField("long[]", "PULL_REQUEST_ID", "{${
      config.pullRequests.joinToString(", ") { it.id.toString() }
    }}")
    buildConfigField("long[]", "PULL_REQUEST_COMMIT_DATE", "{${
      config.pullRequests.joinToString(", ") { it.commitDate.toString() }
    }}")
    buildConfigField("String[]", "PULL_REQUEST_COMMIT", "{${
      config.pullRequests.joinToString(", ") { "\"${it.commitShort}\"" }
    }}")
    buildConfigField("String[]", "PULL_REQUEST_COMMIT_FULL", "{${
      config.pullRequests.joinToString(", ") { "\"${it.commitLong}\"" }
    }}")
    buildConfigField("String[]", "PULL_REQUEST_URL", "{${
      config.pullRequests.joinToString(", ") { "\"${tgxGit.remoteUrl}/pull/${it.id}/files/${it.commitLong}\"" }
    }}")
    buildConfigField("String[]", "PULL_REQUEST_AUTHOR", "{${
      config.pullRequests.joinToString(", ") { "\"${it.author}\"" }
    }}")

    // WebRTC version

    val webrtcGit = providers.of(GitVersionValueSource::class) {
      parameters.module = layout.projectDirectory.dir("jni/third_party/webrtc")
    }.get()
    buildConfigString("WEBRTC_COMMIT", webrtcGit.commitHashShort)
    buildConfigString("WEBRTC_COMMIT_URL", webrtcGit.commitUrl)

    // tgcalls version

    val tgcallsGit = providers.of(GitVersionValueSource::class) {
      parameters.module = layout.projectDirectory.dir("jni/third_party/tgcalls")
    }.get()
    buildConfigString("TGCALLS_COMMIT", tgcallsGit.commitHashShort)
    buildConfigString("TGCALLS_COMMIT_URL", tgcallsGit.commitUrl)

    // FFmpeg version

    val ffmpegGit = providers.of(GitVersionValueSource::class) {
      parameters.module = layout.projectDirectory.dir("jni/third_party/ffmpeg")
    }.get()
    buildConfigString("FFMPEG_COMMIT", ffmpegGit.commitHashShort)
    buildConfigString("FFMPEG_COMMIT_URL", ffmpegGit.commitUrl)

    // WebP version

    val webpGit = providers.of(GitVersionValueSource::class) {
      parameters.module = layout.projectDirectory.dir("jni/third_party/webp")
    }.get()
    buildConfigString("WEBP_COMMIT", webpGit.commitHashShort)
    buildConfigString("WEBP_COMMIT_URL", webpGit.commitUrl)

    // Set application version

    val timeZone = TimeZone.getTimeZone("UTC")
    val then = Calendar.getInstance(timeZone)
    then.timeInMillis = config.creationDateMillis
    val now = Calendar.getInstance(timeZone)
    now.timeInMillis = tgxGit.commitDate * 1000L
    if (now.timeInMillis < then.timeInMillis)
      fatal("Invalid commit time!")
    val minorVersion = monthYears(now, then)

    versionCode = config.applicationVersion
    versionName = "${config.majorVersion}.${minorVersion}"
  }

  sourceSets.getByName("main") {
    java.srcDirs("./src/google/java") // TODO: Exclude in FOSS variant
  }

  lint {
    disable += "MissingTranslation"
    checkDependencies = true
  }

  buildFeatures {
    buildConfig = true
  }

  flavorDimensions += arrayOf("SDK", "ABI")
  androidComponents.beforeVariants { variantBuilder ->
    val sdkFlavor = variantBuilder.productFlavors.first { it.first == "SDK" }.second
    val sdkVariant = Sdk.VARIANTS.values.first { it.flavor == sdkFlavor }
    val abiFlavor = variantBuilder.productFlavors.first { it.first == "ABI" }.second
    val abiVariant = Abi.VARIANTS.values.first { it.flavor == abiFlavor }
    if (sdkVariant.maxSdk != null) {
      variantBuilder.maxSdk = sdkVariant.maxSdk
    }
    variantBuilder.enable = sdkVariant.minSdk >= abiVariant.minSdk &&
      !(abiVariant.flavor == "universal" && sdkVariant.flavor == "legacy") &&
      (variantBuilder.buildType != "debug" || sdkVariant.flavor == "legacy" || (abiVariant.flavor == "x86" || abiVariant.flavor == "x64" || abiVariant.flavor == "universal"))
  }
  productFlavors {
    Sdk.VARIANTS.forEach { (sdk, variant) ->
      create(variant.flavor) {
        dimension = "SDK"
        versionCode = (sdk + 1)
        isDefault = sdk == Sdk.LATEST

        val actualMinSdk = if (config.isHuaweiBuild) {
          maxOf(variant.minSdk, Config.MIN_SDK_VERSION_HUAWEI)
        } else {
          variant.minSdk
        }
        val selectedMinSdk = maxOf(variant.minSdk, actualMinSdk)
        minSdk = selectedMinSdk
        if (selectedMinSdk < 21) {
          proguardFile("proguard-r8-bug-android-4.x-workaround.pro")
        }

        val flags = listOf(
          "-w",
          "-Werror=return-type",
          "-ferror-limit=0",
          "-fno-exceptions",

          "-O3",
          "-finline-functions"
        )
        externalNativeBuild.cmake {
          arguments(
            "-DANDROID_PLATFORM=android-${selectedMinSdk}",
            "-DTGX_FLAVOR=${variant.flavor}",
            "-DANDROID_STL=${if (Config.SHARED_STL) "c++_shared" else "c++_static"}",
            "-DCMAKE_BUILD_WITH_INSTALL_RPATH=ON",
            "-DCMAKE_SKIP_RPATH=ON",
            "-DCMAKE_C_VISIBILITY_PRESET=hidden",
            "-DCMAKE_CXX_VISIBILITY_PRESET=hidden",
            "-DCMAKE_SHARED_LINKER_FLAGS=-Wl,--gc-sections,--icf=safe -Wl,--build-id=sha1",
            "-DCMAKE_C_FLAGS=-D_LARGEFILE_SOURCE=1 ${flags.joinToString(" ")}",
            "-DCMAKE_CXX_FLAGS=-std=c++17 ${flags.joinToString(" ")}"
          )
        }

        sourceSets.getByName(variant.flavor) {
          Config.ANDROIDX_MEDIA_EXTENSIONS.forEach { extension ->
            java.srcDirs("../thirdparty/androidx-media/${variant.flavor}/libraries/${extension}/src/main/java")
          }
          if (variant.flavor != "legacy") {
            kotlin.srcDirs("./src/postLegacy/kotlin")
            java.srcDirs("./src/postLegacy/java")
          }
          if (variant.flavor != "latest") {
            kotlin.srcDirs("./src/preLatest/kotlin")
            java.srcDirs("./src/preLatest/java")
          }
        }

        Sdk.VARIANTS.forEach { (subSdk, subVariant) ->
          buildConfigBool("${subVariant.flavor.uppercase()}_FLAVOR", sdk == subSdk)
        }

        var extraProguardFileCount = 0

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
            "../thirdparty/androidx-media/${variant.flavor}/libraries/${extension}/proguard-rules.txt"
          )
          if (proguardFile.exists()) {
            extraProguardFileCount++
            proguardFile(proguardFile)
          }
        }

        if (extraProguardFileCount > 0) {
          project.logger.lifecycle("[proguard]: Applied $extraProguardFileCount extra proguard files for \"${variant.flavor}\" flavor")
        }
      }
    }

    Abi.VARIANTS.forEach { (abi, variant) ->
      create(variant.flavor) {
        dimension = "ABI"
        versionCode = (abi + 1)
        isDefault = abi == 0
        ndkVersion = if (variant.is64Bit) {
          config.primaryNdkVersion
        } else {
          config.legacyNdkVersion
        }
        ndkPath = File(sdkDirectory, "ndk/$ndkVersion").absolutePath
        buildConfigString("NDK_VERSION", ndkVersion)
        buildConfigBool("WEBP_ENABLED", true) // variant.minSdk < 19
        if (ndk.abiFilters.isNotEmpty())
          error(ndk.abiFilters.joinToString())
        ndk.abiFilters.addAll(variant.filters)
        externalNativeBuild.ndkBuild.abiFilters(*variant.filters)
        externalNativeBuild.cmake.abiFilters(*variant.filters)
      }
    }
  }

  applicationVariants.configureEach {
    val abiFlavor = productFlavors.first { it.dimension == "ABI" }
    val abi = (abiFlavor.versionCode ?: fatal("null")) - 1
    val abiVariant = Abi.VARIANTS[abi] ?: fatal("null")
    val versionCode = defaultConfig.versionCode ?: fatal("null")

    val sdkFlavor = productFlavors.first { it.dimension == "SDK" }
    val sdk = (sdkFlavor.versionCode ?: fatal("null")) - 1
    val sdkVariant = Sdk.VARIANTS[sdk] ?: fatal("null")

    val recaptchaVersion = when (sdkVariant.flavor) {
      "legacy" -> libs.google.recaptcha.legacy
      "lollipop" -> libs.google.recaptcha.lollipop
      "latest" -> libs.google.recaptcha.latest
      else -> error(sdkVariant.flavor)
    }.get().version!!

    val versionCodeOverride = versionCode * 1000 + if (!buildType.isDebuggable) (sdk * 100 + abi) else 0
    val versionNameOverride = StringBuilder("${versionName}.${defaultConfig.versionCode}").apply {
      if (extra.has("app_version_suffix")) {
        append(extra["app_version_suffix"])
      }
      if (config.extension != "none") {
        append("-${config.extension}")
      }
      if (!sdkVariant.displayName.isNullOrEmpty()) {
        append("-${sdkVariant.displayName}")
      }
      if (abiVariant.displayName != "universal" || (config.extension == "none" && sdkVariant.displayName.isNullOrEmpty())) {
        append("-${abiVariant.displayName}")
      }
      if (extra.has("app_name_suffix")) {
        append("-${extra["app_name_suffix"]}")
      }
      if (buildType.isDebuggable) {
        append("-debug")
      }
    }.toString()

    val fileName = "${config.outputFileNamePrefix}-${versionNameOverride.replace(Regex("-universal(?=-|$)"), "")}"

    buildConfigField("int", "ORIGINAL_VERSION_CODE", versionCode.toString())
    buildConfigField("int", "ABI", abi.toString())
    buildConfigField("String", "ORIGINAL_VERSION_NAME", "\"${versionName}.${defaultConfig.versionCode}\"")
    buildConfigField("String", "RECAPTCHA_VERSION", "\"${recaptchaVersion}\"")

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
        if (Config.SHARED_STL) {
          set.add("lib/$abi/libc++_shared.so")
        }
        set.add("tdlib/openssl/$abi/lib/libcryptox.so")
        set.add("tdlib/openssl/$abi/lib/libsslx.so")
        set.add("tdlib/src/main/libs/$abi/libtdjni.so")
      }
    }
  }
}

gradle.projectsEvaluated {
  tasks.preBuild.configure {
    dependsOn(
      generateResourcesAndThemes,
      checkEmojiKeyboard,
      generatePhoneFormat,
      updateExceptions,
    )
  }
  tasks.named {
    it.startsWith("pre") && it.endsWith("ReleaseBuild")
  }.configureEach {
    dependsOn(updateLanguages)
    if (!config.isExperimentalBuild) {
      dependsOn(validateApiTokens)
    }
  }
}

dependencies {
  implementation(project(":extension:${config.extension}"))
  // TDLib: https://github.com/tdlib/td/blob/master/CHANGELOG.md
  implementation(project(":tdlib"))
  implementation(project(":tgcalls"))
  implementation(project(":vkryl:core"))
  implementation(project(":vkryl:leveldb"))
  implementation(project(":vkryl:android"))
  implementation(project(":vkryl:td"))
  // AndroidX: https://developer.android.com/jetpack/androidx/versions
  flavorImplementation(
    libs.androidx.activity.legacy,
    libs.androidx.activity.lollipop,
    libs.androidx.activity.latest
  )
  flavorImplementation(
    libs.androidx.gridlayout.legacy,
    libs.androidx.gridlayout.latest
  )
  flavorImplementation(
    libs.androidx.recyclerview.legacy,
    libs.androidx.recyclerview.latest
  )
  flavorImplementation(
    libs.androidx.constraintlayout.legacy,
    libs.androidx.constraintlayout.latest
  )
  flavorImplementation(
    libs.androidx.viewpager.legacy,
    libs.androidx.viewpager.latest
  )
  flavorImplementation(
    libs.androidx.browser.legacy,
    libs.androidx.browser.latest
  )
  flavorImplementation(
    libs.androidx.work.runtime.legacy,
    libs.androidx.work.runtime.lollipop,
    libs.androidx.work.runtime.latest
  )
  flavorImplementation(
    libs.androidx.exifinterface.legacy,
    libs.androidx.exifinterface.latest
  )
  implementation(libs.androidx.biometric)
  implementation(libs.androidx.palette)
  implementation(libs.androidx.collection)
  implementation(libs.androidx.interpolator)
  // CameraX: https://developer.android.com/jetpack/androidx/releases/camera
  flavorImplementation(
    libs.androidx.camera.camera2.legacy,
    libs.androidx.camera.camera2.legacy,
    libs.androidx.camera.camera2.latest
  )
  flavorImplementation(
    libs.androidx.camera.video.legacy,
    libs.androidx.camera.video.legacy,
    libs.androidx.camera.video.latest
  )
  flavorImplementation(
    libs.androidx.camera.lifecycle.legacy,
    libs.androidx.camera.lifecycle.legacy,
    libs.androidx.camera.lifecycle.latest
  )
  flavorImplementation(
    libs.androidx.camera.view.legacy,
    libs.androidx.camera.view.legacy,
    libs.androidx.camera.view.latest
  )
  // Google Play Services: https://developers.google.com/android/guides/releases
  flavorImplementation(
    libs.google.play.services.base.legacy,
    libs.google.play.services.base.lollipop,
    libs.google.play.services.base.latest
  )
  flavorImplementation(
    libs.google.play.services.basement.legacy,
    libs.google.play.services.basement.lollipop,
    libs.google.play.services.basement.latest
  )
  flavorImplementation(
    libs.google.play.services.maps.legacy,
    libs.google.play.services.maps.latest
  )
  flavorImplementation(
    libs.google.play.services.location.legacy,
    libs.google.play.services.location.latest
  )
  flavorImplementation(
    libs.google.play.services.safetynet.legacy,
    libs.google.play.services.safetynet.latest
  )
  // ML Kit: https://developers.google.com/ml-kit/release-notes
  flavorImplementation(
    libs.google.play.services.mlkit.barcode.scanning.legacy,
    libs.google.play.services.mlkit.barcode.scanning.latest
  )
  flavorImplementation(
    libs.google.mlkit.language.id.legacy,
    libs.google.mlkit.language.id.latest
  )
  // Firebase: https://firebase.google.com/support/release-notes/android
  flavorImplementation(
    libs.google.firebase.messaging.legacy,
    libs.google.firebase.messaging.lollipop,
    libs.google.firebase.messaging.latest
  ) {
    exclude(group = "com.google.firebase", module = "firebase-core")
    exclude(group = "com.google.firebase", module = "firebase-analytics")
    exclude(group = "com.google.firebase", module = "firebase-measurement-connector")
  }
  // Play Integrity: https://developer.android.com/google/play/integrity/reference/com/google/android/play/core/release-notes
  flavorImplementation(
    libs.google.play.integrity.legacy,
    libs.google.play.integrity.lollipop,
    libs.google.play.integrity.latest
  )
  // ReCaptcha: https://cloud.google.com/recaptcha/docs/release-notes
  flavorImplementation(
    libs.google.recaptcha.legacy,
    libs.google.recaptcha.lollipop,
    libs.google.recaptcha.latest
  )
  // AndroidX/media: https://github.com/androidx/media/blob/release/RELEASENOTES.md
  flavorImplementation(
    libs.androidx.media.common.legacy,
    libs.androidx.media.common.lollipop,
    libs.androidx.media.common.latest
  )
  flavorImplementation(
    libs.androidx.media.transformer.legacy,
    libs.androidx.media.transformer.lollipop,
    libs.androidx.media.transformer.latest
  )
  flavorImplementation(
    libs.androidx.media.effect.legacy,
    libs.androidx.media.effect.lollipop,
    libs.androidx.media.effect.latest
  )
  flavorImplementation(
    libs.androidx.media.exoplayer.legacy,
    libs.androidx.media.exoplayer.lollipop,
    libs.androidx.media.exoplayer.latest
  )
  flavorImplementation(
    libs.androidx.media.exoplayer.hls.legacy,
    libs.androidx.media.exoplayer.hls.lollipop,
    libs.androidx.media.exoplayer.hls.latest
  )
  latestImplementation(libs.androidx.media.inspector.latest)
  // Play In-App Updates: https://developer.android.com/reference/com/google/android/play/core/release-notes-in_app_updates
  implementation(libs.google.play.app.update)
  // The Checker Framework: https://checkerframework.org/CHANGELOG.md
  compileOnly(libs.checkerframework)
  // OkHttp: https://github.com/square/okhttp/blob/master/CHANGELOG.md
  flavorImplementation(
    libs.okhttp.legacy,
    libs.okhttp.latest
  )
  // ShortcutBadger: https://github.com/leolin310148/ShortcutBadger
  implementation(libs.shortcutbadger) {
    artifact { type = "aar" }
  }
  // ReLinker: https://github.com/KeepSafe/ReLinker/blob/master/CHANGELOG.md
  preLatestImplementation(libs.relinker)
  // Konfetti: https://github.com/DanielMartinus/Konfetti/blob/main/README.md
  implementation(libs.konfetti)
  // Transcoder: https://github.com/natario1/Transcoder/blob/master/docs/_about/changelog.md
  legacyImplementation(libs.transcoder)
  // https://github.com/mikereedell/sunrisesunsetlib-java
  implementation(libs.sunriseSunsetCalculator)

  // ZXing: https://github.com/zxing/zxing/blob/master/CHANGES
  implementation(libs.google.zxing.core)

  // subsampling-scale-image-view: https://github.com/davemorrissey/subsampling-scale-image-view
  implementation(libs.subsamplingScaleImageView)

  // mp4parser: https://github.com/sannies/mp4parser/releases
  implementation(libs.mp4parser.isoparser)
}

if (!config.isExperimentalBuild) {
  apply(plugin = libs.plugins.google.services.get().pluginId)
  if (config.isHuaweiBuild) {
    apply(plugin = libs.huawei.agconnect.get().group)
  }
}