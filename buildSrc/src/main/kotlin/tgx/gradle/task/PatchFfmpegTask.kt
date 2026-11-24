package tgx.gradle.task

import org.eclipse.jgit.lib.ConfigConstants.CONFIG_CORE_SECTION
import org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_ATTRIBUTESFILE
import org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_AUTOCRLF
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.patch.Patch
import org.eclipse.jgit.patch.PatchApplier
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import tgx.gradle.fatal
import tgx.gradle.requireDir
import tgx.gradle.validateDir
import java.io.File
import javax.inject.Inject

@CacheableTask
abstract class PatchFfmpegTask : DefaultTask() {
  @get:Internal
  abstract val inputDir: DirectoryProperty

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val inputSources: ConfigurableFileCollection

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val patches: ConfigurableFileCollection

  @get:Input
  abstract val revision: Property<String>

  @get:OutputDirectory
  abstract val outputDir: DirectoryProperty

  @get:Inject
  abstract val fs: FileSystemOperations

  @TaskAction
  fun patchFfmpeg() {
    val input = requireDir(inputDir.get().asFile)
    val output = validateDir(outputDir.get().asFile)

    fs.delete {
      delete(output)
    }
    fs.copy {
      from(input) {
        exclude(".git")
      }
      into(output)
    }
    // Without it FFmpeg asks git for its version, which is now that of this repository
    writeTextToFile(output.resolve("VERSION")) {
      revision.get()
    }

    // Each is a fix of FFmpeg's upstream that the branch of the submodule lacks
    val sortedPatches = patches.files.sortedBy { it.name }
    createRepository(output).use { repository ->
      sortedPatches.forEach { file ->
        applyPatch(repository, file)
      }
    }
  }

  private fun createRepository(files: File): Repository {
    // JGit applies patches only to the files of a repository, which the copy is not. It's created
    // as bare, since otherwise JGit adds .git with the path of the repository to the copy
    val gitDir = temporaryDir.resolve("git")
    fs.delete {
      delete(gitDir)
    }
    val bare = FileRepositoryBuilder().setGitDir(gitDir).setBare().build()
    bare.use {
      it.create(true)
      // Otherwise the copy depends on git settings of the machine
      it.config.setBoolean(CONFIG_CORE_SECTION, null, CONFIG_KEY_AUTOCRLF, false)
      it.config.setString(CONFIG_CORE_SECTION, null, CONFIG_KEY_ATTRIBUTESFILE, "")
      it.config.save()
    }
    return FileRepositoryBuilder().setGitDir(gitDir).setWorkTree(files).build()
  }

  private fun applyPatch(repository: Repository, file: File) {
    val patch = Patch()
    file.inputStream().use(patch::parse)
    val errors = when {
      patch.errors.isNotEmpty() -> patch.errors
      // JGit skips a file without changes that it can read, and still succeeds
      patch.files.isEmpty() || patch.files.any { it.hunks.isEmpty() } ->
        listOf("The patch or a file of it has no changes")
      else -> PatchApplier(repository).applyPatch(patch).errors
    }
    if (errors.isNotEmpty()) {
      fatal(
        "${file.name} doesn't apply to FFmpeg, delete it if FFmpeg has it now: " +
          "${file.absolutePath}\n${errors.joinToString("\n")}"
      )
    }
  }
}
