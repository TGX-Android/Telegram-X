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
