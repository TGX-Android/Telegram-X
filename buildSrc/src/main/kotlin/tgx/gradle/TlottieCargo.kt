package tgx.gradle

import java.io.File

/**
 * Cargo of the version of Rust of version.properties from rust.dir of local.properties, with the
 * options of builds of tlottie for one ABI, which builds of its profile of drawing share. Cargo
 * gets only the variables that are set here: nothing comes from variables of Rust and Cargo of the
 * environment, see [inherits]
 */
class TlottieCargo(
  rustDir: File,
  rustVersion: String,
  abi: String,
  branchProtection: Boolean
) {
  val target = when (abi) {
    "arm64-v8a" -> "aarch64-linux-android"
    "armeabi-v7a" -> "armv7-linux-androideabi"
    "x86_64" -> "x86_64-linux-android"
    "x86" -> "i686-linux-android"
    else -> fatal("Unsupported abi: $abi")
  }

  // rustup keeps each version in toolchains/<version>-<host> of its home
  val toolchain: File = rustDir.resolve("toolchains").listFiles()
    ?.firstOrNull { it.name.startsWith("$rustVersion-") }
    ?: fatal("Rust $rustVersion isn't installed in rust.dir=$rustDir, see scripts/setup-rust.sh")

  // Host of the toolchain, e.g. x86_64-unknown-linux-gnu
  val host: String = toolchain.name.removePrefix("$rustVersion-")

  // Crates that Cargo downloads, e.g. dependencies of tlottie, have paths in its home
  private val cargoHome = File(System.getProperty("user.home"), ".cargo")

  private val isArm32 = target == "armv7-linux-androideabi"

  // PAC and BTI, which the C and C++ of arm64 have in the same flavors, are unstable options of
  // Rust that RUSTC_BOOTSTRAP allows; core and alloc are built from rust-src with them, since lld
  // drops BTI of the library for any object without it
  val buildsStd = branchProtection && target == "aarch64-linux-android"

  // Canonical, as rustc takes the path of its sources
  private val stdSources = toolchain.canonicalFile.resolve("lib/rustlib/src/rust")

  init {
    if (buildsStd && !stdSources.resolve("library").isDirectory) {
      fatal("rust-src of Rust $rustVersion isn't installed, see scripts/setup-rust.sh")
    }
  }

  // Path of std in messages of panics of its prebuilt crates, which rustc replaces by the local
  // path of rust-src where it's installed
  private val stdPath: String by lazy {
    val rustc = ProcessBuilder(toolchain.resolve("bin/rustc").absolutePath, "-vV").start()
    val prefix = "commit-hash: "
    val commit = rustc.inputStream.bufferedReader().readLines().firstOrNull {
      it.startsWith(prefix)
    } ?: fatal("rustc of $toolchain doesn't tell its commit")
    "/rustc/${commit.removePrefix(prefix)}"
  }

  fun rustFlags(source: File): MutableList<String> {
    // Paths are remapped and debug info is left out, so that the archive depends neither on the
    // machine nor on rust-src, except for names of functions of builds of std, which depend on the
    // host
    val flags = mutableListOf(
      "--remap-path-prefix=${source.absolutePath}=tlottie",
      "--remap-path-prefix=${cargoHome.absolutePath}=cargo",
      "--remap-path-prefix=${stdSources.absolutePath}=$stdPath",
      "-Cdebuginfo=0"
    )
    if (isArm32) {
      // NEON, which the NDK enables for C++ code of armeabi-v7a, isn't a default of the target
      flags += "-Ctarget-feature=+neon"
    }
    if (buildsStd) {
      flags += "-Zbranch-protection=bti,pac-ret"
    }
    return flags
  }

  fun environment(targetDir: File, rustFlags: List<String>): Map<String, String> = buildMap {
    put("RUSTC", toolchain.resolve("bin/rustc").absolutePath)
    put("CARGO_TARGET_DIR", targetDir.absolutePath)
    put("CARGO_INCREMENTAL", "0")
    put("SOURCE_DATE_EPOCH", "0")
    // Separated by 0x1F, so that paths may have spaces
    put("CARGO_ENCODED_RUSTFLAGS", rustFlags.joinToString("\u001f"))
    if (buildsStd) {
      put("RUSTC_BOOTSTRAP", "1")
    }
  }

  fun command(): Array<String> = arrayOf(
    toolchain.resolve("bin/cargo").absolutePath, "rustc",
    "--locked",
    "--profile", "release-nostd",
    "--no-default-features",
    "--features", "cpu,no-std,c-api",
    "--crate-type", "staticlib",
    "--target", target
  ) + if (buildsStd) arrayOf("-Zbuild-std=core,alloc") else emptyArray()

  fun library(targetDir: File): File = targetDir.resolve("$target/release-nostd/libtlottie.a")

  companion object {
    fun inherits(name: String): Boolean {
      // Variables of the environment that builds of tlottie don't take, e.g. CARGO_HOME,
      // RUSTUP_TOOLCHAIN and RUSTFLAGS
      return !name.startsWith("CARGO") && !name.startsWith("RUST")
    }
  }
}
