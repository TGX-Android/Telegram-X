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

import java.util.HashMap;
import java.util.Map;

import me.vkryl.core.collection.LongSet;
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
  }

  public interface Watcher {
    void onCustomEmojiLoaded (TdlibEmojiManager context, long customEmojiId, Entry entry);
  }

  private final Tdlib tdlib;
  private final Object dataLock = new Object();
  private final Map<Long, Entry> entries = new HashMap<>();
  private final LongSet postponedCustomEmojiIds = new LongSet();
  private final LongSet loadingCustomEmojiIds = new LongSet();
  private final ReferenceLongMap<Watcher> watchers = new ReferenceLongMap<>(true);

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

  @Nullable
  public Entry findOrPostponeRequest (long customEmojiId, Watcher watcher) {
    synchronized (dataLock) {
      Entry entry = entries.get(customEmojiId);
      if (entry != null) {
        return entry;
      }
      if (!loadingCustomEmojiIds.has(customEmojiId)) {
        postponedCustomEmojiIds.add(customEmojiId);
      }
      if (watcher != null) {
        watchers.add(customEmojiId, watcher);
      }
      return null;
    }
  }

  public void forgetWatcher (long customEmojiId, Watcher watcher) {
    watchers.remove(customEmojiId, watcher);
  }

  @UiThread
  public void performPostponedRequests () {
    final long[][] customEmojiIdsChunks;
    synchronized (dataLock) {
      if (postponedCustomEmojiIds.size() == 0) {
        return;
      }
      loadingCustomEmojiIds.addAll(postponedCustomEmojiIds);
      customEmojiIdsChunks = postponedCustomEmojiIds.toArray(TdConstants.MAX_CUSTOM_EMOJI_COUNT_PER_REQUEST);
      postponedCustomEmojiIds.clear();
    }
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
    synchronized (dataLock) {
      if (this.contextId != contextId)
        return;
      entries.put(entry.customEmojiId, entry);
    }
    ReferenceList<Watcher> list = watchers.removeAll(entry.customEmojiId);
    if (list != null) {
      for (Watcher watcher : list) {
        watcher.onCustomEmojiLoaded(this, entry.customEmojiId, entry);
      }
      list.clear();
    }
  }
}
