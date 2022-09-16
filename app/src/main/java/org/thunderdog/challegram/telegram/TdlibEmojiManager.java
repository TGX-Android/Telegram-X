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
 * File created on 26/08/2022, 22:48.
 */

package org.thunderdog.challegram.telegram;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.util.BatchOperationHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import me.vkryl.core.collection.LongSet;
import me.vkryl.core.lambda.RunnableData;
import me.vkryl.core.reference.ReferenceList;
import me.vkryl.core.reference.ReferenceLongMap;
import me.vkryl.td.Td;
import me.vkryl.td.TdConstants;

public class TdlibEmojiManager implements CleanupStartupDelegate {
  public static class Entry {
    public final long customEmojiId;
    public final @Nullable TdApi.Sticker sticker;
    public final @Nullable TdApi.Error error;

    public Entry (@NonNull TdApi.Sticker sticker) {
      this.customEmojiId = sticker.customEmojiId;
      this.sticker = sticker;
      this.error = null;
    }

    public Entry (long customEmojiId, @NonNull TdApi.Error error) {
      this.customEmojiId = customEmojiId;
      this.sticker = null;
      this.error = error;
    }

    public boolean isNotFound () {
      return error != null || sticker == null;
    }

    public boolean isAnimated () {
      return sticker != null && Td.isAnimated(sticker.format);
    }

    public boolean isStatic () {
      return sticker != null && !Td.isAnimated(sticker.format);
    }
  }

  public interface Watcher {
    void onCustomEmojiLoaded (TdlibEmojiManager context, long customEmojiId, Entry entry);
  }

  private final Tdlib tdlib;
  private final Object dataLock = new Object();
  private final Map<Long, Entry> entries = new HashMap<>();
  private final LongSet postponedCustomEmojiIds = new LongSet();
  private final LongSet loadingCustomEmojiIds = new LongSet();
  private final ReferenceLongMap<Watcher> watcherReferences = new ReferenceLongMap<>(true);
  private final Map<Long, List<Watcher>> watchers = new LinkedHashMap<>();

  private int contextId;

  public TdlibEmojiManager (Tdlib tdlib) {
    this.tdlib = tdlib;
    tdlib.listeners().addCleanupListener(this);
  }

  // Listeners

  @Override
  public void onPerformRestart () {
    synchronized (dataLock) {
      contextId++;
      entries.clear();
      // TODO cancel all pending requests
    }
  }

  // Impl

  @UiThread
  public void findOrRequest (long customEmojiId, @NonNull RunnableData<Entry> callback) {
    Watcher watcher = (context, customEmojiId1, entry) ->
      callback.runWithData(entry);
    Entry entry = findOrPostponeRequest(customEmojiId, watcher, true);
    if (entry != null) {
      callback.runWithData(entry);
    } else {
      performPostponedRequest(customEmojiId);
    }
  }

  @Nullable
  public Entry findOrPostponeRequest (long customEmojiId, Watcher watcher) {
    return findOrPostponeRequest(customEmojiId, watcher, false);
  }

  @Nullable
  private Entry findOrPostponeRequest (long customEmojiId, Watcher watcher, boolean strongReference) {
    synchronized (dataLock) {
      Entry entry = entries.get(customEmojiId);
      if (entry != null) {
        return entry;
      }
      if (!loadingCustomEmojiIds.has(customEmojiId)) {
        postponedCustomEmojiIds.add(customEmojiId);
      }
      if (watcher != null) {
        if (strongReference) {
          addWatcherImpl(customEmojiId, watcher);
        } else {
          watcherReferences.add(customEmojiId, watcher);
        }
      }
      return null;
    }
  }

  private void addWatcherImpl (long customEmojiId, Watcher watcher) {
    List<Watcher> list = watchers.get(customEmojiId);
    if (list != null && !list.contains(watcher)) {
      list.add(watcher);
    } else {
      list = new ArrayList<>();
      list.add(watcher);
      watchers.put(customEmojiId, list);
    }
  }

