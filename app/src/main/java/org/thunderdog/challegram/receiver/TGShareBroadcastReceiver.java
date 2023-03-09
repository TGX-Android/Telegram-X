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
 * File created on 14/08/2017
 */
package org.thunderdog.challegram.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;

import me.vkryl.core.StringUtils;

public class TGShareBroadcastReceiver extends BroadcastReceiver {
  @Override
  public void onReceive (Context context, Intent intent) {
    String url = intent.getDataString();
    if (!StringUtils.isEmpty(url)) {
      Intent shareIntent = new Intent(Intent.ACTION_SEND);
      shareIntent.setType("text/plain");
      shareIntent.putExtra(Intent.EXTRA_TEXT, url);
      Intent chooserIntent = Intent.createChooser(shareIntent, Lang.getString(R.string.ShareLink));
      chooserIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      context.startActivity(chooserIntent);
    }
  }
}
