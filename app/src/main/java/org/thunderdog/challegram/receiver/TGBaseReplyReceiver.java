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
 * File created on 08/04/2017
 */
package org.thunderdog.challegram.receiver;

import android.annotation.TargetApi;
import android.app.RemoteInput;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.telegram.TdlibNotificationExtras;

public abstract class TGBaseReplyReceiver extends BroadcastReceiver {
  public static final String EXTRA_VOICE_REPLY = "extra_voice_reply";
  // public static final boolean READ_IMMEDIATELY = Build.VERSION.SDK_INT < Build.VERSION_CODES.P || !BuildConfig.DEBUG;

  @Override
  @TargetApi(Build.VERSION_CODES.KITKAT_WATCH)
  public void onReceive (Context context, Intent intent) {
    final Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);
    if (remoteInput == null) {
      return;
    }
    final CharSequence text = remoteInput.getCharSequence(EXTRA_VOICE_REPLY);
    TdlibNotificationExtras extras = TdlibNotificationExtras.parse(intent.getExtras());
    TdlibManager.performExternalReply(context, text, extras);
  }
}