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
 * File created on 08/03/2016 at 08:10
 */
package org.thunderdog.challegram.component.chat;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;

import java.util.ArrayList;
import java.util.Collections;

import me.vkryl.core.lambda.CancellableRunnable;
import me.vkryl.td.MessageId;

public class MessagesSearchManager {
  private static final int SEARCH_DELAY = 100;
  private static final int SEARCH_LOAD_LIMIT = 20;

  private static final int FLAG_LOADING = 0x01;
  private static final int FLAG_CAN_LOAD_MORE = 0x02;

  private final Tdlib tdlib;
  private final Delegate delegate;
  private int contextId;
  private int flags;

  private int currentIndex, currentTotalCount;
  private long currentChatId, currentMessageThreadId;
  private TdApi.MessageSender currentFromSender;
  private boolean currentIsSecret;
  private String currentInput;
  private String currentSecretOffset;
  private ArrayList<TdApi.Message> currentSearchResults;

  public interface Delegate {
    void showSearchResult (int index, int totalCount, MessageId messageId);
    void onAwaitNext ();
    void onTryToLoadPrevious ();
    void onTryToLoadNext ();
  }

  public MessagesSearchManager (Tdlib tdlib, Delegate delegate) {
    this.tdlib = tdlib;
    this.delegate = delegate;
  }

  public void onPrepare () {
    // reset();
  }

  public void onDismiss () {
    reset(0l, 0l, "");
  }

  private int reset (long chatId, long messageThreadId, String input) {
    currentChatId = chatId;
    currentMessageThreadId = messageThreadId;
    currentInput = input;
    flags = currentIndex = currentTotalCount = 0;
    if (currentSearchResults != null) {
      currentSearchResults.clear();
    }
    return ++contextId;
  }

  public static final int STATE_NO_INPUT = -1;
  public static final int STATE_LOADING = -2;
  public static final int STATE_NO_RESULTS = -3;

  private CancellableRunnable searchRunnable;

  // Typing the search
  public void search (final long chatId, final long messageThreadId, final TdApi.MessageSender fromSender, final boolean isSecret, final String input) {
    final int contextId = reset(chatId, messageThreadId, input);

    if (input.length() == 0 && fromSender == null) {
      delegate.showSearchResult(STATE_NO_INPUT, 0, null);
      return;
    }

    currentIsSecret = isSecret;
    currentFromSender = fromSender;

    flags |= FLAG_LOADING;
    delegate.showSearchResult(STATE_LOADING, 0, null);

    if (searchRunnable != null) {
      searchRunnable.cancel();
      searchRunnable = null;
    }

    searchRunnable = new CancellableRunnable() {
      @Override
      public void act () {
        searchInternal(contextId, chatId, messageThreadId, fromSender, isSecret, input, 0, null);
      }
    };
    UI.post(searchRunnable, isSecret ? 0 : SEARCH_DELAY);
  }

  private void dispatchSecretMessages (final int contextId, final boolean isMore, final TdApi.FoundMessages messages) {
    if (this.contextId == contextId) {
      UI.post(() -> parseSecretMessages(contextId, isMore, messages));
    }
  }

  private void searchInternal (final int contextId, final long chatId, final long messageThreadId, final TdApi.MessageSender fromSender, final boolean isSecret, final String input, final long fromMessageId, final String nextSearchOffset) {
    if (this.contextId != contextId) {
      return;
    }
    TdApi.Function<?> function;
    if (isSecret) {
      function = new TdApi.SearchSecretMessages(chatId, input, nextSearchOffset, SEARCH_LOAD_LIMIT, new TdApi.SearchMessagesFilterEmpty());
    } else {
      function = new TdApi.SearchChatMessages(chatId, input, fromSender, fromMessageId, 0, SEARCH_LOAD_LIMIT, new TdApi.SearchMessagesFilterEmpty(), messageThreadId);
    }
    tdlib.client().send(function, object -> {
      switch (object.getConstructor()) {
        case TdApi.Messages.CONSTRUCTOR: {
          dispatchMessages(contextId, fromMessageId != 0, (TdApi.Messages) object);
          break;
        }
        case TdApi.FoundMessages.CONSTRUCTOR: {
          dispatchSecretMessages(contextId, fromMessageId != 0, (TdApi.FoundMessages) object);
          break;
        }
        case TdApi.Error.CONSTRUCTOR: {
          UI.showError(object);
          dispatchMessages(contextId, fromMessageId != 0, null);
          break;
        }
        default: {
          if (isSecret) {
            Log.unexpectedTdlibResponse(object, TdApi.SearchSecretMessages.class, TdApi.FoundMessages.class);
          } else {
            Log.unexpectedTdlibResponse(object, TdApi.SearchChatMessages.class, TdApi.Messages.class);
          }
          break;
        }
      }
    });
  }

