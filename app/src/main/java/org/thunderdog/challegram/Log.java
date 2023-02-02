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
 * File created on 07/09/2017
 */
package org.thunderdog.challegram;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Message;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.otaliastudios.transcoder.internal.utils.Logger;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.BaseThread;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import me.vkryl.android.SdkVersion;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.RunnableData;
import me.vkryl.core.reference.ReferenceList;
import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.util.Blob;

public class Log {
  public static final String LOG_TAG = "tgx";

  public static boolean needMeasureLaunchSpeed () {
    return Log.checkLogLevel(Log.LEVEL_VERBOSE);
  }

  // Log files grabbing

  /**
   * Returns Log storage directory.
   * In case it doesn't exist, method will try to create it
   *
   * @return Log storage directory. @null in case of error
   */
  public static @Nullable File getLogDir () {
    File file = new File(UI.getAppContext().getFilesDir(), "logs");
    if (!file.exists() && !file.mkdir()) {
      android.util.Log.e(LOG_TAG, "Couldn't open logs directory: " + file.getAbsolutePath());
      return null;
    }
    return file;
  }

  /**
   * Grab a list of available log files.
   *
   * @return List of log files. @null in case of error
   */
  public static @Nullable
  LogFiles getLogFiles () {
    synchronized (Log.class) {
      return getLogFilesImpl();
    }
  }

  /**
   * Grab a list of available log files asynchronously.
   *
   * Returns: List of log files. @null in case of error
   */
  public static void getLogFiles (@NonNull RunnableData<LogFiles> callback) {
    preparePool();
    if (pool != null) {
      pool.sendMessage(Message.obtain(pool.getHandler(), ACTION_GET_LOG_FILES, callback), 0);
    } else {
      synchronized (Log.class) {
        callback.runWithData(getLogFilesImpl());
      }
    }
  }

  private static @Nullable LogFiles getLogFilesImpl () {
    File logDir = getLogDir();
    if (logDir != null) {
      File[] filesRaw = logDir.listFiles();
      if (filesRaw != null) {
        Arrays.sort(filesRaw, (o1, o2) -> {
          long t1 = o1.lastModified();
          long t2 = o2.lastModified();
          if (t1 == t2) {
            return 0;
          }
          return t1 < t2 ? 1 : -1;
        });
        List<File> files = new ArrayList<>(filesRaw.length);
        long logsCount = 0;
        long crashesCount = 0;
        for (File file : filesRaw) {
          String name = file.getName();
          if (!name.startsWith(TdlibManager.LOG_FILE)) {
            files.add(file);
            if (name.startsWith(Log.CRASH_PREFIX)) {
              crashesCount++;
            } else {
              logsCount++;
            }
          }
        }
        return new LogFiles(files, logsCount, crashesCount, U.getTotalUsedSpace(files));
      }
    }
    return null;
  }

  public static class LogFiles {
    public final List<File> files;
    public long logsCount;
    public long crashesCount;
    public long totalSize;

    public LogFiles (List<File> files, long logsCount, long crashesCount, long totalSize) {
      this.files = files;
      this.logsCount = logsCount;
      this.crashesCount = crashesCount;
      this.totalSize = totalSize;
    }

    public boolean isEmpty () {
      return files.isEmpty();
    }
  }

  // Log Capture

  private static boolean isCapturing;

  /**
   * Starts a log capture.
   *
   * @return true if capture has been started
   *         false on error
   */
  public static boolean startCapture () {
    synchronized (Log.class) {
      if (!isCapturing) {
        isCapturing = startCaptureImpl();
      }
      return isCapturing;
    }
  }
  private static native boolean startCaptureImpl ();

  /**
   * Ends a log capture.
   *
   * @return path to a log capture file
   *         null if nothing has been captured
   */
  public static @Nullable String endCapture () {
    synchronized (Log.class) {
      if (isCapturing) {
        isCapturing = false;
        resetCapturedCounters();
        String result = endCaptureImpl();
        return result.isEmpty() ? null : result;
      } else {
        return null;
      }
    }
  }
  private static native String endCaptureImpl ();

  public static boolean isCapturing () {
    synchronized (Log.class) {
      return isCapturing;
    }
  }

  private static long capturedWarnings, capturedErrors;
  private static void resetCapturedCounters () {
    capturedWarnings = capturedErrors = 0;
  }

  public static long getCapturedWarnings () {
    return capturedWarnings;
  }

  public static long getCapturedErrors () {
    return capturedErrors;
  }

  // Common

  public static void deleteAll (LogFiles list, @Nullable RunnableData<LogFiles> after, @Nullable RunnableData<LogFiles> onProgress) {
    close();
    synchronized (Log.class) {
      if ((runtimeFlags & RUNTIME_NOT_ASYNC) == 0 && preparePool() != null && pool != null) {
        pool.sendMessage(Message.obtain(pool.getHandler(), ACTION_DELETE_ALL, new Object[] {list, after, onProgress}), 0);
      } else {
        deleteAllImpl(list, after, onProgress);
      }
    }
  }

