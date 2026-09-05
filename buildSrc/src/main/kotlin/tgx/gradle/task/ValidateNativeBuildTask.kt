package tgx.gradle.task

import Config
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.*
import tgx.gradle.requireDir
import tgx.gradle.requireFile

abstract class ValidateNativeBuildTask : DefaultTask() {
  @get:Input
  abstract val abiFilters: SetProperty<String>


  @get:InputDirectory
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val jetpackMediaDir: DirectoryProperty

  @get:InputDirectory
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val opusDir: DirectoryProperty

  @get:InputDirectory
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val libvpxDir: DirectoryProperty

  @get:InputDirectory
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val ffmpegDir: DirectoryProperty

  @TaskAction
  fun validateDirs() {
    requireDir(jetpackMediaDir.get().asFile)
    requireDir(opusDir.get().asFile)

    for (abiFilter in abiFilters.get()) {
      requireDir(libvpxDir.dir(abiFilter).get().asFile)
      requireDir(libvpxDir.file("$abiFilter/include/vpx").get().asFile)
      requireFile(libvpxDir.file("$abiFilter/lib/libvpx.a").get().asFile)

      requireDir(ffmpegDir.dir(abiFilter).get().asFile)
      for (extension in Config.FFMPEG_LIBS) {
        requireDir(ffmpegDir.dir("$abiFilter/include/lib$extension").get().asFile)
        requireFile(ffmpegDir.dir("$abiFilter/lib/lib${extension}.a").get().asFile)
      }
    }
  }
}