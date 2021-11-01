// File with static configuration, that is meant to be adjusted only once

object Config {
  const val MIN_SDK_VERSION = 16
  val EXOPLAYER_EXTENSIONS = arrayOf("ffmpeg", "flac", "opus", "vp9")
  val SUPPORTED_ABI = arrayOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
}

object LibraryVersions {
  const val MULTIDEX = "2.0.1"
  const val DESUGAR = "1.1.5"
  const val ANDROIDX_CORE = "1.7.0"
  const val ANNOTATIONS = "1.2.0"
}

class AbiVariant (val flavor: String, vararg val filters: String = Config.SUPPORTED_ABI, val displayName: String = filters[0], val sideLoadOnly: Boolean = false) {
  init {
    if (filters.isEmpty())
      error("Empty filters passed")
    for (filter in filters) {
      if (!Config.SUPPORTED_ABI.contains(filter))
        error("Unsupported abi filter: $filter")
    }
  }

  val minSdkVersion: Int
    get() {
      var minSdkVersion = maxOf(21, Config.MIN_SDK_VERSION)
      for (filter in filters) {
        if (filter != "arm64-v8a" && filter != "x86_64") {
          minSdkVersion = Config.MIN_SDK_VERSION
          break
        }
      }
      return minSdkVersion
    }
}

object Abi {
  const val UNIVERSAL = 0
  const val ARMEABI_V7A = 1
  const val ARM64_V8A = 2
  const val X86 = 3
  const val X64 = 4

  val VARIANTS = mapOf(
    Pair(UNIVERSAL, AbiVariant("universal", displayName = "universal", sideLoadOnly = true)),
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
  const val VERSION = 4 // Emoji 13.1
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