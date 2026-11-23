package tgx.gradle.task

import org.eclipse.jgit.lfs.LfsPointer
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*
import tgx.gradle.fatal
import tgx.gradle.requireDir
import java.io.File

abstract class ValidateGitSetupTask : DefaultTask() {
  @get:Internal
  abstract val mainDir: DirectoryProperty

  @get:InputFile
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val gitmodulesFile: RegularFileProperty

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val submoduleMarkers: ConfigurableFileCollection

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val lfsFiles: ConfigurableFileCollection

  @TaskAction
  fun validateGitSetup() {
    val mainRepo = requireDir(mainDir.get().asFile)
    if (!mainRepo.resolve(".git").isDirectory) {
      fatal("Fetch repository with submodules via git")
    }
    if (mainRepo.absolutePath.any(Char::isWhitespace)) {
      fatal("Repository path must not have whitespaces")
    }
    val missing = submoduleMarkers.filterNot {
      it.exists()
    }.map {
      it.parentFile.absolutePath
    }
    if (missing.isNotEmpty()) {
      fatal(
        "Submodules are not initialized:\n" +
        "${missing.joinToString("\n") }\n\n" +
        "Run: git submodule update --init --recursive\n\nThen try again."
      )
    }

    val pointers = lfsFiles.files.filter { isLfsPointer(it) }
    if (pointers.isNotEmpty()) {
      fatal(
        "LFS objects are not fetched:\n" +
        "${pointers.joinToString("\n") }\n\n" +
        "Install git-lfs and run: cd tdlib && git lfs fetch\n\nThen try again."
      )
    }
  }

  companion object {
    fun isLfsPointer(f: File): Boolean =
      f.length() < 1024 && f.inputStream().use { LfsPointer.parseLfsPointer(it) != null }
  }
}