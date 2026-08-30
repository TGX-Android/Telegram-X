package tgx.gradle.task

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.*
import org.jetbrains.kotlin.konan.file.File
import tgx.gradle.createEmptyDir
import tgx.gradle.requireDir
import tgx.gradle.requireFile

private const val TAG = "FFmpeg"

@CacheableTask
abstract class BuildFfmpegTask : BuildNativeLibraryTask() {
  @get:InputDirectory
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val libvpxDir: DirectoryProperty

  @TaskAction
  fun buildFfmpeg() {
    val input = requireDir(inputDir.get().asFile)
    val libvpx = requireDir(libvpxDir.get().asFile)

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
      "-O2",
      "-fPIC",
      "-fpie",
      "-ffunction-sections",
      "-fdata-sections",
      "-fvisibility=hidden",
      "-fvisibility-inlines-hidden",
      "-fno-strict-aliasing",
      "-fomit-frame-pointer",
      "-flto=full",
      "-fno-fast-math",
      "-ftree-vectorize",
      "-funroll-loops",
      "-w",
      "-DCONFIG_LINUX_PERF=0",
      "-I${requireDir(
        libvpx.resolve("include")
      ).absolutePath}"
    )
    val ldFlags = mutableListOf(
      "-L${requireDir(
        libvpx.resolve("lib")
      ).absolutePath}",
      "-lvpx",
      "-fPIC",
      "-flto=full"
    )
    val clangLibs = requireDir(
      prebuilt.resolve("lib64/clang/12.0.9/lib/linux")
    )
    val extraLibs = mutableListOf<String>()
    val ndkAbi: String
    when (abi) {
      "arm64-v8a" -> {
        ndkAbi = "aarch64-linux-android"
        cFlags += "-march=armv8-a"
        extraParams.addAll(arrayOf(
          "--disable-x86asm",
          "--enable-neon"
        ))
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
          "-marm",
          "-mtune=cortex-a8",
          "-I${cpuFeatures.absolutePath}"
        ))
        ldFlags.addAll(listOf(
          "-L${clangLibs.absolutePath}",
          "-Wl,--fix-cortex-a8"
        ))
        extraLibs.addAll(listOf(
          "-lunwind",
          "-lclang_rt.builtins-arm-android"
        ))
        extraParams.addAll(arrayOf(
          "--enable-neon",
          "--disable-x86asm"
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
        extraParams.addAll(arrayOf(
          "--enable-x86asm",
          "--x86asmexe=${requireFile(
            prebuilt.resolve("bin/yasm")
          ).absolutePath}"
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
        ldFlags += "-L${clangLibs.absolutePath}"
        extraLibs += "-lclang_rt.builtins-i686-android"
        extraParams.addAll(arrayOf(
          "--disable-asm",
          "--disable-x86asm"
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
      "CXXFLAGS" to "$cppFlags -std=c++11",
      "LDFLAGS" to "-L${requireDir(
        sysroot.resolve("usr/lib")
      ).absolutePath}",

      "PATH" to arrayOf(
        "${requireDir(
          prebuilt.resolve("bin")
        ).absolutePath}",
        System.getenv("PATH")?.takeIf { it.isNotEmpty() }
      ).filterNotNull().joinToString(File.pathSeparator),

      "AR" to "${requireFile(
        prebuilt.resolve("bin/llvm-ar")
      ).absolutePath}",

      "CC" to "${cc.absolutePath}",
      "AS" to "${cc.absolutePath}",
      "LD" to "${cc.absolutePath}",

      "CXX" to "${cxx.absolutePath}",
      "CPP" to "${cxx.absolutePath}",

      "ASFLAGS" to "-D__ANDROID__",
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
      ).absolutePath}",
    )

    val output = createEmptyDir(fs,
      outputDir.get().asFile
    )
    val build = createEmptyDir(fs,
      buildDir.get().asFile
    )

    // ffmpeg

    val configure = requireFile(
      input.resolve("configure")
    )
    val ffmpegArch = when (abi) {
      "arm64-v8a" ->
        "aarch64"
      "armeabi-v7a" ->
        "arm"
      "x86_64", "x86" ->
        abi
      else ->
        error("Unreachable")
    }

    val commands = linkedMapOf(
      "configure" to arrayOf(
        configure.absolutePath,
        "--prefix=${output.absolutePath}",
        "--sysroot=${sysroot.absolutePath}",
        *arrayOf(
          "nm",
          "ar",
          "as",
          "strip",
          "cc",
          "cxx",
          "ranlib"
        ).map {
          "--${it}=${env[it.uppercase()]!!}"
        }.toTypedArray(),
        "--arch=${ffmpegArch}",
        "--target-os=linux",
        "--enable-stripping",
        "--enable-cross-compile",
        "--enable-pic",
        "--disable-shared",
        "--enable-static",
        "--enable-small",
        "--extra-cflags=$cppFlags",
        "--extra-ldflags=${
          ldFlags.joinToString(" ")
        }",
        "--extra-libs=${
          extraLibs.joinToString(" ")
        }",
        "--enable-version3",
        "--enable-gpl",
        "--disable-linux-perf",
        "--disable-everything",
        "--disable-doc",
        "--disable-htmlpages",
        "--disable-network",
        "--disable-zlib",
        "--disable-avdevice",
        "--disable-debug",
        "--disable-programs",
        "--enable-runtime-cpudetect",
        "--enable-pthreads",

        "--enable-hwaccels",
        "--enable-protocol=file",
        *arrayOf(
          "scale",
          "overlay"
        ).map {
          "--enable-filter=$it"
        }.toTypedArray(),
        "--enable-libvpx",
        *arrayOf(
          "h264",
          "mpeg4",
          "gif",
          "alac",
          "aac",
          "libvpx_vp9"
        ).map {
          "--enable-decoder=$it"
        }.toTypedArray(),
        *arrayOf(
          "mov",
          "matroska",
          "gif"
        ).map {
          "--enable-demuxer=$it"
        }.toTypedArray(),
        *extraParams.toTypedArray()
      ),
      "make" to arrayOf(
        "make",
        "-j${Runtime.getRuntime().availableProcessors()}"
      ),
      "install" to arrayOf(
        "make",
        "-j${Runtime.getRuntime().availableProcessors()}",
        "install"
      )
    )

    val logFile = build.resolve("build.log")

    runCommands(
      TAG,
      env,
      build,
      logFile,
      commands
    )
  }
}