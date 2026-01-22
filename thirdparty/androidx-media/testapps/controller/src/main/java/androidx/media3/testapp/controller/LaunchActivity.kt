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
package androidx.media3.testapp.controller

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.session.MediaSessionManager as ActiveSessionManager
import android.os.Bundle
import android.service.notification.NotificationListenerService
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.NotificationManagerCompat
import androidx.media3.testapp.controller.findapps.FindActiveMediaSessionApps
import androidx.media3.testapp.controller.findapps.FindMediaApps
import androidx.media3.testapp.controller.findapps.FindMediaServiceApps
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * App entry point. Presents a list of apps that implement
 * [androidx.media3.session.MediaSessionService], [androidx.media3.session.MediaLibraryService], or
 * [androidx.media.MediaBrowserServiceCompat]. Also presents a separate list of active media session
 * apps.
 */
class LaunchActivity : AppCompatActivity() {
  private lateinit var mediaAppListAdapter: MediaAppListAdapter
  private lateinit var mediaSessionApps: MediaAppListAdapter.Section
  private val sessionAppsUpdated =
    object : FindMediaApps.AppListUpdatedCallback {
      override fun onAppListUpdated(mediaAppEntries: List<MediaAppDetails>) {
        if (mediaAppEntries.isEmpty()) {
          mediaSessionApps.setError(
            R.string.no_apps_found,
            R.string.no_apps_reason_no_media_services,
          )
        } else {
          mediaSessionApps.setAppsList(mediaAppEntries)
        }
      }
    }
  private var activeSessionListener: ActiveSessionListener? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setContentView(R.layout.activity_launch)

    val toolbar: Toolbar? = findViewById(R.id.toolbar)
    setSupportActionBar(toolbar)

    mediaAppListAdapter =
      MediaAppListAdapter(
        object : MediaAppListAdapter.MediaAppSelectedListener {
          override fun onMediaAppClicked(mediaAppDetails: MediaAppDetails) {
            startActivity(
              MediaAppControllerActivity.buildIntent(this@LaunchActivity, mediaAppDetails)
            )
          }
        }
      )

    activeSessionListener = ActiveSessionListener()
    mediaSessionApps = mediaAppListAdapter.addSection(R.string.media_app_header_media_service)

    val mediaAppsList: RecyclerView? = findViewById(R.id.app_list)
    mediaAppsList?.layoutManager = LinearLayoutManager(this)
    mediaAppsList?.setHasFixedSize(true)
    mediaAppsList?.adapter = mediaAppListAdapter
  }

  override fun onStart() {
    super.onStart()

    activeSessionListener!!.onStart()

    // Finds apps that implement MediaSessionService, MediaLibraryService, or
    // MediaBrowserServiceCompat.
    FindMediaServiceApps(context = this, this.packageManager, this.resources, sessionAppsUpdated)
      .execute()
  }

  override fun onStop() {
    super.onStop()
    activeSessionListener!!.onStop()
  }

  /**
   * Encapsulates the functionality of looking for and observing updates to active media sessions.
   */
  private inner class ActiveSessionListener {
    private val activeSessionApps: MediaAppListAdapter.Section =
      mediaAppListAdapter.addSection(R.string.media_app_header_active_session)
    private val activeSessionManager: ActiveSessionManager =
      getSystemService(Context.MEDIA_SESSION_SERVICE) as ActiveSessionManager
    private val sessionAppsUpdated =
      object : FindMediaApps.AppListUpdatedCallback {
        override fun onAppListUpdated(mediaAppEntries: List<MediaAppDetails>) =
          if (mediaAppEntries.isEmpty()) {
            activeSessionApps.setError(
              R.string.no_apps_found,
              R.string.no_apps_reason_no_active_sessions,
            )
          } else {
            activeSessionApps.setAppsList(mediaAppEntries)
          }
      }
    private lateinit var findActiveMediaSessionApps: FindActiveMediaSessionApps
    private val sessionsChangedListener =
      ActiveSessionManager.OnActiveSessionsChangedListener { findActiveMediaSessionApps.execute() }

    fun onStart() {
      if (!NotificationListener.isEnabled(this@LaunchActivity)) {
        activeSessionApps.setError(
          R.string.no_apps_found,
          R.string.no_apps_reason_missing_permission,
          R.string.action_notification_permissions_settings,
        ) {
          startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        }
      } else {
        val listenerComponent = ComponentName(this@LaunchActivity, NotificationListener::class.java)
        findActiveMediaSessionApps =
          FindActiveMediaSessionApps(
            activeSessionManager,
            listenerComponent,
            packageManager,
            resources,
            this@LaunchActivity,
            sessionAppsUpdated,
          )
        activeSessionManager.addOnActiveSessionsChangedListener(
          sessionsChangedListener,
          listenerComponent,
        )
        findActiveMediaSessionApps.execute()
      }
    }

    fun onStop() {
      activeSessionManager.removeOnActiveSessionsChangedListener(sessionsChangedListener)
    }
  }

  /**
   * A notification listener service that allows us to grab active media sessions from their
   * notifications.
   */
  class NotificationListener : NotificationListenerService() {
    companion object {
      // Helper method to check if our notification listener is enabled. In order to get active
      // media sessions, we need an enabled notification listener component.
      fun isEnabled(context: Context): Boolean {
        return NotificationManagerCompat.getEnabledListenerPackages(context)
          .contains(context.packageName)
      }
    }
  }
}