  private static void deleteAllImpl (LogFiles list, @Nullable RunnableData<LogFiles> after, @Nullable RunnableData<LogFiles> onProgress) {
    final int count = list.files.size();
    for (int i = count - 1; i >= 0; i--) {
      File file = list.files.get(i);
      long size = file.length();
      boolean isCrash = file.getName().startsWith(Log.CRASH_PREFIX);
      if (file.delete()) {
        if (isCrash) {
          list.crashesCount--;
        } else {
          list.logsCount--;
        }
        list.totalSize -= size;
        list.files.remove(i);
        if (onProgress != null) {
          onProgress.runWithData(list);
        }
      }
    }
    if (after != null) {
      after.runWithData(getLogFilesImpl());
    }
  }

  public static boolean deleteFile (File file) {
    if (isCapturing()) {
      return false;
    }
    close();
    boolean res;
    synchronized (Log.class) {
      res = file.delete();
    }
    if (res) {
      notifyLogFilesAltered();
    }
    return res;
  }

  public static void close () {
    boolean needCloseCapture = false;
    synchronized (Log.class) {
      if (isCapturing) {
        needCloseCapture = true;
      } else {
        if ((runtimeFlags & RUNTIME_NOT_ASYNC) == 0 && pool != null) {
          pool.sendMessage(Message.obtain(pool.getHandler(), ACTION_LOG_CLOSE), 0);
        } else {
          closeLogImpl();
        }
      }
    }
    if (needCloseCapture) {
      endCapture();
    }
  }
  private static native void closeLogImpl ();

  // Output listeners

  public interface OutputListener {
    void onLogOutput (int tag, int level, String message, @Nullable Throwable t);
    void onLogFilesAltered ();
  }

  private static ReferenceList<OutputListener> outputListeners;

  public static void addOutputListener (OutputListener listener) {
    if (outputListeners == null) {
      synchronized (Log.class) {
        if (outputListeners == null) {
          outputListeners = new ReferenceList<>(true);
        }
      }
    }
    outputListeners.add(listener);
  }

  public static void removeOutputListener (OutputListener listener) {
    if (hasListeners()) {
      outputListeners.remove(listener);
    }
  }

  private static void notifyOutputListeners (int tag, int level, String message, @Nullable Throwable t) {
    if (hasListeners()) {
      for (OutputListener listener : outputListeners) {
        listener.onLogOutput(tag, level, message, t);
      }
    }
  }

  private static boolean hasListeners () {
    if (outputListeners == null) {
      synchronized (Log.class) {
        if (outputListeners == null) {
          return false;
        }
      }
    }
    return true;
  }

  private static void notifyLogFilesAltered () {
    if (hasListeners()) {
      for (OutputListener listener : outputListeners) {
        listener.onLogFilesAltered();
      }
    }
  }

  // Implementation

  public static final String CRASH_PREFIX = "crash.";
  public static final String CALL_PREFIX = "call.";

  public static final int LEVEL_ASSERT = 0;
  public static final int LEVEL_ERROR = 1;
  public static final int LEVEL_WARNING = 2;
  public static final int LEVEL_INFO = 3;
  public static final int LEVEL_DEBUG = 4;
  public static final int LEVEL_VERBOSE = 5;

  public static final int SETTING_ANDROID_LOG = 0x01;
  public static final int SETTING_DISABLE_FULLY = 0x02;

  public static final int RUNTIME_NOT_ASYNC = 0x01;

  private static final int ACTION_LOG_TO_FILE = 0;
  private static final int ACTION_LOG_CLOSE = 1;
  private static final int ACTION_GET_LOG_FILES = 2;
  private static final int ACTION_DELETE_ALL = 3;

  // Don't forget to duplicate new values in log.h
  public static final int TAG_NETWORK_STATE = 1;
  public static final int TAG_VOIP = 1 << 1;
  public static final int TAG_FCM = 1 << 2;
  public static final int TAG_MESSAGES_LOADER = 1 << 3;
  public static final int TAG_INTRO = 1 << 4;
  public static final int TAG_IMAGE_LOADER = 1 << 5;
  public static final int TAG_SPEED_TEXT = 1 << 6;
  public static final int TAG_YOUTUBE = 1 << 7;
  public static final int TAG_CRASH = 1 << 8;
  public static final int TAG_GIF_LOADER = 1 << 9;
  public static final int TAG_CAMERA = 1 << 10;
  public static final int TAG_VOICE = 1 << 11;
  public static final int TAG_EMOJI = 1 << 12;
  public static final int TAG_LUX = 1 << 13;
  public static final int TAG_VIDEO = 1 << 14;
  public static final int TAG_ROUND = 1 << 15;
  public static final int TAG_COMPRESS = 1 << 16;
  public static final int TAG_CONTACT = 1 << 17;
  public static final int TAG_PAINT = 1 << 18;
  public static final int TAG_PLAYER = 1 << 19;
  public static final int TAG_NDK = 1 << 20;
  public static final int TAG_ACCOUNTS = 1 << 21;

