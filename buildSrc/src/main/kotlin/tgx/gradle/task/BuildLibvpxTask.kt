package tgx.gradle.task

import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.konan.file.File
import tgx.gradle.createEmptyDir
import tgx.gradle.fatal
import tgx.gradle.requireDir
import tgx.gradle.requireFile

@CacheableTask
abstract class BuildLibvpxTask : BuildNativeLibraryTask() {
  @TaskAction
  fun buildLibvpx() {
    val input = requireDir(inputDir.get().asFile)

    val flavor = this.sdkFlavor.get()
    val abi = this.abi.get()
    val hostTag = this.hostTag.get()
    val ndkVersion = this.ndkVersion.get()

    val ndk = requireDir(
      sdkDir.get().asFile.resolve("ndk/$ndkVersion")
    )
    val prebuilt = requireDir(ndk.resolve(
      "toolchains/llvm/prebuilt/$hostTag"
    ))
    val sysroot = requireDir(prebuilt.resolve(
      "sysroot"
    ))
    val androidApiLevel = when (flavor) {
      "legacy" -> 16
      "lollipop" -> 21
      "marshmallow" -> 23
      "latest" -> 24
      else -> error("Unknown flavor: $flavor")
    }

    val extraParams = mutableListOf<String>()
    val cFlags = mutableListOf(
      "-O3",
      "-fpic",
      "-fpie",
      "-ffunction-sections",
      "-fdata-sections",
      "-fvisibility=hidden",
      "-fvisibility-inlines-hidden",
      "-fno-strict-aliasing",
      "-fomit-frame-pointer"
    )
    val ldFlags = mutableListOf(
      "-L${requireDir(
        sysroot.resolve("usr/lib")
      ).absolutePath}"
    )
    val ndkAbi: String
    when (abi) {
      "arm64-v8a" -> {
        ndkAbi = "aarch64-linux-android"
        cFlags += "-march=armv8-a"
        extraParams += "--enable-neon"
      }
      "armeabi-v7a" -> {
        ndkAbi = "armv7a-linux-androideabi"
        extraParams += "--enable-neon"

        val cpuFeatures = requireDir(
          ndk.resolve("sources/android/cpufeatures")
        )
        cFlags.addAll(listOf(
          "-march=armv7-a",
          "-mfloat-abi=softfp",
          "-mfpu=neon",
          "-mthumb",
          "-mtune=cortex-a8",
          "-I${cpuFeatures.absolutePath}"
        ))
      }
      "x86_64" -> {
        ndkAbi = "x86_64-linux-android"
        cFlags.addAll(listOf(
          "-march=x86-64",
          "-msse4.2",
          "-mpopcnt",
          "-fPIC"
        ))
      }
      "x86" -> {
        ndkAbi = "i686-linux-android"
        cFlags.addAll(listOf(
          "-march=i686",
          "-msse3",
          "-mfpmath=sse",
          "-fPIC"
        ))
      }
      else -> {
        error("Unsupported abi: $abi")
      }
    }
    val cc = requireFile(prebuilt.resolve(
      "bin/${ndkAbi}${androidApiLevel}-clang"
    ))
    val cxx = requireFile(prebuilt.resolve(
      "bin/${ndkAbi}${androidApiLevel}-clang++"
    ))
    val cppFlags = cFlags.joinToString(" ")
    val env = mapOf(
      "CFLAGS" to cppFlags,
      "CPPFLAGS" to cppFlags,
      "CXXFLAGS" to "$cppFlags -std=c++17",
      "LDFLAGS" to ldFlags.joinToString(" "),

      "PATH" to arrayOf(
        "${requireDir(
          prebuilt.resolve("bin")
        ).absolutePath}",
        System.getenv("PATH")?.takeIf { it.isNotEmpty() }
      ).filterNotNull().joinToString(File.pathSeparator),

      "AR" to "${
        requireFile(
          prebuilt.resolve("bin/llvm-ar")
        ).absolutePath
      }",

      "CC" to "${cc.absolutePath}",
      "AS" to "${cc.absolutePath}",
      "LD" to "${cc.absolutePath}",

      "CXX" to "${cxx.absolutePath}",
      "CPP" to "${cxx.absolutePath}",

      "YASM" to "${requireFile(
        prebuilt.resolve("bin/yasm")
      ).absolutePath}",

      "STRIP" to "${requireFile(
        prebuilt.resolve("bin/llvm-strip")
      ).absolutePath}",
      "RANLIB" to "${requireFile(
        prebuilt.resolve("bin/llvm-ranlib")
      ).absolutePath}",
      "NM" to "${requireFile(
        prebuilt.resolve("bin/llvm-nm")
      ).absolutePath}"
    )

    // Output

    val output = createEmptyDir(fs,
      outputDir.get().asFile
    )
    val build = createEmptyDir(fs,
      buildDir.get().asFile
    )

    // libvpx

    val configure = requireFile(
      input.resolve("configure")
    )
    val libvpxTarget = when (abi) {
      "arm64-v8a" ->
        "arm64-android-gcc"
      "armeabi-v7a" ->
        "armv7-android-gcc"
      "x86_64" ->
        "x86_64-android-gcc"
      "x86" ->
        "x86-android-gcc"
      else ->
        error("Unreachable")
    }
    extraParams +=
      if (abi != "armeabi-v7a" || flavor != "legacy") {
        "--enable-runtime-cpu-detect"
      } else {
        "--disable-runtime-cpu-detect"
      }

    val commands = linkedMapOf(
      "configure" to arrayOf(
        configure.absolutePath,
        "--libc=${sysroot.absolutePath}",
        "--prefix=${output.absolutePath}",
        "--target=${libvpxTarget}",
        *extraParams.toTypedArray(),
        "--as=auto",
        "--disable-docs",
        "--enable-pic",
        "--enable-libyuv",
        "--enable-static",
        "--enable-small",
        "--enable-optimizations",
        "--enable-better-hw-compatibility",
        "--enable-realtime-only",
        "--enable-vp8",
        "--enable-vp9",
        "--disable-webm-io",
        "--disable-examples",
        "--disable-tools",
        "--disable-debug",
        "--disable-unit-tests",
        "--disable-libyuv"
      ),
      "make" to arrayOf(
        "make",
        "-j${Runtime.getRuntime().availableProcessors()}",
        "install"
      )
    )

    val logFile = build.resolve("build.log")
    for (command in commands) {
      logFile.outputStream().use { log ->
        exec.exec {
          standardOutput = log
          errorOutput = log
          isIgnoreExitValue = true
          workingDir = build
          environment(env)
          commandLine(*command.value)
        }.let { result ->
          if (result.exitValue != 0) {
            fatal("libvpx ${command.key} failed [$flavor, $abi], see: ${logFile.absolutePath}")
          }
        }
      }
    }
  }
}