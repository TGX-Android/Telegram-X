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
 *
 * File created on 19/11/2016
 */
package org.thunderdog.challegram.service;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.TDLib;
import org.thunderdog.challegram.telegram.TdlibAccount;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.HashMap;
import java.util.Map;

import me.vkryl.td.JSON;

public class FirebaseListenerService extends FirebaseMessagingService {
  @Override
  public void onNewToken (@NonNull String newToken) {
    UI.initApp(getApplicationContext());
    TDLib.Tag.notifications("onNewToken %s, sending to all accounts", newToken);
    TdlibManager.instance().runWithWakeLock(manager -> {
      manager.setDeviceToken(new TdApi.DeviceTokenFirebaseCloudMessaging(newToken, true));
    });
  }

  @Override
  public void onDeletedMessages () {
    UI.initApp(getApplicationContext());
    TDLib.Tag.notifications("onDeletedMessages: performing sync for all accounts");
    TdlibManager.makeSync(getApplicationContext(), TdlibAccount.NO_ID, TdlibManager.SYNC_CAUSE_DELETED_MESSAGES, 0, !TdlibManager.inUiThread(), 0);
  }

  @Override
  public void onMessageReceived (@NonNull RemoteMessage remoteMessage) {
    UI.initApp(getApplicationContext());

    final String payload = makePayload(remoteMessage);
    final long sentTime = remoteMessage.getSentTime();
    final long pushId = Settings.instance().newPushId();

    PushProcessor pushProcessor = new PushProcessor(this);
    pushProcessor.processPush(pushId, payload, sentTime, remoteMessage.getTtl());
  }

  // Utils

  private static String makePayload (final RemoteMessage remoteMessage) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("google.sent_time", remoteMessage.getSentTime());
    RemoteMessage.Notification notification = remoteMessage.getNotification();
    if (notification != null) {
      payload.put("google.notification.sound", notification.getSound());
    } else {
      Intent intent = remoteMessage.toIntent();
      Bundle extras = intent.getExtras();
      if (extras != null) {
        if (extras.containsKey("gcm.n.sound2")) {
          payload.put("google.notification.sound", extras.getString("gcm.n.sound2"));
        } else if (extras.containsKey("gcm.n.sound")) {
          payload.put("google.notification.sound", extras.getString("gcm.n.sound"));
        }
      }
    }
    Map<String, String> data = remoteMessage.getData();
    if (data != null) {
      payload.putAll(data);
    }
    return JSON.stringify(payload);
  }
}
