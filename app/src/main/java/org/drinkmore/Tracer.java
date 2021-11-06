package org.drinkmore;

import androidx.annotation.IntDef;
import androidx.annotation.Keep;
import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.N;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.unsorted.Settings;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Locale;

/**
 * Date: 12/03/2019
 * Author: default
 */
@SuppressWarnings("unused")
public class Tracer {
  private static final String PREFIX = "Client fatal error (%d): [ 0][t 1][%d][Tracer.cpp:15][!Td]\t%s\n";

  private static String format (String message) {
    return String.format(Locale.US, PREFIX, Client.getClientCount(), System.currentTimeMillis(), message);
  }

  private static class ClientException_1460 extends RuntimeException {
    private ClientException_1460 (String message) {
      super(format(message));
    }
  }

  private static class DatabaseError extends ClientException_1460 {
    private DatabaseError (String message) {
      super(message + ", versionCode: " + BuildConfig.VERSION_CODE);
    }
  }

  private static class TdlibLaunchError extends ClientException_1460 {
    private TdlibLaunchError (String message) {
      super(message);
    }
  }

  private static class TdlibLostPromiseError extends ClientException_1460 {
    private TdlibLostPromiseError (String message) {
      super(message);
    }
  }

  private static void throwError (Throwable throwable) {
    StackTraceElement[] elements = throwable.getStackTrace();
    if (elements != null) {
      StackTraceElement[] newElements = new StackTraceElement[elements.length + 1];
      System.arraycopy(elements, 0, newElements, 1, elements.length);
      newElements[0] = new StackTraceElement("org.drinkmore.Tracer", "throwError", "Tracer.java", 49);
      throwable.setStackTrace(newElements);
    }
    if (throwable instanceof ClientException_1460)
      throw (ClientException_1460) throwable;
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

  private static void onFatalError (Throwable throwable, @Cause int cause) {
    final class ThrowError implements Runnable {
      private final Throwable error;

      private ThrowError(Throwable error) {
        this.error = error;
      }

      @Override
      public void run() {
        switch (cause) {
          case Cause.FATAL_ERROR:
            throwAssertionError(error);
            break;
          case Cause.DATABASE_ERROR:
            throw new DatabaseError(error.getClass().getSimpleName() + ": " + error.getMessage());
          case Cause.LAUNCH_ERROR:
            throwLaunchError(error);
            break;
          case Cause.TDLIB_LAUNCH_ERROR:
            throw new TdlibLaunchError(error.getMessage());
          case Cause.TDLIB_HANDLER_ERROR:
            throwTdlibHandlerError(error);
            break;
          case Cause.TDLIB_LOST_PROMISE_ERROR:
            throw new TdlibLostPromiseError(error.getMessage());
          case Cause.NOTIFICATION_ERROR:
            throwNotificationError(error);
            break;
          case Cause.UI_ERROR:
            throwUiError(error);
            break;
          case Cause.OTHER_ERROR:
            throwError(error);
            break;

          case Cause.TEST_INDIRECT:
            throwTestError(error);
            break;
          case Cause.TEST_DIRECT:
            throwError(error);
            break;
        }
      }

      // message only

      private void throwTestError (Throwable error) {
        throw new ClientException_1460(error.getMessage());
      }

      private void throwAssertionError (Throwable error) {
        throw new ClientException_1460(error.getMessage());
      }

      // Full trace

      private void throwLaunchError (Throwable error) {
        throwError(error);
      }

      private void throwTdlibHandlerError (Throwable error) {
        throwError(error);
      }

      private void throwNotificationError (Throwable error) {
        throwError(error);
      }

      private void throwUiError (Throwable error) {
        throwError(error);
      }
    }

    new Thread(new ThrowError(throwable), "Application fatal error thread").start();
    while (true) {
      try {
        Thread.sleep(1000 /* milliseconds */);
      } catch (InterruptedException ignore) {
        Thread.currentThread().interrupt();
      }
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

  public static void onTdlibFatalError (int accountId, @Nullable Class<? extends TdApi.Function> function, TdApi.Error error, @Nullable StackTraceElement[] stackTrace) {
    String message = (function != null ? function.getSimpleName() : "unknown") + ": " + TD.toErrorString(error);
    Settings.instance().storeCrash(accountId, message, Settings.CRASH_FLAG_SOURCE_TDLIB_PARAMETERS);
    if (stackTrace != null) {
      Throwable t = new TdlibLaunchError(message);
      t.setStackTrace(stackTrace);
      onFatalError(t, Cause.TDLIB_HANDLER_ERROR);
    } else {
      onFatalError(new TdlibLaunchError(message), Cause.TDLIB_HANDLER_ERROR);
    }
  }

  /*public static void onTonFatalError (@Nullable Class<? extends TonApi.Function> function, TonApi.Error error, @Nullable StackTraceElement[] stackTrace) {
    String message = (function != null ? function.getSimpleName() : "unknown") + ": " + TD.makeErrorString(error);
    Settings.instance().storeCrash(message, Settings.CRASH_FLAG_SOURCE_TON_PARAMETERS);
    if (stackTrace != null) {
      Throwable t = new TdlibLaunchError(message);
      t.setStackTrace(stackTrace);
      onFatalError(t, Cause.TDLIB_HANDLER_ERROR);
    } else {
      onFatalError(new TdlibLaunchError(message), Cause.TDLIB_HANDLER_ERROR);
    }
  }*/

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