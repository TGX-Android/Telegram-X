@file:Suppress("UnstableApiUsage", "AvoidApplyPluginMethod")

import androidx.baselineprofile.gradle.consumer.BaselineProfileConsumerExtension
import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.BuildConfigField
import com.android.build.api.variant.impl.VariantOutputImpl
import org.gradle.kotlin.dsl.support.uppercaseFirstChar
import tgx.gradle.*
import tgx.gradle.source.GitVersionSource
import tgx.gradle.task.*
import java.util.*

plugins {
  id("java-toolchain-convention")
  id(libs.plugins.android.application.get().pluginId)
  id("tgx-config")
  id("tgx-module")
}

val config = tgxConfig.config.get()
val generateBaselineProfile = tgxConfig.generateBaselineProfile.get()
val useLegacyNdk = tgxConfig.useLegacyNdk.get()
val appliedNdkVersion = if (useLegacyNdk) {
  config.build.legacyNdkVersion
} else {
  config.build.primaryNdkVersion
}
val ndkMinSdkVersion = appliedNdkVersion.ndkVersionToMinSdk()

val generateThemes = tasks.register<GenerateThemesTask>("generateThemes") {
  group = "Setup"
  description = "Generates fresh ids, theme resources and utility methods based on current theme files"
  // Input
  colorsAndProperties.set(layout.projectDirectory.file(
    "src/main/other/themes/colors-and-properties.xml"
  ))
  themeFiles.from(layout.projectDirectory.dir(
    "src/main/other/themes"
  ).asFileTree.matching {
    include("*.tgx-theme")
  })
  // Output
  resOutputDir.set(layout.buildDirectory.dir(
    "generated/tgx/themes/res"
  ))
  javaOutputDir.set(layout.buildDirectory.dir(
    "generated/tgx/themes/java"
  ))
  kotlinOutputDir.set(layout.buildDirectory.dir(
    "generated/tgx/themes/kotlin"
  ))
}
val generateLangFunctions = tasks.register<GenerateLangFunctions>("generateLangFunctions") {
  group = "Setup"
  description = "Generates extra string resources and utility methods based on strings.xml"
  // Input
  stringsXml.set(layout.projectDirectory.file(
    "src/main/res/values/strings.xml"
  ))
  colorIdJava.set(generateThemes.flatMap {
    it.javaOutputDir.file(
      "org/thunderdog/challegram/theme/ColorId.java"
    )
  })
  propertyIdJava.set(generateThemes.flatMap {
    it.javaOutputDir.file(
      "org/thunderdog/challegram/theme/PropertyId.java"
    )
  })
  // Output
  resOutputDir.set(layout.buildDirectory.dir(
    "generated/tgx/strings/res"
  ))
  kotlinOutputDir.set(layout.buildDirectory.dir(
    "generated/tgx/strings/kotlin"
  ))
}
val generateEmojiSetsTask = tasks.register<GenerateEmojiSetsTask>("checkEmojiKeyboard") {
  group = "Setup"
  description = "Checks that all supported emoji can be entered from the keyboard"
  // Input
  emojiCode.set(layout.projectDirectory.file(
    "src/main/java/org/thunderdog/challegram/tool/EmojiCode.java"
  ))
  emojiCodeColored.set(layout.projectDirectory.file(
    "src/main/java/org/thunderdog/challegram/tool/EmojiCodeColored.java"
  ))
  // Output
  kotlinOutputDir.set(layout.buildDirectory.dir(
    "generated/tgx/emojis/kotlin"
  ))
}
val generateExceptions = tasks.register<GenerateExceptionsTask>("updateExceptions") {
  group = "Setup"
  description = "Updates exception class names with the app or TDLib version number in order to have separate group on Google Play Developer Console"
  applicationVersion.set(
    config.applicationVersion
  )
  javaOutputDir.set(layout.buildDirectory.dir(
    "generated/tgx/exceptions/java"
  ))
}
val validateApiTokens = tasks.register<ValidateApiTokensTask>("validateApiTokens") {
  group = "Setup"
  description = "Validates some API tokens to make sure they work properly and won't cause problems"
  applicationId.set(
    config.applicationId
  )
  googleServicesJson.set(layout.projectDirectory.file(
    "google-services.json"
  ))
}
val fetchLocalizedStrings = tasks.register<FetchLocalizedStringsTask>("fetchLocalizedStrings") {
  group = "Setup"
  description = "Generates and updates all strings.xml resources based on translations.telegram.org"
  resOutputDir.set(layout.buildDirectory.dir(
    "generated/tgx/locales/res"
  ))
}

