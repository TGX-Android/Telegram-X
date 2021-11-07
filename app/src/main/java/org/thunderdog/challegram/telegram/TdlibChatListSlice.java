package org.thunderdog.challegram.telegram;

import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.vkryl.core.lambda.Filter;
import me.vkryl.core.lambda.Future;
import me.vkryl.core.lambda.RunnableData;
import me.vkryl.td.ChatPosition;

public class TdlibChatListSlice {
  private final Tdlib tdlib;
  private final TdlibChatList sourceList;
  private final Filter<TdApi.Chat> filter;
  private final boolean keepPositions;

  private ChatListListener listener, subListener;
  private final List<Entry> filteredList = new ArrayList<>();
  private RunnableData<List<Entry>> subCallback;
  private int maxSize;
  private int displayCount;

  public static class Entry extends TdlibChatList.Entry {
    private long broughtToTopTime;
    private boolean keepPosition;

    public Entry (TdApi.Chat chat, TdApi.ChatPosition position, boolean keepPosition) {
      super(chat, position);
      this.keepPosition = keepPosition;
    }

    public void bringToTop () {
      this.broughtToTopTime = SystemClock.uptimeMillis();
      this.keepPosition = true;
    }

    @Override
    public int compareTo (TdlibChatList.Entry other) {
      long otherTime = other instanceof Entry ? ((Entry) other).broughtToTopTime : 0;
      if (this.broughtToTopTime != otherTime) {
        return Long.compare(otherTime, this.broughtToTopTime);
      }
      return super.compareTo(other);
    }
  }

  public TdlibChatListSlice (Tdlib tdlib, TdApi.ChatList chatList, Filter<TdApi.Chat> filter, boolean keepPositions) {
    this.tdlib = tdlib;
    this.sourceList = tdlib.chatList(chatList);
    this.filter = filter;
    this.keepPositions = keepPositions;
    this.haveCustomModifications = keepPositions;
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
    // TODO lookup without increase in number of operations
    int index = 0;
    for (Entry entry : filteredList) {
      if (entry.chat.id == chatId) {
        return index;
      }
      index++;
    }
    return -1;
  }

  private int findExistingIndex (int originalIndex, long chatId) {
    return needSort() ? indexOfChat(chatId) : originalIndex;
  }

  protected int findInsertIndex (Entry entry) {
    final int atIndex = Collections.binarySearch(filteredList, entry);
    if (atIndex >= 0)
      throw new IllegalStateException();
    return -atIndex - 1;
  }

  public void initializeList (@NonNull ChatListListener subListener, @NonNull RunnableData<List<Entry>> subCallback, int initialChunkSize, Runnable onLoadInitialChunk) {
    if (this.listener != null)
      throw new IllegalStateException();

    this.subListener = subListener;
    this.listener = new ChatListListener() {
      @Override
      public void onChatChanged (TdlibChatList chatList, TdApi.Chat chat, int index, Tdlib.ChatChange changeInfo) {
        index = findExistingIndex(index, chat.id);
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
        Entry entry = new Entry(chat, changeInfo.position, keepPositions);
        if (needSort()) {
          atIndex = findInsertIndex(entry);
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
        fromIndex = findExistingIndex(fromIndex, chat.id);
        if (fromIndex != -1 && !filteredList.get(fromIndex).keepPosition) {
          /*Entry removedEntry =*/ filteredList.remove(fromIndex);
          if (fromIndex < displayCount) {
            subListener.onChatRemoved(chatList, chat, fromIndex, changeInfo);
            displayCount--;
            dispatchChats(ChangeFlags.ITEM_REMOVED);
          }
        }
      }

      @Override
      public void onChatMoved (TdlibChatList chatList, TdApi.Chat chat, int fromIndex, int toIndex, Tdlib.ChatChange changeInfo) {
        final boolean needSort = needSort();
        if (needSort) {
          fromIndex = findExistingIndex(fromIndex, chat.id);
          if (fromIndex == -1) // chat is filtered out
            return;
        }
        Entry movedEntry = filteredList.remove(fromIndex);
        if (!movedEntry.keepPosition) {
          movedEntry.effectivePosition.order = changeInfo.position.order;
          movedEntry.effectivePosition.isPinned = changeInfo.position.isPinned;
          movedEntry.effectivePosition.source = changeInfo.position.source;
        }
        if (needSort) {
          toIndex = findInsertIndex(movedEntry);
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
          subListener.onChatAdded(chatList, chat, toIndex, changeInfo);
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
              final Entry entry = new Entry(chat, position, keepPositions);
              int atIndex = findInsertIndex(entry);
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
        int addedCount = 0;
        ((ArrayList<?>) this.filteredList).ensureCapacity(this.filteredList.size() + moreChats.size());
        for (TdlibChatList.Entry entry : moreChats) {
          if ((filter == null || filter.accept(entry.chat)) && (!haveCustomModifications || indexOfChat(entry.chat.id) == -1)) {
            this.filteredList.add(new Entry(entry.chat, entry.effectivePosition, keepPositions));
            addedCount++;
          }
        }
        if (addedCount == 0) {
          return;
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
      final List<Entry> slice = new ArrayList<>(notifyCount);
      for (int index = 0; index < notifyCount; index++) {
        slice.add(filteredList.get(displayCount + index));
      }
      if (modifySlice(slice, displayCount)) {
        haveCustomModifications = true;
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

  private boolean haveCustomModifications;

  private boolean needSort () {
    return filter != null || haveCustomModifications;
  }

  protected boolean modifySlice (List<Entry> slice, int displayCount) {
    // Override in children, e.g. additional filtering and ordering
    return false;
  }

  public void bringToTop (long chatId, @Nullable Future<TdApi.Function> createFunction, @Nullable Runnable after) {
    if (this.listener == null)
      throw new IllegalStateException();
    tdlib.chat(chatId, createFunction, chat -> {
      final int fromIndex = indexOfChat(chatId);
      if (fromIndex != -1) {
        /*if (fromIndex == 0) // No need to do anything
          return;*/
        haveCustomModifications = true;
        // Just move item to top
        Entry entry = filteredList.remove(fromIndex);
        entry.bringToTop();
        final int toIndex = findInsertIndex(entry);
        filteredList.add(toIndex, entry);
        if (fromIndex != toIndex) {
          if (fromIndex < displayCount) {
            subListener.onChatMoved(sourceList, entry.chat, fromIndex, toIndex, new Tdlib.ChatChange(entry.effectivePosition, Tdlib.ChatChange.ALL));
            subListener.onChatListChanged(sourceList, ChatListListener.ChangeFlags.ITEM_MOVED);
          } else {
            subListener.onChatAdded(sourceList, entry.chat, toIndex, new Tdlib.ChatChange(entry.effectivePosition, Tdlib.ChatChange.ALL));
            displayCount++;
            subListener.onChatListChanged(sourceList, ChatListListener.ChangeFlags.ITEM_ADDED);
          }
        }
      } else {
        if (chat == null)
          return;
        haveCustomModifications = true;
        // Force add item to top
        Entry entry = new Entry(chat, ChatPosition.findPosition(chat, sourceList.chatList()), keepPositions);
        entry.bringToTop();
        final int atIndex = findInsertIndex(entry);
        filteredList.add(atIndex, entry);
        subListener.onChatAdded(sourceList, entry.chat, atIndex, new Tdlib.ChatChange(entry.effectivePosition, Tdlib.ChatChange.ALL));
        displayCount++;
        subListener.onChatListChanged(sourceList, ChatListListener.ChangeFlags.ITEM_ADDED);
      }
      if (after != null) {
        after.run();
      }
    });
  }
}
