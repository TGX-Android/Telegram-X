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
 * File created on 06/07/2018
 */
package org.thunderdog.challegram.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.telegram.TdlibNotificationExtras;
import org.thunderdog.challegram.tool.Intents;

public class TGMessageReceiver extends BroadcastReceiver {
  @Override
  public void onReceive (Context context, Intent intent) {
    if (intent == null)
      return;
    String action = intent.getAction();
    if (action == null)
      return;
    int externalActionId;
    switch (action) {
      case Intents.ACTION_MESSAGE_READ:
        externalActionId = TdlibManager.EXTERNAL_ACTION_MARK_AS_READ;
        break;
      case Intents.ACTION_MESSAGE_MUTE:
        externalActionId = TdlibManager.EXTERNAL_ACTION_MUTE;
        break;
      default:
        return;
    }
    TdlibNotificationExtras extras = TdlibNotificationExtras.parse(intent.getExtras());
    TdlibManager.performExternalAction(context, externalActionId, extras);
  }
}
