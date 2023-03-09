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
 * File created on 04/02/2019
 */
package org.thunderdog.challegram.sync;

import android.content.Context;

import org.thunderdog.challegram.Log;

public class SyncHelper {
  public static void register (Context context, int accountId) {
    Log.v(Log.TAG_FCM, "SyncHelper.register, accountId:%d", accountId);
    SyncAdapter.register(context);
    // SyncWorker.register(accountId);
  }

  public static void cancel (Context context, int accountId) {
    Log.v(Log.TAG_FCM, "SyncHelper.cancel, accountId:%d", accountId);
    // SyncAdapter.cancel(accountId);
    // SyncWorker.cancel(accountId);
  }
}
