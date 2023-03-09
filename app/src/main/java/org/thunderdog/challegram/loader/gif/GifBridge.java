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
 * File created on 01/03/2016 at 11:13
 */
package org.thunderdog.challegram.loader.gif;

import android.view.View;

import androidx.annotation.AnyThread;
import androidx.annotation.Keep;
import androidx.annotation.UiThread;
import androidx.collection.ArraySet;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.N;
import org.thunderdog.challegram.telegram.Tdlib;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import me.vkryl.core.lambda.RunnableData;
import me.vkryl.td.Td;

public class GifBridge {
  private static GifBridge instance;

  public static GifBridge instance () {
    if (instance == null) {
      instance = new GifBridge();
    }
    return instance;
  }

  private static final int THREAD_POOL_SIZE = 2;
  private final GifBridgeThread thread;
  private final HashMap<String, GifRecord> records = new HashMap<>();
  private final HashMap<Integer, ArrayList<GifRecord>> fileIdToRecordList = new HashMap<>();
  private final ArrayList<GifRecord> playingRoundVideos = new ArrayList<>();
  // TODO: rework to executors
  private int lastUsedThread, lastUsedEmojiThread;
  private final GifThread[] threads, emojiThreads;
  private final GifThread[] lottieThreads;

  private GifBridge () {
    N.gifInit();
    thread = new GifBridgeThread();
    // TODO: rework to executors
    threads = new GifThread[THREAD_POOL_SIZE];
    for (int i = 0; i < threads.length; i++) {
      threads[i] = new GifThread(i);
    }
    emojiThreads = new GifThread[THREAD_POOL_SIZE];
    for (int i = 0; i < emojiThreads.length; i++) {
      emojiThreads[i] = new GifThread(i);
    }
    lottieThreads = new GifThread[3];
    for (int i = 0; i < lottieThreads.length; i++) {
      lottieThreads[i] = new GifThread(i);
    }
  }

  private GifThread obtainFrameThread (GifFile file) {
    if (file.getGifType() == GifFile.TYPE_TG_LOTTIE) {
      return lottieThreads[file.getOptimizationMode()];
    } else {
      // TODO rework to executors
      if (file.getOptimizationMode() == GifFile.OptimizationMode.EMOJI) {
        if (++lastUsedEmojiThread == THREAD_POOL_SIZE) {
          lastUsedEmojiThread = 0;
        }
        return emojiThreads[lastUsedEmojiThread];
      } else {
        if (++lastUsedThread == THREAD_POOL_SIZE) {
          lastUsedThread = 0;
        }
        return threads[lastUsedThread];
      }
    }
  }

  public GifBridgeThread getBaseThread () {
    return thread;
  }

  @Keep
  private final Set<GifWatcher> tempWatchers = new ArraySet<>();

  public void loadFile (final GifFile file, RunnableData<GifWatcherReference> callback) {
    AtomicReference<GifWatcherReference> reference = new AtomicReference<>();
    GifWatcher watcher = (file1, state) -> {
      callback.runWithData(reference.get());
    };
    tempWatchers.add(watcher);
    reference.set(new GifWatcherReference(watcher));
    requestFile(file, reference.get());
  }

  public void requestFile (GifFile file, GifWatcherReference reference) {
    if (thread != Thread.currentThread()) {
      thread.requestFile(file, reference);
      return;
    }

    String key = file.toString();

    if (Log.isEnabled(Log.TAG_GIF_LOADER)) {
      Log.i(Log.TAG_GIF_LOADER, "#%s: requestFile, type: %s, path: %s", key, file.getClass().getSimpleName(), file.getFilePath());
    }

    GifRecord record = records.get(key);

    if (record == null) {
      GifActor actor = new GifActor(file, obtainFrameThread(file));
      record = new GifRecord(file, actor, reference);
      synchronized (records) {
        records.put(key, record);
        ArrayList<GifRecord> recordList = fileIdToRecordList.get(file.getFileId());
        if (recordList == null) {
          recordList = new ArrayList<>();
          fileIdToRecordList.put(file.getFileId(), recordList);
        }
        recordList.add(record);
        if (file.isRoundVideo()) {
          playingRoundVideos.add(record);
        }
      }
      if (Log.isEnabled(Log.TAG_GIF_LOADER)) {
        Log.i(Log.TAG_GIF_LOADER, "#%s: actor started", key);
      }
      actor.act();
    } else {
      if (Log.isEnabled(Log.TAG_GIF_LOADER)) {
        Log.i(Log.TAG_GIF_LOADER, "#%s: watched joined existing actor", key);
      }
      synchronized (records) {
        record.addWatcher(reference);
      }
    }
  }

