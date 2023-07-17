package org.thunderdog.challegram.telegram;

import org.drinkless.tdlib.TdApi;

public interface ChatFoldersListener {
  default void onChatFoldersChanged (TdApi.ChatFolderInfo[] chatFolders, int mainChatListPosition) { }
}
