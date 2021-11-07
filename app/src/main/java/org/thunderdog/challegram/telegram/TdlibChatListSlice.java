package org.thunderdog.challegram.telegram;

import androidx.annotation.NonNull;

import org.drinkless.td.libcore.telegram.TdApi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.vkryl.core.lambda.RunnableData;
import me.vkryl.td.ChatPosition;

public class TdlibChatListSlice {
  private final Tdlib tdlib;
  private final TdlibChatList sourceList;
  private final ChatFilter filter;

  private ChatListListener listener, subListener;
  private final List<TdlibChatList.Entry> filteredList = new ArrayList<>();
  private RunnableData<List<TdApi.Chat>> subCallback;
  private int maxSize;
  private int displayCount;

  public TdlibChatListSlice (Tdlib tdlib, TdApi.ChatList chatList, ChatFilter filter) {
    this.tdlib = tdlib;
    this.sourceList = tdlib.chatList(chatList);
    this.filter = filter;
  }

  private int loadedCount () {
    synchronized (filteredList) {
      return filteredList.size();
    }
  }

  public boolean needProgressPlaceholder () {
    return displayCount == 0 && !sourceList.isEndReached();
  }

  public boolean canLoad () {
    return displayCount < loadedCount() || sourceList.canLoad();
  }

  public boolean isEndReached () {
    return displayCount == loadedCount() && sourceList.isEndReached();
  }

  private int indexOfChat (long chatId) {
    int index = 0;
    for (TdlibChatList.Entry entry : filteredList) {
      if (entry.chat.id == chatId) {
        return index;
      }
      index++;
    }
    return -1;
  }

  private int normalizeIndex (int originalIndex, long chatId) {
    return filter != null ? indexOfChat(chatId) : originalIndex;
  }

