package org.thunderdog.challegram.telegram;

import org.drinkless.tdlib.TdApi;

public interface ChatFolderListener {
  default void onChatFolderNewChatsChanged (int chatFolderId) {
  }

  default void onChatFolderInviteLinkDeleted (int chatFolderId, String inviteLink) {

  }

  default void onChatFolderInviteLinkChanged (int chatFolderId, TdApi.ChatFolderInviteLink inviteLink) {

  }

  default void onChatFolderInviteLinkCreated (int chatFolderId, TdApi.ChatFolderInviteLink inviteLink) {

  }
}
