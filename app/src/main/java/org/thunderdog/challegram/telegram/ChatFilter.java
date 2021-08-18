package org.thunderdog.challegram.telegram;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;

import me.vkryl.core.lambda.Filter;

/**
 * Date: 2019-12-21
 * Author: default
 */
public interface ChatFilter extends Filter<TdApi.Chat> {
  default int getTotalStringRes () { return R.string.xChats; }
  default int getEmptyStringRes () { return R.string.NoChats; }
  default boolean canFilterMessages () { return false; }
  default int getMessagesStringRes (boolean isArchive) { return R.string.general_Messages; }

  static ChatFilter gamesFilter (Tdlib tdlib) {
    return chat -> chat.type.getConstructor() != TdApi.ChatTypeSecret.CONSTRUCTOR && !tdlib.isChannelChat(chat);
  }

  static ChatFilter groupsInviteFilter (Tdlib tdlib) {
    return new ChatFilter() {
      @Override
      public boolean accept (TdApi.Chat chat) {
        return tdlib.isMultiChat(chat) && tdlib.canInviteUsers(chat);
      }

      @Override
      public int getTotalStringRes () {
        return R.string.xGroups;
      }

      @Override
      public int getEmptyStringRes () {
        return R.string.NoGroupsToShow;
      }
    };
  }

  static ChatFilter groupsFilter (Tdlib tdlib) {
    return new ChatFilter() {
      @Override
      public boolean accept (TdApi.Chat chat) {
        return tdlib.isMultiChat(chat);
      }

      @Override
      public int getTotalStringRes () {
        return R.string.xGroups;
      }

      @Override
      public int getEmptyStringRes () {
        return R.string.NoGroups;
      }

      @Override
      public int getMessagesStringRes(boolean isArchive) {
        return isArchive ? R.string.MessagesArchiveGroups : R.string.MessagesGroups;
      }

      @Override
      public boolean canFilterMessages () {
        return true;
      }
    };
  }

  static ChatFilter channelsFilter (Tdlib tdlib) {
    return new ChatFilter() {
      @Override
      public boolean accept (TdApi.Chat chat) {
        return tdlib.isChannel(chat.id);
      }

      @Override
      public int getTotalStringRes () {
        return R.string.xChannels;
      }

      @Override
      public int getEmptyStringRes () {
        return R.string.NoChannels;
      }

      @Override
      public int getMessagesStringRes(boolean isArchive) {
        return isArchive ? R.string.MessagesArchiveChannels : R.string.MessagesChannels;
      }

      @Override
      public boolean canFilterMessages () {
        return true;
      }
    };
  }

  static ChatFilter privateFilter (Tdlib tdlib) {
    return new ChatFilter() {
      @Override
      public boolean accept (TdApi.Chat chat) {
        return tdlib.isUserChat(chat) && !tdlib.isBotChat(chat);
      }

      @Override
      public int getEmptyStringRes () {
        return R.string.NoPrivateChats;
      }

      @Override
      public int getMessagesStringRes(boolean isArchive) {
        return isArchive ? R.string.MessagesArchivePrivate : R.string.MessagesPrivate;
      }

      @Override
      public boolean canFilterMessages () {
        return true;
      }
    };
  }

  static ChatFilter botsFilter (Tdlib tdlib) {
    return new ChatFilter() {
      @Override
      public boolean accept (TdApi.Chat chat) {
        return tdlib.isBotChat(chat);
      }

      @Override
      public int getTotalStringRes () {
        return R.string.xBots;
      }

      @Override
      public int getEmptyStringRes () {
        return R.string.NoBotsChats;
      }

      @Override
      public int getMessagesStringRes(boolean isArchive) {
        return isArchive ? R.string.MessagesArchiveBots : R.string.MessagesBots;
      }

      @Override
      public boolean canFilterMessages () {
        return true;
      }
    };
  }

  static ChatFilter unreadFilter (Tdlib tdlib) {
    return new ChatFilter() {
      @Override
      public boolean accept (TdApi.Chat chat) {
        return chat.unreadCount > 0 || chat.isMarkedAsUnread;
      }

      @Override
      public int getEmptyStringRes () {
        return R.string.NoUnreadChats;
      }
    };
  }
}
