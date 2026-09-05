package tgx.gradle.task

import Config
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.*
import tgx.gradle.requireDir
import tgx.gradle.requireFile

abstract class ValidateNativeBuildTask : DefaultTask() {
  @get:InputDirectory
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val jetpackMediaDir: DirectoryProperty

  @get:InputDirectory
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val opusDir: DirectoryProperty

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val libvpxDirs: ConfigurableFileCollection

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val ffmpegDirs: ConfigurableFileCollection

  @TaskAction
  fun validateDirs() {
    requireDir(jetpackMediaDir.get().asFile)
    requireDir(opusDir.get().asFile)
    for (libvpxDir in libvpxDirs) {
      requireDir(libvpxDir)
      requireDir(libvpxDir.resolve("include/vpx"))
      requireFile(libvpxDir.resolve("lib/libvpx.a"))
    }

    for (ffmpegDir in ffmpegDirs) {
      requireDir(ffmpegDir)
      for (extension in Config.FFMPEG_LIBS) {
        requireDir(ffmpegDir.resolve("include/lib$extension"))
        requireFile(ffmpegDir.resolve("lib/lib${extension}.a"))
      }
    }
  }
}