  public static final int TAG_TDLIB_FILES = 1 << 29;
  public static final int TAG_TDLIB_OPTIONS = 1 << 30;

  public static final int[] TAGS = {
    TAG_FCM,
    TAG_ACCOUNTS,
    TAG_PLAYER,
    TAG_NDK,
    TAG_CONTACT,
    TAG_VIDEO,
    TAG_LUX,
    TAG_COMPRESS,
    TAG_PAINT,
    TAG_VOICE,
    TAG_ROUND,
    TAG_INTRO,
    TAG_NETWORK_STATE,
    TAG_VOIP,
    TAG_MESSAGES_LOADER,
    TAG_GIF_LOADER,
    TAG_IMAGE_LOADER,
    TAG_SPEED_TEXT,
    TAG_YOUTUBE,
    TAG_CAMERA,
    TAG_EMOJI,

    TAG_TDLIB_FILES,
    TAG_TDLIB_OPTIONS
  };

  private static boolean loaded;
  private static int level, settings, runtimeFlags;
  private static long tags;

  private static @Nullable BaseThread pool;

  // == Settings ==

  private static void setThirdPartyLogLevels (int level) {
    switch (level) {
      case LEVEL_WARNING:
        Logger.setLogLevel(Logger.LEVEL_WARNING);
        break;
      case LEVEL_INFO:
      case LEVEL_DEBUG:
        Logger.setLogLevel(Logger.LEVEL_INFO);
        break;
      case LEVEL_VERBOSE:
        Logger.setLogLevel(Logger.LEVEL_VERBOSE);
        break;
      case LEVEL_ASSERT:
      case LEVEL_ERROR:
      default:
        Logger.setLogLevel(Logger.LEVEL_ERROR);
        break;
    }
  }

  private static void load () {
    if (!loaded) {
      N.init();
      load(Settings.instance().pmc());
    }
  }

  public static void load (SharedPreferences prefs) {
    if (!loaded) {
      N.init();
      if (prefs == null) {
        settings = Log.SETTING_ANDROID_LOG;
        level = Log.LEVEL_VERBOSE;
        tags = TAG_NDK | TAG_CRASH;
      } else {
        settings = prefs.getInt(Settings.KEY_LOG_SETTINGS, 0);
        level = prefs.getInt(Settings.KEY_LOG_LEVEL, Log.LEVEL_ASSERT);
        long defaultTags = Log.TAG_CRASH | Log.TAG_FCM | Log.TAG_ACCOUNTS;
        if (Config.DEBUG_GALAXY_TAB_2) {
          defaultTags |= Log.TAG_INTRO;
        }
        tags = prefs.getLong(Settings.KEY_LOG_TAGS, defaultTags);
      }

      setLogLevelImpl(level);
      setLogTagsImpl(tags);
      setThirdPartyLogLevels(level);

      loaded = true;
    }
  }

  public static int getLogLevel () {
    if (!loaded) {
      load();
    }
    return level;
  }

  public static void setLogLevel (int level) {
    if (getLogLevel() != level) {
      Log.level = level;
      setLogLevelImpl(level);
      setThirdPartyLogLevels(level);
      Settings.instance().putInt(Settings.KEY_LOG_LEVEL, level);
    }
  }
  private static native void setLogLevelImpl (int level);

  public static boolean checkSetting (int setting) {
    if (!loaded) {
      load();
    }
    return (settings & setting) == setting;
  }
  public static void setSetting (int setting, boolean enabled) {
    if (enabled != checkSetting(setting)) {
      settings = BitwiseUtils.setFlag(settings, setting, enabled);
      Settings.instance().putInt(Settings.KEY_LOG_SETTINGS, setting);
    }
  }

  public static void setRuntimeFlag (int runtimeFlag, boolean enabled) {
    synchronized (Log.class) {
      if (enabled != ((runtimeFlags & runtimeFlag) == runtimeFlag)) {
        runtimeFlags = BitwiseUtils.setFlag(runtimeFlags, runtimeFlag, enabled);
      }
    }
  }

