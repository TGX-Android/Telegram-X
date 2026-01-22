/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.testapp.controller.findapps

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import androidx.media3.session.SessionToken
import androidx.media3.testapp.controller.MediaAppDetails

/**
 * Implementation of [FindMediaApps] that uses [MediaSessionManager] to populate the list of media
 * service apps.
 */
class FindMediaServiceApps
constructor(
  private val context: Context,
  private val packageManager: PackageManager,
  private val resources: Resources,
  callback: AppListUpdatedCallback
) : FindMediaApps(callback) {

  override val mediaApps: List<MediaAppDetails>
    get() {
      return getMediaAppsFromSessionTokens(
        SessionToken.getAllServiceTokens(context),
        packageManager,
        resources
      )
    }

  private fun getMediaAppsFromSessionTokens(
    sessionTokens: Set<SessionToken>,
    packageManager: PackageManager,
    resources: Resources
  ): List<MediaAppDetails> {
    return sessionTokens.map {
      MediaAppDetails.create(packageManager, resources, sessionToken = it)
    }
  }
}
