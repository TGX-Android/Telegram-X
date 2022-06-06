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
 * File created on 11/04/2017
 */
package org.thunderdog.challegram.unsorted;

import android.os.Build;
import android.os.SystemClock;

import org.drinkmore.Tracer;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.N;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.telegram.TdlibManager;

import java.util.concurrent.atomic.AtomicBoolean;

public class AppState {
  private static final AtomicBoolean isInitialized = new AtomicBoolean(false);

  private static void onInitializationAlreadyCompleted () {
    TdlibManager.instance().watchDog().letsHelpDoge();
  }

  public static synchronized void initApplication () {
    // check if already initialized

    if (isInitialized.get()) {
      onInitializationAlreadyCompleted();
      return;
    }

    // initialization

    if (Config.USE_CUSTOM_CRASH_MANAGER) {
      CrashManager.instance().register();
    }

    long startStep = SystemClock.uptimeMillis();

    N.init();
    Settings.instance();

    boolean needMeasure = Log.needMeasureLaunchSpeed();

    try {
      if (BuildConfig.DEBUG)
        Test.executeBeforeAppInit();
      if (needMeasure) {
        Log.i("==== INITIALIZATION STARTED IN %dMS ===\nManufacturer: %s, Product: %s", SystemClock.uptimeMillis() - startStep, Build.MANUFACTURER, Build.PRODUCT);
        startStep = SystemClock.uptimeMillis();
      }
      TdlibManager.instance();
      if (needMeasure) {
        Log.i("==== INITIALIZATION FINISHED IN %dms ===", SystemClock.uptimeMillis() - startStep);
      }
    } catch (Throwable t) {
      Tracer.onLaunchError(t);
      Log.e("App initialization failed", t);
      return;
    }

    isInitialized.set(true);

    // after

    if (BuildConfig.DEBUG) {
      Test.executeAfterAppInit();
    }
  }

  public static void ensureReady () {
    if (!isInitialized.get()) {
      try {
        throw new AssertionError("Trying to do something before application initialization. Log: \n" + NLoader.instance().collectLog());
      } catch (AssertionError e) {
        Tracer.onLaunchError(e);
      }
    }
  }
}