  public void initializeList (@NonNull ChatListListener subListener, @NonNull RunnableData<List<TdApi.Chat>> subCallback, int initialChunkSize, Runnable onLoadInitialChunk) {
    if (this.listener != null)
      throw new IllegalStateException();

    this.subListener = subListener;
    this.listener = new ChatListListener() {
      @Override
      public void onChatChanged (TdlibChatList chatList, TdApi.Chat chat, int index, Tdlib.ChatChange changeInfo) {
        index = normalizeIndex(index, chat.id);
        if (index != -1) {
          if (index < displayCount) {
            subListener.onChatChanged(chatList, chat, index, changeInfo);
            subListener.onChatListChanged(chatList, ChangeFlags.ITEM_METADATA_CHANGED);
          }
        }
      }

      @Override
      public void onChatAdded (TdlibChatList chatList, TdApi.Chat chat, int atIndex, Tdlib.ChatChange changeInfo) {
        if (filter != null) {
          if (!filter.accept(chat))
            return;
        }
        TdlibChatList.Entry entry = new TdlibChatList.Entry(chat, changeInfo.position);
        if (filter != null) {
          atIndex = Collections.binarySearch(filteredList, entry);
          if (atIndex >= 0)
            throw new IllegalStateException();
          atIndex = -atIndex - 1;
        }
        if (atIndex == filteredList.size()) {
          filteredList.add(entry);
        } else {
          filteredList.add(atIndex, entry);
        }
        if (atIndex < displayCount) {
          subListener.onChatAdded(chatList, chat, atIndex, changeInfo);
          displayCount++;
          subListener.onChatListChanged(chatList, ChangeFlags.ITEM_ADDED);
        } else {
          dispatchChats(0);
        }
      }

      @Override
      public void onChatRemoved (TdlibChatList chatList, TdApi.Chat chat, int fromIndex, Tdlib.ChatChange changeInfo) {
        fromIndex = normalizeIndex(fromIndex, chat.id);
        if (fromIndex == -1)
          return;
        /*TdlibChatList.Entry removedEntry =*/ filteredList.remove(fromIndex);
        if (fromIndex < displayCount) {
          subListener.onChatRemoved(chatList, chat, fromIndex, changeInfo);
          displayCount--;
          dispatchChats(ChangeFlags.ITEM_REMOVED);
        }
      }

      @Override
      public void onChatMoved (TdlibChatList chatList, TdApi.Chat chat, int fromIndex, int toIndex, Tdlib.ChatChange changeInfo) {
        if (filter != null) {
          fromIndex = normalizeIndex(fromIndex, chat.id);
          if (fromIndex == -1)
            return;
        }
        TdlibChatList.Entry movedEntry = filteredList.remove(fromIndex);
        movedEntry.effectivePosition.order = changeInfo.position.order;
        movedEntry.effectivePosition.isPinned = changeInfo.position.isPinned;
        movedEntry.effectivePosition.source = changeInfo.position.source;
        if (filter != null) {
          toIndex = Collections.binarySearch(filteredList, movedEntry);
          if (toIndex >= 0)
            throw new IllegalStateException();
          toIndex = -toIndex - 1;
          if (toIndex == fromIndex) {
            filteredList.add(toIndex, movedEntry);
            if (toIndex < displayCount) {
              subListener.onChatChanged(chatList, chat, toIndex, changeInfo);
              subListener.onChatListChanged(chatList, ChangeFlags.ITEM_METADATA_CHANGED);
            }
            return;
          }
        }
        filteredList.add(toIndex, movedEntry);
        if (fromIndex < displayCount && toIndex < displayCount) {
          subListener.onChatMoved(chatList, chat, fromIndex, toIndex, changeInfo);
          subListener.onChatListChanged(chatList, ChangeFlags.ITEM_MOVED);
        } else if (fromIndex < displayCount) {
          subListener.onChatRemoved(chatList, chat, fromIndex, changeInfo);
          displayCount--;
          dispatchChats(ChangeFlags.ITEM_REMOVED);
        } else if (toIndex < displayCount) {
          subListener.onChatAdded(chatList, chat, fromIndex, changeInfo);
          displayCount++;
          subListener.onChatListChanged(chatList, ChangeFlags.ITEM_ADDED);
        } else {
          // Do nothing, as the chat is outside of displaying range
        }
      }

      @Override
      public void onChatListChanged (TdlibChatList chatList, int changeFlags) {
        // called when subListener is called
      }

      @Override
      public void onChatListStateChanged (TdlibChatList chatList, int newState, int oldState) {
        subListener.onChatListStateChanged(chatList, newState, oldState);
      }

      @Override
      public void onChatListItemChanged (TdlibChatList chatList, TdApi.Chat chat, int changeType) {
        final int existingIndex = indexOfChat(chat.id);
        if (existingIndex == -1) {
          if (filter != null && filter.accept(chat)) { // chat became unfiltered
            TdApi.ChatPosition position = ChatPosition.findPosition(chat, chatList.chatList());
            if (position != null && position.order != 0) {
              final TdlibChatList.Entry entry = new TdlibChatList.Entry(chat, position);
              int atIndex = Collections.binarySearch(filteredList, entry);
              if (atIndex >= 0)
                throw new IllegalStateException();
              atIndex = -atIndex - 1;
              if (atIndex == filteredList.size()) {
                filteredList.add(entry);
                dispatchChats(0);
                return;
              }
              filteredList.add(atIndex, entry);
              subListener.onChatAdded(chatList, chat, atIndex, new Tdlib.ChatChange(position, Tdlib.ChatChange.ALL));
              displayCount++;
              subListener.onChatListChanged(chatList, ChangeFlags.ITEM_ADDED);
            }
          }
          return; // do nothing, as chat is not from this list
        }

        if (existingIndex < displayCount) {
          subListener.onChatListItemChanged(chatList, chat, changeType);
          subListener.onChatListChanged(chatList, ChangeFlags.ITEM_METADATA_CHANGED);
        }
      }
    };

    this.subCallback = subCallback;
    final RunnableData<List<TdlibChatList.Entry>> callback = (moreChats) -> {
      synchronized (filteredList) {
        if (filter != null) {
          int addedCount = 0;
          for (TdlibChatList.Entry entry : moreChats) {
            if (filter.accept(entry.chat)) {
              this.filteredList.add(entry);
              addedCount++;
            }
          }
          if (addedCount == 0) {
            return;
          }
        } else {
          this.filteredList.addAll(moreChats);
        }
      }
      dispatchChats(0);
    };

    loadingInitialChunk = true;
    sourceList.initializeList(filter, listener, callback, this.maxSize = initialChunkSize, () -> {
      loadingInitialChunk = false;
      dispatchChats(0);
      if (onLoadInitialChunk != null) {
        onLoadInitialChunk.run();
      }
    });
  }

  private boolean loadingInitialChunk;

  @TdlibThread
  private int dispatchChats (final int changeFlags) {
    if (loadingInitialChunk) {
      return 0;
    }
    final int notifyCount = Math.max(0, Math.min(filteredList.size(), maxSize) - displayCount);
    if (notifyCount > 0) {
      final List<TdApi.Chat> slice = new ArrayList<>(notifyCount);
      for (int index = 0; index < notifyCount; index++) {
        slice.add(filteredList.get(displayCount + index).chat);
      }
      subCallback.runWithData(slice);
      this.displayCount += slice.size();
      subListener.onChatListChanged(sourceList, changeFlags | ChatListListener.ChangeFlags.ITEM_ADDED);
    }
    return notifyCount;
  }

  public void loadMore (int moreCount, Runnable after) {
    if (loadingInitialChunk)
      return;
    tdlib.runOnTdlibThread(() -> {
      maxSize += moreCount;
      if (dispatchChats(0) != 0) {
        if (after != null) {
          after.run();
        }
      } else {
        sourceList.loadMore(moreCount, () -> {
          dispatchChats(0);
          if (after != null) {
            after.run();
          }
        });
      }
    });
  }

  public void unsubscribeFromUpdates (ChatListListener subListener) {
    if (this.listener != null) {
      sourceList.unsubscribeFromUpdates(this.listener);
      this.listener = null;
    }
  }
}
