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
 *
 * File created on 05/04/2015 at 08:53
 */
package org.thunderdog.challegram

import android.content.Context
import androidx.multidex.MultiDexApplication
import androidx.work.Configuration
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import org.thunderdog.challegram.push.FirebaseDeviceTokenRetriever
import org.thunderdog.challegram.service.PushHandler
import org.thunderdog.challegram.telegram.TdlibNotificationUtils
import org.thunderdog.challegram.tool.UI
import tgx.bridge.DeviceTokenRetriever
import tgx.bridge.PushManagerBridge
import tgx.bridge.DeviceTokenRetrieverFactory
import tgx.extension.TelegramXExtension

class BaseApplication : MultiDexApplication(), Configuration.Provider {
  private lateinit var scope: CoroutineScope

  override fun onCreate() {
    super.onCreate()
    scope = MainScope()

    var googlePlayServicesAvailable = U.isGooglePlayServicesAvailable(applicationContext)
    if (BuildConfig.DEBUG && TelegramXExtension.name == "hms") {
      // Test HMS
      googlePlayServicesAvailable = false
    }
    PushManagerBridge.initialize(
      scope,

      PushHandler(),
      object : DeviceTokenRetrieverFactory {
        override fun onCreateNewTokenRetriever(context: Context): DeviceTokenRetriever =
          TelegramXExtension.createNewTokenRetriever(context, googlePlayServicesAvailable).takeIf {
            !BuildConfig.EXPERIMENTAL
          } ?:
          FirebaseDeviceTokenRetriever()
      }
    )

    UI.initApp(applicationContext)

    if (!BuildConfig.EXPERIMENTAL) {
      TelegramXExtension.configure(this, TdlibNotificationUtils.getDeviceTokenRetriever())
      if (!TdlibNotificationUtils.isFirebaseTokenRetriever()) {
        FirebaseMessaging.getInstance().isAutoInitEnabled = false
      }
    }
  }

  override val workManagerConfiguration: Configuration
    get() = Configuration.Builder().build()
}