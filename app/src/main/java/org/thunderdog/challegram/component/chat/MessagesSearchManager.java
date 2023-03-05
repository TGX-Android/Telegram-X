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

import androidx.collection.LongSparseArray;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;
import org.jetbrains.annotations.Nullable;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import me.vkryl.core.lambda.CancellableRunnable;
import me.vkryl.td.MessageId;

public class MessagesSearchManager {
  public static final int SEARCH_DIRECTION_TOP = 0;
  public static final int SEARCH_DIRECTION_BOTTOM = 1;
  public static final int SEARCH_DIRECTION_AROUND = 2;

  private static final int SEARCH_DELAY = 100;
  private static final int SEARCH_LOAD_LIMIT = 20;

  private static final int FLAG_LOADING = 0x01;
  private static final int FLAG_CAN_LOAD_MORE_TOP = 0x02;
  private static final int FLAG_CAN_LOAD_MORE_BOTTOM = 0x04;

  private final Tdlib tdlib;
  private final Delegate delegate;
  private int contextId;
  private int flags;

  private final MessagesSearchManagerMiddleware searchManagerMiddleware;

  private TdApi.SearchMessagesFilter currentSearchFilter;
  private int currentTotalCount;
  private long currentChatId, currentMessageThreadId;
  private TdApi.MessageSender currentFromSender;
  private boolean currentIsSecret;
  private String currentInput;
  private String currentSecretOffset;

  private long currentDisplayedMessage = 0;
  private long newestFoundMessageId = -1;
  private final ArrayList<TdApi.Message> currentSearchResults = new ArrayList<>();
  private final LongSparseArray<TdApi.Message> currentSearchResultsArr = new LongSparseArray<>();

  public interface Delegate {
    void showSearchResult (int index, int totalCount, boolean knownIndex, boolean knownTotalCount, MessageId messageId);
    void onSearchUpdateTotalCount (int index, int totalCount, boolean knownIndex, boolean knownTotalCount);
    void onAwaitNext (boolean next);
    void onTryToLoadPrevious ();
    void onTryToLoadNext ();
  }

  public MessagesSearchManager (Tdlib tdlib, Delegate delegate, MessagesSearchManagerMiddleware searchManagerMiddleware) {
    this.tdlib = tdlib;
    this.delegate = delegate;
    this.searchManagerMiddleware = searchManagerMiddleware;
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
    flags = currentTotalCount = 0;
    currentSearchResults.clear();
    currentSearchResultsArr.clear();
    currentDisplayedMessage = 0;
    newestFoundMessageId = -1;
    return ++contextId;
  }

  public static final int STATE_NO_INPUT = -1;
  public static final int STATE_LOADING = -2;
  public static final int STATE_NO_RESULTS = -3;

  private CancellableRunnable searchRunnable;

  private MessageId foundTargetMessageId;

  // Typing the search
  public void search (final long chatId, final long messageThreadId, final TdApi.MessageSender fromSender, final TdApi.SearchMessagesFilter filter, final boolean isSecret, final String input, MessageId foundMsgId) {
    final int contextId = reset(chatId, messageThreadId, input);

    if (input.length() == 0 && fromSender == null && filter == null) {
      delegate.showSearchResult(STATE_NO_INPUT, 0, true, true, null);
      return;
    }

    currentIsSecret = isSecret;
    currentFromSender = fromSender;
    foundTargetMessageId = foundMsgId;
    currentDisplayedMessage = foundMsgId != null ? foundMsgId.getMessageId(): 0;
    currentSearchFilter = filter;
    searchManagerMiddleware.setDelegate(newTotalCount -> {
      delegate.onSearchUpdateTotalCount(getMessageIndex(currentDisplayedMessage), currentTotalCount = newTotalCount, knownIndex(), knownTotalCount());
      currentTotalCount = newTotalCount;
    });

    flags |= FLAG_LOADING;
    delegate.showSearchResult(STATE_LOADING, 0, true, true, null);

    if (searchRunnable != null) {
      searchRunnable.cancel();
      searchRunnable = null;
    }

    searchRunnable = new CancellableRunnable() {
      @Override
      public void act () {
        searchInternal(contextId, chatId, messageThreadId, fromSender, filter, isSecret, input, 0, null, SEARCH_DIRECTION_TOP);
      }
    };
    UI.post(searchRunnable, isSecret ? 0 : SEARCH_DELAY);
  }

