/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

// File with static configuration, that is meant to be adjusted only once

import tgx.gradle.*
import java.io.File
import java.util.*

object Config {
  const val MIN_SDK_VERSION = 16
  const val MIN_SDK_VERSION_HUAWEI = 17
  val JAVA_VERSION = org.gradle.api.JavaVersion.VERSION_25
  val ANDROIDX_MEDIA_EXTENSIONS = arrayOf(
    "decoder_ffmpeg",
    "decoder_flac",
    "decoder_opus",
    "decoder_vp9"
  )

  val FFMPEG_LIBS = arrayOf(
    "swresample",
    "avformat",
    "swscale",
    "avcodec",
    "avfilter",
    "avutil"
  )
}

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

data class BuildVersions(
  val compileSdkVersion: Int,
  val targetSdkVersion: Int,
  val buildToolsVersion: String,
  val legacyNdkVersion: String,
  val primaryNdkVersion: String
) {
  constructor(version: Properties) : this(
    compileSdkVersion =
      version.getIntOrThrow("version.sdk_compile"),
    targetSdkVersion =
      version.getIntOrThrow("version.sdk_target"),
    buildToolsVersion =
      version.getOrThrow("version.build_tools"),
    legacyNdkVersion =
      version.getOrThrow("version.ndk_legacy"),
    primaryNdkVersion =
      version.getOrThrow("version.ndk_primary")
  )
}

data class ApplicationConfig(
  val sdkDir: String,
  val msys2Dir: String,

  val applicationName: String,
  val applicationId: String,
  val extension: String,
  val sourceCodeUrl: String,

  val applicationVersion: Int,
  val majorVersion: Int,

  val isExperimentalBuild: Boolean,
  val isHuaweiBuild: Boolean,
  val forceOptimize: Boolean,
  val doNotObfuscate: Boolean,
  val build: BuildVersions,

  val nativeLibraryVersion: String,
  val leveldbVersion: String,
  val emojiVersion: Int,

  val telegramApiId: Int,
  val telegramApiHash: String,
  val safetyNetToken: String?,
  val appDownloadUrl: String?,
  val googlePlayUrl: String?,
  val galaxyStoreUrl: String?,
  val huaweiAppGalleryUrl: String?,
  val amazonAppStoreUrl: String?,

  val pullRequests: List<PullRequest>,

  val outputFileNamePrefix: String,
  val creationDateMillis: Long,

  val keystorePropertiesPath: String?
)

data class Keystore(
  val file: File,
  val password: String,
  val keyAlias: String,
  val keyPassword: String
) {
  constructor(configPath: String) : this(loadProperties(configPath))
  constructor(file: File) : this(loadProperties(file))
  constructor(config: org.jetbrains.kotlin.konan.properties.Properties) : this(
    file = File(config.getOrThrow("keystore.file")),
    password = config.getOrThrow("keystore.password"),
    keyAlias = config.getOrThrow("key.alias"),
    keyPassword = config.getOrThrow("key.password")
  )
}

