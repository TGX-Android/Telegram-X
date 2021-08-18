package org.thunderdog.challegram.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.telegram.TdlibNotificationExtras;
import org.thunderdog.challegram.tool.Intents;

/**
 * Date: 7/6/18
 * Author: default
 */
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
