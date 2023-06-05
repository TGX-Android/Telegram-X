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
 * File created on 21/07/2022, 21:27
 */
package org.thunderdog.challegram.util;

import android.os.Build;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.StringDef;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.telegram.TdlibAccount;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.AppState;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.leveldb.LevelDB;
import me.vkryl.td.JSON;
import me.vkryl.td.Td;

public class Crash {
  @Retention(RetentionPolicy.SOURCE)
  @IntDef(flag = true, value = {
    Flags.RESOLVED,
    Flags.INTERACTED,
    Flags.SOURCE_TDLIB,
    Flags.SOURCE_TDLIB_PARAMETERS,
    Flags.SOURCE_UNCAUGHT_EXCEPTION,
    Flags.SAVE_APPLICATION_LOG_EVENT,
    Flags.APPLICATION_LOG_EVENT_SAVED,
  })
  public @interface Flags {
    int
      RESOLVED = 1, // User pressed "Launch App". No need to show the screen the second time for the same crash.
      INTERACTED = 1 << 1, // User interacted with the recovery screen. Giving him an ability to set-up everything properly.
      SOURCE_TDLIB = 1 << 2, // Crash originally came from TDLib
      SOURCE_TDLIB_PARAMETERS = 1 << 3, // Crash originally came from TDLib setTdlibParameters
      SOURCE_UNCAUGHT_EXCEPTION = 1 << 4,
      SAVE_APPLICATION_LOG_EVENT = 1 << 5, // Crash has to be sent through saveApplicationLogEvent
      APPLICATION_LOG_EVENT_SAVED = 1 << 6; // Crash sent through saveApplicationLogEvent
  }

