/*
 * This file is a part of Telegram X
 * Copyright © 2014-2023 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 10/01/2019
 */
package org.thunderdog.challegram.telegram;

import me.vkryl.leveldb.LevelDB;
import org.thunderdog.challegram.unsorted.Settings;

public class TdlibCounter {
  public int totalChatCount;
  public int chatCount, chatUnmutedCount;
  public int markedAsUnreadCount, markedAsUnreadUnmutedCount;
  public int messageCount, messageUnmutedCount;

  public TdlibCounter () { }

  TdlibCounter (TdlibCounter counter) {
    reset(counter);
  }

  public TdlibCounter (int totalChatCount, int chatCount, int chatUnmutedCount, int markedAsUnreadCount, int markedAsUnreadUnmutedCount, int messageCount, int messageUnmutedCount) {
    this.totalChatCount = totalChatCount;
    this.chatCount = chatCount;
    this.chatUnmutedCount = chatUnmutedCount;
    this.markedAsUnreadCount = markedAsUnreadCount;
    this.markedAsUnreadUnmutedCount = markedAsUnreadUnmutedCount;
    this.messageCount = messageCount;
    this.messageUnmutedCount = messageUnmutedCount;
  }

  public void reset (TdlibCounter copy) {
    this.chatCount = copy.chatCount;
    this.chatUnmutedCount = copy.chatUnmutedCount;
    this.markedAsUnreadCount = copy.markedAsUnreadCount;
    this.markedAsUnreadUnmutedCount = copy.markedAsUnreadUnmutedCount;
    this.messageCount = copy.messageCount;
    this.messageUnmutedCount = copy.messageUnmutedCount;
  }

  public void add (TdlibCounter add) {
    this.chatCount += add.chatCount;
    this.chatUnmutedCount += add.chatUnmutedCount;
    this.markedAsUnreadCount = add.markedAsUnreadCount;
    this.markedAsUnreadUnmutedCount += add.markedAsUnreadUnmutedCount;
    this.messageCount += add.messageCount;
    this.messageUnmutedCount += add.messageUnmutedCount;
  }

  public boolean isEmpty () {
    return chatCount == 0 && chatUnmutedCount == 0 && messageCount == 0 && messageUnmutedCount == 0 && markedAsUnreadCount == 0 && markedAsUnreadUnmutedCount == 0;
  }

  public boolean setChatCounters (int totalCount, int chatCount, int chatUnmutedCount, int markedAsUnreadCount, int markedAsUnreadUnmutedCount) {
    if (this.totalChatCount != totalCount || this.chatCount != chatCount || this.chatUnmutedCount != chatUnmutedCount || this.markedAsUnreadCount != markedAsUnreadCount || this.markedAsUnreadUnmutedCount != markedAsUnreadUnmutedCount) {
      this.totalChatCount = totalCount;
      this.chatCount = chatCount;
      this.chatUnmutedCount = chatUnmutedCount;
      this.markedAsUnreadCount = markedAsUnreadCount;
      this.markedAsUnreadUnmutedCount = markedAsUnreadUnmutedCount;
      return true;
    }
    return false;
  }

  public TdlibCounter subtract (TdlibCounter c) {
    return new TdlibCounter(Math.max(totalChatCount - c.totalChatCount, 0), Math.max(chatCount - c.chatCount, 0), Math.max(chatUnmutedCount - c.chatUnmutedCount, 0), Math.max(markedAsUnreadCount - c.markedAsUnreadCount, 0), Math.max(markedAsUnreadUnmutedCount - c.markedAsUnreadUnmutedCount, 0), Math.max(messageCount - c.messageCount, 0), Math.max(messageUnmutedCount - c.messageUnmutedCount, 0));
  }

  // Prefix

  private static final String PREFIX_CHATS = "chats_all";
  private static final String PREFIX_CHATS_UNMUTED = "chats";
  private static final String PREFIX_MARKED = "marked_all";
  private static final String PREFIX_MARKED_UNMUTED = "marked";
  private static final String PREFIX_MESSAGES = "messages_all";
  private static final String PREFIX_MESSAGES_UNMUTED = "messages";

  void restore (String prefix) {
    for (LevelDB.Entry entry : Settings.instance().pmc().find(prefix)) {
      switch (entry.key().substring(prefix.length())) {
        case PREFIX_CHATS:
          chatCount = entry.asInt();
          break;
        case PREFIX_CHATS_UNMUTED:
          chatUnmutedCount = entry.asInt();
          break;
        case PREFIX_MARKED:
          markedAsUnreadCount = entry.asInt();
          break;
        case PREFIX_MARKED_UNMUTED:
          markedAsUnreadUnmutedCount = entry.asInt();
          break;
        case PREFIX_MESSAGES:
          messageCount = entry.asInt();
          break;
        case PREFIX_MESSAGES_UNMUTED:
          messageUnmutedCount = entry.asInt();
          break;
      }
    }
  }

  void save (String prefix, boolean areChats) {
    if (isEmpty()) {
      Settings.instance().pmc().removeByPrefix(prefix);
    } else {
      LevelDB editor = Settings.instance().edit();

      if (areChats) {
        if (chatCount > 0)
          editor.putInt(prefix + PREFIX_CHATS, chatCount);
        else
          editor.remove(prefix + PREFIX_CHATS);

        if (chatUnmutedCount > 0)
          editor.putInt(prefix + PREFIX_CHATS_UNMUTED, chatUnmutedCount);
        else
          editor.remove(prefix + PREFIX_CHATS_UNMUTED);

        if (markedAsUnreadCount > 0)
          editor.putInt(prefix + PREFIX_MARKED, markedAsUnreadCount);
        else
          editor.remove(prefix + PREFIX_MARKED);

        if (markedAsUnreadUnmutedCount > 0)
          editor.putInt(prefix + PREFIX_MARKED_UNMUTED, markedAsUnreadUnmutedCount);
        else
          editor.remove(prefix + PREFIX_MARKED_UNMUTED);
      } else {
        if (messageCount > 0)
          editor.putInt(prefix + PREFIX_MESSAGES, messageCount);
        else
          editor.remove(prefix + PREFIX_MESSAGES);

        if (messageUnmutedCount > 0)
          editor.putInt(prefix + PREFIX_MESSAGES_UNMUTED, messageUnmutedCount);
        else
          editor.remove(prefix + PREFIX_MESSAGES_UNMUTED);
      }

      editor.apply();
    }
  }
}
