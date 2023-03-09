/*
 * This file is a part of Telegram X
 * Copyright © 2014-2023 (tgx-android@pm.me)
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

import org.drinkless.td.libcore.telegram.TdApi;

import java.util.Collection;

public final class TdlibEmojiReactionsManager extends TdlibDataManager<String, TdApi.EmojiReaction, TdlibEmojiReactionsManager.Entry> {
  public static class Entry extends AbstractEntry<String, TdApi.EmojiReaction> {
    public Entry (@NonNull String key, @Nullable TdApi.EmojiReaction value, @Nullable TdApi.Error error) {
      super(key, value, error);
    }
  }

  public interface Watcher extends TdlibDataManager.Watcher<String, TdApi.EmojiReaction, TdlibEmojiReactionsManager.Entry> { }

  public TdlibEmojiReactionsManager (Tdlib tdlib) {
    super(tdlib);
  }

  @Override
  protected Entry newEntry (@NonNull String key, @Nullable TdApi.EmojiReaction value, @Nullable TdApi.Error error) {
    return new Entry(key, value, error);
  }

  @Override
  protected void requestData (int contextId, Collection<String> keysToRequest) {
    for (String emoji : keysToRequest) {
      tdlib.client().send(new TdApi.GetEmojiReaction(emoji), result -> {
        switch (result.getConstructor()) {
          case TdApi.EmojiReaction.CONSTRUCTOR: {
            TdApi.EmojiReaction reaction = (TdApi.EmojiReaction) result;
            processData(contextId, emoji, reaction);
            break;
          }
          case TdApi.Error.CONSTRUCTOR: {
            TdApi.Error error = (TdApi.Error) result;
            processError(contextId, emoji, error);
            break;
          }
        }
      });
    }
  }
}
