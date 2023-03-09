/*
 * This file is a part of Telegram X
 * Copyright © 2014-2023 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 06/01/2019
 */
package org.thunderdog.challegram.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.telegram.TdlibNotificationExtras;

public class TGRemoveAllReceiver extends BroadcastReceiver {
  @Override
  public void onReceive (Context context, Intent intent) {
    TdlibNotificationExtras extras = TdlibNotificationExtras.parseCategory(intent.getExtras());
    TdlibManager.performExternalAction(context, TdlibManager.EXTERNAL_ACTION_MARK_ALL_AS_HIDDEN, extras);
  }
}
