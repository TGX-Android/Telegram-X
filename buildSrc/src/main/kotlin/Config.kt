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
  val JAVA_VERSION = org.gradle.api.JavaVersion.VERSION_21
  val ANDROIDX_MEDIA_EXTENSIONS = arrayOf(
    "decoder_ffmpeg",
    "decoder_flac",
    "decoder_opus",
    "decoder_vp9"
  )
  val SUPPORTED_ABI = arrayOf("armeabi-v7a", "arm64-v8a", "x86_64", "x86")
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

class AbiVariant (val flavor: String, vararg val filters: String = arrayOf(), val displayName: String = filters[0]) {
  init {
    if (filters.isEmpty())
      fatal("Empty filters passed")
    for (filter in filters) {
      if (!Config.SUPPORTED_ABI.contains(filter))
        fatal("Unsupported abi filter: $filter")
    }
  }

  val is64Bit: Boolean
    get() {
      for (filter in filters) {
        if (filter != "arm64-v8a" && filter != "x86_64") {
          return false
        }
      }
      return true
    }

  val isTestingLab: Boolean
    get() {
      return flavor == Abi.VARIANTS[Abi.LAB]!!.flavor
    }

  val minSdk: Int
    get() = if (is64Bit) {
      21
    } else {
      16
    }
}

@Suppress("MemberVisibilityCanBePrivate")
object Abi {
  const val UNIVERSAL = 0
  const val ARMEABI_V7A = 1
  const val ARM64_V8A = 2
  const val X86 = 3
  const val X64 = 4
  const val LAB = 5

  val VARIANTS = mapOf(
    Pair(UNIVERSAL, AbiVariant("universal", displayName = "universal", filters = arrayOf("arm64-v8a", "armeabi-v7a"))),
    Pair(ARMEABI_V7A, AbiVariant("arm32", "armeabi-v7a")),
    Pair(ARM64_V8A, AbiVariant("arm64", "arm64-v8a")),
    Pair(X86, AbiVariant("x86", "x86")),
    Pair(X64, AbiVariant("x64", "x86_64", displayName = "x64")),
    Pair(LAB, AbiVariant("lab", displayName = "testing", filters = arrayOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86"))),
  )
}

data class SdkVariant(
  val minSdk: Int = Config.MIN_SDK_VERSION,
  val maxSdk: Int? = null,
  val flavor: String,
  val displayName: String? = flavor
) {
  val isLatest: Boolean =
    flavor == "latest"
  val isMarshmallow: Boolean =
    flavor == "marshmallow"
  val isLollipop: Boolean =
    flavor == "lollipop"
  val isLegacy: Boolean =
    flavor == "legacy"

  val jetpackMediaFlavor: String =
    flavor.takeIf { !isMarshmallow } ?: "latest"
}

object Sdk {
  const val LEGACY = 0
  const val LOLLIPOP = 1
  const val MARSHMALLOW = 2
  const val LATEST = 3

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
    Pair(LATEST, SdkVariant(
      flavor = "latest",
      minSdk = 24,
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