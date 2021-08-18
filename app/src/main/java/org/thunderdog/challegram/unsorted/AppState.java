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

/**
 * Date: 11/4/17
 * Author: default
 */

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
