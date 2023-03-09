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
 * File created on 13/07/2017
 */
package org.thunderdog.challegram.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.thunderdog.challegram.MainActivity;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.UI;

public class TGSecretCodeReceiver extends BroadcastReceiver {
  @Override
  public void onReceive (Context context, Intent intent) {
    if ("android.provider.Telephony.SECRET_CODE".equals(intent.getAction())) {
      String uri = intent.getDataString();
      if (uri == null) {
        return;
      }

      String[] sep = uri.split("://");
      if (sep.length > 0 && sep[1].equals("83534726")) {
        Intent launchIntent = new Intent(UI.getAppContext(), MainActivity.class);
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        launchIntent.setAction(Intents.randomAction(Intents.ACTION_OPEN_LOGS));
        context.startActivity(launchIntent);
      }

    }
  }
}