val patchJetpackMediaTasks = Sdk.VARIANTS.values.associateBy({ it.jetpackMediaFlavor }) { variant ->
  tasks.register<PatchJetpackMediaTask>(
    "patchJetpackMedia${variant.flavor.uppercaseFirstChar()}"
  ) {
    group = "Setup"
    description = "Copies patched androidx-media extensions for ${variant.flavor} flavor"
    inputDirs.from(Config.ANDROIDX_MEDIA_EXTENSIONS.map { extension ->
      layout.projectDirectory.dir(
        "thirdparty/androidx-media/${
          variant.jetpackMediaFlavor
        }/libraries/$extension/src/main/jni"
      )
    })
    outputDir.set(layout.buildDirectory.dir(
      "generated/tgx/androidx-media/${variant.jetpackMediaFlavor}"
    ))
  }
}

val patchJetpackMedia = tasks.register("patchJetpackMedia") {
  group = "Setup"
  description = "Copies patched androidx-media extensions for all flavors"
  dependsOn(patchJetpackMediaTasks.values)
}

val patchOpusTask = tasks.register<PatchOpusTask>(
  "patchOpus"
) {
  group = "Setup"
  description = "Creates a patched copy of opus"
  inputDir.set(layout.projectDirectory.dir(
    "jni/third_party/opus"
  ))
  inputSources.from(inputDir.asFileTree.matching {
    exclude(
      ".git",
      ".github",
      "doc",
      "tests",
      "**/*.md"
    )
  })
  outputDir.set(layout.buildDirectory.dir(
    "generated/tgx/opus"
  ))
}

val buildLibvpxTasks = Sdk.VARIANTS.values.flatMap { sdkVariant ->
  val abiVariants = if (sdkVariant.minSdk >= 21) {
    arrayOf("arm64", "arm32", "x86", "x64")
  } else {
    arrayOf("arm32", "x86")
  }
  abiVariants.map { abiVariant ->
    Pair(Pair(sdkVariant.flavor, abiVariant), tasks.register<BuildLibvpxTask>(
      "buildLibvpx${sdkVariant.flavor.uppercaseFirstChar()}${abiVariant.uppercaseFirstChar()}"
    ) {
      group = "Setup"
      description = "Builds libvpx for ${sdkVariant.flavor}, $abiVariant flavor"
      // System
      sdkDir.set(File(config.sdkDir))
      // sdkDir.fileValue(File(config.sdkDir))
      ndkVersion.set(android.ndkVersion)
      hostTag.set(findHostTag())
      // Input
      inputDir.set(layout.projectDirectory.dir(
        "jni/third_party/libvpx"
      ))
      inputSources.from(inputDir.asFileTree.matching {
        exclude(
          ".git",
          "test",
          "third_party",
          "tools",
          "examples",
          "*.md"
        )
      })
      sdkFlavor.set(sdkVariant.flavor)
      abi.set(abiVariant.toAbiFilter())
      // Output
      buildDir.set(layout.buildDirectory.dir(
        "generated/tgx/libvpx-build/${sdkVariant.flavor}/${abiVariant.toAbiFilter()}"
      ))
      outputDir.set(layout.buildDirectory.dir(
        "generated/tgx/libvpx/${sdkVariant.flavor}/${abiVariant.toAbiFilter()}"
      ))
    })
  }
}.toMap()
val buildLibvpxTask = tasks.register("buildLibvpx") {
  group = "Setup"
  description = "Builds libvpx for all flavors"
  dependsOn(buildLibvpxTasks.values)
}

