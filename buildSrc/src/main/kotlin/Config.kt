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

object Config {
  const val PRIMARY_SDK_VERSION = 21
  const val MIN_SDK_VERSION = 16
  val JAVA_VERSION = org.gradle.api.JavaVersion.VERSION_11
  val ANDROIDX_MEDIA_EXTENSIONS = arrayOf("decoder_ffmpeg", "decoder_flac", "decoder_opus", "decoder_vp9")
  val SUPPORTED_ABI = arrayOf("armeabi-v7a", "arm64-v8a", "x86_64", "x86")
}

object LibraryVersions {
  const val MULTIDEX = "2.0.1"
  const val DESUGAR = "2.0.4"
  const val ANDROIDX_CORE = "1.12.0"
  const val ANNOTATIONS = "1.7.1"
  const val ANDROIDX_MEDIA = "1.3.0"
}

class AbiVariant (val flavor: String, vararg val filters: String = arrayOf(), val displayName: String = filters[0]) {
  init {
    if (filters.isEmpty())
      error("Empty filters passed")
    for (filter in filters) {
      if (!Config.SUPPORTED_ABI.contains(filter))
        error("Unsupported abi filter: $filter")
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

  val minSdkVersion: Int
    get() = if (is64Bit) {
      Config.PRIMARY_SDK_VERSION
    } else {
      Config.MIN_SDK_VERSION
    }
}

@Suppress("MemberVisibilityCanBePrivate")
object Abi {
  const val UNIVERSAL = 0
  const val ARMEABI_V7A = 1
  const val ARM64_V8A = 2
  const val X86 = 3
  const val X64 = 4

  val VARIANTS = mapOf(
    Pair(UNIVERSAL, AbiVariant("universal", displayName = "universal", filters = arrayOf("arm64-v8a", "armeabi-v7a"))),
    Pair(ARMEABI_V7A, AbiVariant("arm32", "armeabi-v7a")),
    Pair(ARM64_V8A, AbiVariant("arm64", "arm64-v8a")),
    Pair(X86, AbiVariant("x86", "x86")),
    Pair(X64, AbiVariant("x64", "x86_64", displayName = "x64"))
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