  /**
   * Checks whether specified tag logging is enabled.
   *
   * Generally, there's no need to call this method,
   * because this check will be anyway made later in log() call.
   *
   * However, for performance reasons, it's better to make
   * this check before all log levels <= LOG_LEVEL_WARNING,
   * to avoid GCs because of new Object[0] for varargs.
   *
   * Don't use this method for important notices with level <= LOG_LEVEL_WARNING,
   * because otherwise such messages will be ignored.
   *
   * @param tag Tag to check
   * @return true if specified log tag is enabled
   */
  public static boolean isEnabled (int tag) {
    if (!loaded) {
      load();
    }
    return (tags & tag) == tag;
  }
  public static void setEnabled (int tag, boolean enabled) {
    setEnabledTags(BitwiseUtils.setFlag(tags, tag, enabled));
  }
  public static void setEnabledTags (long tags) {
    if (Log.tags != tags) {
      Log.tags = tags;
      setLogTagsImpl(tags);
      Settings.instance().putLong(Settings.KEY_LOG_TAGS, tags);
    }
  }
  private static native void setLogTagsImpl (long flags);

  public static boolean checkLogLevel (int level) {
    return level <= getLogLevel();
  }

  public static boolean needLog (int tag, int level) {
    return isEnabled(tag) && checkLogLevel(level);
  }

  // Runtime checks

  private static boolean checkPermission (int logFlag, long logLevel) {
    if (!loaded) {
      load();
    }
    return (isCapturing || level >= logLevel) && (logFlag == 0 || (tags & logFlag) == logFlag || logLevel <= LEVEL_WARNING);
  }

  private static int getAndroidPriority (int level) {
    switch (level) {
      case LEVEL_ASSERT:
        return android.util.Log.ASSERT;
      case LEVEL_ERROR:
        return android.util.Log.ERROR;
      case LEVEL_WARNING:
        return android.util.Log.WARN;
      case LEVEL_INFO:
        return android.util.Log.INFO;
      case LEVEL_DEBUG:
        return android.util.Log.DEBUG;
      case LEVEL_VERBOSE:
        return android.util.Log.VERBOSE;
    }
    throw new AssertionError(level);
  }

  public static native String getLogTag (int tag);
  public static native String getLogTagDescription (int tag);

  private static @Nullable BaseThread preparePool () {
    if (pool == null) {
      synchronized (Log.class) {
        if (pool == null) {
          File logDir = getLogDir();
          if (logDir != null) {
            pool = new BaseThread("Log") {
              @Override
              protected void process (Message msg) {
                synchronized (Log.class) {
                  switch (msg.what) {
                    case ACTION_LOG_TO_FILE: {
                      logToFileImpl(msg.arg1, msg.arg2, (String) msg.obj);
                      break;
                    }
                    case ACTION_LOG_CLOSE: {
                      closeLogImpl();
                      break;
                    }
                    case ACTION_GET_LOG_FILES: {
                      //noinspection unchecked
                      ((RunnableData<LogFiles>) msg.obj).runWithData(getLogFilesImpl());
                      break;
                    }
                    case ACTION_DELETE_ALL: {
                      Object[] args = (Object[]) msg.obj;

                      //noinspection unchecked
                      deleteAllImpl((LogFiles) args[0], (RunnableData<LogFiles>) args[1], (RunnableData<LogFiles>) args[2]);

                      args[0] = null;
                      args[1] = null;
                      args[2] = null;
                      break;
                    }
                  }
                }
              }
            };
            String osArch = U.getCpuArchitecture();
            if (osArch == null) {
              osArch = "";
            }
            setInternalValues(
              // Log dir
              logDir.getAbsolutePath(),
              osArch,

              // Related to Application Build
              BuildConfig.VERSION_NAME,
              BuildConfig.ORIGINAL_VERSION_CODE,

              // Related to Android version
              Build.VERSION.SDK_INT,
              SdkVersion.getPrettyName(),

              // Related to device
              Build.MODEL,
              Build.BRAND,
              Build.DISPLAY,
              Build.PRODUCT,
              Build.MANUFACTURER,
              Build.FINGERPRINT,

              Screen.widestSide(),
              Screen.smallestSide(),
              Screen.density()
            );
          }
        }
      }
    }
    return pool;
  }

  private static void logToFile (int tag, int level, String msg, boolean async) {
    preparePool();
    if (pool != null && async) {
      pool.sendMessage(Message.obtain(pool.getHandler(), ACTION_LOG_TO_FILE, tag, level, msg), 0);
    } else {
      synchronized (Log.class) {
        logToFileImpl(tag, level, msg);
      }
    }
  }
  private static native void logToFileImpl (int tag, int level, String msg);

  private static native void setInternalValues (String logDir,
                                                String osArch,
                                                String appVersionSignature, int appVersionCode,
                                                int sdkVersion, String sdkVersionName,
                                                String deviceModel, String deviceBrand, String deviceDisplay, String deviceProduct, String deviceManufacturer,
                                                String deviceFingerprint,
                                                int screenWidth, int screenHeight, float density);

  private static native String getDeviceInformation ();

