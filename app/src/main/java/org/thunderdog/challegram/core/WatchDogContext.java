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
 * File created on 27/02/2017
 */
package org.thunderdog.challegram.core;

import android.content.Context;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.Build;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.telegram.TdlibManager;

public class WatchDogContext {
  private static final IntentFilter INTENT_FILTER;

  static {
    INTENT_FILTER = new IntentFilter();
    INTENT_FILTER.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
    INTENT_FILTER.addAction(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY);
    // INTENT_FILTER.addAction(Airplane mode?);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      INTENT_FILTER.addAction(android.media.AudioManager.ACTION_HEADSET_PLUG);
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      INTENT_FILTER.addAction(ConnectivityManager.ACTION_RESTRICT_BACKGROUND_CHANGED);
    }
  }

  private final Context context;
  private final WatchDog watchDog;
  private final WatchDogHelper receiver;
  private final WatchDogObserver observer;
  private boolean registered;

  public WatchDogContext (Context context, TdlibManager manager) {
    this.context = context;
    this.watchDog = new WatchDog(manager);
    this.receiver = new WatchDogHelper(watchDog);
    this.observer = new WatchDogObserver();
  }

  public WatchDog get () {
    return watchDog;
  }

  public void register () {
    if (!registered) {
      registered = true;
      try {
        context.registerReceiver(receiver, INTENT_FILTER);
      } catch (Throwable t) {
        Log.w("Cannot register intent receiver", t);
      }
      observer.register();
    }
  }

  public void unregister () {
    if (registered) {
      registered = false;
      try {
        context.unregisterReceiver(receiver);
      } catch (Throwable t) {
        Log.w("Cannot unregister intent receiver", t);
      }
      observer.unregister();
    }
  }

}
