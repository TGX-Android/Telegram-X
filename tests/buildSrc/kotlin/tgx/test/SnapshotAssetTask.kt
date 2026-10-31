package tgx.test

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.nio.file.Files

// Name of the snapshot in assets of tests, see SnapshotApplier
private const val SNAPSHOT_ASSET = "snapshot.zip"

/**
 * Adds the snapshot of -PsnapshotFile to assets of tests by a link, not a copy, so that the
 * sessions stay in one file outside the clone until assets are packaged
 */
@DisableCachingByDefault(because = "The build cache would keep the snapshot outside the clone")
abstract class SnapshotAssetTask : DefaultTask() {
  @get:InputFile
  @get:PathSensitive(PathSensitivity.NONE)
  abstract val snapshot: RegularFileProperty

  @get:OutputDirectory
  abstract val outputDirectory: DirectoryProperty

  @TaskAction
  fun link() {
    val directory = outputDirectory.get().asFile
    directory.deleteRecursively()
    directory.mkdirs()
    Files.createSymbolicLink(
      directory.resolve(SNAPSHOT_ASSET).toPath(),
      snapshot.get().asFile.toPath()
    )
  }
}
