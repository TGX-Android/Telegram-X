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
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;

import java.util.Objects;

import me.vkryl.td.MessageId;
import me.vkryl.td.Td;

public class ThreadInfo {
  public static final ThreadInfo INVALID = new ThreadInfo(new TdApi.MessageThreadInfo(), 0, false);
  public static final int UNKNOWN_UNREAD_MESSAGE_COUNT = -1;

  private final boolean areComments;
  private final TdApi.MessageThreadInfo threadInfo;
  private final long contextChatId;

  private ThreadInfo (@NonNull TdApi.MessageThreadInfo threadInfo, long contextChatId, boolean areComments) {
    this.areComments = areComments;
    this.threadInfo = threadInfo;
    this.contextChatId = contextChatId;
    setDraft(threadInfo.draftMessage); // nulls draftMessage.replyToMessageId if draft is reply to one of message from which the thread starts
  }

  public static @NonNull ThreadInfo openedFromMessage (@NonNull Tdlib tdlib, @NonNull TdApi.MessageThreadInfo threadInfo, @Nullable MessageId messageId) {
    return openedFromChat(tdlib, threadInfo, messageId != null ? messageId.getChatId() : 0);
  }

  public static @NonNull ThreadInfo openedFromChat (@NonNull Tdlib tdlib, @NonNull TdApi.MessageThreadInfo threadInfo, long chatId) {
    return openedFromChat(tdlib, threadInfo, chatId, 0);
  }

  public static @NonNull ThreadInfo openedFromChat (@NonNull Tdlib tdlib, @NonNull TdApi.MessageThreadInfo threadInfo, long chatId, long contextChatId) {
    TdApi.Message oldestMessage = getOldestMessage(threadInfo);
    boolean areComments = tdlib.isChannelAutoForward(oldestMessage);
    if (contextChatId == 0 && areComments && oldestMessage != null && chatId != oldestMessage.chatId) {
      //noinspection ConstantConditions
      contextChatId = oldestMessage.forwardInfo.fromChatId;
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

  public long getNewestMessageId () {
    TdApi.Message newestMessage = getNewestMessage();
    return newestMessage != null ? newestMessage.id : 0;
  }

  public @Nullable TdApi.Message getOldestMessage () {
    return getOldestMessage(threadInfo);
  }

  public @Nullable TdApi.Message getNewestMessage() {
    return getNewestMessage(threadInfo);
  }

  public static @Nullable TdApi.Message getNewestMessage (@Nullable TdApi.MessageThreadInfo threadInfo) {
    if (threadInfo != null && threadInfo.messages != null && threadInfo.messages.length > 0) {
      return threadInfo.messages[0];
    }
    return null;
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

  public boolean hasUnreadMessages (@Nullable TdApi.Chat chat) {
    long lastGlobalReadInboxMessageId = chat != null && chat.id == threadInfo.chatId ? chat.lastReadInboxMessageId : 0;
    return hasUnreadMessages(lastGlobalReadInboxMessageId);
  }

  public boolean hasUnreadMessages (long lastGlobalReadInboxMessageId) {
    if (threadInfo.unreadMessageCount != UNKNOWN_UNREAD_MESSAGE_COUNT) {
      return threadInfo.unreadMessageCount > 0;
    }
    return Td.hasUnread(threadInfo.replyInfo, lastGlobalReadInboxMessageId);
  }

  public int getUnreadMessageCount () {
    return threadInfo.unreadMessageCount;
  }

  public int getSize () {
    return threadInfo.replyInfo.replyCount;
  }

  public void setSize (int size) {
    threadInfo.replyInfo.replyCount = size;
  }

  public void setReplyInfo (@NonNull TdApi.MessageReplyInfo replyInfo) {
    threadInfo.unreadMessageCount = UNKNOWN_UNREAD_MESSAGE_COUNT; // TODO(nikita-toropov) unreadMessageCount
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

  public long getLastReadInboxMessageId () {
    return threadInfo.replyInfo.lastReadInboxMessageId;
  }

  public long getLastReadOutboxMessageId () {
    return threadInfo.replyInfo.lastReadOutboxMessageId;
  }

  public long getLastMessageId () {
    return threadInfo.replyInfo.lastMessageId;
  }

  public @Nullable TdApi.DraftMessage getDraft () {
    return threadInfo.draftMessage;
  }

  public void setDraft (@Nullable TdApi.DraftMessage draftMessage) {
    long replyToMessageId = draftMessage != null ? draftMessage.replyToMessageId : 0;
    if (replyToMessageId != 0) {
      for (TdApi.Message message : threadInfo.messages) {
        if (message.id == replyToMessageId) {
          draftMessage.replyToMessageId = 0;
          break;
        }
      }
    }
    threadInfo.draftMessage = draftMessage;
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
