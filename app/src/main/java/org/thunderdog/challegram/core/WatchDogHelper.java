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
 * File created on 21/05/2015 at 18:53
 */
package org.thunderdog.challegram.core;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.os.Build;

import org.thunderdog.challegram.player.TGPlayerController;
import org.thunderdog.challegram.telegram.TdlibManager;

public class WatchDogHelper extends BroadcastReceiver {
  private final WatchDog watchDog;

  public WatchDogHelper (WatchDog watchDog) {
    this.watchDog = watchDog;
  }

  @Override
  public void onReceive (Context context, final Intent intent) {

    final String action = intent.getAction();

    if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(action)) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        TdlibManager.instance().player().setPauseReason(TGPlayerController.PAUSE_REASON_NOISY, true);
      } else {
        TdlibManager.instance().player().pauseWithReason(TGPlayerController.PAUSE_REASON_NOISY);
      }
      return;
    }

    if (ConnectivityManager.CONNECTIVITY_ACTION.equals(action)) {
      watchDog.letsHelpDoge();
      return;
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      if (android.media.AudioManager.ACTION_HEADSET_PLUG.equals(action)) {
        if (intent.getIntExtra("state", 0) == 1) {
          TdlibManager.instance().player().setPauseReason(TGPlayerController.PAUSE_REASON_NOISY, false);
        }
        return;
      }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      if (ConnectivityManager.ACTION_RESTRICT_BACKGROUND_CHANGED.equals(action)) {
        watchDog.letsHelpDoge();
        return;
      }
    }

    watchDog.letsHelpDoge();
  }
}
