package org.thunderdog.challegram.telegram;

import androidx.annotation.IntDef;

import org.drinkless.td.libcore.telegram.TdApi;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public interface ChatListListener {
  default void onChatChanged (TdlibChatList chatList, TdApi.Chat chat, int index, Tdlib.ChatChange changeInfo) { }
  default void onChatAdded (TdlibChatList chatList, TdApi.Chat chat, int atIndex, Tdlib.ChatChange changeInfo) { }
  default void onChatRemoved (TdlibChatList chatList, TdApi.Chat chat, int fromIndex, Tdlib.ChatChange changeInfo) { }
  default void onChatMoved (TdlibChatList chatList, TdApi.Chat chat, int fromIndex, int toIndex, Tdlib.ChatChange changeInfo) {  }

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    ChangeType.ITEM_METADATA_CHANGED,
    ChangeType.ITEM_ADDED,
    ChangeType.ITEM_REMOVED,
    ChangeType.ITEM_MOVED
  })
  @interface ChangeType {
    int ITEM_METADATA_CHANGED = 0;
    int ITEM_ADDED = 1;
    int ITEM_REMOVED = 2;
    int ITEM_MOVED = 3;
  }
  default void onChatListChanged (TdlibChatList chatList, @ChangeType int changeType) { }

  default void onChatListStateChanged (TdlibChatList chatList,
                                       @TdlibChatList.State int newState,
                                       @TdlibChatList.State int oldState) { }

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    ItemChangeType.TITLE,
    ItemChangeType.READ_INBOX,
    ItemChangeType.LAST_MESSAGE,
    ItemChangeType.DRAFT,
  })
  @interface ItemChangeType {
    int TITLE = 0;
    int READ_INBOX = 1;
    int LAST_MESSAGE = 2;
    int DRAFT = 3;
  }
  default void onChatListItemChanged (TdlibChatList chatList, TdApi.Chat chat, @ItemChangeType int changeType) { }
}
