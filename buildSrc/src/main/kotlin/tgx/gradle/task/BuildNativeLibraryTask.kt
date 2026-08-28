package tgx.gradle.task

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.process.ExecOperations
import javax.inject.Inject

abstract class BuildNativeLibraryTask : DefaultTask() {
  @get:Internal
  abstract val sdkDir: DirectoryProperty

  @get:Input
  abstract val ndkVersion: Property<String>

  @get:Input
  abstract val hostTag: Property<String>

  @get:Internal
  abstract val inputDir: DirectoryProperty

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val inputSources: ConfigurableFileCollection

  @get:Input
  abstract val sdkFlavor: Property<String>

  @get:Input
  abstract val abi: Property<String>

  @get:Internal
  abstract val buildDir: DirectoryProperty

  @get:OutputDirectory
  abstract val outputDir: DirectoryProperty

  @get:Inject
  abstract val exec: ExecOperations

  @get:Inject
  abstract val fs: FileSystemOperations
}