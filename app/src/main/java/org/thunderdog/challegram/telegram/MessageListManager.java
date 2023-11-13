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
 */
package org.thunderdog.challegram.telegram;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.data.TD;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.RunnableInt;
import me.vkryl.td.Td;

public final class MessageListManager extends ListManager<TdApi.Message> implements MessageListener, Comparator<TdApi.Message> {
  public interface ChangeListener extends ListManager.ListChangeListener<TdApi.Message> { }

  private final long chatId;
  private final long startFromMessageId;
  private final @Nullable String query;
  private final @Nullable TdApi.MessageSender sender;
  private final @Nullable TdApi.SearchMessagesFilter filter;
  private final long messageThreadId;

  private boolean onlyLocalEndReached, onlyLocalReverseEndReached;

  public MessageListManager (Tdlib tdlib, int initialLoadCount, int loadCount, @Nullable ChangeListener listener, long chatId, long startFromMessageId,
                             @Nullable String query,
                             @Nullable TdApi.MessageSender sender,
                             @Nullable TdApi.SearchMessagesFilter filter,
                             long messageThreadId) {
    super(tdlib, initialLoadCount, loadCount, startFromMessageId != 0, listener);
    this.chatId = chatId;
    this.startFromMessageId = startFromMessageId;
    this.query = query;
    this.sender = sender;
    this.filter = filter;
    this.messageThreadId = messageThreadId;
    subscribeToUpdates();
    loadTotalCount(null);
    addChangeListener(maxMessageIdListener);
  }

  @Override
  protected void loadTotalCount (@Nullable Runnable after) {
    fetchMessageCount(true, count -> {
      if (count != -1) {
        if (getTotalCount() == COUNT_UNKNOWN) {
          setTotalCount(count);
        }
        if (after != null)
          after.run();
      } else {
        fetchMessageCount(false, serverCount -> {
          if (serverCount != -1 && getTotalCount() == COUNT_UNKNOWN) {
            setTotalCount(serverCount);
          }
          if (after != null) {
            after.run();
          }
        });
      }
    });
  }

  public long maxMessageId () {
    return items.isEmpty() ? 0 : items.get(0).id;
  }

  public long minMessageId () {
    return items.isEmpty() ? 0 : items.get(items.size() - 1).id;
  }

  public void ensureMessageAvailability (long messageId) {
    // Make sure that message with specified or lower id is present in the list.
    if (minMessageId() <= messageId || isEndReached()) {
      return;
    }

    // TODO
  }

  private boolean matchesFilter (TdApi.Message message) {
    return message.chatId == chatId && (!hasFilter() || (
      StringUtils.isEmpty(query) && // unsupported
      (messageThreadId == 0 || message.messageThreadId == messageThreadId) &&
      (sender == null || Td.equalsTo(message.senderId, sender)) &&
      (filter == null || Td.matchesFilter(message, filter))
    ));
  }

  private boolean hasFilter () {
    return filter != null || hasComplexFilter();
  }

  private boolean hasComplexFilter () {
    return !StringUtils.isEmpty(query) || sender != null || messageThreadId != 0;
  }

  private void fetchMessageCount (boolean local, @Nullable RunnableInt callback) {
    if (!hasComplexFilter() && filter != null) {
      tdlib.send(new TdApi.GetChatMessageCount(chatId, filter, local), (chatMessageCount, error) -> {
        final int count;
        if (error != null) {
          Log.e("GetChatMessageCount: %s, filter:%s, chatId:%s", TD.toErrorString(error), filter, chatId);
          count = -1;
        } else {
          count = chatMessageCount.count;
        }
        if (callback != null) {
          runOnUiThread(() ->
            callback.runWithInt(count)
          );
        }
      });
    } else {
      TdApi.Function<?> function;
      if (hasComplexFilter()) {
        if (local) {
          if (callback != null) {
            callback.runWithInt(-1);
          }
          return;
        }
        function = new TdApi.SearchChatMessages(chatId, query, sender, 0, 0, 1, filter, messageThreadId);
      } else {
        function = new TdApi.GetChatHistory(chatId, 0, 0, 1, local);
      }
      tdlib.client().send(function, result -> {
        final int count;
        switch (result.getConstructor()) {
          case TdApi.Messages.CONSTRUCTOR: {
            count = ((TdApi.Messages) result).totalCount;
            break;
          }
          case TdApi.FoundChatMessages.CONSTRUCTOR: {
            count = ((TdApi.FoundChatMessages) result).totalCount;
            break;
          }
          case TdApi.Error.CONSTRUCTOR: {
            Log.e("%s: %s, chatId: %d", function.getClass().getSimpleName(), TD.toErrorString(result), chatId);
            count = -1;
            break;
          }
          default:
            Log.unexpectedTdlibResponse(result, function.getClass(), TdApi.Messages.class);
            throw new AssertionError(result.toString());
        }
        if (callback != null) {
          runOnUiThread(() ->
            callback.runWithInt(count)
          );
        }
      });
    }
  }