  public static String getDeviceInformationString () {
    return String.format(
      Locale.US,
      "App: %s\nSDK: %d (%s)\nManufacturer: %s\nModel: %s\nBrand: %s\nDisplay: %s\nProduct: %s\nFingerprint: %s\nScreen: %dx%d (%f)\n",
      BuildConfig.VERSION_NAME,

      Build.VERSION.SDK_INT,
      SdkVersion.getPrettyName(),

      Build.MANUFACTURER,
      Build.MODEL,
      Build.BRAND,
      Build.DISPLAY,
      Build.PRODUCT,
      Build.FINGERPRINT,

      Screen.widestSide(),
      Screen.smallestSide(),
      Screen.density()
    );
  }

  // Log

  public static void log (int tag, int level, @NonNull String fmt, @Nullable Throwable t, Object... args) {
    if (!loaded) {
      load();
    }
    boolean force = Config.USE_CRASHLYTICS && level <= LEVEL_ERROR;
    boolean hasPermission = checkPermission(tag, level);
    if (hasPermission || force) {
      final String sourceMessage = args.length != 0 ? String.format(Locale.US, fmt, args) : fmt;
      if ((settings & SETTING_ANDROID_LOG) != 0 || force) {
        final int priority = getAndroidPriority(level);
        final String androidTag = tag != 0 ? getLogTag(tag) : null;
        String androidMessage;
        if (androidTag != null) {
          androidMessage = "[" + androidTag + "] " + sourceMessage;
        } else {
          androidMessage = sourceMessage;
        }
        if (hasPermission) {
          if (t != null) {
            switch (priority) {
              case android.util.Log.ASSERT:
              case android.util.Log.ERROR:
                android.util.Log.e(LOG_TAG, androidMessage, t);
                break;
              case android.util.Log.WARN:
                android.util.Log.w(LOG_TAG, androidMessage, t);
                break;
              case android.util.Log.INFO:
                android.util.Log.i(LOG_TAG, androidMessage, t);
                break;
              case android.util.Log.DEBUG:
                android.util.Log.d(LOG_TAG, androidMessage, t);
                break;
              case android.util.Log.VERBOSE:
                android.util.Log.v(LOG_TAG, androidMessage, t);
                break;
            }
          } else {
            android.util.Log.println(priority, LOG_TAG, androidMessage);
          }
        }
        if (force) {
          logExternally(androidMessage, t);
        }
      }
      if (hasPermission && ((settings & SETTING_DISABLE_FULLY) == 0 || isCapturing)) {
        final String fileMessage;
        if (t != null) {
          StringBuilder b = new StringBuilder(sourceMessage);
          if (b.length() > 0) {
            b.append('\n');
          }
          toStringBuilder(t, 10, b);
          fileMessage = b.toString();
        } else {
          fileMessage = sourceMessage;
        }

        logToFile(tag, level, fileMessage, (runtimeFlags & RUNTIME_NOT_ASYNC) == 0);
        if (isCapturing) {
          switch (level) {
            case LEVEL_ERROR: {
              capturedErrors++;
              break;
            }
            case LEVEL_WARNING: {
              capturedWarnings++;
              break;
            }
          }
        }
      }
      notifyOutputListeners(tag, level, sourceMessage, t);
      if (level == LEVEL_ASSERT && !isCapturing) {
        throw new AssertionError(sourceMessage);
      }
    }
  }

  public static void initLibraries (Context activity) {
    if (Config.USE_CRASHLYTICS) {
      // io.fabric.sdk.android.Fabric.with(this, new com.crashlytics.android.Crashlytics());
    }
  }

  public static void logExternally (@NonNull String message, @Nullable Throwable t) {
    if (Config.USE_CRASHLYTICS) {
      /*com.crashlytics.android.Crashlytics.log(message);
      if (t != null) {
        com.crashlytics.android.Crashlytics.logException(t);
      }*/
    }
  }

  public static void log (int tag, int level, @NonNull String fmt, Object... args) {
    log(tag, level, fmt, null, args);
  }

  public static void log (int tag, @NonNull String fmt, Object... args) {
    log(0, tag, fmt, args);
  }

  public static void v (int tag, @NonNull String fmt, Object... args) {
    log(tag, LEVEL_VERBOSE, fmt, args);
  }

  public static void v (int tag, @NonNull String fmt, Throwable t, Object... args) {
    log(tag, LEVEL_VERBOSE, fmt, t, args);
  }

  public static void v (@NonNull String fmt, Object... args) {
    log(0, LEVEL_VERBOSE, fmt, args);
  }

  public static void v (@NonNull String fmt, Throwable t, Object... args) {
    log(0, LEVEL_VERBOSE, fmt, t, args);
  }
  public static void v (Throwable t) {
    log(0, LEVEL_VERBOSE, "", t);
  }

