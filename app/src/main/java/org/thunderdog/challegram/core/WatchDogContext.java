package org.thunderdog.challegram.core;

import android.content.Context;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.Build;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.telegram.TdlibManager;

/**
 * Date: 27/02/2017
 * Author: default
 */

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
