package org.thunderdog.challegram.sync;

import android.content.Context;

import org.thunderdog.challegram.Log;

/**
 * Date: 04/02/2019
 * Author: default
 */
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
