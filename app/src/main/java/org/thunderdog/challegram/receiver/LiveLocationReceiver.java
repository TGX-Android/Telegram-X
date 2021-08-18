package org.thunderdog.challegram.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.tool.Intents;

import me.vkryl.core.StringUtils;

/**
 * Date: 3/5/18
 * Author: default
 */

public class LiveLocationReceiver extends BroadcastReceiver {
  @Override
  public void onReceive (Context context, Intent intent) {
    final String action = intent.getAction();
    Log.i("onReceive LiveLocation action:%s", action);
    if (StringUtils.isEmpty(action)) {
      return;
    }
    switch (action) {
      case Intents.ACTION_LOCATION_STOP: {
        TdlibManager.instance().liveLocation().finishBroadcast();
        break;
      }
    }
  }
}