  public View findAnyView (GifFile file) {
    synchronized (records) {
      GifRecord record = records.get(file.toString());
      if (record == null || !record.hasWatchers())
        return null;
      for (GifWatcherReference reference : record.getWatchers()) {
        View view = reference.findTargetView(file);
        if (view != null)
          return view;
      }
    }
    return null;
  }

  public void removeWatcher (GifWatcherReference reference) {
    if (thread != Thread.currentThread()) {
      thread.removeWatcher(reference);
      return;
    }

    synchronized (records) {
      ArrayList<String> itemsToRemove = null;
      for (HashMap.Entry<String, GifRecord> entry : records.entrySet()) {
        GifRecord record = entry.getValue();
        if (record.removeWatcher(reference) && !record.hasWatchers()) {
          if (itemsToRemove == null) {
            itemsToRemove = new ArrayList<>();
          }
          itemsToRemove.add(entry.getKey());
          int fileId = record.getFile().getFileId();
          ArrayList<GifRecord> recordList = fileIdToRecordList.get(fileId);
          if (recordList != null && recordList.remove(record) && recordList.isEmpty()) {
            fileIdToRecordList.remove(fileId);
          }
          if (record.getFile().isRoundVideo()) {
            playingRoundVideos.remove(record);
          }
        }
      }
      if (itemsToRemove != null) {
        for (String item : itemsToRemove) {
          GifRecord record = records.remove(item);
          if (record != null) {
            record.getActor().cancel();
            if (Log.isEnabled(Log.TAG_GIF_LOADER)) {
              Log.i(Log.TAG_GIF_LOADER, "#%s: actor cancelled", record.getFile().toString());
            }
          }
        }
      }
    }
  }

  // TG or HTTP reader thread
  public boolean onProgress (Tdlib tdlib, int fileId, float progress) {
    boolean found = false;

    synchronized (records) {
      ArrayList<GifRecord> records = this.fileIdToRecordList.get(fileId);
      if (records != null) {
        for (GifRecord record : records) {
          for (GifWatcherReference reference : record.getWatchers()) {
            reference.gifProgress(record.getFile(), progress);
          }
          record.getActor().cacheProgress(progress);
        }
        found = true;
      }
    }

    if (Log.isEnabled(Log.TAG_GIF_LOADER)) {
      Log.d(Log.TAG_GIF_LOADER, "#%d: onProgress, progress: %f found: %b", fileId, progress, found);
    }

    return found;
  }

  // TG or HTTP reader thread
  public boolean onLoad (Tdlib tdlib, TdApi.File file) {
    synchronized (records) {
      ArrayList<GifRecord> records = this.fileIdToRecordList.get(file.id);
      if (records != null) {
        for (GifRecord record : records) {
          if (Log.isEnabled(Log.TAG_GIF_LOADER)) {
            Log.i(Log.TAG_GIF_LOADER, "#%d: onLoad", file.id);
          }
          TdApi.File localFile = record.getFile().getFile();
          Td.copyTo(file, localFile);
          thread.onLoad(record.getActor(), file);
        }
        return true;
      }
    }
    return false;
  }

  // GifBridge thread
  boolean scheduleNextFrame (GifActor actor, int fileId, long delay, boolean force) {
    return thread.scheduleNextFrame(actor, fileId, delay, force);
  }

  boolean canScheduleNextFrame (GifActor actor, int fileId) {
    return thread.canScheduleNextFrame(actor, fileId);
  }

  // Decoder thread
  void nextFrameReady (GifActor actor, boolean restarted) {
    thread.nextFrameReady(actor, restarted);
  }

  // Decoder thread
  void onGifLoaded (GifFile file, GifState gif) {
    if (thread != Thread.currentThread()) {
      thread.onGifLoad(file, gif);
      return;
    }

    synchronized (records) {
      GifRecord record = records.get(file.toString());

      if (record != null) {
        for (GifWatcherReference reference : record.getWatchers()) {
          reference.gifLoaded(file, gif);
        }
        record.getActor().onGifLoaded(gif);
      }
    }
  }

  @AnyThread
  void dispatchGifFrameChanged (GifFile file, GifState gif, boolean isRestart) {
    GifReceiver.getHandler().post(() -> {
      onGifFrameDeadlineReached(file, gif, isRestart);
    });
  }

  @UiThread
  void onGifFrameDeadlineReached (GifFile file, GifState gif, boolean isRestart) {
    synchronized (records) {
      if (gif.setCanApplyNext()) {
        GifRecord record = records.get(file.toString());
        if (record != null) {
          for (GifWatcherReference reference : record.getWatchers()) {
            reference.gifFrameChanged(file, isRestart);
          }
        }
      }
    }
  }
}
