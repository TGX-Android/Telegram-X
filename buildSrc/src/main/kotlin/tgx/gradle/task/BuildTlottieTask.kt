package tgx.gradle.task

import org.eclipse.jgit.lib.Constants
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
import tgx.gradle.source.GitInformationSource
import java.io.File

private const val TAG = "tlottie"

/**
 * Builds tlottie, which draws Lottie animations, as a static library for one ABI by Cargo of the
 * pinned version of Rust. Paths are remapped and builds aren't incremental, so that the archive
 * doesn't depend on the machine, with branch protection except for its host, see TlottieCargo
 */
@CacheableTask
abstract class BuildTlottieTask : BuildNativeLibraryTask() {
  @get:Internal
  abstract val rustDir: DirectoryProperty

  @get:Input
  abstract val rustVersion: Property<String>

  @get:Input
  abstract val branchProtection: Property<Boolean>

  // Profile of drawing for optimizations guided by it, see GenerateTlottieProfileTask
  @get:InputFile
  @get:Optional
  @get:PathSensitive(PathSensitivity.NONE)
  abstract val profile: RegularFileProperty

  // Commits of tlottie, versions of Rust and hosts that profiles were made with
  @get:InputFile
  @get:Optional
  @get:PathSensitive(PathSensitivity.NONE)
  abstract val profiles: RegularFileProperty

  @TaskAction
  fun buildTlottie() {
    val input = requireDir(inputDir.get().asFile)
    val cargo = TlottieCargo(
      rustDir.get().asFile,
      rustVersion.get(),
      abi.get(),
      branchProtection.get()
    )
    val output = createEmptyDir(fs, outputDir.get().asFile)
    // Flavors with the same options share the build of their ABI, since the library doesn't depend
    // on the NDK or the SDK: Cargo builds it once and finds it fresh for the next flavor
    val build = buildDir.get().asFile.apply { mkdirs() }
    val logFile = build.resolve("build.log")

    val rustFlags = cargo.rustFlags(input)
    val usedProfile = profile.orNull?.asFile?.takeIf { isMadeForBuild(it, input, cargo) }
    if (usedProfile != null) {
      rustFlags += "-Cprofile-use=${usedProfile.absolutePath}"
      // LLVM is silent about functions without a record by default
      rustFlags += "-Cllvm-args=-pgo-warn-missing-function"
    }
    runCommands(
      TAG,
      cargo.environment(build, rustFlags),
      input,
      logFile,
      mapOf("cargo" to cargo.command()),
      TlottieCargo::inherits
    )
    if (usedProfile != null) {
      checkRecords(usedProfile, logFile)
    }
    fs.copy {
      from(requireFile(cargo.library(build)))
      into(output.resolve("lib"))
    }
  }

  private fun task(profile: File) = ":app:generateTlottieProfile[${profile.nameWithoutExtension}]"

  private fun isMadeForBuild(profile: File, input: File, cargo: TlottieCargo): Boolean {
    // A profile made for other code leaves changed functions without its optimizations, and in a
    // release it means that it wasn't made again after an update, so the build warns
    val name = profile.nameWithoutExtension
    val commit = GitInformationSource.openRepository(input).use {
      it.resolve(Constants.HEAD)?.name
    }
    val made = profiles.orNull?.asFile?.takeIf { it.isFile }?.let { loadProperties(it) }
    val madeCommit = made?.getProperty("$name.tlottie")
    val madeVersion = made?.getProperty("$name.rust")
    if (madeCommit != commit || madeVersion != rustVersion.get()) {
      logger.warn(
        "Profile of tlottie for $name was made for tlottie $madeCommit and Rust $madeVersion, " +
          "the build has tlottie $commit and Rust ${rustVersion.get()}: make it again by " +
          task(profile)
      )
    }
    // Names of functions depend on the host where core and alloc are built, so on another host the
    // profile has almost no records of this build, and it's built without the profile
    val madeHost = made?.getProperty("$name.host")
    if (cargo.buildsStd && madeHost != cargo.host) {
      logger.lifecycle(
        "Profile of tlottie for $name was made on $madeHost, so this build on ${cargo.host} is " +
          "without it"
      )
      return false
    }
    return true
  }

  private fun checkRecords(profile: File, logFile: File) {
    // rustc warns about functions whose code differs from the profile, or without a record in it
    val lines = logFile.readLines()
    val mismatches = lines.count { "hash mismatch" in it }
    val missing = lines.count { "no profile data available" in it }
    if (mismatches > 0 || missing > 0) {
      logger.warn(
        "Profile of tlottie for ${profile.nameWithoutExtension} doesn't match $mismatches " +
          "functions and has no record of $missing, see ${logFile.absolutePath}: make it again " +
          "by ${task(profile)}"
      )
    }
  }
}
