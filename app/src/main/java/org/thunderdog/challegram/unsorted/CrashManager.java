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
 * File created on 17/08/2015 at 06:26
 */
package org.thunderdog.challegram.unsorted;

import android.annotation.SuppressLint;
import android.os.Looper;
import android.os.Process;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Lang;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import me.vkryl.core.StringUtils;
import me.vkryl.td.Td;

public class CrashManager {
  private static CrashManager instance;

  public static CrashManager instance () {
    if (instance == null) {
      instance = new CrashManager();
    }
    return instance;
  }

  private final Thread.UncaughtExceptionHandler crashHandler;
  private Thread.UncaughtExceptionHandler defaultHandler;

  private CrashManager () {
    crashHandler = this::onCrash;
  }

  public void register () {
    if (defaultHandler == null) {
      defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
    }
    Thread.setDefaultUncaughtExceptionHandler(crashHandler);
  }

  public void crash () {
    Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
    Looper looper = Looper.myLooper();
    Thread.UncaughtExceptionHandler currentHandler = looper != null ? looper.getThread().getUncaughtExceptionHandler() : null;
    Log.v("defaultHandler: %s, current: %s", defaultHandler, currentHandler);
    throw new RuntimeException("test");
  }

  private static String getFileName (long crashId) {
    return "crash." +
      BuildConfig.ORIGINAL_VERSION_NAME +
      "." +
      BuildConfig.COMMIT +
      (crashId != 0 ? "." + crashId : "") +
      ".log";
  }

  private File getNewFile () {
    File logsDir = Log.getLogDir();
    if (logsDir == null) {
      return null;
    }
    int index = 0;
    File crashFile;
    do {
      crashFile = new File(logsDir, getFileName(++index));
    } while (crashFile.exists());
    return crashFile;
  }

  @SuppressLint ("CommitPrefEdits")
  private void onCrash (@NonNull Thread thread, @NonNull Throwable ex) {
    processCrash(null, thread, ex);
  }

  private final AtomicBoolean isCrashing = new AtomicBoolean();

  private static String buildCrash (@Nullable String description, @NonNull Thread thread, @NonNull Throwable ex) {
    StringBuilder crash = new StringBuilder();
    crash.append(U.getUsefulMetadata(null));
    crash.append("\n\nCrashed on: ");
    crash.append(Lang.dateYearShortTime(System.currentTimeMillis(), TimeUnit.MILLISECONDS));
    if (!StringUtils.isEmpty(description)) {
      crash.append("\nCrash comment: ").append(description);
    }
    crash.append("\nCrash on: ").append(thread.getClass().getSimpleName()).append(" ").append(thread.getName());
    crash.append("\n\n");
    crash.append(Log.toString(ex));
    return crash.toString();
  }

  @SuppressLint("CommitPrefEdits")
  private void processCrash (String description, @NonNull Thread thread, @NonNull Throwable ex) {
    if (isCrashing.getAndSet(true)) {
      return;
    }
    ex.printStackTrace();
    Log.setRuntimeFlag(Log.RUNTIME_NOT_ASYNC, true);
    try {
      final String result = buildCrash(description, thread, ex);
      final File outputFile = getNewFile();
      if (outputFile != null) {
        try (FileOutputStream stream = new FileOutputStream(outputFile)) {
          stream.write(result.getBytes(StringUtils.UTF_8));
        } catch (IOException e) {
          Log.e(Log.TAG_CRASH, "Unable to save crash file", e);
        }
      } else {
        Log.e(Log.TAG_CRASH, "Unable to find crash file");
      }
      Log.e(Log.TAG_CRASH, "Application crashed", ex);
    } catch (Throwable t) {
      try {
        Log.e(Log.TAG_CRASH, "Unable to build crash", t);
      } catch (Throwable wellWeHaveJustReallyFuckedUp) {
        // Oh Dear!
      }
    }
    Log.setRuntimeFlag(Log.RUNTIME_NOT_ASYNC, false);
    isCrashing.set(false);
    if (defaultHandler != null) {
      Thread.setDefaultUncaughtExceptionHandler(defaultHandler);
      defaultHandler.uncaughtException(thread, Td.normalizeError(ex));
      Thread.setDefaultUncaughtExceptionHandler(crashHandler);
    } else {
      Process.killProcess(Process.myPid());
      System.exit(10);
    }
  }

  public void crash (@Nullable String description, @NonNull Throwable ex) {
    processCrash(description, Thread.currentThread(), ex);
  }

  public void test () {
    throw new RuntimeException("This is a crash test");
  }
}