val buildFfmpegTasks = Sdk.VARIANTS.values.flatMap { sdkVariant ->
  val abiVariants = if (sdkVariant.minSdk >= 21) {
    arrayOf("arm64", "arm32", "x86", "x64")
  } else {
    arrayOf("arm32", "x86")
  }
  abiVariants.map { abiVariant ->
    val key = Pair(sdkVariant.flavor, abiVariant)
    val task = tasks.register<BuildFfmpegTask>(
      "buildFfmpeg${sdkVariant.flavor.uppercaseFirstChar()}${abiVariant.uppercaseFirstChar()}"
    ) {
      group = "Setup"
      description = "Builds FFmpeg for ${sdkVariant.flavor}, $abiVariant flavor"
      // System
      sdkDir.fileValue(File(config.sdkDir))
      ndkVersion.set(android.ndkVersion)
      hostTag.set(findHostTag())
      // Input
      inputDir.set(layout.projectDirectory.dir(
        "jni/third_party/ffmpeg"
      ))
      inputSources.from(inputDir.asFileTree.matching {
        exclude(
          ".git",
          "doc",
          "tests",
          "*.md"
        )
      })
      sdkFlavor.set(sdkVariant.flavor)
      abi.set(abiVariant.toAbiFilter())
      libvpxDir.set(layout.buildDirectory.dir(
        "generated/tgx/libvpx/${sdkVariant.flavor}/${abiVariant.toAbiFilter()}"
      ))
      // Output
      buildDir.set(layout.buildDirectory.dir(
        "generated/tgx/ffmpeg-build/${sdkVariant.flavor}/${abiVariant.toAbiFilter()}"
      ))
      outputDir.set(layout.buildDirectory.dir(
        "generated/tgx/ffmpeg/${sdkVariant.flavor}/${abiVariant.toAbiFilter()}"
      ))
      dependsOn(buildLibvpxTasks[key] ?: error("libvpx task not found for $key"))
    }
    Pair(key, task)
  }
}.toMap()
val buildFfmpegTask = tasks.register("buildFfmpeg") {
  group = "Setup"
  description = "Builds FFmpeg for all flavors"
  dependsOn(buildFfmpegTasks.values)
}

