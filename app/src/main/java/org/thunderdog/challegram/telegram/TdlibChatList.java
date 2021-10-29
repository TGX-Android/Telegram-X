package org.thunderdog.challegram.telegram;

import androidx.annotation.AnyThread;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.data.TD;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import me.vkryl.core.lambda.Filter;
import me.vkryl.core.lambda.RunnableBool;
import me.vkryl.core.lambda.RunnableData;
import me.vkryl.td.ChatPosition;
import me.vkryl.td.Td;

public class TdlibChatList implements Comparator<TdlibChatList.Entry>, CounterChangeListener {
  public static class Entry {
    public final TdApi.Chat chat;
    public TdApi.ChatPosition effectivePosition;

    public Entry (TdApi.Chat chat, TdApi.ChatPosition position) {
      this.chat = chat;
      this.effectivePosition = new TdApi.ChatPosition(
        position.list,
        position.order,
        position.isPinned,
        position.source
      );
    }
  }

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    State.END_NOT_REACHED,
    State.LOADING,
    State.END_REACHED
  })
  public @interface State {
    int END_NOT_REACHED = 0;
    int LOADING = 1;
    int END_REACHED = 2;
  }

  private final Tdlib tdlib;
  private final TdApi.ChatList chatList;
  private final ArrayList<Entry> list = new ArrayList<>();
  private final List<Runnable> onLoadMore = new ArrayList<>();
  private final List<RunnableData<TdApi.Chat>> perChatCallbacks = new ArrayList<>();

  private @State int state = State.END_NOT_REACHED;

  TdlibChatList (Tdlib tdlib, TdApi.ChatList chatList) {
    this.tdlib = tdlib;
    this.chatList = chatList;
  }

  // Listeners API

  public void subscribeToUpdates (ChatListListener listener) {
    tdlib.listeners().subscribeToChatListUpdates(chatList, listener);
  }

  public void unsubscribeFromUpdates (ChatListListener listener) {
    tdlib.listeners().unsubscribeFromChatListUpdates(chatList, listener);
  }

  // State API

  public boolean isLoading () {
    return state == State.LOADING;
  }

  public boolean canLoad () {
    return state == State.END_NOT_REACHED;
  }

  public TdApi.ChatList chatList () {
    return chatList;
  }

  public boolean isEndReached () {
    return state == State.END_REACHED;
  }

  public boolean isAvailable () {
    return totalCount() > 0;
  }

  public int totalCount () {
    return Math.max(tdlib.getTotalChatsCount(chatList), count(null));
  }

  public int count (@Nullable Filter<TdApi.Chat> filter) {
    synchronized (list) {
      if (filter == null) {
        return list.size();
      }
      int count = 0;
      for (Entry entry : list) {
        if (filter.accept(entry.chat)) {
          count++;
        }
      }
      return count;
    }
  }

  public List<Entry> listCopy (@Nullable Filter<TdApi.Chat> filter) {
    synchronized (list) {
      return listCopyImpl(filter);
    }
  }

  private List<Entry> listCopyImpl (@Nullable Filter<TdApi.Chat> filter) {
    final List<Entry> copy = new ArrayList<>(list.size());
    for (Entry entry : list) {
      if (filter == null || filter.accept(entry.chat)) {
        copy.add(new Entry(entry.chat, entry.effectivePosition));
      }
    }
    return copy;
  }

  public boolean hasUnreadMentions () {
    synchronized (list) {
      for (Entry entry : list) {
        if (entry.chat.unreadMentionCount > 0)
          return true;
      }
      return false;
    }
  }

  public boolean hasScheduledMessages () {
    synchronized (list) {
      for (Entry entry : list) {
        if (entry.chat.hasScheduledMessages)
          return true;
      }
      return false;
    }
  }

  public boolean hasFailedMessages () {
    synchronized (list) {
      for (Entry entry : list) {
        if (TD.isFailed(entry.chat.lastMessage))
          return true;
      }
      return false;
    }
  }

  public int maxDate () {
    synchronized (list) {
      int maxDate = 0;
      for (Entry entry : list) {
        if (entry.chat.lastMessage != null) {
          maxDate = Math.max(entry.chat.lastMessage.date, maxDate);
          if (!ChatPosition.isPinned(entry.chat, chatList))
            break;
        }
      }
      return maxDate;
    }
  }

  public void iterate (RunnableData<TdApi.Chat> callback) {
    synchronized (list) {
      for (Entry entry : list) {
        callback.runWithData(entry.chat);
      }
    }
  }

  // Load API

  @AnyThread
  public void initializeList (@Nullable Filter<TdApi.Chat> filter, ChatListListener listener, @NonNull RunnableData<List<Entry>> callback, int initialChunk, Runnable onLoadInitialChunk) {
    getChats(filter, (list) -> {
      callback.runWithData(list);
      subscribeToUpdates(listener);
    });
    loadAtLeast(filter, initialChunk, onLoadInitialChunk);
  }

  @AnyThread
  public void getChats (@Nullable Filter<TdApi.Chat> filter, @NonNull RunnableData<List<Entry>> callback) {
    if (!tdlib.inTdlibThread()) {
      tdlib.runOnTdlibThread(() -> getChats(filter, callback));
      return;
    }
    // No need to sync, as all changes are made on tdlib thread
    List<Entry> entries = listCopyImpl(filter);
    callback.runWithData(entries);
  }

  @AnyThread
  public void loadAtLeast (@Nullable Filter<TdApi.Chat> filter, int count, @Nullable Runnable after) {
    loadAtLeast(filter, count, count, after);
  }

  @AnyThread
  public void loadAtLeast (@Nullable Filter<TdApi.Chat> filter, int minCount, int desiredCount, @Nullable Runnable after) {
    if (!tdlib.inTdlibThread()) {
      tdlib.runOnTdlibThread(() -> loadAtLeast(filter, minCount, desiredCount, after));
      return;
    }
    Runnable act = new Runnable() {
      @Override
      public void run () {
        int count = count(filter);
        if (isEndReached() || count >= minCount) {
          if (after != null) {
            after.run();
          }
          return;
        }
        loadMore(desiredCount - count, this);
      }
    };
    act.run();
  }

  @AnyThread
  public void loadMore (int limit, @Nullable Runnable after) {
    if (!tdlib.inTdlibThread()) {
      tdlib.runOnTdlibThread(() -> loadMore(limit, after));
      return;
    }
    if (state == State.END_REACHED) {
      if (after != null) {
        after.run();
      }
      return;
    }
    if (after != null) {
      onLoadMore.add(after);
    }
    if (state == State.LOADING) {
      return;
    }
    setState(State.LOADING);
    tdlib.client().send(new TdApi.LoadChats(chatList, limit), result -> {
      boolean endReached = result.getConstructor() == TdApi.Error.CONSTRUCTOR;
      setState(endReached ? State.END_REACHED : State.END_NOT_REACHED);
      if (!onLoadMore.isEmpty()) {
        List<Runnable> callbacks = new ArrayList<>(onLoadMore);
        onLoadMore.clear();
        for (Runnable runnable : callbacks) {
          runnable.run();
        }
      }
    });
  }

  @AnyThread
  public void loadAll (@NonNull RunnableData<TdApi.Chat> perChatCallback, @Nullable RunnableBool after) {
    if (!tdlib.inTdlibThread()) {
      tdlib.runOnTdlibThread(() -> loadAll(perChatCallback, after));
      return;
    }
    Runnable act = new Runnable() {
      @Override
      public void run () {
        boolean endReached = isEndReached();
        if (endReached) {
          perChatCallbacks.remove(perChatCallback);
        }
        if (after != null) {
          after.runWithBool(endReached);
        }
        if (!endReached) {
          loadMore(100, this);
        }
      }
    };
    for (Entry entry : list) {
      perChatCallback.runWithData(entry.chat);
    }
    perChatCallbacks.add(perChatCallback);
    loadMore(100, act);
  }

  // List utils

  @Override
  public int compare (Entry o1, Entry o2) {
    return o1.effectivePosition.order != o2.effectivePosition.order ?
      Long.compare(o2.effectivePosition.order, o1.effectivePosition.order) :
      Long.compare(o2.chat.id, o1.chat.id);
  }

  private int indexOfEntry (long chatId) {
    int index = 0;
    for (Entry entry : list) {
      if (entry.chat.id == chatId) {
        return index;
      }
      index++;
    }
    return -1;
  }

  // Updates handling

  @TdlibThread
  void onUpdateNewChat (TdApi.Chat chat) {
    if (chat.positions != null) {
      TdApi.ChatPosition position = ChatPosition.findPosition(chat, chatList);
      if (position != null && position.order != 0) {
        addChatToList(new Entry(chat, position), new Tdlib.ChatChange(position, 0));
      }
    }
  }

  @TdlibThread
  void onUpdateChatPosition (TdApi.Chat chat, Tdlib.ChatChange changeInfo) {
    TdApi.ChatPosition position = changeInfo.position;
    int prevIndex = indexOfEntry(chat.id);
    if (prevIndex == -1) {
      if (position.order != 0) {
        addChatToList(new Entry(chat, position), changeInfo);
      }
    } else if (position.order == 0) {
      removeChatFromList(prevIndex, changeInfo);
    } else {
      final Entry existingEntry;
      if (changeInfo.orderChanged()) {
        int newIndex;
        synchronized (list) {
          existingEntry = list.remove(prevIndex);
          Td.copyTo(position, existingEntry.effectivePosition);
          newIndex = Collections.binarySearch(this.list, existingEntry, this);
          if (newIndex >= 0)
            throw new IllegalStateException();
          newIndex = newIndex * -1 - 1;
          list.add(newIndex, existingEntry);
        }
        if (newIndex != prevIndex) {
          tdlib.listeners().updateChatMoved(this, existingEntry.chat, prevIndex, newIndex, changeInfo);
          return;
        }
      } else {
        existingEntry = list.get(prevIndex);
      }
      if (changeInfo.metadataChanged()) {
        tdlib.listeners().updateChatChanged(this, existingEntry.chat, prevIndex, changeInfo);
      }
    }
  }

  // Internal

  private void addChatToList (Entry entry, Tdlib.ChatChange changeInfo) {
    int atIndex = Collections.binarySearch(this.list, entry, this);
    if (atIndex >= 0)
      throw new IllegalStateException();
    atIndex = atIndex * -1 - 1;
    synchronized (list) {
      list.add(atIndex, entry);
    }
    for (RunnableData<TdApi.Chat> perChatCallback : perChatCallbacks) {
      perChatCallback.runWithData(entry.chat);
    }
    tdlib.listeners().updateChatAdded(this, entry.chat, atIndex, changeInfo);
  }

  private void removeChatFromList (int fromIndex, Tdlib.ChatChange changeInfo) {
    Entry entry;
    synchronized (list) {
      entry = list.remove(fromIndex);
    }
    tdlib.listeners().updateChatRemoved(this, entry.chat, fromIndex, changeInfo);
  }

  private void setState (@State int newState) {
    if (this.state != newState) {
      final int oldState = this.state;
      this.state = newState;
      tdlib.listeners().updateChatListStateChanged(this, newState, oldState);
    }
  }
}
