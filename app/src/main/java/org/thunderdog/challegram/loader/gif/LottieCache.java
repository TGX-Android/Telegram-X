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
 * File created on 03/08/2019
 */
package org.thunderdog.challegram.loader.gif;

import android.content.SharedPreferences;
import android.os.SystemClock;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.core.BaseThread;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.unsorted.Settings;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.vkryl.core.FileUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.leveldb.LevelDB;

public class LottieCache {
  private static LottieCache instance;

  public static LottieCache instance () {
    if (instance == null) {
      synchronized (LottieCache.class) {
        if (instance == null) {
          instance = new LottieCache();
        }
      }
    }
    return instance;
  }

  private final BaseThread generationThread = new BaseThread("LottieGenerationThread"), generationFullThread = new BaseThread("LottieGenerationThread2");

  private LottieCache () { }

  public boolean clear () {
    if (FileUtils.delete(LottieCache.getCacheDir(), true)) {
      Settings.instance().pmc().removeByPrefix(LOTTIE_KEY_PREFIX);
      cancelScheduledGc();
      return true;
    }
    return false;
  }

  private long scheduledAt;

  private static class Entry implements Comparable<Entry> {
    public final String key;
    public final long time;

    public final int accountId;
    public final String directory, fileName;

    public Entry (String key, long time) {
      this.key = key;
      this.time = time;
      String[] fileData = key.substring(LOTTIE_KEY_PREFIX.length()).split("/", 3);
      if (fileData.length != 3)
        throw new IllegalArgumentException(key);
      this.accountId = StringUtils.parseInt(fileData[0], -1);
      if (accountId == -1)
        throw new IllegalArgumentException(key);
      this.directory = fileData[1];
      this.fileName = fileData[2];
      if (StringUtils.isEmpty(directory) || StringUtils.isEmpty(fileName))
        throw new IllegalArgumentException(key);
    }

    public static boolean validateKey (String key) {
      String[] fileData = key.substring(LOTTIE_KEY_PREFIX.length()).split("/", 3);
      if (fileData.length != 3)
        return false;
      int accountId = StringUtils.parseInt(fileData[0], -1);
      if (accountId == -1)
        return false;
      String directory = fileData[1];
      String fileName = fileData[2];
      if (StringUtils.isEmpty(directory) || StringUtils.isEmpty(fileName))
        return false;
      return true;
    }

    @Override
    public int compareTo (Entry o) {
      return Long.compare(time, o.time);
    }

    public File toFile (File cacheDir) {
      return new File(new File(new File(cacheDir, Integer.toString(accountId)), directory), fileName);
    }
  }

  private void limitFileCount (int count, String key) {
    generationThread.post(() -> {
      final LevelDB db = Settings.instance().pmc();
      final File cacheDir = getCacheDir();
      List<Entry> entries = new ArrayList<>();
      List<String> brokenKeys = null;
      for (LevelDB.Entry entry : db.find(LOTTIE_KEY_PREFIX)) {
        Entry e;
        try {
          e = new Entry(entry.key(), entry.asLong());
        } catch (IllegalArgumentException t) {
          Log.e("Bad lottie cache key: %s", t);
          if (brokenKeys == null)
            brokenKeys = new ArrayList<>();
          brokenKeys.add(entry.key());
          continue;
        }
        entries.add(e);
      }
      if (brokenKeys != null) {
        db.edit();
        for (String brokenKey : brokenKeys) {
          db.remove(brokenKey);
        }
        db.apply();
      }
      Collections.sort(entries);
      int extraCount = (entries.size() - count);
      int index = 0;
      SharedPreferences.Editor editor = null;
      while (extraCount > 0 && index < entries.size()) {
        Entry entry = entries.get(index++);
        if (!entry.key.equals(key)) {
          if (editor == null)
            editor = db.edit();
          delete(cacheDir, entry, editor);
          extraCount--;
        }
      }
      if (editor != null) {
        editor.apply();
      }
    }, 0);
  }

  private static void delete (File cacheDir, Entry entry, SharedPreferences.Editor editor) {
    File file = entry.toFile(cacheDir);
    if (!file.exists() || file.delete()) {
      editor.remove(entry.key);
    }
  }