  @Retention(RetentionPolicy.SOURCE)
  @StringDef({
    CacheKey.APP_VERSION_CODE,
    CacheKey.SDK_VERSION,
    CacheKey.INSTALLATION_ID,
    CacheKey.FLAGS,
    CacheKey.DATE,
    CacheKey.UPTIME,
    CacheKey.MESSAGE,
    CacheKey.ACCOUNT_ID,
    CacheKey.RUNNING_TDLIB_COUNT
  })
  private @interface CacheKey {
    String
      APP_VERSION_CODE = "app",
      SDK_VERSION = "sdk",
      INSTALLATION_ID = "iid",
      FLAGS = "flags",
      DATE = "time",
      UPTIME = "uptime",
      MESSAGE = "rip",
      ACCOUNT_ID = "id",
      RUNNING_TDLIB_COUNT = "td_count";
  }
  
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    Type.UNKNOWN,
    Type.TDLIB,
    Type.DISK_FULL,
    Type.TDLIB_EXTERNAL_ERROR,
    Type.TDLIB_DATABASE_BROKEN,
    Type.TDLIB_INITIALIZATION_FAILURE,
    Type.UNCAUGHT_EXCEPTION
  })
  public @interface Type {
    int UNKNOWN = 0,
      TDLIB = 1,
      DISK_FULL = 2,
      TDLIB_EXTERNAL_ERROR = 3,
      TDLIB_DATABASE_BROKEN = 4,
      TDLIB_INITIALIZATION_FAILURE = 5,
      UNCAUGHT_EXCEPTION = 6;
  }

  public final long id;
  public final String message;
  public final long date;
  public final long uptime;
  public final int appVersionCode, sdkVersion;
  public final @Nullable AppBuildInfo appBuildInfo;
  public final int accountId;
  public final int runningTdlibCount;

  private @Flags int flags;

  private Crash (long id, String message, long date, long uptime, @Flags int flags, int appVersionCode, int sdkVersion, @Nullable AppBuildInfo appBuildInfo, int accountId, int runningTdlibCount) {
    this.id = id;
    this.message = message;
    this.date = date;
    this.uptime = uptime;
    this.flags = flags;
    this.appVersionCode = appVersionCode;
    this.sdkVersion = sdkVersion;
    this.appBuildInfo = appBuildInfo;
    this.accountId = accountId;
    this.runningTdlibCount = runningTdlibCount;
  }

  public @Flags int getFlags () {
    return flags;
  }

  public boolean setFlag (@Flags int flag, boolean value) {
    return setFlags(BitwiseUtils.setFlag(flags, flag, value));
  }

  public boolean setFlags (@Flags int flags) {
    if (this.flags != flags) {
      this.flags = flags;
      return true;
    }
    return false;
  }

  public boolean isTdlibLogicError () {
    return BitwiseUtils.hasFlag(flags, Flags.SOURCE_TDLIB | Flags.SOURCE_TDLIB_PARAMETERS);
  }

  public boolean shouldShowAtApplicationStart () {
    if (appVersionCode != BuildConfig.ORIGINAL_VERSION_CODE || BitwiseUtils.hasFlag(flags, Flags.RESOLVED)) {
      // User has installed a new APK or pressed "Launch App". Forgetting the last error.
      return false;
    }
    if (BitwiseUtils.hasFlag(flags, Flags.INTERACTED)) {
      // User has seen the "Aw, snap!" screen, but didn't press "Launch App" afterwards.
      return true;
    }
    if (BuildConfig.DEBUG || BuildConfig.EXPERIMENTAL) {
      // Experimental builds should always allow interacting with the crash on the next app launch
      return true;
    }
    if (TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - date) > 15 ||
      uptime > TimeUnit.MINUTES.toMillis(1)) {
      // Do not show "Aw, snap!" if app crashed more than 15 minutes ago,
      // or if uptime before crash was over a minute
      return false;
    }
    return true;
  }

  public @Type int getType () {
    if (BitwiseUtils.hasFlag(flags, Flags.SOURCE_TDLIB)) {
      if (Td.isDiskFullError(message)) {
        return Type.DISK_FULL;
      } else if (Td.isDatabaseBrokenError(message)) {
        return Type.TDLIB_DATABASE_BROKEN;
      }
      if (Td.isExternalError(message)) {
        return Type.TDLIB_EXTERNAL_ERROR;
      } else {
        return Type.TDLIB;
      }
    }
    if (BitwiseUtils.hasFlag(flags, Flags.SOURCE_TDLIB_PARAMETERS)) {
      return Type.TDLIB_INITIALIZATION_FAILURE;
    }
    if (BitwiseUtils.hasFlag(flags, Flags.SOURCE_UNCAUGHT_EXCEPTION)) {
      return Type.UNCAUGHT_EXCEPTION;
    }
    return Type.UNKNOWN;
  }

  private String getStringType () {
    switch (getType()) {
      case Type.TDLIB_DATABASE_BROKEN:
        return "db_corrupted";
      case Type.TDLIB_EXTERNAL_ERROR:
        return "external_error";
      case Type.DISK_FULL:
        return "disk_full";
      case Type.TDLIB:
        return "tdlib_fatal_error";
      case Type.TDLIB_INITIALIZATION_FAILURE:
        return "tdlib_init_failed";
      case Type.UNCAUGHT_EXCEPTION:
        return "uncaught_exception";
      case Type.UNKNOWN:
        break;
    }
    return "unknown_error";
  }

  public @Nullable TdApi.SaveApplicationLogEvent toSaveFunction (final String crashDeviceId) {
    if (appBuildInfo == null) {
      // do not allow saving application event if we don't know anything about crashed build
      return null;
    }
    String type = getStringType();
    if (isTdlibLogicError() && !(StringUtils.isEmpty(appBuildInfo.getTdlibCommitFull()) && StringUtils.isEmpty(appBuildInfo.getTdlibVersion()))) {
      if (!StringUtils.isEmpty(appBuildInfo.getTdlibVersion())) {
        type += "_" + appBuildInfo.getTdlibVersion();
      }
      if (!StringUtils.isEmpty(appBuildInfo.getTdlibCommitFull())) {
        type += "_" + appBuildInfo.tdlibCommit();
      }
    }
    return new TdApi.SaveApplicationLogEvent(type, appBuildInfo.maxCommitDate(), toJsonData(crashDeviceId));
  }

  public TdApi.JsonValue toJsonData (final String crashDeviceId) {
    return JSON.toObject(toMap(crashDeviceId));
  }

  public Map<String, Object> toMap (final String crashDeviceId) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("message", message);
    if (runningTdlibCount != RUNNING_TDLIB_COUNT_UNKNOWN) {
      result.put("running_tdlib_count", runningTdlibCount);
    }
    result.put("date", date);
    result.put("uptime", uptime);
    result.put("sdk", sdkVersion);
    if (appBuildInfo != null) {
      result.put("app", appBuildInfo.toMap());
    }
    result.put("cpu", U.getCpuArchitecture());
    result.put("crash_id", id);
    result.put("package_id", UI.getAppContext().getPackageName());
    result.put("device", TdlibManager.deviceInformation());
    result.put("fingerprint", U.getApkFingerprint("SHA1"));
    result.put("device_id", crashDeviceId);
    return result;
  }

  public void saveTo (LevelDB pmc, String keyPrefix) {
    pmc.putInt(keyPrefix + CacheKey.APP_VERSION_CODE, appVersionCode);
    pmc.putInt(keyPrefix + CacheKey.SDK_VERSION, sdkVersion);
    if (appBuildInfo != null) {
      pmc.putLong(keyPrefix + CacheKey.INSTALLATION_ID, appBuildInfo.getInstallationId());
    }
    pmc.putInt(keyPrefix + CacheKey.FLAGS, flags);
    pmc.putLong(keyPrefix + CacheKey.DATE, date);
    pmc.putLong(keyPrefix + CacheKey.UPTIME, uptime);
    pmc.putString(keyPrefix + CacheKey.MESSAGE, message);
    pmc.putInt(keyPrefix + CacheKey.ACCOUNT_ID, accountId);
    if (runningTdlibCount != RUNNING_TDLIB_COUNT_UNKNOWN) {
      pmc.putInt(keyPrefix + CacheKey.RUNNING_TDLIB_COUNT, runningTdlibCount);
    } else {
      pmc.remove(keyPrefix + CacheKey.RUNNING_TDLIB_COUNT);
    }
  }

  public void saveFlags (LevelDB pmc, String prefix) {
    pmc.putInt(prefix + CacheKey.FLAGS, flags);
  }

  public static int restoreFlags (LevelDB pmc, String keyPrefix) {
    return pmc.getInt(keyPrefix + CacheKey.FLAGS, 0);
  }

  public static long restoreInstallationId (LevelDB pmc, String keyPrefix) {
    return pmc.getLong(keyPrefix + CacheKey.INSTALLATION_ID, 0);
  }

  public interface AppBuildInfoRestorer {
    @Nullable AppBuildInfo restoreBuildInformation (long installationId);
  }

  public static final int RUNNING_TDLIB_COUNT_UNKNOWN = -1;

  public static class Builder {
    private String message = "empty";
    private long uptime = AppState.uptime();
    private int flags;
    private int accountId = TdlibAccount.NO_ID;
    private long id = 0;
    private long date = System.currentTimeMillis();
    private int appVersionCode = BuildConfig.ORIGINAL_VERSION_CODE;
    private int sdkVersion = Build.VERSION.SDK_INT;
    private int runningTdlibCount = RUNNING_TDLIB_COUNT_UNKNOWN;
    private @Nullable AppBuildInfo appBuildInfo;

    public Builder () {}

    public Builder (String message) {
      message(message);
    }

    public Builder (String message, Thread thread, Throwable error) {
      message(message + "\n" +
        "Thread: " + thread.getClass().getSimpleName() + ", name: " + thread.getName() + "\n" +
        Log.toString(error)
      );
      flags(Flags.SOURCE_UNCAUGHT_EXCEPTION);
    }

    public Builder (int accountId, String message) {
      accountId(accountId);
      message(message);
    }

    public Builder message (String message) {
      this.message = StringUtils.isEmpty(message) ? "empty" : message;
      return this;
    }

    public Builder uptime (long uptime) {
      this.uptime = uptime;
      return this;
    }

    public Builder flags (@Flags int flags) {
      this.flags = flags;
      return this;
    }

    public Builder addFlags (int flags) {
      this.flags |= flags;
      return this;
    }

    public Builder accountId (int accountId) {
      this.accountId = accountId;
      return this;
    }

    public Builder id (long id) {
      this.id = id;
      return this;
    }

    public Builder date (long date) {
      this.date = date;
      return this;
    }

    public Builder appVersionCode (int appVersionCode) {
      this.appVersionCode = appVersionCode;
      return this;
    }

    public Builder sdkVersion (int sdkVersion) {
      this.sdkVersion = sdkVersion;
      return this;
    }

    public Builder appBuildInfo (AppBuildInfo appBuildInfo) {
      this.appBuildInfo = appBuildInfo;
      return this;
    }

    public Builder runningTdlibCount (int runningTdlibCount) {
      this.runningTdlibCount = runningTdlibCount;
      return this;
    }

    public Crash build () {
      return new Crash(id, message, date, uptime, flags, appVersionCode, sdkVersion, appBuildInfo, accountId, runningTdlibCount);
    }

    public boolean restoreField (LevelDB.Entry entry, String keyPrefix, @Nullable AppBuildInfoRestorer appBuildInfoRestorer) {
      final @CacheKey String key = entry.key().substring(keyPrefix.length());
      switch (key) {
        case CacheKey.APP_VERSION_CODE:
          appVersionCode(entry.asInt());
          break;
        case CacheKey.SDK_VERSION:
          sdkVersion(entry.asInt());
          break;
        case CacheKey.INSTALLATION_ID:
          appBuildInfo(appBuildInfoRestorer != null ? appBuildInfoRestorer.restoreBuildInformation(entry.asLong()) : null);
          break;
        case CacheKey.FLAGS:
          flags(entry.asInt());
          break;
        case CacheKey.DATE:
          date(entry.asLong());
          break;
        case CacheKey.UPTIME:
          uptime(entry.asLong());
          break;
        case CacheKey.MESSAGE:
          message(entry.asString());
          break;
        case CacheKey.ACCOUNT_ID:
          accountId(entry.asInt());
          break;
        case CacheKey.RUNNING_TDLIB_COUNT:
          runningTdlibCount(entry.asInt());
          break;
        default:
          return false;
      }
      return true;
    }
  }
}
