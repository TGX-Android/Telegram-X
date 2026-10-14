package tgx.gradle.task

import org.gradle.api.Action
import org.gradle.api.Task
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.process.ExecOperations
import tgx.gradle.fatal
import tgx.gradle.requireFile
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject

abstract class OptimizeWithAapt2Task @Inject constructor(
  private val exec: ExecOperations,
  private val fs: FileSystemOperations
) : Action<Task> {
  abstract val aapt2File: RegularFileProperty
  abstract val minSdk: Property<Int>

  override fun execute(task: Task) {
    if (minSdk.get() < 33) {
      fatal("Rudimentary ${className()} task for minSdk=${minSdk.get()}")
    }
    val resources = task.outputs.files.asFileTree.matching {
      include("**/*.ap_")
    }.files
    if (resources.isEmpty()) {
      fatal("${task.name}: no .ap_ outputs found")
    }
    resources.forEach { inputFile ->
      val tempFile = File(inputFile.parentFile, "${inputFile.name}.sparse")
      val output = ByteArrayOutputStream()
      val result = exec.exec {
        executable = requireFile(aapt2File.get().asFile).absolutePath
        args("optimize", "--enable-sparse-encoding")
        if (minSdk.get() >= 34) {
          args("--enable-compact-entries")
        }
        args("-o", tempFile.toPosixPath(), inputFile.toPosixPath())
        standardOutput = output
        errorOutput = output
        isIgnoreExitValue = true
      }
      if (result.exitValue != 0) {
        fs.delete {
          delete(tempFile)
        }
        fatal("aapt2 optimize $inputFile:\n$output")
      }
      moveFile(tempFile, inputFile)
    }
  }
}