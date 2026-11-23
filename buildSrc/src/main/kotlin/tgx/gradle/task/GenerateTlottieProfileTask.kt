package tgx.gradle.task

import org.eclipse.jgit.lib.Constants
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
import tgx.gradle.source.GitInformationSource
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject

private const val DEVICE_DIR = "/data/local/tmp/tlottie-profile"

/**
 * Makes the profile of drawing of tlottie for one ABI, which guides optimizations of its builds,
 * see BuildTlottieTask: tlottie built with instrumentation and the same options draws all frames of
 * samples at the sizes of the app on the connected device of ANDROID_SERIAL, see
 * app/jni/tlottie/profile.c. Samples are .tgs files in folders emoji, sticker and effect, e.g.
 * saved by StickerSamples of tests, without those that benchmarks measure, so that they
 * measure the gain on others. Functions of tlottie that change lose the optimizations, so the
 * profile is made again after updates of tlottie and of Rust, and profiles.properties keeps what
 * it was made with. Needs llvm-tools of the version of Rust:
 * rustup component add llvm-tools --toolchain <version.rust>
 *
 * Names of functions of the build with branch protection depend on the host of the build, as Cargo
 * builds core and alloc there, so its profile fits only builds on -Ptlottie.host=<host of Rust>. On
 * another host it's made of an archive that the host of releases built from the same commit of
 * tlottie by the same version of Rust: -Ptlottie.archive=<libtlottie.a> and
 * -Ptlottie.profiler=<profiler_builtins>
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

  // Name of the profile, e.g. arm64-v8a-protected, in its file and in profiles.properties
  @get:Internal
  abstract val profileName: Property<String>

  @get:Internal
  abstract val branchProtection: Property<Boolean>

  // Instrumented archive of tlottie built on another host, with its profiler_builtins and the host
  @get:Internal
  abstract val archive: RegularFileProperty

  @get:Internal
  abstract val archiveProfiler: RegularFileProperty

  @get:Internal
  abstract val archiveHost: Property<String>

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
    val cargo = TlottieCargo(
      rustDir.get().asFile,
      rustVersion.get(),
      abi,
      branchProtection.get()
    )
    if (cargo.buildsStd && !archiveHost.isPresent) {
      fatal(
        "Host of the builds that get the profile is not set: -Ptlottie.host=<host of Rust>, e.g. " +
          cargo.host
      )
    }
    val host = archiveHost.orNull ?: cargo.host
    if (cargo.buildsStd && !archive.isPresent && host != cargo.host) {
      fatal("tlottie for $host is not built on ${cargo.host}: -Ptlottie.archive=<libtlottie.a>")
    }
    val commit = GitInformationSource.openRepository(input).use {
      it.resolve(Constants.HEAD).name
    }
    val build = createEmptyDir(fs, buildDir.get().asFile)
    val adb = sdkDir.get().asFile.resolve("platform-tools/adb").absolutePath
    val abis = run(adb, "shell", "getprop", "ro.product.cpu.abilist")
    if (abi !in abis.split(",")) {
      fatal("The device of ANDROID_SERIAL doesn't run $abi, only $abis")
    }

    val library: File
    val profiler: File
    if (archive.isPresent) {
      library = requireFile(archive.get().asFile)
      if (!archiveProfiler.isPresent || !archiveHost.isPresent) {
        fatal("tlottie.archive needs tlottie.profiler and tlottie.host")
      }
      profiler = requireFile(archiveProfiler.get().asFile)
    } else {
      library = buildInstrumented(cargo, input, build.resolve("cargo"))
      profiler = cargo.toolchain.resolve("lib/rustlib/${cargo.target}/lib").listFiles()
        ?.firstOrNull { it.name.startsWith("libprofiler_builtins-") }
        ?: fatal("No profiler_builtins in Rust ${rustVersion.get()} for ${cargo.target}")
    }
    val ndk = sdkDir.get().asFile.resolve("ndk/${ndkVersion.get()}")
    val clangTarget = if (abi == "armeabi-v7a") "armv7a-linux-androideabi" else cargo.target
    val runnerFile = build.resolve("profile")
    exec.exec {
      commandLine(
        ndk.resolve("toolchains/llvm/prebuilt/${hostTag.get()}/bin/clang").absolutePath,
        "--target=${clangTarget}24", "-O2",
        "-I${input.resolve("include").absolutePath}",
        requireFile(runner.get().asFile).absolutePath,
        library.absolutePath,
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
    saveMade(commit, host.takeIf { cargo.buildsStd })
    logger.lifecycle("Saved ${output.absolutePath}")
  }

  private fun buildInstrumented(cargo: TlottieCargo, input: File, target: File): File {
    // Counters of instrumentation are written to the device when the runner exits
    val rustFlags = cargo.rustFlags(input).apply {
      add("-Cprofile-generate=$DEVICE_DIR/raw")
      if (cargo.buildsStd) {
        // The core that Cargo builds has no profiler_builtins: the toolchain's is linked
        add("-Zno-profiler-runtime")
      }
    }
    exec.exec {
      workingDir = input
      setEnvironment(environment.filterKeys(TlottieCargo::inherits))
      environment(cargo.environment(target, rustFlags))
      commandLine(*cargo.command())
    }
    return requireFile(cargo.library(target))
  }

  private fun saveMade(commit: String, host: String?) {
    // Commits of tlottie, versions of Rust and hosts of profiles, for warnings of BuildTlottieTask
    val name = profileName.get()
    val file = profiles.get().asFile
    val values = (if (file.isFile) loadProperties(file) else null)
      ?.let { properties -> properties.stringPropertyNames().associateWith { properties[it] } }
      .orEmpty()
      .plus(mapOf("$name.tlottie" to commit, "$name.rust" to rustVersion.get()))
      .plus(listOfNotNull(host?.let { "$name.host" to it }))
    file.writeText(
      "# Commits of tlottie, versions of Rust and hosts that profiles of drawing were made with, " +
        "written by\n# :app:generateTlottieProfile[<profile>]\n" +
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
