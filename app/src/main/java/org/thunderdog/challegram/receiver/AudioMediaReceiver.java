package org.thunderdog.challegram.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.tool.Intents;

import me.vkryl.core.StringUtils;

/**
 * Date: 1/21/18
 * Author: default
 */

public class AudioMediaReceiver extends BroadcastReceiver {
  @Override
  public void onReceive (Context context, Intent intent) {
    final String action = intent.getAction();
    Log.i(Log.TAG_PLAYER, "onReceive action:%s", action);
    if (StringUtils.isEmpty(action)) {
      return;
    }
    switch (action) {
      case Intent.ACTION_MEDIA_BUTTON: {
        // TODO backward support for < 5.0
        break;
      }
      case Intents.ACTION_PLAYBACK_PAUSE:
      case Intents.ACTION_PLAYBACK_PLAY:
        TdlibManager.instance().player().playPauseCurrent();
        break;
      case Intents.ACTION_PLAYBACK_STOP:
        TdlibManager.instance().player().stopPlayback(true);
        break;
      case Intents.ACTION_PLAYBACK_SKIP_NEXT:
        TdlibManager.instance().player().skip(true);
        break;
      case Intents.ACTION_PLAYBACK_SKIP_PREVIOUS:
        TdlibManager.instance().player().skip(false);
        break;
    }
  }
}
