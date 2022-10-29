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

import android.annotation.SuppressLint;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.vkryl.core.collection.LongSet;
import me.vkryl.core.lambda.CancellableRunnable;
import me.vkryl.td.MessageId;
import me.vkryl.td.Td;

public class MessagesSearchManager {
  private static final int SEARCH_DELAY = 100;
  private static final int SEARCH_LOAD_LIMIT = 20;
  private static final int PREFETCH_MESSAGE_COUNT = 3;

  private static final int FLAG_LOADING = 0x01;
  private static final int FLAG_CAN_LOAD_MORE = 0x02;

  private final Tdlib tdlib;
  private final Delegate delegate;
  private int contextId;
  private int flags;

  private int currentIndex, currentTotalCount;
  private long currentChatId, currentMessageThreadId;
  private TdApi.MessageSender currentFromSender;
  private TdApi.SearchMessagesFilter currentMessageTypeFilter;
  private boolean isLocalMessageTypeFilter;
  private boolean currentIsSecret;
  private String currentInput;
  private String currentSecretOffset;
  private ArrayList<TdApi.Message> currentSearchResults;
  private ArrayList<TdApi.Message> locallySearchResults;
  private int[] availableSearchTypes;

  public interface Delegate {
    void showSearchResult (int index, int totalCount, MessageId messageId);
    void availableSearchTypes (List<TdApi.MessageContent> types);
    void availableSearchSenders (List<Long> senders);
    void onAwaitNext ();
    void onTryToLoadPrevious ();
    void onTryToLoadNext ();
    void highlightSearchResult (List<Long> messagesId, String query);
    void clearHighlightedSearchResult ();
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
      delegate.clearHighlightedSearchResult();
      currentSearchResults.clear();
    }
    if (locallySearchResults != null) {
      delegate.clearHighlightedSearchResult();
      locallySearchResults.clear();
    }
    if (availableSearchTypes != null) {
      availableSearchTypes = null;
    }
    currentFromSender = null;
    currentMessageTypeFilter = null;
    isLocalMessageTypeFilter = false;
    return ++contextId;
  }

  public static final int STATE_NO_INPUT = -1;
  public static final int STATE_LOADING = -2;
  public static final int STATE_NO_RESULTS = -3;

  private CancellableRunnable searchRunnable;

  // Typing the search
  public void search (final long chatId, final long messageThreadId, final TdApi.MessageSender fromSender, final TdApi.SearchMessagesFilter filter, final boolean isSecret, final String input, final boolean needHighlightSearchResult) {
    final int contextId = reset(chatId, messageThreadId, input);

    if (input.length() == 0 && fromSender == null) {
      delegate.showSearchResult(STATE_NO_INPUT, 0, null);
      return;
    }

    currentIsSecret = isSecret;
    currentFromSender = fromSender;
    currentMessageTypeFilter = filter;

    flags |= FLAG_LOADING;
    delegate.showSearchResult(STATE_LOADING, 0, null);

    if (searchRunnable != null) {
      searchRunnable.cancel();
      searchRunnable = null;
    }

    searchRunnable = new CancellableRunnable() {
      @Override
      public void act () {
        searchInternal(contextId, chatId, messageThreadId, fromSender, filter, isSecret, input, 0, null, needHighlightSearchResult);
      }
    };
    UI.post(searchRunnable, isSecret ? 0 : SEARCH_DELAY);
  }

  private void dispatchSecretMessages (final int contextId, final boolean isMore, final TdApi.FoundMessages messages, final String query, final boolean withIndexHighlight) {
    if (this.contextId == contextId) {
      UI.post(() -> parseSecretMessages(contextId, isMore, messages, query, withIndexHighlight));
    }
  }

  private void searchInternal (final int contextId, final long chatId, final long messageThreadId, final TdApi.MessageSender fromSender, final TdApi.SearchMessagesFilter filter, final boolean isSecret, final String input, final long fromMessageId, final String nextSearchOffset, final boolean withIndexHighlight) {
    if (this.contextId != contextId) {
      return;
    }
    isLocalMessageTypeFilter = fromSender != null && !(filter == null || filter.getConstructor() == TdApi.SearchMessagesFilterEmpty.CONSTRUCTOR);
    TdApi.Function<?> function;
    if (isSecret) {
      function = new TdApi.SearchSecretMessages(chatId, input, nextSearchOffset, SEARCH_LOAD_LIMIT, isLocalMessageTypeFilter ? null : filter);
    } else {
      function = new TdApi.SearchChatMessages(chatId, input, fromSender, fromMessageId, 0, SEARCH_LOAD_LIMIT, isLocalMessageTypeFilter ? null : filter, messageThreadId);
    }
    tdlib.client().send(function, object -> {
      switch (object.getConstructor()) {
        case TdApi.Messages.CONSTRUCTOR: {
          dispatchMessages(contextId, fromMessageId != 0, (TdApi.Messages) object, input, withIndexHighlight);
          break;
        }
        case TdApi.FoundMessages.CONSTRUCTOR: {
          dispatchSecretMessages(contextId, fromMessageId != 0, (TdApi.FoundMessages) object, input, withIndexHighlight);
          break;
        }
        case TdApi.Error.CONSTRUCTOR: {
          UI.showError(object);
          dispatchMessages(contextId, fromMessageId != 0, null, input, withIndexHighlight);
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

  private void dispatchMessages (final int contextId, final boolean isMore, final TdApi.Messages messages, final String query, final boolean withIndexHighlight) {
    if (this.contextId == contextId) {
      UI.post(() -> parseMessages(contextId, isMore, messages, query, withIndexHighlight));
    }
  }

  private void parseSecretMessages (final int contextId, final boolean isMore, final TdApi.FoundMessages messages, final String query, final boolean withIndexHighlight) {
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
      addUniqueSearchItems(messages.messages);
      if (isLocalMessageTypeFilter) {
        filterMessagesLocally(currentMessageTypeFilter, false, true);
      } else {
        delegate.highlightSearchResult(convertToIds(currentSearchResults), query);
      }
      delegate.showSearchResult(++currentIndex, currentTotalCount, withIndexHighlight ? new MessageId(messages.messages[0].chatId, messages.messages[0].id) : null);
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
    if (isLocalMessageTypeFilter) {
      filterMessagesLocally(currentMessageTypeFilter, true, true);
    } else {
      delegate.clearHighlightedSearchResult();
      delegate.showSearchResult(currentIndex = 0, currentTotalCount = currentSearchResults.size(), withIndexHighlight ? new MessageId(messages.messages[0].chatId, messages.messages[0].id) : null);
      delegate.highlightSearchResult(convertToIds(currentSearchResults), query);
    }
  }

  private void parseMessages (final int contextId, final boolean isMore, final TdApi.Messages messages, final String query, final boolean withIndexHighlight) {
    if (this.contextId != contextId) {
      return;
    }
    flags &= ~FLAG_LOADING;
    if (isMore) {
      if (messages == null || messages.messages.length == 0) {
        flags &= ~FLAG_CAN_LOAD_MORE;
        return;
      }
      addUniqueSearchItems(messages.messages);
      if (isLocalMessageTypeFilter) {
        filterMessagesLocally(currentMessageTypeFilter, false, true);
      } else {
        delegate.highlightSearchResult(convertToIds(currentSearchResults), query);
      }
      delegate.showSearchResult(++currentIndex, currentTotalCount, withIndexHighlight ? new MessageId(messages.messages[0].chatId, messages.messages[0].id) : null);
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
    if (isLocalMessageTypeFilter) {
      filterMessagesLocally(currentMessageTypeFilter, true, true);
    } else {
      delegate.clearHighlightedSearchResult();
      delegate.highlightSearchResult(convertToIds(currentSearchResults), query);
      delegate.showSearchResult(currentIndex = 0, currentTotalCount = messages.totalCount, withIndexHighlight ? new MessageId(messages.messages[0].chatId, messages.messages[0].id) : null);
    }
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
    if (isLocalMessageTypeFilter && nextIndex < locallySearchResults.size()) {
      TdApi.Message message = locallySearchResults.get(nextIndex);
      delegate.showSearchResult(currentIndex = nextIndex, locallySearchResults.size(), new MessageId(message.chatId, message.id));
      return;
    }
    if (nextIndex < currentSearchResults.size()) {
      TdApi.Message message = currentSearchResults.get(nextIndex);
      delegate.showSearchResult(currentIndex = nextIndex, currentTotalCount, new MessageId(message.chatId, message.id));
    } else {
      loadNext(true);
    }
  }

  public boolean isLocalMessageTypeFilter () {
    return isLocalMessageTypeFilter;
  }

  public long getPrefetchedMessageId () {
    if (currentSearchResults != null && currentSearchResults.size() > PREFETCH_MESSAGE_COUNT) {
      return currentSearchResults.get(currentSearchResults.size() - PREFETCH_MESSAGE_COUNT).id;
    } else {
      return -1;
    }
  }

  public void loadNext (boolean withIndexHighlight) {
    if ((flags & FLAG_LOADING) != 0) {
      return;
    }
    if ((flags & FLAG_CAN_LOAD_MORE) != 0) {
      flags |= FLAG_LOADING;
      delegate.onAwaitNext();
      // TODO switch to basic group chat
      searchInternal(contextId, currentChatId, currentMessageThreadId, currentFromSender, currentMessageTypeFilter, currentIsSecret, currentInput, getPrefetchedMessageId(), currentSecretOffset, withIndexHighlight);
    }
  }

  public void processLoadedMessages (ArrayList<TGMessage> items) {
    LongSet set = new LongSet();
    for (TGMessage message: items) {
      set.add(message.getId());
    }
    processLoadedMessages(set);
  }

  public void processLoadedMessages (LongSet msgIds) {
    if (currentSearchResults != null) {
      ArrayList<Long> searchIds = convertToIds(currentSearchResults);
      ArrayList<Long> idsToHighlight = new ArrayList<>();
      for (long id : msgIds) {
        if (searchIds.contains(id)) {
          idsToHighlight.add(id);
        }
      }
      if (isLocalMessageTypeFilter) {
        filterMessagesLocally(currentMessageTypeFilter, true, false);
      } else {
        delegate.highlightSearchResult(idsToHighlight, currentInput);
      }
    }
  }

  public String getCurrentInput () {
    return currentInput;
  }

  public void setSearchSender (final TdApi.MessageSender messageSender) {
    isLocalMessageTypeFilter = messageSender != null && !(currentMessageTypeFilter == null || currentMessageTypeFilter.getConstructor() == TdApi.SearchMessagesFilterEmpty.CONSTRUCTOR);
    currentFromSender = messageSender;
    if (currentInput != null && !currentInput.isEmpty()) {
      searchInternal(contextId, currentChatId, currentMessageThreadId, currentFromSender, currentMessageTypeFilter, currentIsSecret, currentInput, 0, currentSecretOffset, true);
    }
  }

  public void setSearchFilterType (final TdApi.SearchMessagesFilter messagesType) {
    isLocalMessageTypeFilter = currentFromSender != null && messagesType.getConstructor() != TdApi.SearchMessagesFilterEmpty.CONSTRUCTOR;
    if (isLocalMessageTypeFilter) {
      filterMessagesLocally(messagesType, false, true);
    } else {
      currentMessageTypeFilter = messagesType;
      if (currentInput != null && !currentInput.isEmpty()) {
        searchInternal(contextId, currentChatId, currentMessageThreadId, currentFromSender, currentMessageTypeFilter, currentIsSecret, currentInput, 0, currentSecretOffset, true);
      }
    }
  }

  private void filterMessagesLocally (final TdApi.SearchMessagesFilter messagesType, boolean withClear, boolean highlightSearchResult) {
    if (locallySearchResults == null) {
      locallySearchResults = new ArrayList<>();
    }
    if (currentMessageTypeFilter == null || currentMessageTypeFilter.getConstructor() != messagesType.getConstructor() || withClear) {
      currentMessageTypeFilter = messagesType != null ? messagesType : new TdApi.SearchMessagesFilterEmpty();
      locallySearchResults.clear();
    }
    for (TdApi.Message message: currentSearchResults) {
      if (isMessageTypeSame(message.content, currentMessageTypeFilter)) {
        locallySearchResults.add(message);
      }
    }
    if (locallySearchResults.isEmpty()) {
      delegate.clearHighlightedSearchResult();
      delegate.showSearchResult(STATE_NO_RESULTS, 0, null);
    } else {
      delegate.clearHighlightedSearchResult();
      delegate.highlightSearchResult(convertToIds(locallySearchResults), currentInput);
      if (highlightSearchResult) {
        delegate.showSearchResult(currentIndex = 0, currentTotalCount = locallySearchResults.size(), new MessageId(locallySearchResults.get(0).chatId, locallySearchResults.get(0).id));
      }
    }
  }

  private ArrayList<Long> convertToIds (List<TdApi.Message> messages) {
    ArrayList<Long> ids = new ArrayList<>(messages.size());
    for (TdApi.Message message: messages) {
      ids.add(message.id);
    }
    return ids;
  }

  // TODO optimize
  private void addUniqueSearchItems (TdApi.Message[] messages) {
    ArrayList<Long> ids = convertToIds(currentSearchResults);
    for (TdApi.Message message: messages) {
      if (!ids.contains(message.id)) {
        currentSearchResults.add(message);
      }
    }
  }

  @SuppressLint("SwitchIntDef")
  public static boolean isMessageTypeSame (TdApi.MessageContent msg, TdApi.SearchMessagesFilter filter) {
    switch (filter.getConstructor()) {
      case TdApi.SearchMessagesFilterEmpty.CONSTRUCTOR: {
        return true;
      }
      case SearchMessagesFilterText.CONSTRUCTOR: {
        return msg.getConstructor() == TdApi.MessageText.CONSTRUCTOR;
      }
      case TdApi.SearchMessagesFilterPhoto.CONSTRUCTOR: {
        return msg.getConstructor() == TdApi.MessagePhoto.CONSTRUCTOR;
      }
      case TdApi.SearchMessagesFilterVideo.CONSTRUCTOR: {
        return msg.getConstructor() == TdApi.MessageVideo.CONSTRUCTOR;
      }
      case TdApi.SearchMessagesFilterVoiceNote.CONSTRUCTOR: {
        return msg.getConstructor() == TdApi.MessageVoiceNote.CONSTRUCTOR;
      }
      case TdApi.SearchMessagesFilterVideoNote.CONSTRUCTOR: {
        return msg.getConstructor() == TdApi.MessageVideoNote.CONSTRUCTOR;
      }
      case TdApi.SearchMessagesFilterDocument.CONSTRUCTOR: {
        return msg.getConstructor() == TdApi.MessageDocument.CONSTRUCTOR;
      }
      case TdApi.SearchMessagesFilterAudio.CONSTRUCTOR: {
        return msg.getConstructor() == TdApi.MessageAudio.CONSTRUCTOR;
      }
      case TdApi.SearchMessagesFilterAnimation.CONSTRUCTOR: {
        return msg.getConstructor() == TdApi.MessageAnimation.CONSTRUCTOR;
      }
      default: {
        return false;
      }
    }
  }

  public ArrayList<TGMessage> filterMessagesByTypeAndSender (ArrayList<TGMessage> messages) {
    return filterMessagesByTypeAndSender(messages, convertToIds(currentSearchResults), currentMessageTypeFilter, currentFromSender);
  }

  public static ArrayList<TGMessage> filterMessagesByTypeAndSender (ArrayList<TGMessage> messages, ArrayList<Long> searchMsgIds, TdApi.SearchMessagesFilter filter, TdApi.MessageSender sender) {
    ArrayList<TGMessage> result = new ArrayList<>();
    for (TGMessage message: messages) {
      if ((filter == null || isMessageTypeSame(message.getMessage().content, filter)) && (sender == null || Td.equalsTo(message.getMessage().senderId, sender)) && containsMsgId(searchMsgIds, message)) {
        result.add(message);
      }
    }
    return result;
  }

  public static boolean containsMsgId (ArrayList<Long> ids, TGMessage message) {
    for (long id : message.getIds()) {
      if (ids.contains(id)) {
        return true;
      }
    }
    return false;
  }
}