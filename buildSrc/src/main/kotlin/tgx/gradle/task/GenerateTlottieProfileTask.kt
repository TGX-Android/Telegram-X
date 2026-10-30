package tgx.gradle.task

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import tgx.gradle.TlottieCargo
import tgx.gradle.createEmptyDir
import tgx.gradle.fatal
import tgx.gradle.loadProperties
import tgx.gradle.requireDir
import tgx.gradle.requireFile
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject

private const val DEVICE_DIR = "/data/local/tmp/tlottie-profile"

/**
 * Makes the profile of drawing of tlottie for one ABI, which guides optimizations of its builds,
 * see BuildTlottieTask: tlottie built with instrumentation and the same options draws all frames of
 * samples at the sizes of the app on the connected device of ANDROID_SERIAL, see
 * app/jni/tlottie/profile.c. Samples are .tgs files in folders emoji, sticker and effect, e.g.
 * saved by StickerSamples of tests/ui/app, without those that benchmarks measure, so that they
 * measure the gain on others. Functions of tlottie that change lose the optimizations, so the
 * profile is made again after updates of tlottie and of Rust, and profiles.properties keeps what
 * it was made with. Needs llvm-tools of the version of Rust:
 * rustup component add llvm-tools --toolchain <version.rust>
 */
abstract class GenerateTlottieProfileTask : DefaultTask() {
  @get:Internal
  abstract val sdkDir: DirectoryProperty

  @get:Internal
  abstract val ndkVersion: Property<String>

  @get:Internal
  abstract val hostTag: Property<String>

  @get:Internal
  abstract val rustDir: DirectoryProperty

  @get:Internal
  abstract val rustVersion: Property<String>

  @get:Internal
  abstract val abi: Property<String>

  @get:Internal
  abstract val inputDir: DirectoryProperty

  @get:Internal
  abstract val runner: RegularFileProperty

  @get:Internal
  abstract val samples: DirectoryProperty

  @get:Internal
  abstract val profile: RegularFileProperty

  @get:Internal
  abstract val profiles: RegularFileProperty

  @get:Internal
  abstract val buildDir: DirectoryProperty

  @get:Inject
  abstract val exec: ExecOperations

  @get:Inject
  abstract val fs: FileSystemOperations

  @TaskAction
  fun generateTlottieProfile() {
    if (!samples.isPresent) {
      fatal("Samples are not set: -Ptlottie.samples=<folder with emoji, sticker and effect>")
    }
    val input = requireDir(inputDir.get().asFile)
    val abi = this.abi.get()
    val cargo = TlottieCargo(rustDir.get().asFile, rustVersion.get(), abi)
    val build = createEmptyDir(fs, buildDir.get().asFile)
    val adb = sdkDir.get().asFile.resolve("platform-tools/adb").absolutePath
    val abis = run(adb, "shell", "getprop", "ro.product.cpu.abilist")
    if (abi !in abis.split(",")) {
      fatal("The device of ANDROID_SERIAL doesn't run $abi, only $abis")
    }

    // Counters of instrumentation are written to the device when the runner exits
    val target = build.resolve("cargo")
    val rustFlags = cargo.rustFlags(input) + "-Cprofile-generate=$DEVICE_DIR/raw"
    exec.exec {
      workingDir = input
      setEnvironment(environment.filterKeys(TlottieCargo::inherits))
      environment(cargo.environment(target, rustFlags))
      commandLine(*cargo.command())
    }
    val profiler = cargo.toolchain.resolve("lib/rustlib/${cargo.target}/lib").listFiles()
      ?.firstOrNull { it.name.startsWith("libprofiler_builtins-") }
      ?: fatal("No profiler_builtins in Rust ${rustVersion.get()} for ${cargo.target}")
    val ndk = sdkDir.get().asFile.resolve("ndk/${ndkVersion.get()}")
    val clangTarget = if (abi == "armeabi-v7a") "armv7a-linux-androideabi" else cargo.target
    val runnerFile = build.resolve("profile")
    exec.exec {
      commandLine(
        ndk.resolve("toolchains/llvm/prebuilt/${hostTag.get()}/bin/clang").absolutePath,
        "--target=${clangTarget}24", "-O2",
        "-I${input.resolve("include").absolutePath}",
        requireFile(runner.get().asFile).absolutePath,
        requireFile(cargo.library(target)).absolutePath,
        profiler.absolutePath,
        "-Wl,-u,__llvm_profile_runtime", "-lz", "-lm",
        "-o", runnerFile.absolutePath
      )
    }

    run(adb, "shell", "rm", "-rf", DEVICE_DIR)
    run(adb, "shell", "mkdir", "-p", DEVICE_DIR)
    run(adb, "push", requireDir(samples.get().asFile).absolutePath, "$DEVICE_DIR/samples")
    run(adb, "push", runnerFile.absolutePath, "$DEVICE_DIR/")
    logger.lifecycle(run(adb, "shell", "cd $DEVICE_DIR && ./profile samples"))
    run(adb, "pull", "$DEVICE_DIR/raw", build.absolutePath)
    run(adb, "shell", "rm", "-rf", DEVICE_DIR)

    val profdata = cargo.toolchain.resolve("lib/rustlib").listFiles()
      ?.map { it.resolve("bin/llvm-profdata") }
      ?.firstOrNull { it.isFile }
      ?: fatal(
        "No llvm-profdata in Rust ${rustVersion.get()}: " +
          "rustup component add llvm-tools --toolchain ${rustVersion.get()}"
      )
    val raw = build.resolve("raw").listFiles { file -> file.name.endsWith(".profraw") }
    if (raw.isNullOrEmpty()) {
      fatal("The runner wrote no profile")
    }
    val output = profile.get().asFile
    exec.exec {
      commandLine(
        profdata.absolutePath, "merge", "-o", output.absolutePath,
        *raw.map { it.absolutePath }.toTypedArray()
      )
    }
    saveMade(abi, run("git", "-C", input.absolutePath, "rev-parse", "HEAD"))
    logger.lifecycle("Saved ${output.absolutePath}")
  }

  private fun saveMade(abi: String, commit: String) {
    // Commits of tlottie and versions of Rust of profiles, for warnings of BuildTlottieTask
    val file = profiles.get().asFile
    val values = (if (file.isFile) loadProperties(file) else null)
      ?.let { properties -> properties.stringPropertyNames().associateWith { properties[it] } }
      .orEmpty()
      .plus(mapOf("$abi.tlottie" to commit, "$abi.rust" to rustVersion.get()))
    file.writeText(
      "# Commits of tlottie and versions of Rust that profiles of drawing were made with, " +
        "written by\n# :app:generateTlottieProfile[<abi>]\n" +
        values.toSortedMap().entries.joinToString("") { "${it.key}=${it.value}\n" }
    )
  }

  private fun run(vararg command: String): String {
    val out = ByteArrayOutputStream()
    exec.exec {
      standardOutput = out
      commandLine(*command)
    }
    return out.toString().trim()
  }
}
