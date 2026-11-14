package tgx.gradle

import java.io.File

/**
 * Cargo of the version of Rust of version.properties from rust.dir of local.properties, with the
 * options of builds of tlottie for one ABI, which builds of its profile of drawing share. Cargo
 * gets only the variables that are set here: nothing comes from variables of Rust and Cargo of the
 * environment, see [inherits]
 */
class TlottieCargo(rustDir: File, rustVersion: String, abi: String) {
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

  // Crates that Cargo downloads, e.g. dependencies of tlottie, have paths in its home
  private val cargoHome = File(System.getProperty("user.home"), ".cargo")

  private val isArm32 = target == "armv7-linux-androideabi"

  fun rustFlags(source: File): MutableList<String> {
    // Paths are remapped and debug info is left out, so that the archive doesn't depend on the
    // machine
    val flags = mutableListOf(
      "--remap-path-prefix=${source.absolutePath}=tlottie",
      "--remap-path-prefix=${cargoHome.absolutePath}=cargo",
      "-Cdebuginfo=0"
    )
    if (isArm32) {
      // NEON, which the NDK enables for C++ code of armeabi-v7a, isn't a default of the target
      flags += "-Ctarget-feature=+neon"
    }
    return flags
  }

  fun environment(targetDir: File, rustFlags: List<String>): Map<String, String> = mapOf(
    "RUSTC" to toolchain.resolve("bin/rustc").absolutePath,
    "CARGO_TARGET_DIR" to targetDir.absolutePath,
    "CARGO_INCREMENTAL" to "0",
    "SOURCE_DATE_EPOCH" to "0",
    // Separated by 0x1F, so that paths may have spaces
    "CARGO_ENCODED_RUSTFLAGS" to rustFlags.joinToString("\u001f")
  )

  fun command(): Array<String> = arrayOf(
    toolchain.resolve("bin/cargo").absolutePath, "rustc",
    "--locked",
    "--profile", "release-nostd",
    "--no-default-features",
    "--features", "cpu,no-std,c-api",
    "--crate-type", "staticlib",
    "--target", target
  )

  fun library(targetDir: File): File = targetDir.resolve("$target/release-nostd/libtlottie.a")

  companion object {
    fun inherits(name: String): Boolean {
      // Variables of the environment that builds of tlottie don't take, e.g. CARGO_HOME,
      // RUSTUP_TOOLCHAIN and RUSTFLAGS
      return !name.startsWith("CARGO") && !name.startsWith("RUST")
    }
  }
}
