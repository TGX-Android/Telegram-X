package tgx.gradle.task

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.*
import tgx.gradle.fatal
import tgx.gradle.validateDir
import java.io.File

@CacheableTask
abstract class PatchJetpackMediaTask : DefaultTask() {
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val inputDirs: ConfigurableFileCollection

  @get:OutputDirectory abstract val outputDir: DirectoryProperty

  @TaskAction
  fun patchJetpackMedia() {
    val output = validateDir(outputDir.get().asFile)
    if (!output.mkdirs() && !output.isDirectory) {
      fatal("mkdirs failed: ${output.absolutePath}")
    }

    val copiedFiles = mutableSetOf<String>()
    inputDirs.filter { input ->
      validateDir(input)
      input.isDirectory
    }.forEach { input ->
      input.resolve("include").walkTopDown().filter {
        it.isFile
      }.forEach { inputFile ->
        val outputFile = output.resolve(inputFile.relativeTo(input))
        if (!outputFile.parentFile.isDirectory && !outputFile.parentFile.mkdirs())
          fatal("mkdirs failed: ${outputFile.parentFile.absolutePath}")
        inputFile.copyTo(outputFile,
          overwrite = true
        )
        copiedFiles += inputFile.relativeTo(input).path
      }

      input.walkTopDown().onEnter {
        it.parentFile != input || it.name != "include"
      }.filter {
        it.isFile && it.name != "CMakeLists.txt" && it.extension != "sh" && it.extension != "mk"
      }.forEach { file ->
        require(file.isFile)
        val target = output.resolve(file.relativeTo(input))
        writeToFile(target) { out ->
          out.append(patchSourceCode(file))
        }
        copiedFiles += file.name
      }
    }

    var deletedFiles = false
    output.walkTopDown().filter {
      it.isFile
    }.forEach { file ->
      val relative = file.relativeTo(output)
      if (!copiedFiles.contains(relative.path)) {
        logger.lifecycle("Deleting rudimentary file: ${relative.path} / ${file.absolutePath}")
        if (!file.delete()) {
          error("Rudimentary file could not be deleted: ${file.absolutePath}")
        }
        deletedFiles = true
      }
    }
    if (deletedFiles) {
      logger.lifecycle("Copied files include:\n${copiedFiles.joinToString("\n")}")
    }
  }

  private fun patchSourceCode(file: File): String {
    val fileName = file.nameWithoutExtension
    return file.readText()
      .replace(Regex("^#define LOG_TAG \"[^\"]+\"\n", RegexOption.MULTILINE), "")
      .replace(Regex("(?<=^#include <)android/(?=log.h)", RegexOption.MULTILINE), "")
      .replace(Regex("^jint JNI_(?=OnLoad\\s*\\(JavaVM\\s*\\*)", RegexOption.MULTILINE), "extern \"C\" jint ${fileName}_")
      .replace(
        "__android_log_assert(NULL, LOG_TAG, ##",
        "loga(TAG_NDK, "
      )
      .replace(
        Regex("__android_log_print\\(ANDROID_LOG_(ERROR|WARNING|DEBUG|VERBOSE),\\s*LOG_TAG,")
      ) {
        val func = when (
          val level = it.groupValues[1]
        ) {
          "ERROR" -> "loge"
          "WARNING" -> "logw"
          "DEBUG" -> "logd"
          "VERBOSE" -> "logv"
          else -> error("Unknown level: $level")
        }
        "${func}(TAG_NDK,"
    }
  }
}