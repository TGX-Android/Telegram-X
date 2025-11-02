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

import androidx.multidex.MultiDexApplication
import androidx.work.Configuration
import com.google.firebase.messaging.FirebaseMessaging
import org.thunderdog.challegram.service.PushHandler
import org.thunderdog.challegram.tool.UI
import tgx.bridge.PushReceiverBridge
import tgx.extension.TelegramXExtension

class BaseApplication : MultiDexApplication(), Configuration.Provider {
  override fun onCreate() {
    super.onCreate()

    PushReceiverBridge.registerReceiver(PushHandler())
    UI.initApp(applicationContext)

    val googlePlayServicesAvailable = U.isGooglePlayServicesAvailable(applicationContext)

    TelegramXExtension.configure(this, googlePlayServicesAvailable)
    if (TelegramXExtension.shouldDisableFirebaseMessaging(googlePlayServicesAvailable)) {
      FirebaseMessaging.getInstance().isAutoInitEnabled = false
    }
  }

  override val workManagerConfiguration: Configuration
    get() = Configuration.Builder().build()
}