package org.thunderdog.challegram.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.thunderdog.challegram.service.TGCallService;

/**
 * Date: 8/4/18
 * Author: default
 */
public class CallBroadcastReceiver extends BroadcastReceiver {
  @Override
  public void onReceive (Context context, Intent intent) {
    TGCallService service = TGCallService.currentInstance();
    if (service != null) {
      service.processBroadcast(context, intent);
    }
  }
}
