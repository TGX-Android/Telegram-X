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

import androidx.annotation.IntDef;

import org.drinkless.tdlib.TdApi;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public interface ChatListListener {
  default void onChatChanged (TdlibChatList chatList, TdApi.Chat chat, int index, Tdlib.ChatChange changeInfo) { }
  default void onChatAdded (TdlibChatList chatList, TdApi.Chat chat, int atIndex, Tdlib.ChatChange changeInfo) { }
  default void onChatRemoved (TdlibChatList chatList, TdApi.Chat chat, int fromIndex, Tdlib.ChatChange changeInfo) { }
  default void onChatMoved (TdlibChatList chatList, TdApi.Chat chat, int fromIndex, int toIndex, Tdlib.ChatChange changeInfo) {  }

  @Retention(RetentionPolicy.SOURCE)
  @IntDef(value = {
    ChangeFlags.ITEM_METADATA_CHANGED,
    ChangeFlags.ITEM_ADDED,
    ChangeFlags.ITEM_REMOVED,
    ChangeFlags.ITEM_MOVED
  }, flag = true)
  @interface ChangeFlags {
    int ITEM_METADATA_CHANGED = 1;
    int ITEM_ADDED = 1 << 1;
    int ITEM_REMOVED = 1 << 2;
    int ITEM_MOVED = 1 << 3;
  }
  default void onChatListChanged (TdlibChatList chatList, @ChangeFlags int changeFlags) { }

  default void onChatListStateChanged (TdlibChatList chatList,
                                       @TdlibChatList.State int newState,
                                       @TdlibChatList.State int oldState) { }

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    ItemChangeType.TITLE,
    ItemChangeType.READ_INBOX,
    ItemChangeType.LAST_MESSAGE,
    ItemChangeType.DRAFT,
    ItemChangeType.UNREAD_AVAILABILITY_CHANGED,
    ItemChangeType.THEME
  })
  @interface ItemChangeType {
    int TITLE = 0;
    int READ_INBOX = 1;
    int LAST_MESSAGE = 2;
    int DRAFT = 3;
    int UNREAD_AVAILABILITY_CHANGED = 4;
    int THEME = 5;
  }
  default void onChatListItemChanged (TdlibChatList chatList, TdApi.Chat chat, @ItemChangeType int changeType) { }
}
