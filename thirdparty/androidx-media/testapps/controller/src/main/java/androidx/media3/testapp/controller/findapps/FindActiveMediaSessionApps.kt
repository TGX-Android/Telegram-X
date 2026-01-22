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

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import androidx.media3.testapp.controller.MediaAppDetails

/**
 * Implementation of [FindMediaApps] that uses [MediaSessionManager] to populate the list of active
 * media session apps.
 */
class FindActiveMediaSessionApps
constructor(
  private val mediaSessionManager: MediaSessionManager,
  private val componentName: ComponentName,
  private val packageManager: PackageManager,
  private val resources: Resources,
  private val context: Context,
  callback: AppListUpdatedCallback
) : FindMediaApps(callback) {
  override val mediaApps: List<MediaAppDetails>
    get() {
      return getMediaAppsFromMediaControllers(
        mediaSessionManager.getActiveSessions(componentName),
        packageManager,
        resources
      )
    }

  private fun getMediaAppsFromMediaControllers(
    sessionTokens: List<MediaController>,
    packageManager: PackageManager,
    resources: Resources
  ): List<MediaAppDetails> {
    return sessionTokens.map {
      MediaAppDetails.create(packageManager, resources, controller = it, context)
    }
  }
}
