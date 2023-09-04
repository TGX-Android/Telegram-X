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
import androidx.annotation.UiThread;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class StickerSuggestionsProvider {
  private final Tdlib tdlib;

  public interface Callback {
    default void onResultPart (@NonNull String emoji, @NonNull TdApi.Stickers stickers, @NonNull TdApi.StickerType type, boolean isLocal) {}
    default void onResultFull (@NonNull String emoji, @NonNull TdApi.Stickers stickersFromLocal, @NonNull TdApi.Stickers stickersFromServer, @NonNull TdApi.StickerType type) {}
  }

  public StickerSuggestionsProvider (Tdlib tdlib) {
    this.tdlib = tdlib;
  }

  @UiThread
  public void getSuggestions (String emoji, TdApi.StickerType type, long chatId, Callback callback) {
    final SearchContext context = new SearchContext(emoji, type, chatId, callback);
    if (context.mode == Settings.STICKER_MODE_NONE) {
      return;
    }

    getSuggestionsImpl(context, true);
  }

  @UiThread
  private void getSuggestionsImpl (final SearchContext context, final boolean isLocal) {
    if (context.mode == Settings.STICKER_MODE_ALL && isLocal && tdlib.suggestOnlyApiStickers()) {
      getSuggestionsImpl(context, false);
      return;
    }

    TdApi.Function<?> function = isLocal ?
      new TdApi.GetStickers(context.type, context.emoji, 1000, context.chatId):
      new TdApi.SearchStickers(context.type, context.emoji, 1000);

    tdlib.client().send(function, object -> {
      if (object.getConstructor() != TdApi.Stickers.CONSTRUCTOR) {
        return;
      }
      UI.post(() -> {
        final boolean needSearchMore = context.applySuggestions((TdApi.Stickers) object, isLocal);
        if (needSearchMore) {
          getSuggestionsImpl(context, false);
        }
      });
    });
  }

  private static class SearchContext {
    public final String emoji;
    public final TdApi.StickerType type;
    public final long chatId;
    public final Callback callback;
    public final int mode;
    public final Set<Long> ignoredStickerIds = new HashSet<>();

    public TdApi.Stickers suggestionsFromLocal;
    public TdApi.Stickers suggestionsFromServer;

    public SearchContext (String emoji, TdApi.StickerType type, long chatId, Callback callback) {
      this.emoji = emoji;
      this.type = type;
      this.chatId = chatId;
      this.callback = callback;
      this.mode = type.getConstructor() == TdApi.StickerTypeCustomEmoji.CONSTRUCTOR ?
        Settings.instance().getEmojiMode() : Settings.instance().getStickerMode();
    }

    @UiThread
    public boolean applySuggestions (final TdApi.Stickers stickers, boolean isLocal) {
      ArrayList<TdApi.Sticker> stickersToAdd = new ArrayList<>(stickers.stickers.length);
      for (TdApi.Sticker sticker : stickers.stickers) {
        if (ignoredStickerIds.contains(sticker.id)) {
          continue;
        }
        stickersToAdd.add(sticker);
        ignoredStickerIds.add(sticker.id);
      }

      TdApi.Stickers result = new TdApi.Stickers(stickersToAdd.toArray(new TdApi.Sticker[0]));
      callback.onResultPart(emoji, result, type, isLocal);

      boolean isFinish = !isLocal || mode != Settings.STICKER_MODE_ALL;
      if (isLocal) {
        suggestionsFromLocal = result;
      } else {
        suggestionsFromServer = result;
      }
      if (isFinish) {
        callback.onResultFull(emoji, suggestionsFromLocal, suggestionsFromServer, type);
      }
      return !isFinish;
    }
  }
}
