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
 * File created on 04/08/2018
 */
package org.thunderdog.challegram.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.thunderdog.challegram.service.TGCallService;

public class CallBroadcastReceiver extends BroadcastReceiver {
  @Override
  public void onReceive (Context context, Intent intent) {
    TGCallService service = TGCallService.currentInstance();
    if (service != null) {
      service.processBroadcast(context, intent);
    }
  }
}
