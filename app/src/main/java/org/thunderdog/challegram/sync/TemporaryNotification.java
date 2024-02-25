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
 * File created on 17/02/2024
 */
package org.thunderdog.challegram.sync;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.thunderdog.challegram.MainActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.telegram.TdlibNotificationManager;
import org.thunderdog.challegram.tool.Intents;

import me.vkryl.core.StringUtils;

public class TemporaryNotification {
  public static void showNotification (Context context, @NonNull String channelId, @NonNull CharSequence title, @Nullable CharSequence text) {
    NotificationManagerCompat manager = NotificationManagerCompat.from(context);
    if (manager.areNotificationsEnabled()) {
      NotificationCompat.Builder b = new NotificationCompat.Builder(context, channelId)
        .setSmallIcon(R.drawable.baseline_feedback_white_24)
        .setContentTitle(title)
        .setContentIntent(PendingIntent.getActivity(context, 0, new Intent(context, MainActivity.class), Intents.mutabilityFlags(false)));
      if (!StringUtils.isEmpty(text)) {
        b.setContentText(text);
      }
      Notification notification = b.build();
      manager.notify(TdlibNotificationManager.ID_TEMPORARY_NOTIFICATION, notification);
    }
  }

  public static void hide (Context context) {
    NotificationManagerCompat manager = NotificationManagerCompat.from(context);
    manager.cancel(TdlibNotificationManager.ID_TEMPORARY_NOTIFICATION);
  }
}
