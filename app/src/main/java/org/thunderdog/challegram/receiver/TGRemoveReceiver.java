package org.thunderdog.challegram.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.telegram.TdlibNotificationExtras;

/**
 * Date: 06/01/2019
 * Author: default
 */
public class TGRemoveReceiver extends BroadcastReceiver {
  @Override
  public void onReceive (Context context, Intent intent) {
    TdlibNotificationExtras extras = TdlibNotificationExtras.parse(intent.getExtras());
    TdlibManager.performExternalAction(context, TdlibManager.EXTERNAL_ACTION_MARK_AS_HIDDEN, extras);
  }
}
