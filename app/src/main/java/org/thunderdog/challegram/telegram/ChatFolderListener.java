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
 *
 * File created on 19/01/2024
 */
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