  public static void d (int tag, @NonNull String fmt, Object... args) {
    log(tag, LEVEL_DEBUG, fmt, args);
  }

  public static void d (int tag, @NonNull String fmt, Throwable t, Object... args) {
    log(tag, LEVEL_DEBUG, fmt, t, args);
  }

  public static void d (@NonNull String fmt, Object... args) {
    log(0, LEVEL_DEBUG, fmt, args);
  }

  public static void d (@NonNull String fmt, Throwable t, Object... args) {
    log(0, LEVEL_DEBUG, fmt, t, args);
  }

  public static void d (Throwable t) {
    log(0, LEVEL_DEBUG, "", t);
  }

  public static void i (int tag, @NonNull String fmt, Object... args) {
    log(tag, LEVEL_INFO, fmt, args);
  }

  public static void i (int tag, @NonNull String fmt, Throwable t, Object... args) {
    log(tag, LEVEL_INFO, fmt, t, args);
  }

  public static void i (@NonNull String fmt, Object... args) {
    log(0, LEVEL_INFO, fmt, args);
  }

  public static void i (@NonNull String fmt, Throwable t, Object... args) {
    log(0, LEVEL_INFO, fmt, t, args);
  }

  public static void i (Throwable t) {
    log(0, LEVEL_INFO, "", t);
  }

  public static void w (int tag, @NonNull String fmt, Object... args) {
    log(tag, LEVEL_WARNING, fmt, args);
  }

  public static void w (int tag, @NonNull String fmt, Throwable t, Object... args) {
    log(tag, LEVEL_WARNING, fmt, t, args);
  }

  public static void w (@NonNull String fmt, Object... args) {
    log(0, LEVEL_WARNING, fmt, args);
  }

  public static void w (@NonNull String fmt, Throwable t, Object... args) {
    log(0, LEVEL_WARNING, fmt, t, args);
  }

  public static void w (Throwable t) {
    log(0, LEVEL_WARNING, "", t);
  }

  public static void e (int tag, @NonNull String fmt, Object... args) {
    log(tag, LEVEL_ERROR, fmt, args);
  }

  public static void e (int tag, @NonNull String fmt, Throwable t, Object... args) {
    log(tag, LEVEL_ERROR, fmt, t, args);
  }

  public static void e (@NonNull String fmt, Throwable t, Object... args) {
    log(0, LEVEL_ERROR, fmt, t, args);
  }

  public static void e (Throwable t) {
    log(0, LEVEL_ERROR, "", t);
  }

  public static void e (int tag, Throwable t) {
    log(tag, LEVEL_ERROR, "", t);
  }

  public static void e (@NonNull String fmt, Object... args) {
    log(0, LEVEL_ERROR, fmt, args);
  }

  public static void critical (@NonNull String fmt, Object... args) {
    log(0, LEVEL_ERROR, fmt, args);
  }

  public static void critical (int tag, @NonNull String fmt, Object... args) {
    log(tag, LEVEL_ERROR, fmt, args);
  }

  public static void critical (int tag, @NonNull String fmt, Throwable t, Object... args) {
    log(tag, LEVEL_ERROR, fmt, t, args);
  }

  public static void critical (@NonNull String fmt, Throwable t, Object... args) {
    log(0, LEVEL_ERROR, fmt, t, args);
  }

  public static void critical (Throwable t) {
    log(0, LEVEL_ERROR, "", t);
  }

  public static void a (int tag, @NonNull String fmt, Object... args) {
    log(tag, LEVEL_ASSERT, fmt, args);
  }

  public static void a (int tag, @NonNull String fmt, Throwable t, Object... args) {
    log(tag, LEVEL_ASSERT, fmt, t, args);
  }

  public static void a (@NonNull String fmt, Object... args) {
    log(0, LEVEL_ASSERT, fmt, args);
  }

  public static void unexpectedTdlibResponse (TdApi.Object response, @SuppressWarnings("rawtypes") Class<? extends TdApi.Function> function, Class<?>... objects) {
    StringBuilder b = new StringBuilder("Unexpected TDLib response");
    if (function != null) {
      b.append(" for ");
      b.append(function.getName());
    }
    b.append(". Expected: ");
    boolean first = true;
    for (Class<?> object : objects) {
      if (first) {
        first = false;
      } else {
        b.append(", ");
      }
      b.append(object.getName());
    }
    b.append(" but received: ");
    b.append(response != null ? response : "null");
    String message = b.toString();
    UI.showToast(message, Toast.LENGTH_LONG);
    Log.a("%s", message);
  }

  public static void fixme () {
    throw new AssertionError("FIXME");
  }

  public static void bug (String msg, Object... args) {
    e(msg, generateException(3), args);
  }

  public static RuntimeException generateSingleLineException () {
    return generateException(1);
  }

