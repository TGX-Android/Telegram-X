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
 * File created on 03/09/2023
 */
package org.thunderdog.challegram.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class StickerSuggestionsProvider {
  private final Tdlib tdlib;
  private final HashMap<String, TdApi.Stickers> cache = new HashMap<>();
  private CancellableResultHandler suggestionsHandler;

  private String suggestionsEmoji;
  private TdApi.StickerType suggestionsType;
  private Callback suggestionsCallback;
  private long chatId;

  private TdApi.Stickers suggestionsFromLocal;
  private TdApi.Stickers suggestionsFromServer;
  private Set<Long> ignoredStickerIds;

  public interface Callback {
    default void onResultPart (TdApi.Stickers stickers, TdApi.StickerType type, boolean isLocal) {}
    default void onResultFull (Result result) {}
    default void onRequestCanceled (String emoji) {}
  }

  public StickerSuggestionsProvider (Tdlib tdlib) {
    this.tdlib = tdlib;
  }

  @UiThread
  public void getSuggestions (String emoji, TdApi.StickerType type, long chatId, Callback callback) {
    cancel();

    this.chatId = chatId;
    this.suggestionsEmoji = emoji;
    this.suggestionsFromLocal = null;
    this.suggestionsFromServer = null;
    this.suggestionsType = type;
    this.ignoredStickerIds = new HashSet<>();
    this.suggestionsCallback = callback;

    getSuggestionsImpl(true);
  }

  @UiThread
  public void cancel () {
    if (suggestionsHandler != null) {
      suggestionsHandler.cancel();
      suggestionsHandler = null;
      if (suggestionsCallback != null) {
        suggestionsCallback.onRequestCanceled(suggestionsEmoji);
        suggestionsCallback = null;
      }
    }
  }

  @UiThread
  public void clearCache () {
    cache.clear();
  }

  @UiThread
  private void getSuggestionsImpl (boolean isLocal) {
    final int stickerMode = getSearchStickersMode();
    if (stickerMode == Settings.STICKER_MODE_NONE) {
      return;
    }

    if (stickerMode == Settings.STICKER_MODE_ALL && isLocal && tdlib.suggestOnlyApiStickers()) {
      getSuggestionsImpl(false);
      return;
    }

    TdApi.Stickers cached = cache.get(makeCacheKey(suggestionsType, suggestionsEmoji));
    if (!isLocal && cached != null) {
      applyStickerSuggestions(cached, false);
      return;
    }

    TdApi.Function<?> function = isLocal ?
      new TdApi.GetStickers(suggestionsType, suggestionsEmoji, 1000, chatId):
      new TdApi.SearchStickers(suggestionsType, suggestionsEmoji, 1000);

    tdlib.client().send(function, suggestionsHandler = suggestionsHandler(isLocal));
  }

  private CancellableResultHandler suggestionsHandler (final boolean isLocal) {
    return new CancellableResultHandler() {
      @Override
      public void processResult (TdApi.Object object) {
        UI.post(() -> {
          suggestionsHandler = null;
          if (object.getConstructor() != TdApi.Stickers.CONSTRUCTOR) {
            return;
          }
          applyStickerSuggestions((TdApi.Stickers) object, isLocal);
        });
      }
    };
  }

  @UiThread
  private void applyStickerSuggestions (TdApi.Stickers stickers, boolean isLocal) {
    ArrayList<TdApi.Sticker> stickersToAdd = new ArrayList<>(stickers.stickers.length);
    for (TdApi.Sticker sticker: stickers.stickers) {
      if (ignoredStickerIds.contains(sticker.id)) {
        continue;
      }
      stickersToAdd.add(sticker);
      ignoredStickerIds.add(sticker.id);
    }

    TdApi.Stickers result = new TdApi.Stickers(stickersToAdd.toArray(new TdApi.Sticker[0]));
    suggestionsCallback.onResultPart(result, suggestionsType, isLocal);

    boolean isFinish = !isLocal || getSearchStickersMode() != Settings.STICKER_MODE_ALL;
    if (isLocal) {
      suggestionsFromLocal = result;
    } else {
      suggestionsFromServer = result;
      cache.put(makeCacheKey(suggestionsType, suggestionsEmoji), result);
    }
    if (isFinish) {
      suggestionsCallback.onResultFull(new Result(suggestionsEmoji, suggestionsFromLocal, suggestionsFromServer, suggestionsType));
    } else {
      getSuggestionsImpl(false);
    }
  }

  private static String makeCacheKey (TdApi.StickerType type, String emoji) {
    return (type.getConstructor() == TdApi.StickerTypeCustomEmoji.CONSTRUCTOR ? "emoji_": "sticker_") + emoji;
  }

  private int getSearchStickersMode () {
    if (suggestionsType.getConstructor() == TdApi.StickerTypeCustomEmoji.CONSTRUCTOR) {
      return Settings.instance().getEmojiMode();
    } else {
      return Settings.instance().getStickerMode();
    }
  }

  public static class Result {
    public final String emoji;
    public final @NonNull TdApi.Stickers stickersFromLocal;
    public final @NonNull TdApi.Stickers stickersFromServer;
    public final @NonNull TdApi.StickerType type;

    public Result (@NonNull String emoji, @Nullable TdApi.Stickers stickersFromLocal, @Nullable TdApi.Stickers stickersFromServer, @NonNull TdApi.StickerType type) {
      this.stickersFromLocal = stickersFromLocal != null ? stickersFromLocal : new TdApi.Stickers(new TdApi.Sticker[0]);
      this.stickersFromServer = stickersFromServer != null ? stickersFromServer : new TdApi.Stickers(new TdApi.Sticker[0]);
      this.emoji = emoji;
      this.type = type;
    }

    public boolean isEmpty () {
      return size() == 0;
    }

    public int size () {
      return stickersFromLocal.stickers.length + stickersFromServer.stickers.length;
    }
  }
}
