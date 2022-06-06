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
 * File created on 10/03/2019
 */
package org.thunderdog.challegram.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.telegram.TdlibAccount;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.tool.UI;

public class BootListenerService extends Service {
  @Nullable
  @Override
  public IBinder onBind (Intent intent) {
    return null;
  }

  @Override
  public int onStartCommand (Intent intent, int flags, int startId) {
    UI.initApp(getApplicationContext());
    TdlibManager.makeSync(getApplicationContext(), TdlibAccount.NO_ID, TdlibManager.SYNC_CAUSE_BOOT, 0, false, 0);
    return START_NOT_STICKY;
  }
}
