package tgx.gradle.task

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.process.ExecOperations
import tgx.gradle.fatal
import java.io.File
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

  protected fun runCommands(
    tag: String,
    env: Map<String, String>,
    buildDir: File,
    logFile: File,
    commands: Map<String, Array<String>>
  ) {
    for (command in commands) {
      logFile.outputStream().use { log ->
        exec.exec {
          standardOutput = log
          errorOutput = log
          isIgnoreExitValue = true
          workingDir = buildDir
          environment(env)
          commandLine(*command.value)
        }.let { result ->
          if (result.exitValue != 0) {
            fatal("$tag ${command.key} failed [${sdkFlavor.get()}, ${abi.get()}], see: ${logFile.absolutePath}")
          }
        }
      }
    }
  }
}