//noinspection WrongGradleMethod
android {
  namespace = "org.thunderdog.challegram"

  lint {
    disable += arrayOf(
      "MissingTranslation",
      "RtlHardcoded",
      "ClickableViewAccessibility",
      "ViewConstructor",
      "VectorPath",
      "LocaleFolder",
      "StringFormatCount",
      "IconDuplicates",

      "MissingPermission",
      "ScopedStorage",
      "SelectedPhotoAccess",

      "AppCompatCustomView",
      "AppCompatResource",
      "UseCompatLoadingForDrawables",

      // FIXME
      "UnusedResources",
      "ThreadConstraint",
      "SwitchIntDef",
      "WrongConstant"
    )
    checkDependencies = true
  }

  externalNativeBuild {
    cmake {
      path("jni/CMakeLists.txt")
    }
  }

  defaultConfig {
    applicationId = config.applicationId
    targetSdk = config.build.targetSdkVersion
    multiDexEnabled = true

    resValue("string", "AppName", config.applicationName)
    resValue("string", "account_type", "${config.applicationId}.sync.account")
    resValue("string", "content_authority", "${config.applicationId}.sync.provider")

    buildConfigString("PROJECT_NAME", config.applicationName)
    buildConfigBool("SHARED_STL", ndkVersion.ndkVersionMajor() >= 27)
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

    buildConfigInt("TARGET_SDK_INT", config.build.targetSdkVersion)

    buildConfigInt("TELEGRAM_API_ID", config.telegramApiId)
    buildConfigString("TELEGRAM_API_HASH", config.telegramApiHash)

    buildConfigString("TELEGRAM_RESOURCES_CHANNEL", Telegram.RESOURCES_CHANNEL)
    buildConfigString("TELEGRAM_UPDATES_CHANNEL", Telegram.UPDATES_CHANNEL)

    buildConfigInt("EMOJI_VERSION", config.emojiVersion)
    buildConfigString("EMOJI_BUILTIN_ID", Emoji.BUILTIN_ID)

    buildConfigString("LANGUAGE_PACK", Telegram.LANGUAGE_PACK)

    buildConfigString("THEME_FILE_EXTENSION", App.THEME_EXTENSION)

    // Library versions in BuildConfig.java

    var tdlibVersion = ""
    val tdlibCommit = requireFile(project.isolated.rootProject.projectDirectory.file("tdlib/version.txt").asFile).bufferedReader().readLine().take(7)
    val tdlibVersionFile = requireFile(project.isolated.rootProject.projectDirectory.file("tdlib/source/td/CMakeLists.txt").asFile)
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

    buildConfigString("TDLIB_VERSION", tdlibVersion)

    val tgxGitVersionProvider = providers.of(GitVersionSource::class) {
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

    // OpenSSL version

    val openSslGit = providers.of(GitVersionSource::class) {
      parameters.module = layout.projectDirectory.dir("../tdlib/source/openssl")
    }.get()
    buildConfigString("OPENSSL_COMMIT", openSslGit.commitHashShort)
    buildConfigString("OPENSSL_COMMIT_URL", openSslGit.commitUrl)

    // WebRTC version

    val webrtcGit = providers.of(GitVersionSource::class) {
      parameters.module = layout.projectDirectory.dir("jni/third_party/webrtc")
    }.get()
    buildConfigString("WEBRTC_COMMIT", webrtcGit.commitHashShort)
    buildConfigString("WEBRTC_COMMIT_URL", webrtcGit.commitUrl)

    // tgcalls version

    val tgcallsGit = providers.of(GitVersionSource::class) {
      parameters.module = layout.projectDirectory.dir("jni/third_party/tgcalls")
    }.get()
    buildConfigString("TGCALLS_COMMIT", tgcallsGit.commitHashShort)
    buildConfigString("TGCALLS_COMMIT_URL", tgcallsGit.commitUrl)

    // FFmpeg version

    val ffmpegGit = providers.of(GitVersionSource::class) {
      parameters.module = layout.projectDirectory.dir("jni/third_party/ffmpeg")
    }.get()
    buildConfigString("FFMPEG_COMMIT", ffmpegGit.commitHashShort)
    buildConfigString("FFMPEG_COMMIT_URL", ffmpegGit.commitUrl)

    // WebP version

    val webpGit = providers.of(GitVersionSource::class) {
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
    // TODO: Exclude in FOSS variant
    kotlin.directories += "src/google/main/java"
    java.directories += "src/google/main/java"
  }

  lint {
    disable += "MissingTranslation"
    checkDependencies = true
  }

  buildFeatures {
    buildConfig = true
    resValues = true
  }

  flavorDimensions += arrayOf("SDK", "ABI")
  androidComponents.disableRudimentaryVariants { sdkVariant, abiVariant ->
    maxOf(sdkVariant.minSdk, abiVariant.minSdk) >= ndkMinSdkVersion &&
    (sdkVariant.usesLegacyNdk == useLegacyNdk || config.build.primaryNdkVersion == config.build.legacyNdkVersion)
  }
  productFlavors {
    Sdk.VARIANTS.forEach { (sdkIndex, variant) ->
      create(variant.flavor) {
        dimension = "SDK"
        isDefault = sdkIndex == Sdk.LATEST

        if (generateBaselineProfile && !variant.isLatest) {
          matchingFallbacks += Sdk.VARIANTS[Sdk.LATEST]!!.flavor
        }
        Sdk.VARIANTS.forEach { (subSdkIndex, subVariant) ->
          buildConfigBool("${subVariant.flavor.uppercase()}_FLAVOR", sdkIndex == subSdkIndex)
        }

        val selectedMinSdk = maxOf(
          variant.minSdk,
          Config.MIN_SDK_VERSION_HUAWEI.takeIf { config.isHuaweiBuild } ?: 0,
          ndkMinSdkVersion
        )
        minSdk = selectedMinSdk
        if (selectedMinSdk < 21) {
          proguardFile("proguard-r8-bug-android-4.x-workaround.pro")
        }

        if (selectedMinSdk > Sdk.VARIANTS[Sdk.LEGACY]!!.minSdk) {
          lint {
            disable += "ObsoleteSdkInt"
          }
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
          targets += arrayOf("tgxjni", "tgcallsjni")
          arguments(
            "-DANDROID_PLATFORM=android-${selectedMinSdk}",
            "-DANDROID_STL=${if (appliedNdkVersion.ndkVersionMajor() >= 27) "c++_shared" else "c++_static"}",
            "-DCMAKE_BUILD_WITH_INSTALL_RPATH=ON",
            "-DCMAKE_SKIP_RPATH=ON",
            "-DCMAKE_C_VISIBILITY_PRESET=hidden",
            "-DCMAKE_CXX_VISIBILITY_PRESET=hidden",
            "-DCMAKE_SHARED_LINKER_FLAGS=-Wl,--gc-sections,--icf=safe -Wl,--build-id=sha1",
            "-DCMAKE_C_FLAGS=-D_LARGEFILE_SOURCE=1 ${flags.joinToString(" ")}",
            "-DCMAKE_CXX_FLAGS=-std=c++17 ${flags.joinToString(" ")}",
            "-DTGX_FLAVOR=${variant.flavor}",
            "-DTGX_ROOT_DIR=${project.isolated.rootProject.projectDirectory.asFile.absolutePath}",
            "-DFFMPEG_LIBS=${Config.FFMPEG_LIBS.joinToString(";")}"
          )

          val dirs = mapOf(
            "ANDROIDX_MEDIA_DIR" to layout.buildDirectory.dir(
              "generated/tgx/androidx-media/${variant.jetpackMediaFlavor}"
            ),
            "OPUS_DIR" to layout.buildDirectory.dir(
              "generated/tgx/opus"
            ),
            "LIBVPX_DIR" to layout.buildDirectory.dir(
              "generated/tgx/libvpx/${variant.flavor}"
            ),
            "FFMPEG_DIR" to layout.buildDirectory.dir(
              "generated/tgx/ffmpeg/${variant.flavor}"
            )
          ).map {
            "-D${it.key}=${it.value.get().asFile.absolutePath}"
          }.toTypedArray()
          arguments(*dirs)
        }

        sourceSets.getByName(variant.flavor) {
          Config.ANDROIDX_MEDIA_EXTENSIONS.forEach { extension ->
            java.directories += "thirdparty/androidx-media/${
              variant.jetpackMediaFlavor
            }/libraries/${extension}/src/main/java"
          }
          val extraFolders = findExtraFolders(variant)
          extraFolders.forEach { folderName ->
            kotlin.directories += "src/$folderName/kotlin"
            java.directories += "src/$folderName/java"
            res.directories += "src/$folderName/res"

            // TODO: Exclude in FOSS variant
            kotlin.directories += "src/google/$folderName/kotlin"
            java.directories += "src/google/$folderName/java"
          }
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
          val proguardFile = project.layout.projectDirectory.file(
            "thirdparty/androidx-media/${
              variant.jetpackMediaFlavor
            }/libraries/${extension}/proguard-rules.txt"
          ).asFile
          if (proguardFile.exists()) {
            extraProguardFileCount++
            proguardFile(proguardFile)
          }
        }

        if (extraProguardFileCount > 0) {
          project.logger.lifecycle("[proguard]: Applied $extraProguardFileCount extra proguard files for \"${variant.flavor}\" flavor")
        } else {
          fatal("Unable to find any proguard files for ${variant.flavor} flavor")
        }
      }
    }

    Abi.VARIANTS.filter { (abiIndex, variant) ->
      (generateBaselineProfile || !variant.isTestingLab)
    }.forEach { (abiIndex, variant) ->
      create(variant.flavor) {
        dimension = "ABI"
        isDefault = abiIndex == 0

        if (generateBaselineProfile && !variant.isTestingLab) {
          matchingFallbacks += Abi.VARIANTS[Abi.LAB]!!.flavor
        }
        Abi.VARIANTS.forEach { (subAbiIndex, subVariant) ->
          buildConfigBool("${subVariant.flavor.uppercase()}_FLAVOR", abiIndex == subAbiIndex)
        }

        ndkVersion = appliedNdkVersion
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

  androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
      if (!config.isExperimentalBuild) {
        variant.lifecycleTasks.registerPreBuild(validateApiTokens)
      }
      variant.sources.res?.addGeneratedSourceDirectory(
        fetchLocalizedStrings, FetchLocalizedStringsTask::resOutputDir
      )
    }

    onVariants { variant ->
      val abiFlavor = variant.productFlavors.first { it.first == "ABI" }.second
      val sdkFlavor = variant.productFlavors.first { it.first == "SDK" }.second

      val (abi, abiVariant) = Abi.VARIANTS.entries.first { it.value.flavor == abiFlavor }
      val (sdk, sdkVariant) = Sdk.VARIANTS.entries.first { it.value.flavor == sdkFlavor }

      val nativeBuildTasks = mutableListOf<TaskProvider<out Task>>()

      nativeBuildTasks.addAll(arrayOf(
        patchJetpackMediaTasks[sdkVariant.jetpackMediaFlavor]!!,
        patchOpusTask
      ))

      abiVariant.filters.filter {
        sdkVariant.minSdk >= 21 || it == "armeabi-v7a" || it == "x86"
      }.map {
        Pair(sdkVariant.flavor, it.toAbiVariant())
      }.forEach { key ->
        val buildLibvpxTask = buildLibvpxTasks[key] ?: error("libvpx task not found for $key")
        val buildFfmpegTask = buildFfmpegTasks[key] ?: error("ffmpeg task not found for $key")
        nativeBuildTasks += buildLibvpxTask
        nativeBuildTasks += buildFfmpegTask
      }

      val buildNativeTask = tasks.register<ValidateNativeBuildTask>("buildNativeDependencies${variant.name.uppercaseFirstChar()}") {
        group = "Setup"
        description = "Builds native dependencies for ${sdkVariant.flavor}, $abiVariant flavor and validates output"
        jetpackMediaDir.set(layout.buildDirectory.dir(
          "generated/tgx/androidx-media/${sdkVariant.jetpackMediaFlavor}"
        ))
        opusDir.set(layout.buildDirectory.dir(
          "generated/tgx/opus"
        ))
        libvpxDirs.from(abiVariant.filters.map { abiFilter ->
          layout.buildDirectory.dir(
            "generated/tgx/libvpx/${sdkVariant.flavor}/$abiFilter"
          )
        })
        ffmpegDirs.from(abiVariant.filters.map { abiFilter ->
          layout.buildDirectory.dir(
            "generated/tgx/ffmpeg/${sdkVariant.flavor}/$abiFilter"
          )
        })
        dependsOn(*nativeBuildTasks.toTypedArray())
      }
      variant.lifecycleTasks.registerPreBuild(buildNativeTask)

      variant.sources.res?.apply {
        addGeneratedSourceDirectory(
          generateThemes, GenerateThemesTask::resOutputDir
        )
        addGeneratedSourceDirectory(
          generateLangFunctions, GenerateLangFunctions::resOutputDir
        )
      }
      variant.sources.java?.apply {
        addGeneratedSourceDirectory(
          generateThemes, GenerateThemesTask::javaOutputDir
        )
        addGeneratedSourceDirectory(
          generateExceptions, GenerateExceptionsTask::javaOutputDir
        )
      }
      variant.sources.kotlin?.apply {
        addGeneratedSourceDirectory(
          generateThemes, GenerateThemesTask::kotlinOutputDir
        )
        addGeneratedSourceDirectory(
          generateLangFunctions, GenerateLangFunctions::kotlinOutputDir
        )
        addGeneratedSourceDirectory(
          generateEmojiSetsTask, GenerateEmojiSetsTask::kotlinOutputDir
        )
      }
    }

    onVariants { variant ->
      val abiFlavor = variant.productFlavors.first { it.first == "ABI" }.second
      val sdkFlavor = variant.productFlavors.first { it.first == "SDK" }.second

      val (abi, abiVariant) = Abi.VARIANTS.entries.first { it.value.flavor == abiFlavor }
      val (sdk, sdkVariant) = Sdk.VARIANTS.entries.first { it.value.flavor == sdkFlavor }

      val flavorVersionCode = if (variant.debuggable) 0 else {
        sdk * 100 + abi
      }
      val flavorVersionNameSuffix = StringBuilder().apply {
        if (config.extension != "none") {
          append("-${config.extension}")
        }
        if (!sdkVariant.displayName.isNullOrEmpty()) {
          append("-${sdkVariant.displayName}")
        }
        if (abiVariant.displayName != "universal" || (config.extension == "none" && sdkVariant.displayName.isNullOrEmpty())) {
          append("-${abiVariant.displayName}")
        }
        if (variant.debuggable) {
          append("-debug")
        }
      }.toString()

      var baseVersionCode: Int? = null
      var baseVersionName: String? = null
      var fileName: String? = null

      variant.outputs.forEach { output ->
        baseVersionCode = output.versionCode.get()
        val modifiedVersionCode = baseVersionCode * 1000 + flavorVersionCode
        output.versionCode.set(modifiedVersionCode)

        baseVersionName = output.versionName.get()
        val modifiedVersionName = "$baseVersionName.$baseVersionCode$flavorVersionNameSuffix"
        output.versionName.set(modifiedVersionName)

        fileName = "${config.outputFileNamePrefix}-${modifiedVersionName.replace(Regex("-universal(?=-|$)"), "")}"
        if (output is VariantOutputImpl) {
          output.outputFileName.set("$fileName.apk")
        }
      }
      require(baseVersionCode != null && baseVersionName != null && fileName != null)

      val recaptchaVersion = selectApiFlavor(
        sdkVariant,
        libs.google.recaptcha.legacy,
        libs.google.recaptcha.lollipop,
        libs.google.recaptcha.marshmallow,
        libs.google.recaptcha.latest
      ).get().version!!
      require(recaptchaVersion.isNotEmpty() && recaptchaVersion.matches(Regex("^[0-9.]+$"))) {
        "Invalid ReCaptcha version: $recaptchaVersion"
      }

      variant.buildConfigFields!!.apply {
        put("ABI", BuildConfigField(
          "int", abi, null
        ))
        put("RECAPTCHA_VERSION", BuildConfigField(
          "String", "\"$recaptchaVersion\"", null
        ))
        put("ORIGINAL_VERSION_CODE", BuildConfigField(
          "int", baseVersionCode, null
        ))
        put("ORIGINAL_VERSION_NAME", BuildConfigField(
          "String", "\"$baseVersionName.$baseVersionCode\"", null
        ))

        var openSslVersionFull = ""
        var openSslReleaseDate = ""
        val openSslVersionFile = requireFile(project.isolated.rootProject.projectDirectory.file("tdlib/openssl/${ndkVersion}/${abiVariant.filters.first()}/include/openssl/opensslv.h").asFile)
        openSslVersionFile.bufferedReader().use { reader ->
          val regex = Regex("^# define (OPENSSL_FULL_VERSION_STR|OPENSSL_RELEASE_DATE)\\s*\"([^\"]+)\"$")
          while (true) {
            val line = reader.readLine() ?: break
            val result = regex.find(line)
            if (result != null) {
              val varName = result.groupValues[1]
              val value = result.groupValues[2]
              when (varName) {
                "OPENSSL_FULL_VERSION_STR" -> openSslVersionFull = value
                "OPENSSL_RELEASE_DATE" -> openSslReleaseDate = value
                else -> error(varName)
              }
              if (openSslVersionFull.isNotEmpty() && openSslReleaseDate.isNotEmpty()) {
                break
              }
            }
          }
        }
        if (openSslVersionFull.isEmpty()) {
          fatal("OpenSSL not found!")
        }
        put("OPENSSL_VERSION_FULL", BuildConfigField(
          "String", "\"$openSslVersionFull\"", null
        ))
        put("OPENSSL_RELEASE_DATE", BuildConfigField(
          "String", "\"$openSslReleaseDate\"", null
        ))
      }

      val extraFolders = findExtraFolders(sdkVariant)
      extraFolders.forEach { folderName ->
        variant.sources.manifests.addStaticManifestFile(
          "src/$folderName/AndroidManifest.xml"
        )
        // TODO: Exclude in FOSS variant
        variant.sources.manifests.addStaticManifestFile(
          "src/google/$folderName/AndroidManifest.xml"
        )
      }

      if (variant.isMinifyEnabled) {
        val variantName = variant.name.uppercaseFirstChar()
        val copyTask = project.tasks.register<Copy>(
          "copy${variantName}MappingFile"
        ) {
          description = "Creates a copy of mapping.txt with a build name"
          from(variant.artifacts.get(SingleArtifact.OBFUSCATION_MAPPING_FILE))
          into(project.layout.buildDirectory.dir("outputs/mapping/${variant.name}"))
          rename("mapping.txt", "$fileName.txt")
        }
        project.afterEvaluate {
          project.tasks.findByName("assemble$variantName")?.finalizedBy(copyTask)
        }
      }
    }
  }
}

if (generateBaselineProfile) {
  apply(plugin = libs.plugins.androidx.baselineprofile.get().pluginId)

  extensions.configure<BaselineProfileConsumerExtension> {
    mergeIntoMain = true
    automaticGenerationDuringBuild = false
    saveInSrc = true
    warnings.disabledVariants = false
  }

  afterEvaluate {
    dependencies.add("latestLabReleaseBaselineProfile", project(":baseline-profile"))
  }
}

dependencies {
  sinceNougatImplementation(libs.androidx.profileinstaller)
  flavorImplementation(
    libs.androidx.tracing.legacy,
    libs.androidx.tracing.lollipop,
    libs.androidx.tracing.latest
  )
  legacyImplementation(libs.androidx.multidex)
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
    libs.androidx.browser.lollipop,
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
    libs.google.play.services.maps.lollipop,
    libs.google.play.services.maps.latest
  )
  flavorImplementation(
    libs.google.play.services.location.legacy,
    libs.google.play.services.location.lollipop,
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
    libs.google.recaptcha.marshmallow,
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
  sinceMarshmallowImplementation(libs.androidx.media.inspector.latest)
  // Play In-App Updates: https://developer.android.com/reference/com/google/android/play/core/release-notes-in_app_updates
  implementation(libs.google.play.app.update)
  // Play Billing: https://developer.android.com/google/play/billing/release-notes
  sinceLollipopImplementation(
    libs.google.play.billing.lollipop,
    libs.google.play.billing.latest
  )
  // The Checker Framework: https://checkerframework.org/CHANGELOG.md
  compileOnly(libs.annotations.checkerframework)
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
  preMarshmallowImplementation(libs.relinker)
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

  // Compiler warnings
  compileOnly(libs.annotations.errorprone)
  compileOnly(libs.annotations.j2objc)
  compileOnly(libs.androidx.room.latest)
  compileOnly(libs.annotations.jsr305)
  compileOnly(libs.annotations.kotlin)
}

if (!config.isExperimentalBuild) {
  apply(plugin = libs.plugins.google.services.get().pluginId)
  if (config.isHuaweiBuild) {
    apply(plugin = libs.huawei.agconnect.get().group)
  }
}