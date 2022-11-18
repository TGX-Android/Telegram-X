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
import org.thunderdog.challegram.component.chat.MessagesManager;
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

  public boolean hasMessages () {
    return threadInfo.messages != null && threadInfo.messages.length > 0;
  }

  public @Nullable TdApi.Message getMessage (long chatId, long messageId) {
    if (threadInfo.chatId != chatId)
      return null;
    for (TdApi.Message message : threadInfo.messages) {
      if (message.chatId == chatId && message.id == messageId) {
        return message;
      }
    }
    return null;
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

  public @Nullable TdApi.Message getNewestMessage () {
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
    if (threadInfo.unreadMessageCount == UNKNOWN_UNREAD_MESSAGE_COUNT) {
      threadInfo.replyInfo = new TdApi.MessageReplyInfo(
        replyInfo.replyCount,
        replyInfo.recentReplierIds,
        Math.max(threadInfo.replyInfo.lastReadInboxMessageId, replyInfo.lastReadInboxMessageId),
        Math.max(threadInfo.replyInfo.lastReadOutboxMessageId, replyInfo.lastReadOutboxMessageId),
        Math.max(threadInfo.replyInfo.lastMessageId, replyInfo.lastMessageId)
      );
    } else {
      threadInfo.replyInfo.replyCount = replyInfo.replyCount;
      threadInfo.replyInfo.recentReplierIds = replyInfo.recentReplierIds;
    }
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
    threadInfo.unreadMessageCount = UNKNOWN_UNREAD_MESSAGE_COUNT;
    long contextChatId = savedState.getLong(prefix + "_contextChatId");
    boolean areComments = savedState.getBoolean(prefix + "_areComments");
    return new ThreadInfo(threadInfo, contextChatId, areComments);
  }

  public @Nullable TGMessage buildHeaderMessage (Tdlib tdlib, MessagesManager context) {
    TGMessage msg = null;
    for (int index = threadInfo.messages.length - 1; index >= 0; index--) {
      TdApi.Message message = threadInfo.messages[index];
      if (message == null) {
        continue;
      }
      if (msg == null) {
        msg = TGMessage.valueOf(context, message, tdlib.chatStrict(message.chatId), this, (TdApi.ChatAdministrator) null);
      } else {
        msg.combineWith(message, true);
      }
    }
    if (msg != null) {
      msg.setIsThreadHeader(true);
    }
    return msg;
  }

  // Updates

  public void updateInteractionInfoChanged (long chatId, long messageId, @Nullable TdApi.MessageInteractionInfo interactionInfo) {
    TdApi.Message message = getMessage(chatId, messageId);
    if (message != null) {
      message.interactionInfo = interactionInfo;
    }
  }

  public void updateMessageContentChanged (long chatId, long messageId, TdApi.MessageContent newContent) {
    TdApi.Message message = getMessage(chatId, messageId);
    if (message != null) {
      message.content = newContent;
    }
  }

  public void updateMessageEdited (long chatId, long messageId, int editDate, @Nullable TdApi.ReplyMarkup replyMarkup) {
    TdApi.Message message = getMessage(chatId, messageId);
    if (message != null) {
      message.editDate = editDate;
      message.replyMarkup = replyMarkup;
    }
  }

  public void updateMessagePinned (long chatId, long messageId, boolean isPinned) {
    TdApi.Message message = getMessage(chatId, messageId);
    if (message != null) {
      message.isPinned = isPinned;
    }
  }

  public void updateMessageOpened (long chatId, long messageId) {
    TdApi.Message message = getMessage(chatId, messageId);
    if (message != null) {
      TD.setMessageOpened(message);
    }
  }

  public void updateMessageMentionRead (long chatId, long messageId) {
    TdApi.Message message = getMessage(chatId, messageId);
    if (message != null) {
      message.containsUnreadMention = false;
    }
  }

  public void updateMessageUnreadReactionsChanged (long chatId, long messageId, @Nullable TdApi.UnreadReaction[] unreadReactions) {
    TdApi.Message message = getMessage(chatId, messageId);
    if (message != null) {
      message.unreadReactions = unreadReactions;
    }
  }

  public void updateMessagesDeleted (long chatId, long[] messageIds) {
    if (threadInfo.chatId != chatId || !hasMessages())
      return;
    int deletedMessageCount = 0;
    long oldestMessageId = getOldestMessageId();
    long newestMessageId = getNewestMessageId();
    TdApi.Message[] messages = null;
    for (long messageId : messageIds) {
      if (messageId < oldestMessageId || messageId > newestMessageId) {
        continue;
      }
      if (messages == null) {
        messages = threadInfo.messages.clone();
      }
      for (int i = 0; i < messages.length; i++) {
        TdApi.Message message = messages[i];
        if (message != null && message.chatId == chatId && message.id == messageId) {
          messages[i] = null;
          deletedMessageCount++;
        }
      }
    }
    if (messages != null && deletedMessageCount > 0) {
      TdApi.Message[] newMessages = new TdApi.Message[messages.length - deletedMessageCount];
      if (newMessages.length > 0) {
        int index = 0;
        for (TdApi.Message message : messages) {
          if (message != null) {
            newMessages[index++] = message;
          }
        }
      }
      threadInfo.messages = newMessages;
    }
  }

  public boolean onNewMessage (TGMessage message) {
    if (message.isScheduled() || message.isNotSent())
      return false;
    long messageId = message.getBiggestId();
    if (threadInfo.replyInfo.lastMessageId < messageId) {
      threadInfo.replyInfo.lastMessageId = messageId;
      if (message.isUnread() && !message.isOutgoing() && messageId > threadInfo.replyInfo.lastReadInboxMessageId &&
        threadInfo.unreadMessageCount != UNKNOWN_UNREAD_MESSAGE_COUNT) {
        threadInfo.unreadMessageCount += 1 + message.getMessageCountBetween(threadInfo.replyInfo.lastReadInboxMessageId, messageId);
      }
      return true;
    }
    return false;
  }

  public void onMessageViewed (TGMessage message) {
    if (message.isScheduled() || message.isNotSent())
      return;
    long messageId = message.getBiggestId();
    threadInfo.replyInfo.lastMessageId = Math.max(threadInfo.replyInfo.lastMessageId, messageId);
    if (message.isOutgoing()) {
      threadInfo.replyInfo.lastReadOutboxMessageId = Math.max(threadInfo.replyInfo.lastReadOutboxMessageId, messageId);
      return;
    }
    if (threadInfo.replyInfo.lastReadInboxMessageId >= messageId) {
      return;
    }
    long lastReadInboxMessageId = threadInfo.replyInfo.lastReadInboxMessageId;
    threadInfo.replyInfo.lastReadInboxMessageId = messageId;
    if (threadInfo.unreadMessageCount == UNKNOWN_UNREAD_MESSAGE_COUNT)
      return;
    if (threadInfo.unreadMessageCount > 0) {
      TdApi.Chat chat = message.getChat();
      long lastGlobalReadInboxMessageId = chat != null ? chat.lastReadInboxMessageId : 0;
      if (Td.hasUnread(threadInfo.replyInfo, lastGlobalReadInboxMessageId)) {
        int readMessageCount = 1 + message.getMessageCountBetween(lastReadInboxMessageId, messageId);
        threadInfo.unreadMessageCount = Math.max(threadInfo.unreadMessageCount - readMessageCount, 0);
      } else {
        threadInfo.unreadMessageCount = 0;
      }
    }
  }
}
