/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.thunderdog.challegram.data;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;

import java.util.Objects;

import me.vkryl.td.MessageId;
import me.vkryl.td.Td;

public class ThreadInfo {
  public static final ThreadInfo INVALID = new ThreadInfo(new TdApi.MessageThreadInfo(), 0, false);

  private final boolean areComments;
  private final TdApi.MessageThreadInfo threadInfo;
  private final long contextChatId;

  private ThreadInfo (@NonNull TdApi.MessageThreadInfo threadInfo, long contextChatId, boolean areComments) {
    this.areComments = areComments;
    this.threadInfo = threadInfo;
    this.contextChatId = contextChatId;

    long messageId = threadInfo.draftMessage != null ? threadInfo.draftMessage.replyToMessageId : 0;
    if (messageId != 0) {
      for (TdApi.Message message : threadInfo.messages) {
        if (message.id == messageId) {
          threadInfo.draftMessage.replyToMessageId = 0;
          break;
        }
      }
    }
  }

  public static @NonNull ThreadInfo openedFromMessage (@NonNull TdApi.MessageThreadInfo threadInfo, @Nullable MessageId messageId) {
    return openedFromChat(threadInfo, messageId != null ? messageId.getChatId() : 0);
  }

  public static @NonNull ThreadInfo openedFromChat (@NonNull TdApi.MessageThreadInfo threadInfo, long chatId) {
    return openedFromChat(threadInfo, chatId, 0);
  }

  public static @NonNull ThreadInfo openedFromChat (@NonNull TdApi.MessageThreadInfo threadInfo, long chatId, long contextChatId) {
    TdApi.Message oldestMessage = getOldestMessage(threadInfo);
    boolean areComments = TD.isChannelAutoForward(oldestMessage);
    if (contextChatId == 0 && areComments && chatId != oldestMessage.chatId) {
      contextChatId = TD.forwardFromGhatId(oldestMessage);
    }
    return new ThreadInfo(threadInfo, contextChatId, areComments);
  }

  @Override public boolean equals (Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ThreadInfo that = (ThreadInfo) o;
    return areComments() == that.areComments() && contextChatId == that.contextChatId && threadInfo.chatId == that.threadInfo.chatId && threadInfo.messageThreadId == that.threadInfo.messageThreadId;
  }

  @Override public int hashCode () {
    return Objects.hash(areComments(), contextChatId, threadInfo.chatId, threadInfo.messageThreadId);
  }

  public boolean belongsTo (long chatId, long messageThreadId) {
    return threadInfo.chatId == chatId && threadInfo.messageThreadId == messageThreadId;
  }

  public TdApi.Message[] getMessages () {
    return threadInfo.messages;
  }

  public long getOldestMessageId () {
    TdApi.Message oldestMessage = getOldestMessage();
    return oldestMessage != null ? oldestMessage.id : 0;
  }

  public @Nullable TdApi.Message getOldestMessage () {
    return getOldestMessage(threadInfo);
  }

  public static @Nullable TdApi.Message getOldestMessage (@Nullable TdApi.MessageThreadInfo threadInfo) {
    if (threadInfo != null && threadInfo.messages != null && threadInfo.messages.length > 0) {
      return threadInfo.messages[threadInfo.messages.length - 1];
    }
    return null;
  }

  public boolean areComments () {
    return areComments;
  }

  public boolean hasUnreadMessages () {
    return Td.hasUnread(threadInfo.replyInfo);
  }

  public int getSize () {
    return threadInfo.replyInfo.replyCount;
  }

  public TdApi.MessageReplyInfo getReplyInfo () {
    return threadInfo.replyInfo;
  }

  public void setReplyInfo (@NonNull TdApi.MessageReplyInfo replyInfo) {
    threadInfo.replyInfo = replyInfo;
  }

  public long getChatId () {
    return threadInfo.chatId;
  }

  public long getContextChatId () {
    return contextChatId != 0 ? contextChatId : getChatId();
  }

  public long getMessageThreadId () {
    return threadInfo.messageThreadId;
  }

  public long getLastReadMessageId () {
    return threadInfo.replyInfo.lastReadInboxMessageId;
  }

  public long getLastMessageId () {
    return threadInfo.replyInfo.lastMessageId;
  }

  public @Nullable TdApi.DraftMessage getDraft () {
    return threadInfo.draftMessage;
  }

  public @Nullable TdApi.InputMessageContent getDraftContent () {
    return threadInfo.draftMessage != null ? threadInfo.draftMessage.inputMessageText : null;
  }

  public String chatHeaderTitle (Tdlib tdlib) {
    return tdlib.chatTitle(getContextChatId());
  }

  public CharSequence chatHeaderSubtitle (Tdlib tdlib) {
    int size = getSize();
    CharSequence subtitle;
    if (areComments()) {
      subtitle = size > 0 ? Lang.pluralBold(R.string.xComments, size) : Lang.getString(R.string.CommentsTitle);
    } else {
      if (size > 0) {
        TdApi.Message message = getOldestMessage();
        if (message != null) {
          String senderName = tdlib.senderName(message.senderId, true);
          subtitle = Lang.pluralBold(R.string.xRepliesToUser, size, senderName);
        } else {
          subtitle = Lang.pluralBold(R.string.xReplies, size);
        }
      } else {
        subtitle = Lang.getString(R.string.RepliesTitle);
      }
    }
    return subtitle;
  }

  public void saveTo (Bundle outState, String prefix) {
    TD.saveMessageThreadInfo(outState, prefix, threadInfo);
    outState.putLong(prefix + "_contextChatId", contextChatId);
    outState.putBoolean(prefix + "_areComments", areComments);
  }

  public static @Nullable ThreadInfo restoreFrom (Tdlib tdlib, Bundle savedState, String prefix) {
    if (!savedState.containsKey(prefix + "_areComments")) {
      return null;
    }
    TdApi.MessageThreadInfo threadInfo = TD.restoreMessageThreadInfo(tdlib, savedState, prefix);
    if (threadInfo == null) {
      return ThreadInfo.INVALID;
    }
    long contextChatId = savedState.getLong(prefix + "_contextChatId");
    boolean areComments = savedState.getBoolean(prefix + "_areComments");
    return new ThreadInfo(threadInfo, contextChatId, areComments);
  }
}
