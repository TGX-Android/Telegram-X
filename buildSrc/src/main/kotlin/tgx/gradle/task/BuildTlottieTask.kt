package tgx.gradle.task

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import tgx.gradle.TlottieCargo
import tgx.gradle.createEmptyDir
import tgx.gradle.loadProperties
import tgx.gradle.requireDir
import tgx.gradle.requireFile
import java.io.ByteArrayOutputStream
import java.io.File

private const val TAG = "tlottie"

/**
 * Builds tlottie, which draws Lottie animations, as a static library for one ABI by Cargo of the
 * pinned version of Rust. Paths are remapped and builds aren't incremental, so that the archive
 * doesn't depend on the machine
 */
@CacheableTask
abstract class BuildTlottieTask : BuildNativeLibraryTask() {
  @get:Internal
  abstract val rustDir: DirectoryProperty

  @get:Input
  abstract val rustVersion: Property<String>

  // Profile of drawing for optimizations guided by it, see GenerateTlottieProfileTask
  @get:InputFile
  @get:Optional
  @get:PathSensitive(PathSensitivity.NONE)
  abstract val profile: RegularFileProperty

  // Commits of tlottie and versions of Rust that profiles were made with
  @get:InputFile
  @get:Optional
  @get:PathSensitive(PathSensitivity.NONE)
  abstract val profiles: RegularFileProperty

  @TaskAction
  fun buildTlottie() {
    val input = requireDir(inputDir.get().asFile)
    val abi = this.abi.get()
    val cargo = TlottieCargo(rustDir.get().asFile, rustVersion.get(), abi)
    val output = createEmptyDir(fs, outputDir.get().asFile)
    // Flavors share the build of their ABI, since the library doesn't depend on the NDK or the SDK:
    // Cargo builds it once and finds it fresh for the next flavor
    val build = buildDir.get().asFile.apply { mkdirs() }
    val logFile = build.resolve("build.log")

    val rustFlags = cargo.rustFlags(input)
    profile.orNull?.let {
      rustFlags += "-Cprofile-use=${it.asFile.absolutePath}"
    }
    runCommands(
      TAG,
      cargo.environment(build, rustFlags),
      input,
      logFile,
      mapOf("cargo" to cargo.command()),
      TlottieCargo::inherits
    )
    if (profile.isPresent) {
      checkProfile(input, abi, logFile)
    }
    fs.copy {
      from(requireFile(cargo.library(build)))
      into(output.resolve("lib"))
    }
  }

  private fun checkProfile(input: File, abi: String, logFile: File) {
    // A profile made for other code leaves changed functions without its optimizations, and in a
    // release it means that it wasn't made again after an update, so the build warns
    val task = ":app:generateTlottieProfile[$abi]"
    val commit = ByteArrayOutputStream().also { out ->
      exec.exec {
        workingDir = input
        standardOutput = out
        isIgnoreExitValue = true
        commandLine("git", "rev-parse", "HEAD")
      }
    }.toString().trim()
    val made = profiles.orNull?.asFile?.takeIf { it.isFile }?.let { loadProperties(it) }
    val madeCommit = made?.getProperty("$abi.tlottie")
    val madeVersion = made?.getProperty("$abi.rust")
    if (madeCommit != commit || madeVersion != rustVersion.get()) {
      logger.warn(
        "Profile of tlottie for $abi was made for tlottie $madeCommit and Rust $madeVersion, " +
          "the build has tlottie $commit and Rust ${rustVersion.get()}: make it again by $task"
      )
    }
    // rustc warns about functions whose code differs from the profile
    val mismatches = logFile.readLines().count { "hash mismatch" in it }
    if (mismatches > 0) {
      logger.warn(
        "Profile of tlottie for $abi doesn't match $mismatches functions, see " +
          "${logFile.absolutePath}: make it again by $task"
      )
    }
  }
}
