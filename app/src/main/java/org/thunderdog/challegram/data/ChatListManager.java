package org.thunderdog.challegram.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.telegram.ChatListener;
import org.thunderdog.challegram.telegram.CounterChangeListener;
import org.thunderdog.challegram.telegram.ListManager;
import org.thunderdog.challegram.telegram.Tdlib;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import me.vkryl.core.lambda.Filter;
import me.vkryl.td.ChatPosition;
import me.vkryl.td.Td;

/**
 * Date: 2019-12-21
 * Author: default
 */
public final class ChatListManager extends ListManager<ChatListManager.ChatEntry> implements ChatListener, CounterChangeListener, Comparator<ChatListManager.ChatEntry> {
  public static class ChatEntry {
    public final long chatId;
    public long order;

    public ChatEntry (long chatId, long order) {
      this.chatId = chatId;
      this.order = order;
    }
  }

  @NonNull
  private final TdApi.ChatList chatList;
  @Nullable
  private final Filter<TdApi.Chat> filter;

  public interface ChangeListener extends ListManager.ListChangeListener<ChatListManager.ChatEntry> { }

  public ChatListManager (Tdlib tdlib, int initialLoadCount, int loadCount, @Nullable ChangeListener listener, @NonNull TdApi.ChatList chatList, @Nullable Filter<TdApi.Chat> filter) {
    super(tdlib, initialLoadCount, loadCount, false, listener);
    this.chatList = chatList;
    this.filter = filter;
    setTotalCount(tdlib.getTotalChatsCount(chatList));
  }

  @NonNull
  public TdApi.ChatList getChatList () {
    return chatList;
  }

  @Override
  protected void subscribeToUpdates () {
    tdlib.listeners().subscribeForAnyUpdates(this);
  }

  @Override
  protected void unsubscribeFromUpdates () {
    tdlib.listeners().unsubscribeFromAnyUpdates(this);
  }

  @Override
  public final int compare (ChatEntry o1, ChatEntry o2) {
    return o1.order != o2.order ? Long.compare(o2.order, o1.order) : Long.compare(o2.chatId, o1.chatId);
  }

  @Override
  protected TdApi.Function nextLoadFunction (boolean reverse, int itemCount, int loadCount) {
    if (this.items.isEmpty()) {
      return new TdApi.GetChats(chatList, Long.MAX_VALUE, 0, loadCount);
    } else {
      ChatEntry entry = this.items.get(this.items.size() - 1);
      return new TdApi.GetChats(chatList, entry.order, entry.chatId, loadCount);
    }
  }

  @Override
  protected ListManager.Response<ChatEntry> processResponse (TdApi.Object response, Client.ResultHandler retryHandler, int retryLoadCount, boolean reverse) {
    TdApi.Chats result = (TdApi.Chats) response;
    List<TdApi.Chat> chats = tdlib.chats(result.chatIds);
    if (!chats.isEmpty() && filter != null) {
      List<TdApi.Chat> filteredList = new ArrayList<>(chats.size());
      for (TdApi.Chat chat : chats) {
        if (filter.accept(chat)) {
          filteredList.add(chat);
        }
      }
      if (filteredList.isEmpty()) {
        TdApi.Chat lastChat = chats.get(chats.size() - 1);
        tdlib.client().send(new TdApi.GetChats(chatList, ChatPosition.getOrder(lastChat, chatList), lastChat.id, retryLoadCount), retryHandler);
        return null;
      }
      chats = filteredList;
    }
    List<ChatEntry> entries = new ArrayList<>(chats.size());
    for (TdApi.Chat chat : chats) {
      entries.add(new ChatEntry(chat.id, ChatPosition.getOrder(chat, chatList)));
    }
    return new ListManager.Response<>(entries, filter != null ? COUNT_UNKNOWN : result.totalCount);
  }

  // List callbacks

  @UiThread
  private int indexOfChat (long chatId) {
    int i = 0;
    for (ChatEntry entry : this.items) {
      if (entry.chatId == chatId) {
        return i;
      }
      i++;
    }
    return -1;
  }

  // CounterChangeListener

  public final boolean isCurrentChatList (@NonNull TdApi.ChatList chatList) {
    return Td.equalsTo(this.chatList, chatList);
  }

  private int unreadChatsCount;

  @Override
  public void onChatCounterChanged (@NonNull TdApi.ChatList chatList, boolean availabilityChanged, int totalCount, int unreadCount, int unreadUnmutedCount) {
    if (isCurrentChatList(chatList)) {
      runOnUiThreadIfReady(() -> {
        if (!setTotalCount(totalCount) && isListAvailable()) {
          if (this.unreadChatsCount != unreadCount) {
            this.unreadChatsCount = unreadCount;
            notifyMetadataChanged();
          }
        }
      });
    }
  }