  private void fetchMessage (long messageId, boolean locally) {
    tdlib.client().send(locally ? new TdApi.GetMessageLocally(chatId, messageId) : new TdApi.GetMessage(chatId, messageId), result -> {
      switch (result.getConstructor()) {
        case TdApi.Message.CONSTRUCTOR: {
          TdApi.Message msg = (TdApi.Message) result;
          if (matchesFilter(msg)) {
            runOnUiThread(() -> {
              addMessage(msg);
            });
          }
          break;
        }
        case TdApi.Error.CONSTRUCTOR: {
          if (locally) { // Try to fetch from server, otherwise do nothing
            fetchMessage(messageId, false);
          }
          break;
        }
      }
    });
  }

  @Override
  protected void subscribeToUpdates () {
    if (chatId != 0) {
      tdlib.listeners().subscribeToMessageUpdates(chatId, this);
    }
  }

  @Override
  protected void unsubscribeFromUpdates () {
    tdlib.listeners().unsubscribeFromMessageUpdates(chatId, this);
  }

  @Override
  public int compare (TdApi.Message o1, TdApi.Message o2) {
    return Long.compare(o2.id, o1.id);
  }

  @Override
  protected TdApi.Function<?> nextLoadFunction (boolean reverse, int itemCount, int loadCount) {
    long fromMessageId = this.items.isEmpty() ? startFromMessageId : this.items.get(reverse ? 0 : this.items.size() - 1).id;
    if (hasFilter()) {
      if (reverse) {
        return new TdApi.SearchChatMessages(chatId, query, sender, fromMessageId, -loadCount, loadCount + 1, filter, messageThreadId);
      } else {
        return new TdApi.SearchChatMessages(chatId, query, sender, nextSearchFromMessageId != null && nextSearchFromMessageId != 0 ? nextSearchFromMessageId : fromMessageId, 0, loadCount, filter, messageThreadId);
      }
    } else {
      if (reverse) {
        return new TdApi.GetChatHistory(chatId, fromMessageId, -itemCount, itemCount + 1, !onlyLocalReverseEndReached);
      } else {
        return new TdApi.GetChatHistory(chatId, fromMessageId, 0, itemCount, !onlyLocalEndReached);
      }
    }
  }

  private Long nextSearchFromMessageId;

  @Override
  protected ListManager.Response<TdApi.Message> processResponse (TdApi.Object response, Client.ResultHandler retryHandler, int retryLoadCount, boolean reverse) {
    List<TdApi.Message> messages;
    int totalCount;
    switch (response.getConstructor()) {
      case TdApi.FoundChatMessages.CONSTRUCTOR: {
        TdApi.FoundChatMessages foundChatMessages = (TdApi.FoundChatMessages) response;
        messages = Arrays.asList(foundChatMessages.messages);
        nextSearchFromMessageId = foundChatMessages.nextFromMessageId;
        totalCount = foundChatMessages.totalCount;
        break;
      }
      case TdApi.Messages.CONSTRUCTOR: {
        TdApi.Messages result = (TdApi.Messages) response;
        messages = Arrays.asList(result.messages);
        totalCount = result.totalCount;
        break;
      }
      default:
        throw new UnsupportedOperationException(response.toString());
    }
    if (!hasFilter() && messages.isEmpty()) {
      if (reverse) {
        onlyLocalReverseEndReached = true;
      } else {
        onlyLocalEndReached = true;
      }
      tdlib.client().send(nextLoadFunction(reverse, this.items.size(), retryLoadCount), retryHandler);
      return null;
    }
    return new Response<>(messages, totalCount);
  }

  // Chat Listeners

  public static final int CAUSE_SEND_SUCCEEDED = 1;
  public static final int CAUSE_SEND_FAILED = 2;
  public static final int CAUSE_CONTENT_CHANGED = 3;
  public static final int CAUSE_EDITED = 4;
  public static final int CAUSE_OPENED = 5;
  public static final int CAUSE_MENTION_READ = 6;
  public static final int CAUSE_INTERACTION_INFO_CHANGED = 7;