  private void searchInternal (final int contextId, final long chatId, final long messageThreadId, final TdApi.MessageSender fromSender, final TdApi.SearchMessagesFilter filter, final boolean isSecret, final String input, final long fromMessageId, final String nextSearchOffset, final int direction) {
    if (this.contextId != contextId) {
      return;
    }

    Client.ResultHandler handler = object -> {
      final boolean isMore = (fromMessageId != 0) && direction != SEARCH_DIRECTION_AROUND;
      switch (object.getConstructor()) {
        case TdApi.Messages.CONSTRUCTOR: {
          TdApi.Messages messages = (TdApi.Messages) object;
          dispatchMessages(contextId, isMore, direction, messages.messages, messages.totalCount);
          break;
        }
        case TdApi.FoundChatMessages.CONSTRUCTOR: {
          TdApi.FoundChatMessages foundChatMessages = (TdApi.FoundChatMessages) object;
          dispatchMessages(contextId, isMore, direction, foundChatMessages.messages, foundChatMessages.totalCount);
          break;
        }
        case TdApi.FoundMessages.CONSTRUCTOR: {
          dispatchSecretMessages(contextId, isMore, (TdApi.FoundMessages) object);
          break;
        }
        case TdApi.Error.CONSTRUCTOR: {
          UI.showError(object);
          dispatchMessages(contextId, isMore, direction, null, 0);
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
    };

    if (isSecret) {
      TdApi.SearchSecretMessages query = new TdApi.SearchSecretMessages(chatId, input, nextSearchOffset, SEARCH_LOAD_LIMIT, filter);
      searchManagerMiddleware.search(query, fromSender, handler);
    } else {
      final int offset = direction == SEARCH_DIRECTION_TOP ? 0 : ( direction == SEARCH_DIRECTION_BOTTOM ? -19: -10);
      TdApi.SearchChatMessages function = new TdApi.SearchChatMessages(chatId, input, fromSender, fromMessageId, offset, SEARCH_LOAD_LIMIT, filter, messageThreadId);
      searchManagerMiddleware.search(function, handler);
    }
  }

  private void dispatchSecretMessages (final int contextId, final boolean isMore, final TdApi.FoundMessages messages) {
    if (this.contextId == contextId) {
      UI.post(() -> parseSecretMessages(contextId, isMore, messages));
    }
  }

  private void dispatchMessages (final int contextId, final boolean isMore, final int direction, final TdApi.Message[] messages, int totalCount) {
    if (this.contextId == contextId) {
      UI.post(() -> parseMessages(contextId, isMore, direction, messages, totalCount));
    }
  }

  private void parseSecretMessages (final int contextId, final boolean isMore, final TdApi.FoundMessages messages) {
    if (this.contextId != contextId) {
      return;
    }
    flags &= ~FLAG_LOADING;
    if (isMore) {
      TdApi.Message currentMessage = currentSearchResultsArr.get(currentDisplayedMessage);
      this.currentSecretOffset = messages.nextOffset;
      if (messages.messages.length == 0) {
        flags &= ~FLAG_CAN_LOAD_MORE_TOP;
        return;
      }
      addAllMessages(messages.messages, SEARCH_DIRECTION_TOP);
      TdApi.Message message = getNextMessage(true);
      if (message == null) {
        flags &= ~FLAG_CAN_LOAD_MORE_TOP;
        if (currentMessage != null) {
          delegate.showSearchResult(getMessageIndex(currentMessage.id), currentTotalCount, knownIndex(), true, new MessageId(currentMessage.chatId, currentMessage.id));
        }
        return;
      }
      currentDisplayedMessage = message.id;
      delegate.showSearchResult(getMessageIndex(currentDisplayedMessage), currentTotalCount, true, true, new MessageId(messages.messages[0].chatId, messages.messages[0].id));
      return;
    }
    if (messages == null || messages.messages.length == 0) {
      delegate.showSearchResult(STATE_NO_RESULTS, 0, true, true, null);
      return;
    }
    currentSearchResults.clear();
    currentSearchResults.ensureCapacity(messages.messages.length);
    addAllMessages(messages.messages, SEARCH_DIRECTION_TOP);
    flags |= FLAG_CAN_LOAD_MORE_TOP;
    currentDisplayedMessage = messages.messages[0].id;
    delegate.showSearchResult(0, currentTotalCount = currentSearchResults.size(), true, true,  new MessageId(messages.messages[0].chatId, messages.messages[0].id));
  }

  private void parseMessages (final int contextId, final boolean isMore, final int direction, final TdApi.Message[] messages, int totalCount) {
    if (this.contextId != contextId) {
      return;
    }
    flags &= ~FLAG_LOADING;
    if (isMore) {
      TdApi.Message currentMessage = currentSearchResultsArr.get(currentDisplayedMessage);
      if (messages == null || messages.length == 0) {
        flags &= ~(direction == SEARCH_DIRECTION_TOP ? FLAG_CAN_LOAD_MORE_TOP: FLAG_CAN_LOAD_MORE_BOTTOM);
        if (currentMessage != null) {
          delegate.showSearchResult(getMessageIndex(currentMessage.id), currentTotalCount, knownIndex(), knownTotalCount(), new MessageId(currentMessage.chatId, currentMessage.id));
        }
        return;
      }
      addAllMessages(messages, direction);
      TdApi.Message message = getNextMessage(direction == SEARCH_DIRECTION_TOP);
      if (message == null) {
        flags &= ~(direction == SEARCH_DIRECTION_TOP ? FLAG_CAN_LOAD_MORE_TOP: FLAG_CAN_LOAD_MORE_BOTTOM);
        if (currentMessage != null) {
          delegate.showSearchResult(getMessageIndex(currentMessage.id), currentTotalCount, knownIndex(), knownTotalCount(), new MessageId(currentMessage.chatId, currentMessage.id));
        }
        return;
      }
      currentDisplayedMessage = message.id;
      delegate.showSearchResult(getMessageIndex(message.id), currentTotalCount, knownIndex(), knownTotalCount(), new MessageId(message.chatId, message.id));
      return;
    }
    if (messages == null || messages.length == 0) {
      delegate.showSearchResult(STATE_NO_RESULTS, 0, true, true, null);
      return;
    }
    if (direction != SEARCH_DIRECTION_AROUND) {
      newestFoundMessageId = messages[0].id;
    }
    currentSearchResults.clear();
    addAllMessages(messages, direction);
    if (currentSearchResults.size() < totalCount) {
      flags |= FLAG_CAN_LOAD_MORE_TOP;
    }
    if (!currentSearchResultsArr.containsKey(newestFoundMessageId)) {
      flags |= FLAG_CAN_LOAD_MORE_BOTTOM;
    }
    if (foundTargetMessageId != null) {
      int index = getMessageIndex(foundTargetMessageId.getMessageId());
      if (index != -1) {
        delegate.showSearchResult(index, currentTotalCount = totalCount, knownIndex(), knownTotalCount(), foundTargetMessageId);
        currentDisplayedMessage = foundTargetMessageId.getMessageId();
        foundTargetMessageId = null;
        return;
      } else if (direction != SEARCH_DIRECTION_AROUND) {
        flags |= FLAG_LOADING;
        currentSearchResults.clear();
        currentSearchResultsArr.clear();
        searchInternal(contextId, currentChatId, currentMessageThreadId, currentFromSender, currentSearchFilter, currentIsSecret, currentInput, foundTargetMessageId.getMessageId(), currentSecretOffset, SEARCH_DIRECTION_AROUND);
        return;
      }
    }
    currentDisplayedMessage = messages[0].id;
    delegate.showSearchResult(0, currentTotalCount = totalCount, knownIndex(), knownTotalCount(), new MessageId(messages[0].chatId, messages[0].id));
  }

  public void moveToNext (boolean next) {
    if ((flags & FLAG_LOADING) != 0) {
      return;
    }

    int prevIndex = getMessageIndex(currentDisplayedMessage);
    int nextIndex = prevIndex + (next ? 1 : -1);
    if (prevIndex < 0 || currentSearchResultsArr.isEmpty()) {
      return;
    }
    if (nextIndex < 0 && knownIndex()) {
      delegate.onTryToLoadPrevious();
      return;
    }
    if (nextIndex >= currentTotalCount) {
      delegate.onTryToLoadNext();
      return;
    }
    if (0 <= nextIndex && nextIndex < currentSearchResultsArr.size()) {
      TdApi.Message message = getMessageByIndex(nextIndex);
      delegate.showSearchResult(getMessageIndex(currentDisplayedMessage = message.id), currentTotalCount, knownIndex(), knownTotalCount(), new MessageId(message.chatId, message.id));
    } else if (next ? canLoadTop(): canLoadBottom()) {
      flags |= FLAG_LOADING;
      delegate.onAwaitNext(next);
      searchInternal(contextId, currentChatId, currentMessageThreadId, currentFromSender, currentSearchFilter, currentIsSecret, currentInput,
        currentDisplayedMessage, currentSecretOffset, next ? SEARCH_DIRECTION_TOP: SEARCH_DIRECTION_BOTTOM);
    }
  }

  public void moveToMessage (MessageId message) {
    int newIndex = getMessageIndex(currentDisplayedMessage = message.getMessageId());
    if (newIndex >= 0) {
      delegate.showSearchResult(newIndex, currentTotalCount, knownIndex(), knownTotalCount(), message);
    } else {
      flags = FLAG_LOADING;
      currentSearchResults.clear();
      currentSearchResultsArr.clear();
      delegate.showSearchResult(STATE_LOADING, 0, true, true, null);
      searchInternal(contextId, currentChatId, currentMessageThreadId, currentFromSender, currentSearchFilter, currentIsSecret, currentInput,
        currentDisplayedMessage, currentSecretOffset, SEARCH_DIRECTION_AROUND);
    }
  }

  public boolean canMoveToNext () {
    final int currentIndex = getMessageIndex(currentDisplayedMessage);
    if (knownIndex()) {
      return currentIndex < (currentTotalCount - 1);
    } else {
      return canLoadTop() || currentIndex < (currentSearchResultsArr.size() - 1);
    }
  }

  public boolean canMoveToPrev () {
    final int currentIndex = getMessageIndex(currentDisplayedMessage);
    return !knownIndex() || currentIndex != 0;
  }

  public boolean isMessageFound (TdApi.Message message) {
    if (message.chatId != currentChatId) return false;
    return currentSearchResultsArr.containsKey(message.id);
  }

  private boolean canLoadBottom () {
    return (flags & FLAG_CAN_LOAD_MORE_BOTTOM) != 0;
  }

  private boolean canLoadTop () {
    return (flags & FLAG_CAN_LOAD_MORE_TOP) != 0;
  }

  private void addAllMessages (TdApi.Message[] messages, int direction) {
    if (direction == SEARCH_DIRECTION_TOP || direction == SEARCH_DIRECTION_AROUND) {
      Collections.addAll(currentSearchResults, messages);
    } else {
      currentSearchResults.addAll(0, Arrays.asList(messages));
    }
    for (TdApi.Message message: messages) {
      currentSearchResultsArr.append(message.id, message);
    }
  }

  private TdApi.Message getMessageByIndex (int index) {
    return currentSearchResultsArr.valueAt(currentSearchResultsArr.size() - 1 - index);
  }

  private int getMessageIndex (long msgId) {
    int index = currentSearchResultsArr.indexOfKey(msgId);
    return (index < 0) ? -1: (currentSearchResultsArr.size() - 1 - index);
  }

  private boolean knownIndex () {
    return currentSearchResultsArr.containsKey(newestFoundMessageId);
  }

  private boolean knownTotalCount () {
    return (!canLoadTop() && !canLoadBottom())
      || (searchManagerMiddleware.getCachedMessagesCount() >= currentTotalCount)
      || (currentSearchResultsArr.size() == currentTotalCount)
      || !((currentSearchFilter != null && currentFromSender != null)
        || (currentFromSender != null && tdlib.isUserChat(currentChatId))
        || MessagesSearchManagerMiddleware.isFilterPolyfill(currentSearchFilter));
  }

  private @Nullable TdApi.Message getNextMessage (boolean next) {
    int prevIndex = currentSearchResultsArr.indexOfKey(currentDisplayedMessage);
    int nextIndex = prevIndex + (next ? -1 : 1);
    return currentSearchResultsArr.valueAt(nextIndex);
  }

}