  private void dispatchMessages (final int contextId, final boolean isMore, final TdApi.Messages messages) {
    if (this.contextId == contextId) {
      UI.post(() -> parseMessages(contextId, isMore, messages));
    }
  }

  private void parseSecretMessages (final int contextId, final boolean isMore, final TdApi.FoundMessages messages) {
    if (this.contextId != contextId) {
      return;
    }
    flags &= ~FLAG_LOADING;
    if (isMore) {
      this.currentSecretOffset = messages.nextOffset;
      if (messages == null || messages.messages.length == 0) {
        flags &= ~FLAG_CAN_LOAD_MORE;
        return;
      }
      Collections.addAll(currentSearchResults, messages.messages);
      delegate.showSearchResult(++currentIndex, currentTotalCount, new MessageId(messages.messages[0].chatId, messages.messages[0].id));
      return;
    }
    if (messages == null || messages.messages.length == 0) {
      delegate.showSearchResult(STATE_NO_RESULTS, 0, null);
      return;
    }
    if (currentSearchResults == null) {
      currentSearchResults = new ArrayList<>(messages.messages.length);
    } else {
      currentSearchResults.clear();
      currentSearchResults.ensureCapacity(messages.messages.length);
    }
    Collections.addAll(currentSearchResults, messages.messages);
    flags |= FLAG_CAN_LOAD_MORE;
    delegate.showSearchResult(currentIndex = 0, currentTotalCount = currentSearchResults.size(), new MessageId(messages.messages[0].chatId, messages.messages[0].id));
  }

  private void parseMessages (final int contextId, final boolean isMore, final TdApi.Messages messages) {
    if (this.contextId != contextId) {
      return;
    }
    flags &= ~FLAG_LOADING;
    if (isMore) {
      if (messages == null || messages.messages.length == 0) {
        flags &= ~FLAG_CAN_LOAD_MORE;
        return;
      }
      Collections.addAll(currentSearchResults, messages.messages);
      delegate.showSearchResult(++currentIndex, currentTotalCount, new MessageId(messages.messages[0].chatId, messages.messages[0].id));
      return;
    }
    if (messages == null || messages.messages.length == 0) {
      delegate.showSearchResult(STATE_NO_RESULTS, 0, null);
      return;
    }
    if (currentSearchResults == null) {
      currentSearchResults = new ArrayList<>();
    } else {
      currentSearchResults.clear();
    }
    Collections.addAll(currentSearchResults, messages.messages);
    if (currentSearchResults.size() < messages.totalCount) {
      flags |= FLAG_CAN_LOAD_MORE;
    }
    delegate.showSearchResult(currentIndex = 0, currentTotalCount = messages.totalCount, new MessageId(messages.messages[0].chatId, messages.messages[0].id));
  }

  public void moveToNext (boolean next) {
    if ((flags & FLAG_LOADING) != 0) {
      return;
    }
    int nextIndex = currentIndex + (next ? 1 : -1);
    if (nextIndex < 0) {
      delegate.onTryToLoadPrevious();
      return;
    }
    if (nextIndex >= currentTotalCount) {
      delegate.onTryToLoadNext();
      return;
    }
    if (currentSearchResults == null) {
      return;
    }
    if (nextIndex < currentSearchResults.size()) {
      TdApi.Message message = currentSearchResults.get(nextIndex);
      delegate.showSearchResult(currentIndex = nextIndex, currentTotalCount, new MessageId(message.chatId, message.id));
    } else if ((flags & FLAG_CAN_LOAD_MORE) != 0) {
      flags |= FLAG_LOADING;
      delegate.onAwaitNext();
      TdApi.Message last = currentSearchResults.isEmpty() ? null : currentSearchResults.get(currentSearchResults.size() - 1);
      // TODO switch to basic group chat
      searchInternal(contextId, currentChatId, currentMessageThreadId, currentFromSender, currentIsSecret, currentInput, last.id, currentSecretOffset);
    }
  }
}