  public static RuntimeException generateSingleLineException (int skipCount) {
    try {
      throw new RuntimeException();
    } catch (RuntimeException e) {
      e.setStackTrace(new StackTraceElement[] {
        e.getStackTrace()[skipCount]
      });
      return e;
    }
  }

  public static RuntimeException generateException () {
    return generateException(1);
  }

  public static RuntimeException generateException (int skipCount) {
    try {
      throw new RuntimeException();
    } catch (RuntimeException e) {
      StackTraceElement[] elements = e.getStackTrace();
      StackTraceElement[] output = new StackTraceElement[elements.length - skipCount];
      System.arraycopy(elements, 0, output, 0, output.length);
      e.setStackTrace(output);
      return e;
    }
  }

  public static String toString (Throwable t) {
    StringBuilder b = new StringBuilder();
    toStringBuilder(t, 10, b);
    return b.toString();
  }

  public static String toString (Throwable t, int limitCauseNum) {
    StringBuilder b = new StringBuilder();
    toStringBuilder(t, limitCauseNum, b);
    return b.toString();
  }

  public static void toStringBuilder (Throwable t, int limitCauseNum, StringBuilder b) {
    toStringBuilder(t, limitCauseNum, b, 0);
  }

  private static void toStringBuilder (Throwable t, int limitCauseNum, StringBuilder b, int causeNo) {
    if (causeNo != 0) {
      b.append('\n');
    }
    b.append("=== ");
    if (causeNo != 0) {
      b.append("Cause #");
      b.append(causeNo);
    } else {
      b.append("Stack Trace Dump");
    }
    b.append(" ===\n");
    String message = t.getMessage();
    if (!StringUtils.isEmpty(message)) {
      b.append(message);
      b.append('\n');
    }

    b.append(getStackTrace(t));
    Throwable cause = t.getCause();
    if (cause != null && causeNo + 1 < limitCauseNum) {
      if (b.length() > 0) {
        toStringBuilder(cause, limitCauseNum, b, causeNo + 1);
      }
    }
  }

  public static String getStackTrace (Throwable t) {
    try (StringWriter sw = new StringWriter(); PrintWriter pw = new PrintWriter(sw)) {
      t.printStackTrace(pw);
      return sw.toString();
    } catch (IOException ignored) {
      return null;
    }
  }

  public static int blobSize (@NonNull Throwable t) {
    return blobSize(t, true);
  }

