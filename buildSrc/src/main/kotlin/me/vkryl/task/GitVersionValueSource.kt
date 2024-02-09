/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package me.vkryl.task

import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import java.net.URI
import java.nio.charset.Charset
import java.util.*
import javax.inject.Inject

abstract class GitVersionValueSource : ValueSource<GitVersionValueSource.Details, ValueSourceParameters.None> {
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
      if (git[3].startsWith("git@")) {
        val index = git[3].indexOf(':', 4)
        val domain = git[3].substring(4, index)
        val endIndex = if (git[3].endsWith(".git")) {
          git[3].length - 4
        } else {
          git[3].length
        }
        val query = git[3].substring(index + 1, endIndex)
        "https://${domain}/${query}"
      } else {
        git[3]
      }
    )

    val commitUrl: String
      get() = String.format(Locale.ENGLISH, "%1\$s/tree/%3\$s", remoteUrl, commitHashShort, commitHashLong)
  }

  @get:Inject
  abstract val execOperations: ExecOperations

  override fun obtain(): Details {
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
      commandLine("git", "rev-parse", "--short", "HEAD")
      standardOutput = output
    }
    execOperations.exec {
      commandLine("git", "rev-parse", "HEAD")
      standardOutput = output
    }
    execOperations.exec {
      commandLine("git", "show", "-s", "--format=%ct")
      standardOutput = output
    }
    execOperations.exec {
      commandLine("git", "config", "--get", "remote.origin.url")
      standardOutput = output
    }
    execOperations.exec {
      commandLine("git", "log", "-1", "--pretty=format:'%an'")
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