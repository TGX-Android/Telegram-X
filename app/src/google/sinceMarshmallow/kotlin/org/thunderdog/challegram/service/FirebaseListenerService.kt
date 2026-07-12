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
 * File created on 19/11/2016
 */
package org.thunderdog.challegram.service

import org.drinkless.tdlib.TdApi.DeviceTokenFirebaseCloudMessaging
import org.thunderdog.challegram.push.DefaultFirebaseMessagingService
import tgx.bridge.PushManagerBridge.onNewToken

@Suppress("MissingFirebaseInstanceTokenRefresh")
class FirebaseListenerService : DefaultFirebaseMessagingService() {
  override fun onRegistered(installationId: String) {
    onNewToken(this, DeviceTokenFirebaseCloudMessaging(installationId, true))
  }
}