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
 * File created on 18/10/2022, 01:17.
 */
package org.thunderdog.challegram.telegram;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;

import java.util.Collection;

import me.vkryl.core.collection.LongSet;
import tgx.td.Td;
import tgx.td.TdConstants;

public final class TdlibEmojiManager extends TdlibDataManager<Long, TdApi.Sticker, TdlibEmojiManager.Entry> {
  public static class Entry extends AbstractEntry<Long, TdApi.Sticker> {
    public final long customEmojiId;

    public Entry (@NonNull Long key, @Nullable TdApi.Sticker value, @Nullable TdApi.Error error) {
      super(key, value, error);
      this.customEmojiId = key;
    }

    public boolean isAnimated () {
      return value != null && Td.isAnimated(value.format);
    }

    public boolean isStatic () {
      return value != null && !Td.isAnimated(value.format);
    }
  }

  public interface Watcher extends TdlibDataManager.Watcher<Long, TdApi.Sticker, TdlibEmojiManager.Entry> {
    void onCustomEmojiLoaded (TdlibEmojiManager context, Entry entry);

    @Override
    default void onEntryLoaded (TdlibDataManager<Long, TdApi.Sticker, Entry> context, Entry entry) {
      onCustomEmojiLoaded((TdlibEmojiManager) context, entry);
    }
  }

  public TdlibEmojiManager (Tdlib tdlib) {
    super(tdlib);
  }

  @Override
  protected Entry newEntry (@NonNull Long key, @Nullable TdApi.Sticker value, @Nullable TdApi.Error error) {
    return new Entry(key, value, error);
  }

  @Override
  protected void requestData (int contextId, Collection<Long> keysToRequest) {
    long[][] customEmojiIdsChunks = toLongArray(keysToRequest, TdConstants.MAX_CUSTOM_EMOJI_COUNT_PER_REQUEST);
    if (customEmojiIdsChunks.length == 0) {
      return;
    }
    for (long[] customEmojiIds : customEmojiIdsChunks) {
      tdlib.send(new TdApi.GetCustomEmojiStickers(customEmojiIds), (stickers, error) -> {
        if (isCancelled(contextId)) {
          return;
        }
        if (error != null) {
          processError(contextId, customEmojiIds, error);
        } else {
          processStickers(contextId, customEmojiIds, stickers.stickers);
        }
      });
    }
  }

  private void processError (int contextId, long[] customEmojiIds, TdApi.Error error) {
    for (long customEmojiId : customEmojiIds) {
      processError(contextId, customEmojiId, error);
    }
  }

  @TdlibThread
  private void processStickers (int contextId, long[] requestedCustomEmojiIds, TdApi.Sticker[] stickers) {
    LongSet remainingCustomEmojiIds = new LongSet(requestedCustomEmojiIds);
    for (TdApi.Sticker sticker : stickers) {
      long customEmojiId = Td.customEmojiId(sticker);
      if (remainingCustomEmojiIds.remove(customEmojiId)) {
        processData(contextId, customEmojiId, sticker);
      } else {
        throw new IllegalArgumentException("GetCustomEmojiStickers returned arbitrary emoji: " + customEmojiId);
      }
    }
    if (!remainingCustomEmojiIds.isEmpty()) {
      for (long customEmojiId : remainingCustomEmojiIds) {
        processData(contextId, customEmojiId, null);
      }
    }
  }

  private static long[][] toLongArray (Collection<Long> list, int limit) {
    final int size = list.size();
    if (size == 0) {
      return new long[0][];
    }
    final int arrayCount = (int) Math.ceil((double) size / (double) limit);
    long[][] result = new long[arrayCount][];
    int index = 0;
    for (Long item : list) {
      int arrayIndex = index / limit;
      int itemIndex = index - limit * arrayIndex;
      if (itemIndex == 0) {
        result[arrayIndex] = new long[Math.min(limit, size - index)];
      }
      result[arrayIndex][itemIndex] = item;
      index++;
    }
    return result;
  }
}
