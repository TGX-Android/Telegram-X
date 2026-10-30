package tgx.test

import com.android.build.api.variant.TestAndroidComponentsExtension
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register
import org.gradle.work.DisableCachingByDefault
import tgx.gradle.fatal
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

// Where SnapshotUpdate of tests/ui leaves the new snapshot for adb pull
private const val PULLED_SNAPSHOT = "/data/local/tmp/tgx-snapshot-update.zip"

// Tests of the latest flavor make the new snapshot
private const val UPDATE_TESTS = "connectedLatestNonMinifiedReleaseAndroidTest"

fun Project.configureSnapshotUpdate() {
  // -PsnapshotUpdate=replace updates the snapshot of -PsnapshotFile from the device of
  // ANDROID_SERIAL, only with the approval of the user, and -PsnapshotUpdate=check does everything
  // but the replacement, see "Session snapshot" in tests/README.md
  val mode = providers.gradleProperty("snapshotUpdate").orNull ?: return
  if (mode != "replace" && mode != "check") {
    fatal("-PsnapshotUpdate=$mode: replace or check")
  }
  val snapshot = snapshotFile.orNull ?: fatal("-PsnapshotUpdate needs -PsnapshotFile")
  val update = tasks.register<UpdateSnapshotTask>("updateSnapshot") {
    this.snapshot.set(snapshot)
    replace.set(mode == "replace")
    dependsOn(UPDATE_TESTS)
  }
  extensions.configure<TestAndroidComponentsExtension> {
    val adb = sdkComponents.adb
    onVariants { variant ->
      variant.instrumentationRunnerArguments.put("class", "tgx.test.SnapshotUpdate")
      variant.instrumentationRunnerArguments.put("snapshotUpdate", "true")
      val artifactsLoader = variant.artifacts.getBuiltArtifactsLoader()
      update.configure {
        this.adb.set(adb)
        applicationId.set(variant.testedApks.map { artifactsLoader.load(it)!!.applicationId })
      }
    }
  }
}

@DisableCachingByDefault(because = "Updates a file outside the build")
abstract class UpdateSnapshotTask : DefaultTask() {
  @get:Internal
  abstract val snapshot: RegularFileProperty

  @get:Internal
  abstract val replace: Property<Boolean>

  @get:Internal
  abstract val adb: RegularFileProperty

  @get:Internal
  abstract val applicationId: Property<String>

  @TaskAction
  fun update() {
    val current = snapshot.get().asFile
    // Next to the current snapshot, so that the replacement is a rename and a failed pull leaves
    // the current one whole
    val pulled = File(current.parentFile, ".${current.name}.new")
    try {
      adb("pull", PULLED_SNAPSHOT, pulled.path)
      val before = current.inputStream().use(::readSnapshotUserIds).sorted()
      val after = pulled.inputStream().use(::readSnapshotUserIds).sorted()
      if (after != before) {
        fatal("The new snapshot has accounts $after, the current one $before")
      }
      if (replace.get()) {
        Files.move(pulled.toPath(), current.toPath(), StandardCopyOption.ATOMIC_MOVE)
        logger.lifecycle("Updated $current, accounts $after")
      } else {
        logger.lifecycle("The new snapshot has the same accounts $after, deleted")
      }
    } finally {
      pulled.delete()
      adb("shell", "rm", "-f", PULLED_SNAPSHOT)
      // The app stops using the sessions of the new snapshot: connected tests uninstall it, an app
      // left installed is cleared
      if (adb("shell", "pm", "path", applicationId.get(), check = false).isNotBlank()) {
        adb("shell", "pm", "clear", applicationId.get())
      }
    }
  }

  private fun adb(vararg arguments: String, check: Boolean = true): String {
    val process =
      ProcessBuilder(adb.get().asFile.path, *arguments).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().readText()
    if (process.waitFor() != 0 && check) {
      fatal("adb ${arguments.joinToString(" ")} failed: $output")
    }
    return output
  }
}
