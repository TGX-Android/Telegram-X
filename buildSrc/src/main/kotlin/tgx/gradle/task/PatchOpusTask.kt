package tgx.gradle.task

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.tasks.*
import org.gradle.process.ExecOperations
import tgx.gradle.validateDir
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject

@CacheableTask
abstract class PatchOpusTask : DefaultTask() {
  @get:InputDirectory
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val inputDir: DirectoryProperty

  @get:OutputDirectory
  abstract val outputDir: DirectoryProperty

  @get:Inject
  abstract val exec: ExecOperations

  @get:Inject
  abstract val fs: FileSystemOperations

  @TaskAction
  fun patchOpus() {
    val input = validateDir(inputDir.get().asFile, mustExist = true)
    val output = validateDir(outputDir.get().asFile)

    val converter = input.resolve("celt/arm/arm2gnu.pl")
    require(converter.isFile)

    fs.delete( {
      delete(output)
    })
    fs.copy {
      from(input) {
        exclude(
          ".git",
          ".github",
          ".gitlab-ci.yml"
        )
      }
      into(output)
    }

    output.resolve("celt/arm")
      .walkTopDown()
      .filter {
        it.isFile && it.extension == "s" && !it.name.endsWith("_gnu.s", ignoreCase = true)
      }
      .forEach { file ->
        val patched = File(file.parentFile, "${file.nameWithoutExtension}_gnu.s")
        convert(converter, file, patched)
        require(file.delete()) {
          "Unable to delete ${file.absolutePath}"
        }
      }

    val armopts = output.resolve("celt/arm/armopts.s.in")
    val temp = File(armopts.parentFile, "${armopts.name}.temp")
    writeToFile(temp) { temp ->
      temp.append(armopts.readText()
        .replace("@OPUS_ARM_MAY_HAVE_EDSP@", "1")
        .replace("@OPUS_ARM_MAY_HAVE_MEDIA@", "1")
        .replace("@OPUS_ARM_MAY_HAVE_NEON@", "1")
      )
    }
    convert(converter, temp, File(armopts.parentFile, "armopts_gnu.s"))
    require(temp.delete()) {
      "Unable to delete ${temp.absolutePath}"
    }
  }

  private fun convert(converter: File, input: File, output: File) {
    val out = ByteArrayOutputStream()
    exec.exec {
      commandLine("perl", converter.absolutePath, input.absolutePath)
      standardOutput = out
    }
    val patched = out.toString().replace(Regex("[-_]gnu\\.S", RegexOption.IGNORE_CASE), "_gnu.s")
    writeToFile(output) { s ->
      s.append(patched)
    }
  }
}