enum class Abi(
  val versionCode: Int
) {
  // do not change: multi-abi APKs intentionally share the same version code
  UNIVERSAL(0),
  ARM(0),
  INTEL(0),

  ARMEABI_V7A(1),
  ARM64_V8A(2),
  X86(3),
  X64(4);

  val isSingleAbi: Boolean
    get() =
      when (this) {
        UNIVERSAL, ARM, INTEL -> false
        else -> true
      }

  fun toDisplayName(): String =
    name.lowercase().replace("_", "-")

  companion object {
    val FILTERS = listOf(
      "arm64-v8a",
      "armeabi-v7a",
      "x86_64",
      "x86"
    )

    fun toFilters(abi: String): List<String> =
      if (abi.isEmpty() || abi == "all") {
        FILTERS
      } else {
        toFilters(fromFilter(abi))
      }

    fun toFilters(abi: Abi): List<String> =
      FILTERS.let { abiFilters ->
        abiFilters.filter { abiFilter ->
          when (abiFilter) {
            "arm64-v8a" -> abi == ARM64_V8A || abi == ARM || abi == UNIVERSAL
            "armeabi-v7a" -> abi == ARMEABI_V7A || abi == ARM || abi == UNIVERSAL
            "x86_64" -> abi == X64 || abi == INTEL || abi == UNIVERSAL
            "x86" -> abi == X86 || abi == INTEL || abi == UNIVERSAL
            else -> {
              fatal("Unknown abi argument: $abi")
            }
          }
        }
      }

    fun fromFilters(abiFilters: List<String>): Abi =
      if (abiFilters.isEmpty() || abiFilters.size > 1) {
        when (abiFilters.size) {
          2 if abiFilters.containsAll(listOf("arm64-v8a", "armeabi-v7a")) ->
            ARM
          2 if abiFilters.containsAll(listOf("x86", "x86_64")) ->
            INTEL
          else ->
            UNIVERSAL
        }
      } else {
        fromFilter(abiFilters[0])
      }

    fun fromFilter(abiFilter: String): Abi =
      when (abiFilter) {
        "arm32", "armeabi-v7a" -> ARMEABI_V7A
        "arm64", "arm64-v8a" -> ARM64_V8A
        "x86" -> X86
        "x64", "x86_64" -> X64
        "all", "universal" -> UNIVERSAL
        "arm" -> ARM
        "intel" -> INTEL
        else -> {
          fatal("Unknown abi filter: $abiFilter")
        }
      }
  }
}

data class SdkVariant(
  val minSdk: Int = Config.MIN_SDK_VERSION,
  val maxSdk: Int? = null,
  val flavor: String,
  val displayName: String? = flavor
) {
  val isLatest: Boolean =
    flavor == "latest"

  val isTens: Boolean =
    flavor == "tens"
  val isMarshmallow: Boolean =
    flavor == "marshmallow"
  val isLollipop: Boolean =
    flavor == "lollipop"
  val isLegacy: Boolean =
    flavor == "legacy"

  val jetpackMediaFlavor: String =
    flavor.takeIf { isLegacy || isLollipop } ?: "latest"

  val usesLegacyNdk: Boolean =
    isLegacy
}

object Sdk {
  const val LEGACY = 0
  const val LOLLIPOP = 1
  const val MARSHMALLOW = 2
  const val NOUGAT_OREO_PIE = 3
  const val Q_R_S = 4
  const val LATEST = 5

  val VARIANTS = mapOf(
    Pair(LEGACY, SdkVariant(
      flavor = "legacy",
      minSdk = 16,
      maxSdk = 20
    )),
    Pair(LOLLIPOP, SdkVariant(
      flavor = "lollipop",
      minSdk = 21,
      maxSdk = 22
    )),
    Pair(MARSHMALLOW, SdkVariant(
      flavor = "marshmallow",
      minSdk = 23,
      maxSdk = 23
    )),
    Pair(NOUGAT_OREO_PIE, SdkVariant(
      flavor = "tens",
      minSdk = 24,
      maxSdk = 28,
      displayName = "nougatOreoPie"
    )),
    Pair(Q_R_S, SdkVariant(
      flavor = "qrs",
      minSdk = 29,
      maxSdk = 32,
      displayName = "QRS"
    )),
    Pair(LATEST, SdkVariant(
      flavor = "latest",
      minSdk = 33,
      displayName = null
    ))
  )
}

object App {
  // File extension to be detected as project's theme. Do not change unless you make changes to themes engine / add exclusive colors
  const val THEME_EXTENSION = "tgx-theme"
}

object Emoji {
  // Identifier of the built-in emoji set. Change it if built-in emoji pack is changed
  const val BUILTIN_ID = "apple"
}

object Telegram {
  // Cloud storage for emoji, icons, fonts, etc
  const val RESOURCES_CHANNEL = "Y2xvdWRfdGd4X2FuZHJvaWRfcmVzb3Vy"

  // Channel where to look up for new apks
  const val UPDATES_CHANNEL = "tgx_log"

  // Language pack on server. Do not change
  const val LANGUAGE_PACK = "android_x"
}