  @UiThread
  private int indexOfMessage (long messageId) {
    int i = 0;
    for (TdApi.Message message : this.items) {
      if (message.id == messageId) {
        return i;
      }
      i++;
    }
    return -1;
  }

  private int addMessage (TdApi.Message message) {
    if (matchesFilter(message)) {
      int newIndex = Collections.binarySearch(this.items, message, this);
      if (newIndex >= 0)
        return -1;
      newIndex = newIndex * -1 - 1;
      if (newIndex != items.size() || isEndReached()) {
        items.add(newIndex, message);
        onItemAdded(message, newIndex);
      } else {
        newIndex = -1;
      }
      changeTotalCount(1);
      return newIndex;
    }
    return -1;
  }

  private void replaceMessage (TdApi.Message message, long oldMessageId, int cause) {
    int fromIndex = indexOfMessage(oldMessageId);
    if (fromIndex != -1) {
      TdApi.Message oldMessage = items.remove(fromIndex);
      int newIndex = addMessage(message);
      if (newIndex == fromIndex) {
        notifyItemChanged(fromIndex, cause);
      } else if (newIndex == -1) {
        onItemRemoved(oldMessage, fromIndex);
      } else {
        onItemMoved(oldMessage, fromIndex, newIndex);
        notifyItemChanged(newIndex, cause);
      }
    } else {
      addMessage(message);
    }
  }

  private void removeMessageAt (int index) {
    TdApi.Message message = this.items.remove(index);
    onItemRemoved(message, index);
    changeTotalCount(-1);
  }

  @Override
  public void onNewMessage (TdApi.Message message) {
    if (matchesFilter(message)) {
      runOnUiThreadIfReady(() ->
        addMessage(message)
      );
    }
  }

  @Override
  public void onMessageSendSucceeded (TdApi.Message message, long oldMessageId) {
    if (message.chatId == chatId) {
      runOnUiThreadIfReady(() ->
        replaceMessage(message, oldMessageId, CAUSE_SEND_SUCCEEDED)
      );
    }
  }

  @Override
  public void onMessageSendFailed (TdApi.Message message, long oldMessageId, TdApi.Error error) {
    if (message.chatId == chatId) {
      runOnUiThreadIfReady(() ->
        replaceMessage(message, oldMessageId, CAUSE_SEND_FAILED)
      );
    }
  }

  @Override
  public void onMessageContentChanged (long chatId, long messageId, TdApi.MessageContent newContent) {
    if (this.chatId == chatId) {
      runOnUiThreadIfReady(() -> {
        int index = indexOfMessage(messageId);
        if (index != -1) {
          TdApi.Message message = items.get(index);
          message.content = newContent;
          if (matchesFilter(message)) {
            notifyItemChanged(index, CAUSE_CONTENT_CHANGED);
          } else { // no longer matches the filter
            removeMessageAt(index);
          }
        }
      });
    }
  }

  @Override
  public void onMessageEdited (long chatId, long messageId, int editDate, @Nullable TdApi.ReplyMarkup replyMarkup) {
    if (this.chatId == chatId) {
      runOnUiThreadIfReady(() -> {
        int index = indexOfMessage(messageId);
        if (index != -1) {
          TdApi.Message message = items.get(index);
          message.id = messageId;
          message.editDate = editDate;
          message.replyMarkup = replyMarkup;
          notifyItemChanged(index, CAUSE_EDITED);
        }
      });
    }
  }

  @Override
  public void onMessagePinned (long chatId, long messageId, boolean isPinned) {
    if (this.chatId == chatId) {
      runOnUiThreadIfReady(() -> {
        int index = indexOfMessage(messageId);
        if (index != -1) {
          TdApi.Message message = items.get(index);
          if (message.isPinned != isPinned) {
            message.isPinned = isPinned;
            if (!matchesFilter(message)) { // no longer matches the filter
              removeMessageAt(index);
            }
          }
        } else if (filter != null && Td.isPinnedFilter(filter) && isPinned) {
          fetchMessage(messageId, true);
        }
      });
    }
  }

  @Override
  public void onMessageOpened (long chatId, long messageId) {
    if (this.chatId == chatId) {
      runOnUiThreadIfReady(() -> {
        int index = indexOfMessage(messageId);
        if (index != -1) {
          TdApi.Message message = items.get(index);
          TD.setMessageOpened(message);
          notifyItemChanged(index, CAUSE_OPENED);
        }
      });
    }
  }