  public void forgetWatcher (long customEmojiId, Watcher watcher) {
    watcherReferences.remove(customEmojiId, watcher);
  }

  private final BatchOperationHandler delayedHandler = new BatchOperationHandler(this::performPostponedRequests, 10);

  @UiThread
  public void performPostponedRequest (long customEmojiId) {
    synchronized (dataLock) {
      if (postponedCustomEmojiIds.isEmpty() || !postponedCustomEmojiIds.remove(customEmojiId)) {
        return;
      }
      loadingCustomEmojiIds.add(customEmojiId);
    }
    requestCustomEmoji(new long[][] {
      {customEmojiId}
    });
  }

  @UiThread
  public void performPostponedRequestsDelayed () {
    delayedHandler.performOperation();
  }

  @UiThread
  public void performPostponedRequests () {
    final long[][] customEmojiIdsChunks;
    synchronized (dataLock) {
      if (postponedCustomEmojiIds.isEmpty()) {
        return;
      }
      loadingCustomEmojiIds.addAll(postponedCustomEmojiIds);
      customEmojiIdsChunks = postponedCustomEmojiIds.toArray(TdConstants.MAX_CUSTOM_EMOJI_COUNT_PER_REQUEST);
      postponedCustomEmojiIds.clear();
    }
    requestCustomEmoji(customEmojiIdsChunks);
  }

  private void requestCustomEmoji (long[][] customEmojiIdsChunks) {
    final int contextId = this.contextId;
    for (long[] customEmojiIds : customEmojiIdsChunks) {
      tdlib.client().send(new TdApi.GetCustomEmojiStickers(customEmojiIds), result -> {
        synchronized (dataLock) {
          if (this.contextId != contextId)
            return;
        }
        switch (result.getConstructor()) {
          case TdApi.Stickers.CONSTRUCTOR: {
            TdApi.Stickers stickers = (TdApi.Stickers) result;
            processStickers(contextId, customEmojiIds, stickers.stickers);
            break;
          }
          case TdApi.Error.CONSTRUCTOR: {
            processError(contextId, customEmojiIds, (TdApi.Error) result);
            break;
          }
        }
        synchronized (dataLock) {
          if (this.contextId != contextId)
            return;
          loadingCustomEmojiIds.removeAll(new LongSet(customEmojiIds));
        }
      });
    }
  }

  @TdlibThread
  private void processStickers (int contextId, long[] customEmojiIds, TdApi.Sticker[] stickers) {
    LongSet remainingIds = stickers.length < customEmojiIds.length ? new LongSet(customEmojiIds) : null;
    for (TdApi.Sticker sticker : stickers) {
      processEntry(contextId, new Entry(sticker));
      if (remainingIds != null) {
        remainingIds.remove(sticker.customEmojiId);
      }
    }
    if (remainingIds != null && !remainingIds.isEmpty()) {
      TdApi.Error error = new TdApi.Error(404, "Not Found");
      for (long customEmojiId : remainingIds) {
        processEntry(contextId, new Entry(customEmojiId, error));
      }
    }
  }

  @TdlibThread
  private void processError (int contextId, long[] customEmojiIds, TdApi.Error error) {
    for (long customEmojiId : customEmojiIds) {
      processEntry(contextId, new Entry(customEmojiId, error));
    }
  }

  @TdlibThread
  private void processEntry (int contextId, Entry entry) {
    List<Watcher> watcherList;
    synchronized (dataLock) {
      if (this.contextId != contextId)
        return;
      entries.put(entry.customEmojiId, entry);
      watcherList = watchers.remove(entry.customEmojiId);
    }
    ReferenceList<Watcher> referenceList = watcherReferences.removeAll(entry.customEmojiId);
    if (referenceList != null) {
      for (Watcher watcher : referenceList) {
        watcher.onCustomEmojiLoaded(this, entry.customEmojiId, entry);
      }
      referenceList.clear();
    }
    if (watcherList != null) {
      for (Watcher watcher : watcherList) {
        watcher.onCustomEmojiLoaded(this, entry.customEmojiId, entry);
      }
    }
  }
}