  private int unreadMessagesCount;

  @Override
  public void onMessageCounterChanged (@NonNull TdApi.ChatList chatList, int unreadCount, int unreadUnmutedCount) {
    if (isCurrentChatList(chatList)) {
      runOnUiThreadIfReady(() -> {
        if (isListAvailable() && this.unreadMessagesCount != unreadCount) {
          this.unreadMessagesCount = unreadCount;
          notifyMetadataChanged();
        }
      });
    }
  }

  // ChatListener

  private void notifyChatChanged (long chatId, int cause) {
    runOnUiThreadIfReady(() -> {
      int i = indexOfChat(chatId);
      if (i != -1) {
        notifyItemChanged(i, cause);
      }
    });
  }

  @Override
  public void onChatPositionChanged (long chatId, TdApi.ChatPosition position, boolean orderChanged, boolean sourceChanged, boolean pinStateChanged) {
    runOnUiThreadIfReady(() -> {
      if (!Td.equalsTo(this.chatList, position.list))
        return;
      final int fromIndex = indexOfChat(chatId);
      final ChatEntry entry; final long prevOrder;
      if (fromIndex != -1) {
        entry = items.get(fromIndex);
        if (entry.order == position.order) {
          return;
        }
        prevOrder = entry.order;

        items.remove(fromIndex);
        entry.order = position.order;
      } else {
        if (!Td.equalsTo(this.chatList, position.list))
          return;
        entry = new ChatEntry(chatId, position.order);
        prevOrder = 0;
      }

      int newIndex = Collections.binarySearch(this.items, entry, this);
      if (newIndex >= 0) {
        if (fromIndex != -1) {
          // Duplicate chat?
          entry.order = prevOrder;
          items.add(fromIndex, entry);
        }
        return;
      }

      newIndex = newIndex * -1 - 1;
      if (newIndex == items.size() && !isEndReached()) {
        if (fromIndex != -1) {
          // Item moved to the end of the list, but we do not know if there is anything between this item and the end of the list
          onItemRemoved(entry, fromIndex);
        }
      } else {
        items.add(newIndex, entry);
        if (fromIndex != -1) {
          onItemMoved(entry, fromIndex, newIndex);
        } else {
          onItemAdded(entry, newIndex);
        }
      }
    });
  }

  public static final int CAUSE_TOP_MESSAGE = 0;
  public static final int CAUSE_TITLE = 1;
  public static final int CAUSE_PHOTO = 2;
  public static final int CAUSE_UNREAD_COUNTER = 3;
  public static final int CAUSE_MENTION_COUNTER = 4;
  public static final int CAUSE_UNREAD_COUNTER_AVAILABILITY = 5;
  public static final int CAUSE_MENTION_COUNTER_AVAILABILITY = 6;
  public static final int CAUSE_SCHEDULED_MESSAGES_AVAILABILITY = 7;

  @Override
  public void onChatTopMessageChanged (long chatId, @Nullable TdApi.Message topMessage) {
    notifyChatChanged(chatId, CAUSE_TOP_MESSAGE);
  }

  @Override
  public void onChatTitleChanged (long chatId, String title) {
    notifyChatChanged(chatId, CAUSE_TITLE);
  }

  @Override
  public void onChatPhotoChanged (long chatId, @Nullable TdApi.ChatPhotoInfo photo) {
    notifyChatChanged(chatId, CAUSE_PHOTO);
  }

  @Override
  public void onChatUnreadMentionCount (long chatId, int unreadMentionCount, boolean availabilityChanged) {
    notifyChatChanged(chatId, availabilityChanged ? CAUSE_MENTION_COUNTER_AVAILABILITY : CAUSE_MENTION_COUNTER);
  }

  @Override
  public void onChatReadInbox (long chatId, long lastReadInboxMessageId, int unreadCount, boolean availabilityChanged) {
    notifyChatChanged(chatId, availabilityChanged ? CAUSE_UNREAD_COUNTER_AVAILABILITY : CAUSE_UNREAD_COUNTER);
  }

  @Override
  public void onChatMarkedAsUnread (long chatId, boolean isMarkedAsUnread) {
    notifyChatChanged(chatId, CAUSE_UNREAD_COUNTER);
  }

  @Override
  public void onChatHasScheduledMessagesChanged (long chatId, boolean hasScheduledMessages) {
    notifyChatChanged(chatId, CAUSE_SCHEDULED_MESSAGES_AVAILABILITY);
  }
}
