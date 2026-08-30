package tgx.gradle.source

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import java.net.URI
import java.nio.charset.Charset
import java.util.*
import javax.inject.Inject

abstract class GitVersionSource : ValueSource<GitVersionSource.Details, GitVersionSource.Params> {
  interface Params : ValueSourceParameters {
    val module: DirectoryProperty
  }

  data class Details(
    val commitHashShort: String,
    val commitHashLong: String,
    val commitDate: Long,
    val remoteUrl: String
  ) {
    constructor(output: String) : this(output.trim().split('\n', limit = 5))
    constructor(git: List<String>) : this(
      git[0],
      git[1],
      git[2].toLong(),
      git[3].let { remoteUrl ->
        val match = Regex("^(?:(https?|ssh)://)?(?:git@)?([a-zA-Z.0-9]+(?::\\d+)?)[/:]([a-zA-Z.0-9\\-_][a-zA-Z.0-9\\-_/]*)(?:\\.git)?$").matchEntire(remoteUrl)
        require(match != null && match.groupValues.size == 4) {
          "Failed to parse URL: $remoteUrl"
        }
        val protocol = match.groupValues[1]
        val host = match.groupValues[2]
        val path = match.groupValues[3]
        when (protocol) {
          "ssh", "" -> "https://${host}/${path}"
          "http", "https" -> "${protocol}://${host}/${path}"
          else -> {
            error("Unknown protocol: $protocol")
          }
        }
      }
    )

    val commitUrl: String
      get() = String.format(Locale.ENGLISH, $$"%1$s/tree/%3$s", remoteUrl, commitHashShort, commitHashLong)
  }

  @get:Inject
  abstract val execOperations: ExecOperations

  override fun obtain(): Details {
    val submodule = parameters.module.get().asFile
    val path = if (submodule.exists() && submodule.isDirectory) {
      submodule.absolutePath
    } else {
      ""
    }
    val output = ByteArrayOutputStream()
    /*execOperations.exec {
      if (System.getProperty("os.name").startsWith("Windows")) {
        commandLine("cmd", "/C", "scripts\\windows\\git-info.cmd")
      } else {
        commandLine("bash", "-c", "echo \"$(git rev-parse --short HEAD) $(git rev-parse HEAD) $(git show -s --format=%ct) $(git config --get remote.origin.url) $(git log -1 --pretty=format:'%an')\"")
      }
      standardOutput = output
    }*/
    // TODO: test Windows support
    execOperations.exec {
      if (path.isNotEmpty()) {
        commandLine("git", "-C", path, "rev-parse", "--short", "HEAD")
      } else {
        commandLine("git", "rev-parse", "--short", "HEAD")
      }
      standardOutput = output
    }
    execOperations.exec {
      if (path.isNotEmpty()) {
        commandLine("git", "-C", path, "rev-parse", "HEAD")
      } else {
        commandLine("git", "rev-parse", "HEAD")
      }
      standardOutput = output
    }
    execOperations.exec {
      if (path.isNotEmpty()) {
        commandLine("git", "-C", path, "show", "-s", "--format=%ct")
      } else {
        commandLine("git", "show", "-s", "--format=%ct")
      }
      standardOutput = output
    }
    execOperations.exec {
      if (path.isNotEmpty()) {
        commandLine("git", "-C", path, "config", "--get", "remote.origin.url")
      } else {
        commandLine("git", "config", "--get", "remote.origin.url")
      }
      standardOutput = output
    }
    execOperations.exec {
      if (path.isNotEmpty()) {
        commandLine("git", "-C", path, "log", "-1", "--pretty=format:'%an'")
      } else {
        commandLine("git", "log", "-1", "--pretty=format:'%an'")
      }
      standardOutput = output
    }
    val data = String(output.toByteArray(), Charset.defaultCharset())
    val details = Details(data)
    if (URI.create(details.remoteUrl).host != "github.com") {
      error("Unfortunately, currently you must host your fork on github.com.")
    }
    return details
  }
}