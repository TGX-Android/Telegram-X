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
 * File created on 28/03/2023
 */
package org.thunderdog.challegram.voip;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.Log;

import java.io.File;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Locale;

public class VoIPLogs {
  private static final int KEEP_COUNT = 6;

  public static class Pair {
    public final File logFile;
    public final File statsLogFile;

    public Pair (File logFile, File statsLogFile) {
      this.logFile = logFile;
      this.statsLogFile = statsLogFile;
    }
  }

  @Nullable
  public static Pair getNewFile (boolean cleanup) {
    Calendar c = Calendar.getInstance();
    File dir = Log.getLogDir();
    if (dir == null) {
      return null;
    }
    if (cleanup) {
      deleteOldCallLogFiles(dir, KEEP_COUNT);
    }
    File[] files = new File[2];
    for (int i = 0; i < files.length; i++) {
      String logFileName = String.format(Locale.US,
        "logs/%s%02d_%02d_%04d_%02d_%02d_%02d%s.log",
        Log.CALL_PREFIX,
        c.get(Calendar.DATE),
        c.get(Calendar.MONTH) + 1,
        c.get(Calendar.YEAR),
        c.get(Calendar.HOUR_OF_DAY),
        c.get(Calendar.MINUTE),
        c.get(Calendar.SECOND),
        i == 1 ? ".stats" : ""
      );
      files[i] = new File(dir, logFileName);
    }
    return new Pair(files[0], files[1]);
  }

  public static boolean deleteAllCallLogFiles () {
    return deleteOldCallLogFiles(Log.getLogDir(), 0);
  }

  private static boolean deleteOldCallLogFiles (File dir, int keepCount) {
    if (dir == null) {
      return false;
    }
    File[] callLogs = dir.listFiles((dir1, name) ->
      name.startsWith(Log.CALL_PREFIX) && name.endsWith(".log")
    );
    if (callLogs == null || callLogs.length == 0) {
      return true;
    }
    Arrays.sort(callLogs, (a, b) ->
      Long.compare(a.lastModified(), b.lastModified())
    );
    boolean success = true;
    for (int i = 0; i < Math.max(0, callLogs.length - keepCount); i++) {
      File file = callLogs[i];
      if (!file.delete()) {
        success = false;
      }
    }
    return success;
  }
}