  public void gc () {
    generationThread.post(() -> {
      long nextTime = -1;
      final File cacheDir = getCacheDir();

      File[] accountDirs = cacheDir.listFiles();
      if (accountDirs != null) {
        for (File accountDir : accountDirs) {
          File[] thumbnailDirs = accountDir.listFiles((dir, name) -> "0".equals(name) || name.startsWith("thumbs"));
          if (thumbnailDirs != null) {
            for (File thumbnailDir : thumbnailDirs) {
              FileUtils.delete(thumbnailDir, true);
            }
          }
        }
      }

      long now = System.currentTimeMillis();
      final LevelDB db = Settings.instance().pmc();
      SharedPreferences.Editor editor = null;
      List<String> brokenKeys = null;
      for (LevelDB.Entry entry : db.find(LOTTIE_KEY_PREFIX)) {
        Entry e;
        try {
          e = new Entry(entry.key(), entry.asLong());
        } catch (IllegalArgumentException t) {
          Log.e("Bad lottie cache key: %s", t);
          if (brokenKeys == null)
            brokenKeys = new ArrayList<>();
          brokenKeys.add(entry.key());
          continue;
        }
        if (now >= e.time) {
          if (editor == null)
            editor = db.edit();
          delete(cacheDir, e, editor);
        } else {
          nextTime = nextTime == -1 ? e.time : Math.min(nextTime, e.time);
        }
      }
      if (editor != null)
        editor.apply();
      if (nextTime != -1) {
        scheduleGc(nextTime - now, true);
      } else {
        cancelScheduledGc();
      }
    }, 0);
  }

  private Runnable gcRunnable = this::gc;

  private void scheduleGc (long timeout, boolean force) {
    if (scheduledAt == 0 || (SystemClock.uptimeMillis() + timeout < scheduledAt) || force) {
      cancelScheduledGc();
      scheduledAt = SystemClock.uptimeMillis() + timeout;
      generationThread.post(gcRunnable, timeout);
    }
  }

  private void cancelScheduledGc () {
    if (scheduledAt != 0) {
      generationThread.getHandler().removeCallbacks(gcRunnable);
      scheduledAt = 0;
    }
  }

  public static File getCacheDir (int accountId, int size, boolean optimize, String colorKey) {
    File cacheDir = getCacheDir();
    if (!cacheDir.exists() && !cacheDir.mkdir())
      return null;
    cacheDir = new File(cacheDir, Integer.toString(accountId));
    if (!cacheDir.exists() && !cacheDir.mkdir())
      return null;
    String folderName = optimize ? "thumbs" + size : Integer.toString(size);
    if (!StringUtils.isEmpty(colorKey))
      folderName += "_" + colorKey;
    cacheDir = new File(cacheDir, folderName);
    if (!cacheDir.exists() && !cacheDir.mkdir())
      return null;
    return cacheDir;
  }

  public BaseThread thread (boolean needOptimize) {
    return needOptimize ? generationThread : generationFullThread;
  }

  private static final String LOTTIE_KEY_PREFIX = "lottie_";

  public static File getCacheFile (GifFile file, boolean optimize, int size, int fitzpatrickType, long keepAliveMs, int maxCount) {
    if (optimize) {
      keepAliveMs = 0;
    }
    String colorKey = fitzpatrickType != 0 ? Integer.toString(fitzpatrickType) : null;
    int accountId = file.tdlib().id();
    File cacheDir = getCacheDir(accountId, size, optimize, colorKey);
    if (cacheDir == null)
      return null;
    File originalFile = new File(file.getFilePath());
    if (keepAliveMs > 0) {
      String key = getCacheFileKey(accountId, optimize, size, colorKey, originalFile.getName());
      Settings.instance().pmc().putLong(key, System.currentTimeMillis() + keepAliveMs);
      instance().limitFileCount(maxCount, key);
      instance().scheduleGc(keepAliveMs, false);
    }
    return new File(cacheDir, originalFile.getName());
  }

  public static String getCacheFileKey (int accountId, boolean optimize, int size, String colorKey, String originalFileName) {
    String cacheKey = LOTTIE_KEY_PREFIX + accountId + "/" + (optimize ? "thumbs" + size : size) + (!StringUtils.isEmpty(colorKey) ? "_" + colorKey : "") + "/" + originalFileName;
    if (!Entry.validateKey(cacheKey))
      throw new IllegalArgumentException(cacheKey);
    return cacheKey;
  }

  public void checkFile (GifFile file, File cacheFile, boolean optimize, int size, int fitzpatrickType) {
    generationThread.post(() -> {
      if (optimize) {
        cacheFile.delete();
      } else {
        String colorKey = fitzpatrickType != 0 ? Integer.toString(fitzpatrickType) : null;
        String key = getCacheFileKey(file.tdlib.accountId(), optimize, size, colorKey, new File(file.getFilePath()).getName());
        long time = Settings.instance().getLong(key, 0);
        if (time == 0 || System.currentTimeMillis() >= time) {
          cacheFile.delete();
          Settings.instance().remove(key);
          gc();
        }
      }
    }, 0);
  }

  public static File getCacheDir () {
    return new File(TD.getCacheDir(true), "tgs");
  }
}