  private static int blobSize (@NonNull Throwable t, boolean isRoot) {
    int count = Blob.sizeOf(t.getClass().getName(), true) +
           1 + (Blob.sizeOf(t.getMessage(), false)) +
           1 + (Blob.sizeOf(StringUtils.equalsOrBothEmpty(t.getMessage(), t.getLocalizedMessage()) ? null : t.getLocalizedMessage(), false)) +
           4;
    StackTraceElement[] stackTrace = t.getStackTrace();
    for (StackTraceElement element : stackTrace) {
      count += 1 + Blob.sizeOf(element.getClassName(), false) + 1 + Blob.sizeOf(element.getMethodName(), false) + 1 + Blob.sizeOf(element.getFileName(), false) + 4;
    }
    if (isRoot) {
      int limit = BLOB_CAUSE_LIMIT;
      Throwable cause = t;
      do {
        Throwable newCause = cause.getCause();
        cause = newCause != cause ? newCause : null;
        if (cause != null && --limit == 0) {
          while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
          }
        }
        count++;
        if (cause != null) {
          count += blobSize(cause, false);
        } else {
          break;
        }
      } while (limit > 0);
    }
    return count;
  }

  public static void toBlob (@NonNull Throwable t, @NonNull Blob blob) {
    toBlob(t, blob, true);
  }

  private static final int BLOB_CAUSE_LIMIT = 5;

  private static void toBlob (@NonNull Throwable t, @NonNull Blob blob, boolean isRoot) {
    blob.writeString(t.getClass().getName());

    String message = t.getMessage();
    boolean hasMessage = !StringUtils.isEmpty(message);
    blob.writeBoolean(hasMessage);
    if (hasMessage) {
      blob.writeString(message);
    }

    String localizedMessage = t.getLocalizedMessage();
    boolean hasLocalizedMessage = !StringUtils.isEmpty(localizedMessage) && !localizedMessage.equals(message);
    blob.writeBoolean(hasLocalizedMessage);
    if (hasLocalizedMessage) {
      blob.writeString(localizedMessage);
    }

    StackTraceElement[] stackTrace = t.getStackTrace();
    blob.writeInt(stackTrace.length);
    if (stackTrace.length > 0) {
      for (StackTraceElement element : stackTrace) {
        String declaringClass = element.getClassName();
        boolean hasDeclaringClass = !StringUtils.isEmpty(declaringClass);

        String methodName = element.getMethodName();
        boolean hasMethodName = !StringUtils.isEmpty(methodName);

        String fileName = element.getFileName();
        boolean hasFileName = !StringUtils.isEmpty(fileName);

        int lineNumber = element.getLineNumber();

        blob.writeBoolean(hasDeclaringClass);
        if (hasDeclaringClass) {
          blob.writeString(declaringClass);
        }

        blob.writeBoolean(hasMethodName);
        if (hasMethodName) {
          blob.writeString(methodName);
        }

        blob.writeBoolean(hasFileName);
        if (hasFileName) {
          blob.writeString(fileName);
        }

        blob.writeInt(lineNumber);
      }
    }

    if (isRoot) {
      int limit = BLOB_CAUSE_LIMIT;
      Throwable cause = t;
      do {
        Throwable newCause = cause.getCause();
        cause = newCause != cause ? newCause : null;
        if (cause != null && --limit == 0) {
          while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
          }
        }
        blob.writeBoolean(cause != null);
        if (cause != null) {
          toBlob(cause, blob, false);
        } else {
          break;
        }
      } while (limit > 0);
    }
  }

  public static ThrowableInfo throwableFromBlob (@NonNull Blob blob) {
    String className = blob.readString();
    String message = blob.readBoolean() ? blob.readString() : null;
    String localizedMessage = blob.readBoolean() ? blob.readString() : null;

    int stackTraceSize = blob.readInt();
    StackTraceElement[] stackTrace = new StackTraceElement[stackTraceSize];
    for (int i = 0; i < stackTrace.length; i++) {
      String declaringClass = blob.readBoolean() ? blob.readString() : "";
      String methodName = blob.readBoolean() ? blob.readString() : "";
      String fileName = blob.readBoolean() ? blob.readString() : "";
      int lineNumber = blob.readInt();
      stackTrace[i] = new StackTraceElement(declaringClass, methodName, fileName, lineNumber);
    }

    ThrowableInfo cause = blob.readBoolean() ? throwableFromBlob(blob) : null;

    return new ThrowableInfo(className, message, localizedMessage, stackTrace, cause);
  }

  public static class ThrowableInfo {
    private final String className;
    private final String message, localizedMessage;
    private final StackTraceElement[] stackTrace;

    @Nullable
    private final ThrowableInfo cause;

    public ThrowableInfo (String className, String message, String localizedMessage, StackTraceElement[] stackTrace, @Nullable ThrowableInfo cause) {
      this.className = className;
      this.message = message;
      this.localizedMessage = localizedMessage;
      this.stackTrace = stackTrace;
      this.cause = cause;
    }

    @Nullable
    public ThrowableInfo getCause () {
      return cause;
    }

    public String getClassName () {
      return className;
    }

    public StackTraceElement[] getStackTrace () {
      return stackTrace;
    }

    @Nullable
    public String getMessage () {
      return message;
    }

    @Nullable
    public String getLocalizedMessage () {
      return !StringUtils.isEmpty(localizedMessage) ? localizedMessage : message;
    }

    private static void toStringBuilder (StringBuilder b, ThrowableInfo info, int causeNo) {
      if (causeNo > 0) {
        b.append("\n\n=== Cause #").append(causeNo).append("===\n");
      }
      b.append(info.className);
      if (!StringUtils.isEmpty(info.message) || !StringUtils.isEmpty(info.localizedMessage)) {
        b.append(": ");
        if (!StringUtils.isEmpty(info.message)) {
          b.append(info.message);
          if (!StringUtils.isEmpty(info.localizedMessage)) {
            b.append(" | ").append(info.localizedMessage);
          }
        } else if (!StringUtils.isEmpty(info.localizedMessage)) {
          b.append(info.localizedMessage);
        }
      }
      b.append("\nStack trace:\n");

      RuntimeException e = new RuntimeException();
      e.setStackTrace(info.stackTrace);
      b.append(Log.getStackTrace(e));
      if (info.cause != null) {
        toStringBuilder(b, info.cause, causeNo + 1);
      }
    }

    @Override
    @NonNull
    public String toString () {
      StringBuilder b = new StringBuilder();
      toStringBuilder(b, this, 0);
      return b.toString();
    }

    @Override
    public boolean equals (@Nullable Object obj) {
      if (obj == this)
        return true;
      if (!(obj instanceof ThrowableInfo))
        return false;
      ThrowableInfo b = (ThrowableInfo) obj;
      return StringUtils.equalsOrBothEmpty(className, b.className) &&
        StringUtils.equalsOrBothEmpty(message, b.message) &&
        StringUtils.equalsOrBothEmpty(localizedMessage, b.localizedMessage) &&
        Arrays.equals(stackTrace, b.stackTrace) &&
        ((cause == null && b.cause == null) || (cause != null && b.cause != null && cause.equals(b.cause)));
    }
  }
}
