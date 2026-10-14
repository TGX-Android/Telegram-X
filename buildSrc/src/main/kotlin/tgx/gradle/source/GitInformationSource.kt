package tgx.gradle.source

import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import tgx.gradle.data.GitInformation
import tgx.gradle.requireDir

abstract class GitInformationSource : ValueSource<GitInformation, GitInformationSource.Params> {
  interface Params : ValueSourceParameters {
    val module: DirectoryProperty
  }

  override fun obtain(): GitInformation {
    val dir = requireDir(parameters.module.get().asFile)
    return FileRepositoryBuilder().apply {
      findGitDir(dir)
      isMustExist = true
    }.build().use { repository ->
      val head = repository.resolve(Constants.HEAD) ?: error("HEAD is missing: ${dir.absolutePath}")
      val commit = repository.parseCommit(head)

      GitInformation(
        commitHashShort = repository.newObjectReader().use { it.abbreviate(head).name() },
        commitHashLong = commit.name,
        commitDate = commit.commitTime.toLong(),
        remoteUrl = cleanRemoteUrl(repository.config.getString("remote", "origin", "url")),
        commitAuthor = commit.authorIdent.name
      )
    }
  }

  companion object {
    private fun cleanRemoteUrl(remoteUrl: String): String {
      val match = Regex("^(?:(https?|ssh)://)?(?:git@)?([a-zA-Z.0-9]+(?::\\d+)?)[/:]([a-zA-Z.0-9\\-_][a-zA-Z.0-9\\-_/]*)(?:\\.git)?$").matchEntire(remoteUrl)
      require(match != null && match.groupValues.size == 4) {
        "Failed to parse URL: $remoteUrl"
      }
      val protocol = match.groupValues[1]
      val host = match.groupValues[2]
      val path = match.groupValues[3]
      return when (protocol) {
        "ssh", "" -> "https://${host}/${path}"
        "http", "https" -> "${protocol}://${host}/${path}"
        else -> {
          error("Unknown protocol: $protocol")
        }
      }
    }
  }
}