  @Override
  public void onMessageMentionRead (long chatId, long messageId) {
    if (this.chatId == chatId) {
      runOnUiThreadIfReady(() -> {
        int index = indexOfMessage(messageId);
        if (index != -1) {
          TdApi.Message message = items.get(index);
          if (message.containsUnreadMention) {
            message.containsUnreadMention = false;
            if (matchesFilter(message)) {
              notifyItemChanged(index, CAUSE_MENTION_READ);
            } else { // no longer matches the filter
              removeMessageAt(index);
            }
          }
        }
      });
    }
  }

  @Override
  public void onMessageUnreadReactionsChanged (long chatId, long messageId, @Nullable TdApi.UnreadReaction[] unreadReactions, int unreadReactionCount) {
    if (this.chatId == chatId) {
      runOnUiThreadIfReady(() -> {
        int index = indexOfMessage(messageId);
        if (index != -1) {
          TdApi.Message message = items.get(index);
          message.unreadReactions = unreadReactions;
          notifyItemChanged(index, CAUSE_INTERACTION_INFO_CHANGED);
        }
      });
    }
  }

  @Override
  public void onMessageInteractionInfoChanged (long chatId, long messageId, @Nullable TdApi.MessageInteractionInfo interactionInfo) {
    if (this.chatId == chatId) {
      runOnUiThreadIfReady(() -> {
        int index = indexOfMessage(messageId);
        if (index != -1) {
          TdApi.Message message = items.get(index);
          message.interactionInfo = interactionInfo;
          notifyItemChanged(index, CAUSE_INTERACTION_INFO_CHANGED);
        }
      });
    }
  }

  @Override
  public void onMessagesDeleted (long chatId, long[] messageIds) {
    if (this.chatId == chatId) {
      runOnUiThreadIfReady(() -> {
        int removedCount = 0;
        for (long messageId : messageIds) {
          int index = indexOfMessage(messageId);
          if (index != -1) {
            TdApi.Message message = items.remove(index);
            onItemRemoved(message, index);
            removedCount++;
          }
        }
        changeTotalCount(-removedCount);
      });
    }
  }

  // Max. message identifier

  private long maxMessageId;

  private final ListManager.ListChangeListener<TdApi.Message> maxMessageIdListener = new ListManager.ListChangeListener<TdApi.Message>() {
    @Override
    public void onItemsAdded (ListManager<TdApi.Message> list, List<TdApi.Message> items, int startIndex, boolean isInitialChunk) {
      if (startIndex == 0) {
        setMaxMessageId(items.get(0).id);
      }
    }

    @Override
    public void onItemAdded (ListManager<TdApi.Message> list, TdApi.Message item, int toIndex) {
      if (toIndex == 0) {
        setMaxMessageId(item.id);
      }
    }

    @Override
    public void onItemChanged (ListManager<TdApi.Message> list, TdApi.Message item, int index, int cause) {
      if (index == 0) {
        setMaxMessageId(item.id);
      }
    }

    @Override
    public void onItemMoved (ListManager<TdApi.Message> list, TdApi.Message item, int fromIndex, int toIndex) {
      if (toIndex == 0) {
        setMaxMessageId(item.id);
      } else if (fromIndex == 0) {
        setMaxMessageId(items.get(0).id);
      }
    }

    @Override
    public void onItemRemoved (ListManager<TdApi.Message> list, TdApi.Message removedItem, int fromIndex) {
      if (fromIndex == 0) {
        setMaxMessageId(items.isEmpty() ? 0 : items.get(0).id);
      }
    }
  };

  public interface MaxMessageIdListener {
    void onMaxMessageIdChanged (ListManager<TdApi.Message> list, long maxMessageId);
  }

  private final List<MaxMessageIdListener> maxMessageIdListeners = new ArrayList<>();

  public void addMaxMessageIdListener (MaxMessageIdListener listener) {
    maxMessageIdListeners.add(listener);
  }

  public void removeMaxMessageIdListener (MaxMessageIdListener listener) {
    maxMessageIdListeners.remove(listener);
  }

  private void setMaxMessageId (long messageId) {
    if (this.maxMessageId != messageId) {
      this.maxMessageId = messageId;
      if (!isDestroyed()) {
        for (int i = maxMessageIdListeners.size() - 1; i >= 0; i--) {
          maxMessageIdListeners.get(i).onMaxMessageIdChanged(this, messageId);
        }
      }
    }
  }

  public long getMaxMessageId () {
    return maxMessageId;
  }
}

