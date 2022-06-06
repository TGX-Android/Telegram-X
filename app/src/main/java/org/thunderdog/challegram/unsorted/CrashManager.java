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
 * File created on 17/08/2015 at 06:26
 */
package org.thunderdog.challegram.unsorted;

import android.annotation.SuppressLint;
import android.os.Looper;
import android.os.Process;

import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.CrashLog;
import org.thunderdog.challegram.tool.UI;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

import me.vkryl.core.StringUtils;

public class CrashManager {
  private static CrashManager instance;

  public static CrashManager instance () {
    if (instance == null) {
      instance = new CrashManager();
    }
    return instance;
  }

  private Handler handler;
  private Thread.UncaughtExceptionHandler defaultHandler;

  private CrashManager () {
    handler = new Handler();
  }

  public void register () {
    if (defaultHandler == null) {
      defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
    }
    Thread.setDefaultUncaughtExceptionHandler(handler);
  }

  public void crash () {
    Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
    Looper looper = Looper.myLooper();
    Thread.UncaughtExceptionHandler currentHandler = looper != null ? looper.getThread().getUncaughtExceptionHandler() : null;
    Log.v("defaultHandler: %s, current: %s", defaultHandler, currentHandler);
    throw new RuntimeException("test");
  }

  private static String getFileName (long crashId) {
    StringBuilder b = new StringBuilder();
    b.append("crash.");
    /*if (Config.BETA) {
      b.append("beta.");
    }*/
    b.append(BuildConfig.VERSION_NAME);
    b.append(".");
    b.append(crashId);
    b.append(".log");
    return b.toString();
  }

  private File getFile (long crashId) {
    String path = UI.getAppContext().getFilesDir().getPath();
    if (path.charAt(path.length() - 1) == '/') {
      path = path + "logs/" + getFileName(crashId);
    } else {
      path = path + "/logs/" + getFileName(crashId);
    }
    File file = new File(path);
    File parent = file.getParentFile();
    if (!parent.exists() && !parent.mkdirs()) {
      return null;
    }
    return file;
  }

  @SuppressLint ("CommitPrefEdits")
  private void onCrash (Thread thread, Throwable ex) {
    processCrash(null, thread, ex);
  }

  private volatile boolean isCrashing;

  @SuppressLint("CommitPrefEdits")
  private void processCrash (String description, Thread thread, Throwable ex) {
    if (isCrashing) {
      return;
    }
    isCrashing = true;
    if (ex != null) {
      ex.printStackTrace();
    }
    Log.setRuntimeFlag(Log.RUNTIME_NOT_ASYNC, true);
    StringBuilder crash = new StringBuilder();

    try {
      final long crashId = Settings.instance().getLong(KEY_CRASH_ID, 0) + 1;
      File file = getFile(crashId);
      crash.append(Log.getDeviceInformationString());
      crash.append("\n\nCrashed on: ");
      crash.append(Lang.dateYearShortTime(System.currentTimeMillis(), TimeUnit.MILLISECONDS));
      crash.append("\nCrash comment: ");
      crash.append(description == null ? "Uncaught crash" : description);
      crash.append("\nCrashed Thread: ");
      crash.append(thread == null ? "null" : thread.getName());
      crash.append("\n\n");
      crash.append(Log.toString(ex));
      String result = crash.toString();
      if (file == null) {
        Log.e(Log.TAG_CRASH, "crashFile == null");
      } else {
        try {
          FileOutputStream trace = new FileOutputStream(file);
          trace.write(result.getBytes(StringUtils.UTF_8));
          trace.close();
          Settings.instance().putLong(KEY_CRASH_ID, crashId);
        } catch (IOException io) {
          Log.w(Log.TAG_CRASH, "Cannot save crash file", io);
        }
      }
      Log.e(Log.TAG_CRASH, "Application crashed", ex);
    } catch (Throwable t) {
      try {
        Log.e(Log.TAG_CRASH, "Unable to build crash: %s", t, crash.toString());
      } catch (Throwable wellWeHaveJustReallyFuckedUp) {
        // Oh Dear!
      }
    }

    Log.setRuntimeFlag(Log.RUNTIME_NOT_ASYNC, false);
    isCrashing = false;
    if (defaultHandler != null && ex != null) {
      Thread.setDefaultUncaughtExceptionHandler(defaultHandler);
      defaultHandler.uncaughtException(thread, ex);
      Thread.setDefaultUncaughtExceptionHandler(handler);
    } else {
      Process.killProcess(Process.myPid());
      System.exit(10);
    }
  }

  public void crash (String description, Throwable ex) {
    processCrash(description, Thread.currentThread(), ex);
  }

  public void test () {
    throw new RuntimeException("This is a crash test");
  }

