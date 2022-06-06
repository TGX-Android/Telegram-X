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

import me.vkryl.leveldb.LevelDB
import org.thunderdog.challegram.BuildConfig

data class AppBuildInfo(
  val versionCode: Int,
  val versionName: String,
  val firstRunDate: Long,
  val commit: String,
  val commitFull: String,
  val commitUrl: String,
  val commitDate: Long,
  val pullRequests: List<PullRequest>
) {
  constructor() : this(
    BuildConfig.VERSION_CODE,
    BuildConfig.VERSION_NAME,
    System.currentTimeMillis(),
    BuildConfig.COMMIT,
    BuildConfig.COMMIT_FULL,
    BuildConfig.COMMIT_URL,
    BuildConfig.COMMIT_DATE,
    builtinPullRequests()
  )

  fun saveTo (editor: LevelDB, keyPrefix: String) {
    editor
      .putInt("${keyPrefix}_code", versionCode)
      .putString("${keyPrefix}_name", versionName)
      .putLong("${keyPrefix}_started", firstRunDate)
      .putString("${keyPrefix}_commit", commit)
      .putString("${keyPrefix}_full", commitFull)
      .putString("${keyPrefix}_url", commitUrl)
      .putLong("${keyPrefix}_date", commitDate)
    editor
      .putLongArray("${keyPrefix}_prs", pullRequests.map { it.id }.toLongArray())
    if (pullRequests.isNotEmpty()) {
      pullRequests.forEach { pullRequest ->
        pullRequest.saveTo(editor, "${keyPrefix}_pr${pullRequest.id}")
      }
    }
  }
  
  companion object {
    fun restoreFrom (pmc: LevelDB, keyPrefix: String): AppBuildInfo {
      val prIds = pmc.getLongArray("${keyPrefix}_prs") ?: longArrayOf()
      val pullRequests = if (prIds.isNotEmpty()) {
        prIds.map { pullRequestId ->
          PullRequest.restoreFrom(pmc, "${keyPrefix}_pr${pullRequestId}")
        }
      } else {
        emptyList()
      }
      return AppBuildInfo(
        pmc.getInt("${keyPrefix}_code", 0),
        pmc.getString("${keyPrefix}_name", "")!!,
        pmc.getLong("${keyPrefix}_started", 0),
        pmc.getString("${keyPrefix}_commit", "")!!,
        pmc.getString("${keyPrefix}_full", "")!!,
        pmc.getString("${keyPrefix}_url", "")!!,
        pmc.getLong("${keyPrefix}_date", 0),
        pullRequests
      )
    }
  }
}

data class PullRequest (
  val id: Long,
  val commit: String,
  val commitFull: String,
  val commitUrl: String,
  val commitDate: Long
) {
  fun saveTo (editor: LevelDB, keyPrefix: String) {
    editor
      .putLong("${keyPrefix}_id", id)
      .putString("${keyPrefix}_commit", commit)
      .putString("${keyPrefix}_full", commitFull)
      .putString("${keyPrefix}_url", commitUrl)
      .putLong("${keyPrefix}_date", commitDate)
  }
  
  companion object {
    fun restoreFrom (pmc: LevelDB, keyPrefix: String): PullRequest {
      return PullRequest(
        pmc.getLong("${keyPrefix}_id", 0),
        pmc.getString("${keyPrefix}_commit", "")!!,
        pmc.getString("${keyPrefix}_full", "")!!,
        pmc.getString("${keyPrefix}_url", "")!!,
        pmc.getLong("${keyPrefix}_date", 0)
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
        BuildConfig.PULL_REQUEST_COMMIT_DATE[index]
      )
    }
  }
}