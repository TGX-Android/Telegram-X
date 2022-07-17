/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.thunderdog.challegram.util

import me.vkryl.core.limit
import me.vkryl.leveldb.LevelDB
import me.vkryl.td.tdlibCommitHashFull
import org.thunderdog.challegram.BuildConfig
import kotlin.math.max

data class AppBuildInfo(
  val installationId: Long,
  val versionCode: Int,
  val versionName: String,
  val firstRunDate: Long,
  val commit: String,
  val commitFull: String,
  val commitDate: Long,
  val tdlibCommitFull: String?,
  val pullRequests: List<PullRequest>
) {
  constructor(installationId: Long) : this(
    installationId,
    BuildConfig.ORIGINAL_VERSION_CODE,
    BuildConfig.ORIGINAL_VERSION_NAME,
    System.currentTimeMillis(),
    BuildConfig.COMMIT,
    BuildConfig.COMMIT_FULL,
    BuildConfig.COMMIT_DATE,
    tdlibCommitHashFull(),
    builtinPullRequests()
  )

  fun saveTo (editor: LevelDB, keyPrefix: String) {
    editor
      .putInt("${keyPrefix}_code", versionCode)
      .putString("${keyPrefix}_name", versionName)
      .putLong("${keyPrefix}_started", firstRunDate)
      .putString("${keyPrefix}_commit", commit)
      .putString("${keyPrefix}_full", commitFull)
      .putLong("${keyPrefix}_date", commitDate)
    if (!tdlibCommitFull.isNullOrEmpty()) {
      editor.putString("${keyPrefix}_tdlib", tdlibCommitFull)
    }
    editor
      .putLongArray("${keyPrefix}_prs", pullRequests.map { it.id }.toLongArray())
    if (pullRequests.isNotEmpty()) {
      pullRequests.forEach { pullRequest ->
        pullRequest.saveTo(editor, "${keyPrefix}_pr${pullRequest.id}")
      }
    }
  }

  fun commitUrl (): String {
    return "${BuildConfig.REMOTE_URL}/tree/${commitFull}"
  }

  fun changesUrlFrom (previousBuild: AppBuildInfo): String? {
    return if (this.commitDate > previousBuild.commitDate) {
      "${BuildConfig.REMOTE_URL}/compare/${previousBuild.commit}...${this.commit}"
    } else {
      null
    }
  }

  fun tdlibCommitUrl (): String? {
    return if (!this.tdlibCommitFull.isNullOrEmpty()) {
      return "${BuildConfig.TDLIB_REMOTE_URL}/tree/${tdlibCommitFull}"
    } else {
      null
    }
  }

  fun tdlibChangesUrlFrom (previousBuild: AppBuildInfo): String? {
    return if (this.commitDate > previousBuild.commitDate && !this.tdlibCommitFull.isNullOrEmpty() && !previousBuild.tdlibCommitFull.isNullOrEmpty()) {
      "${BuildConfig.TDLIB_REMOTE_URL}/compare/${previousBuild.tdlibCommit()}...${this.tdlibCommit()}"
    } else {
      null
    }
  }

  fun tdlibCommit (): String? = this.tdlibCommitFull.limit(7)

  fun pullRequestsList (): String? = if (this.pullRequests.isNotEmpty()) {
    this.pullRequests.joinToString { it -> "#{$it.id} ($it.commit)" }
  } else {
    null
  }
  
  companion object {
    @JvmStatic fun restoreFrom (pmc: LevelDB, installationId: Long, keyPrefix: String): AppBuildInfo {
      val prIds = pmc.getLongArray("${keyPrefix}_prs") ?: longArrayOf()
      val pullRequests = if (prIds.isNotEmpty()) {
        prIds.map { pullRequestId ->
          PullRequest.restoreFrom(pmc, "${keyPrefix}_pr${pullRequestId}")
        }
      } else {
        emptyList()
      }
      return AppBuildInfo(
        installationId,
        pmc.getInt("${keyPrefix}_code", 0),
        pmc.getString("${keyPrefix}_name", "")!!,
        pmc.getLong("${keyPrefix}_started", 0),
        pmc.getString("${keyPrefix}_commit", "")!!,
        pmc.getString("${keyPrefix}_full", "")!!,
        pmc.getLong("${keyPrefix}_date", 0),
        pmc.getString("${keyPrefix}_tdlib", null),
        pullRequests
      )
    }

    @JvmStatic fun maxCommitDate (): Long {
      return max(BuildConfig.COMMIT_DATE, BuildConfig.PULL_REQUEST_COMMIT_DATE.maxOrNull() ?: 0)
    }
  }
}

data class PullRequest (
  val id: Long,
  val commit: String,
  val commitFull: String,
  val commitUrl: String,
  val commitDate: Long,
  val commitAuthor: String
) {
  fun saveTo (editor: LevelDB, keyPrefix: String) {
    editor
      .putLong("${keyPrefix}_id", id)
      .putString("${keyPrefix}_commit", commit)
      .putString("${keyPrefix}_full", commitFull)
      .putString("${keyPrefix}_url", commitUrl)
      .putLong("${keyPrefix}_date", commitDate)
      .putString("${keyPrefix}_author", commitAuthor)
  }
  
  companion object {
    fun restoreFrom (pmc: LevelDB, keyPrefix: String): PullRequest {
      return PullRequest(
        pmc.getLong("${keyPrefix}_id", 0),
        pmc.getString("${keyPrefix}_commit", "")!!,
        pmc.getString("${keyPrefix}_full", "")!!,
        pmc.getString("${keyPrefix}_url", "")!!,
        pmc.getLong("${keyPrefix}_date", 0),
        pmc.getString("${keyPrefix}_author", "")!!
      )
    } 
  }
}

fun builtinPullRequests (): List<PullRequest> {
  return if (BuildConfig.PULL_REQUEST_ID.isEmpty()) {
    return emptyList()
  } else {
    BuildConfig.PULL_REQUEST_ID.mapIndexed { index, pullRequestId ->
      PullRequest(
        pullRequestId,
        BuildConfig.PULL_REQUEST_COMMIT[index],
        BuildConfig.PULL_REQUEST_COMMIT_FULL[index],
        BuildConfig.PULL_REQUEST_URL[index],
        BuildConfig.PULL_REQUEST_COMMIT_DATE[index],
        BuildConfig.PULL_REQUEST_AUTHOR[index]
      )
    }
  }
}