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
 * File created on 03/05/2019
 */
package org.thunderdog.challegram.telegram;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.data.TD;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import me.vkryl.core.lambda.Filter;
import me.vkryl.core.lambda.RunnableData;
import me.vkryl.core.lambda.RunnableLong;
import me.vkryl.core.util.FilteredIterator;

public class TdlibResourceManager {
  private final Tdlib tdlib;
  private final String channelUsername;
  private long chatId;

  public TdlibResourceManager (Tdlib tdlib, String channelUsername) {
    this.tdlib = tdlib;
    this.channelUsername = channelUsername;
  }

  private void withChat (RunnableLong callback)  {
    tdlib.awaitInitialization(() -> {
      tdlib.incrementJobReferenceCount();
      if (chatId != 0) {
        callback.runWithLong(chatId);
        tdlib.decrementJobReferenceCount();
      } else {
        tdlib.client().send(new TdApi.SearchPublicChat(channelUsername), chatResult -> {
          switch (chatResult.getConstructor()) {
            case TdApi.Chat.CONSTRUCTOR: {
              chatId = ((TdApi.Chat) chatResult).id;
              break;
            }
            case TdApi.Error.CONSTRUCTOR: {
              Log.e("Unable to get resources channel @%s: %s", channelUsername, TD.toErrorString(chatResult));
              break;
            }
          }
          callback.runWithLong(chatId);
          tdlib.decrementJobReferenceCount();
        });
      }
    });
  }

  private void fetchRemoteMessages (RunnableData<List<TdApi.Message>> onDone) {
    withChat(chatId -> {
      if (chatId == 0) {
        onDone.runWithData(null);
        return;
      }
      tdlib.incrementJobReferenceCount();
      tdlib.openChat(chatId, null);
      tdlib.fetchAllMessages(chatId, null, new TdApi.SearchMessagesFilterDocument(), messages -> {
        tdlib.closeChat(chatId, null, false);
        onDone.runWithData(messages);
        tdlib.decrementJobReferenceCount();
      });
    });
  }

  public void findResource (RunnableData<TdApi.Message> onDone, String query, long afterDateMs) {
    withChat(chatId -> {
      if (chatId == 0) {
        onDone.runWithData(null);
        return;
      }
      tdlib.incrementJobReferenceCount();
      tdlib.openChat(chatId, null, () -> {
        tdlib.client().send(new TdApi.SearchChatMessages(chatId, query, null, 0, 0, 1, new TdApi.SearchMessagesFilterDocument(), 0, 0), result -> {
          switch (result.getConstructor()) {
            case TdApi.FoundChatMessages.CONSTRUCTOR: {
              TdApi.FoundChatMessages messages = (TdApi.FoundChatMessages) result;
              if (messages.messages.length > 0 && TimeUnit.SECONDS.toMillis(messages.messages[0].date) > afterDateMs) {
                onDone.runWithData(messages.messages[0]);
              } else {
                onDone.runWithData(null);
              }
              break;
            }
            case TdApi.Error.CONSTRUCTOR: {
              Log.e("Unable to fetch resource in @%s: %s", channelUsername, TD.toErrorString(result));
              onDone.runWithData(null);
              break;
            }
          }
          tdlib.closeChat(chatId, null, false);
          tdlib.decrementJobReferenceCount();
        });
      });
    });
  }

  public void fetchResources (RunnableData<List<TdApi.Message>> onDone, Filter<TdApi.Message> filter) {
    fetchRemoteMessages(messages -> {
      if (filter != null && messages != null) {
        List<TdApi.Message> filtered = new ArrayList<>();
        for (TdApi.Message message : new FilteredIterator<>(messages, filter)) {
          filtered.add(message);
        }
        onDone.runWithData(filtered);
      } else if (messages != null) {
        onDone.runWithData(messages);
      } else {
        onDone.runWithData(new ArrayList<>());
      }
    });
  }
}