  /*public void submit () {
    if (TdlibCache.instance().getMyUserId() == 0 || Strings.isEmpty(Config.DEVELOPER_USERNAME)) {
      return;
    }
    if (!Config.AUTO_SUBMIT_CRASHES) {
      UI.showToast(R.string.DevWontForgetThat, Toast.LENGTH_SHORT); // TODO easter egg in Borderlands style
    }
    Background.instance().post(new Runnable() {
      @Override
      public void run () {
        TG.getClientInstance().send(new TdApi.SearchPublicChat(Config.DEVELOPER_USERNAME), new Client.ResultHandler() {
          @Override
          public void onResult (TdApi.Object object) {
            final long chatId = TD.getChatId(object);
            if (chatId != 0) {
              final TdApi.Chat chat = TGDataManager.instance().getChatStrict(chatId);
              if (TD.hasWritePermission(chat)) {
                MessagesHelper.send(chatId, true, 0, new TdApi.InputMessageDocument(TD.createInputFile(currentCrash.getFile()), null, null));
                CrashManager.instance().revoke(currentCrash.getId());
              }
            }
          }
        });
      }
    });
  }*/

  /*public void delete () {
    UI.showToast(R.string.DevWillRememberThat, Toast.LENGTH_SHORT);
  }*/

  private CrashLog currentCrash;

  public void revoke (long id) {
    Settings.instance().putLong(KEY_CRASH_ID_REPORTED, id);
  }

  @SuppressWarnings(value = "SpellCheckingInspection")
  private static final String KEY_CRASH_ID = BuildConfig.DEBUG ? "crash_id_debug" : "crash_id_release";
  private static final String KEY_CRASH_ID_REPORTED = BuildConfig.DEBUG ? "crash_id_reported_debug" : "crash_id_reported_release";

  // private static AlertDialog lastDialog;

  public void check () {
    /*if (true) {
      return;
    }
    if (!WatchDog.instance().isOnline() || TdlibCache.instance().getMyUserId() == 0 || Strings.isEmpty(Config.DEVELOPER_USERNAME)) {
      return;
    }
    Background.instance().post(new Runnable() {
      @Override
      public void run () {
        long lastCrashId = Settings.instance().getLong(KEY_CRASH_ID, 0);
        File file = getFile(lastCrashId);
        if (file != null && file.exists()) {
          long lastCrashCheck = Settings.instance().getLong(KEY_CRASH_ID_REPORTED, 0);

          if (lastCrashId <= lastCrashCheck) {
            return;
          }

          try {
            final String crash = getStringFromFile(file);
            if (crash != null && (currentCrash == null || currentCrash.getId() < lastCrashId)) {
              currentCrash = new CrashLog(lastCrashId, file);
              UI.post(new Runnable() {
                @Override
                public void run () {
                  BaseActivity context = UI.getUiContext();
                  if (context != null) {
                    if (lastDialog != null && lastDialog.isShowing()) {
                      return;
                    }
                    if (Config.AUTO_SUBMIT_CRASHES) {
                      CrashManager.instance().submit();
                    } else {
                      AlertDialog.Builder builder = new AlertDialog.Builder(context, Theme.dialogTheme());
                      builder.setTitle(UI.getString(R.string.XCrashed, UI.getString(R.string.appName)));
                      builder.setMessage(Strings.replaceTags(UI.getString(R.string.SendCrashToDev, Config.DEVELOPER_USERNAME, Strings.buildSize(currentCrash.getFile().length()))));
                      builder.setPositiveButton(Lang.getOK(), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick (DialogInterface dialog, int which) {
                          CrashManager.instance().submit();
                        }
                      });
                      builder.setNegativeButton(R.string.Later, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick (DialogInterface dialog, int which) {
                          dialog.dismiss();
                        }
                      });
                      builder.setCancelable(false);
                      lastDialog = context.showAlert(builder);
                    }
                  }
                }
              }, 2500);
            }
          } catch (Throwable t) {
            // ignored
          }
        }
      }
    });*/
  }

  public static String convertStreamToString(InputStream is) throws Exception {
    BufferedReader reader = new BufferedReader(new InputStreamReader(is));
    StringBuilder sb = new StringBuilder();
    String line;
    while ((line = reader.readLine()) != null) {
      sb.append(line).append("\n");
    }
    reader.close();
    return sb.toString();
  }

  public static String getStringFromFile (File file) throws Exception {
    FileInputStream fin = new FileInputStream(file);
    String ret = convertStreamToString(fin);
    //Make sure you close all streams.
    fin.close();
    return ret;
  }

  public static class Handler implements Thread.UncaughtExceptionHandler {
    @Override
    public void uncaughtException (Thread thread, Throwable ex) {
      instance().onCrash(thread, ex);
    }
  }
}
