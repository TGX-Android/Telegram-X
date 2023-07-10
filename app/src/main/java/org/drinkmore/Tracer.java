/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 12/03/2019
 */
package org.drinkmore;

import androidx.annotation.IntDef;
import androidx.annotation.Keep;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.N;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccount;
import org.thunderdog.challegram.util.Crash;
import org.thunderdog.challegram.unsorted.Settings;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Locale;

@SuppressWarnings("unused")
public class Tracer {
  static String format (String message) {
    return String.format(Locale.US, "Client fatal error: %s", message);
  }

  private static void throwErrorOnAnotherThread (Throwable throwable) {
    new Thread(() -> {
      throwError(throwable);
      System.exit(1);
    }).start();
    do {
      try {
        Thread.sleep(1000);
      } catch (InterruptedException ignored) { }
    } while (true);
  }

  private static void throwError (Throwable throwable) {
    Settings.instance().pmc().apply(); // Release any locks

    if (throwable instanceof ClientException)
      throw (ClientException) throwable;
    if (throwable instanceof RuntimeException) {
      throw (RuntimeException) throwable;
    }
    StackTraceElement[] elements = throwable.getStackTrace();
    StackTraceElement[] newElements = new StackTraceElement[elements.length + 1];
    System.arraycopy(elements, 0, newElements, 1, elements.length);
    newElements[0] = new StackTraceElement("org.drinkmore.Tracer", "throwError", "Tracer.java", 50);
    throwable.setStackTrace(newElements);
    RuntimeException exception = new RuntimeException(format(throwable.getClass().getSimpleName() + ": " + throwable.getMessage()), throwable.getCause());
    exception.setStackTrace(throwable.getStackTrace());
    throw exception;
  }

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    Cause.FATAL_ERROR,
    Cause.LAUNCH_ERROR,
    Cause.DATABASE_ERROR,
    Cause.TDLIB_HANDLER_ERROR,
    Cause.TDLIB_LAUNCH_ERROR,
    Cause.NOTIFICATION_ERROR,
    Cause.UI_ERROR,
    Cause.OTHER_ERROR,
    Cause.TDLIB_LOST_PROMISE_ERROR,

    Cause.TEST_INDIRECT,
    Cause.TEST_DIRECT,
  })
  public @interface Cause {
    int
      FATAL_ERROR = 0,
      LAUNCH_ERROR = 1,
      DATABASE_ERROR = 2,
      TDLIB_HANDLER_ERROR = 3,
      TDLIB_LAUNCH_ERROR = 4,
      NOTIFICATION_ERROR = 5,
      UI_ERROR = 6,
      OTHER_ERROR = 7,
      TDLIB_LOST_PROMISE_ERROR = 8,

      TEST_INDIRECT = 100,
      TEST_DIRECT = 101;
  }

  private static void onFatalError (Throwable error, @Cause int cause) {
    switch (cause) {
      case Cause.FATAL_ERROR:
        ClientException.throwAssertionError(error);
        break;
      case Cause.DATABASE_ERROR:
        throw new ClientException.DatabaseError(error.getClass().getSimpleName() + ": " + error.getMessage());
      case Cause.TDLIB_LAUNCH_ERROR:
        throw new ClientException.TdlibLaunchError(error.getMessage());
      case Cause.TDLIB_LOST_PROMISE_ERROR:
        throw new ClientException.TdlibLostPromiseError(error.getMessage());
      case Cause.LAUNCH_ERROR:
      case Cause.NOTIFICATION_ERROR:
      case Cause.UI_ERROR:
      case Cause.OTHER_ERROR:
      case Cause.TEST_DIRECT:
        throwError(error);
        break;
      case Cause.TEST_INDIRECT:
        ClientException.throwTestError(error);
        break;
      case Cause.TDLIB_HANDLER_ERROR:
        throwErrorOnAnotherThread(error);
        break;
    }
  }

  // Public API

  public static void onLaunchError (Throwable throwable) {
    onFatalError(throwable, Cause.LAUNCH_ERROR);
  }

  public static void onOtherError (Throwable throwable) {
    onFatalError(throwable, Cause.OTHER_ERROR);
  }

  public static void onDatabaseError (Throwable throwable) {
    onFatalError(throwable, Cause.DATABASE_ERROR);
  }

  public static void canvasFailure (IllegalArgumentException e, int saveCount) {
    Log.e("Restore count: %d", saveCount);
    IllegalArgumentException otherError = new IllegalArgumentException(e.getMessage() + ", saveCount = " + saveCount);
    otherError.setStackTrace(e.getStackTrace());
    onOtherError(otherError);
  }

  public static void onTdlibFatalError (@Nullable Tdlib tdlib, @Nullable Class<? extends TdApi.Function<?>> function, TdApi.Error error, @Nullable StackTraceElement[] stackTrace) {
    String message = (function != null ? function.getSimpleName() : "unknown") + ": " + TD.toErrorString(error);
    Settings.instance().storeCrash(new Crash.Builder()
      .message(message)
      .accountId(tdlib != null ? tdlib.accountId() : TdlibAccount.NO_ID)
      .flags(Crash.Flags.SOURCE_TDLIB_PARAMETERS)
    );
    if (stackTrace != null) {
      Throwable t = new ClientException.TdlibLaunchError(message);
      t.setStackTrace(stackTrace);
      onFatalError(t, Cause.TDLIB_HANDLER_ERROR);
    } else {
      onFatalError(new ClientException.TdlibLaunchError(message), Cause.TDLIB_HANDLER_ERROR);
    }
  }

  public static void onTdlibHandlerError (Throwable throwable) {
    onFatalError(throwable, Cause.TDLIB_HANDLER_ERROR);
  }

  public static void onTdlibLostPromiseError (String message) {
    onFatalError(new AssertionError(message), Cause.TDLIB_LOST_PROMISE_ERROR);
  }

  public static void onNotificationError (Throwable throwable) {
    onFatalError(throwable, Cause.NOTIFICATION_ERROR);
  }

  public static void onUiError (Throwable throwable) {
    onFatalError(throwable, Cause.UI_ERROR);
  }

  public static void onDrawBitmapError (Throwable t) {
    Log.e("Bug: cannot draw bitmap", t);
  }

  @Keep
  static void onFatalError (String message, int cause) {
    onFatalError(new AssertionError(message), cause);
  }

  // Experiments

  public static void test1 (String message) {
    // Indirect throw via new exception
    onFatalError(new AssertionError(message), Cause.TEST_INDIRECT);
  }

  public static void test2 (String message) {
    // Direct throw
    onFatalError(new AssertionError(message), Cause.TEST_DIRECT);
  }

  public static void test3 (String message) {
    // Indirect throw via new exception & calling from NDK
    N.onFatalError(message, Cause.TEST_INDIRECT);
  }

  public static void test4 (String message) {
    // Direct throw from NDK
    N.onFatalError(message, Cause.TEST_DIRECT);
  }

  public static void test5 (String message) {
    // Just throws AssertionError from NDK
    N.throwDirect(message);
  }
}