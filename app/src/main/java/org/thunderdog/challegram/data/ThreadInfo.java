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
package org.thunderdog.challegram.data;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.MessageThreadListener;
import org.thunderdog.challegram.telegram.Tdlib;

import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.ObjectUtils;
import me.vkryl.core.reference.ReferenceList;
import me.vkryl.td.MessageId;
import me.vkryl.td.Td;

public class ThreadInfo {
  public static final ThreadInfo INVALID = new ThreadInfo(null, new TdApi.MessageThreadInfo(), 0, false);
  public static final int UNKNOWN_UNREAD_MESSAGE_COUNT = -1;

  private final ReferenceList<MessageThreadListener> listeners = new ReferenceList<>();

  private final @Nullable Tdlib tdlib;
  private final boolean areComments;
  private final TdApi.MessageThreadInfo threadInfo;
  private final long contextChatId;

  private ThreadInfo (@Nullable Tdlib tdlib, @NonNull TdApi.MessageThreadInfo threadInfo, long contextChatId, boolean areComments) {
    this.tdlib = tdlib;
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
    return new ThreadInfo(tdlib, threadInfo, contextChatId, areComments);
  }

  @Override public boolean equals (Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ThreadInfo that = (ThreadInfo) o;
    return areComments() == that.areComments() && contextChatId == that.contextChatId && threadInfo.chatId == that.threadInfo.chatId && threadInfo.messageThreadId == that.threadInfo.messageThreadId;
  }

  @Override public int hashCode () {
    Object[] objects = new Object[] {areComments(), contextChatId, threadInfo.chatId, threadInfo.messageThreadId};
    return ObjectUtils.hashCode(objects);
  }

  public boolean belongsTo (long chatId, long messageThreadId) {
    return threadInfo.chatId == chatId && threadInfo.messageThreadId == messageThreadId;
  }

  public boolean hasMessages () {
    return threadInfo.messages != null && threadInfo.messages.length > 0;
  }

  public boolean isRootMessage (@Nullable TdApi.MessageReplyTo replyTo) {
    if (replyTo != null && replyTo.getConstructor() == TdApi.MessageReplyToMessage.CONSTRUCTOR) {
      TdApi.MessageReplyToMessage replyToMessage = (TdApi.MessageReplyToMessage) replyTo;
      TdApi.Message message = getMessage(replyToMessage.messageId);
      return message != null && message.chatId == replyToMessage.chatId;
    }
    return false;
  }

  public @Nullable TdApi.Message getMessage (long messageId) {
    for (TdApi.Message message : threadInfo.messages) {
      if (message.id == messageId) {
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

  public boolean hasUnreadMessages () {
    return hasUnreadMessages(getGlobalLastReadInboxMessageId());
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

  public int getReplyCount () {
    TdApi.MessageReplyInfo replyInfo = threadInfo.replyInfo;
    return replyInfo != null ? replyInfo.replyCount : 0;
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
    TdApi.MessageReplyInfo replyInfo = threadInfo.replyInfo;
    return replyInfo != null ? replyInfo.lastReadInboxMessageId : 0;
  }

  public long getLastReadOutboxMessageId () {
    TdApi.MessageReplyInfo replyInfo = threadInfo.replyInfo;
    return replyInfo != null ? replyInfo.lastReadOutboxMessageId : 0;
  }

  public long getLastMessageId () {
    TdApi.MessageReplyInfo replyInfo = threadInfo.replyInfo;
    return replyInfo != null ? replyInfo.lastMessageId : 0;
  }

  public @Nullable TdApi.DraftMessage getDraft () {
    return threadInfo.draftMessage;
  }

  public void setDraft (@Nullable TdApi.DraftMessage draftMessage) {
    TdApi.InputMessageReplyToMessage replyToMessage = draftMessage != null && draftMessage.replyTo instanceof TdApi.InputMessageReplyToMessage ? ((TdApi.InputMessageReplyToMessage) draftMessage.replyTo) : null;
    if (replyToMessage != null && Td.isEmpty(replyToMessage.quote)) {
      for (TdApi.Message message : threadInfo.messages) {
        if (message.chatId == replyToMessage.chatId && message.id == replyToMessage.messageId) {
          draftMessage.replyTo = null;
          break;
        }
      }
    }
    threadInfo.draftMessage = draftMessage;
  }

  public @Nullable TdApi.InputMessageContent getDraftContent () {
    return threadInfo.draftMessage != null ? threadInfo.draftMessage.inputMessageText : null;
  }

  public String chatHeaderTitle () {
    if (tdlib == null)
      return null;
    return tdlib.chatTitle(getContextChatId());
  }

  public CharSequence chatHeaderSubtitle () {
    if (tdlib == null)
      return null;
    int size = getReplyCount();
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
    return new ThreadInfo(tdlib, threadInfo, contextChatId, areComments);
  }

  public @Nullable TGMessage buildHeaderMessage (MessagesManager context) {
    if (tdlib == null)
      return null;
    TGMessage msg = null;
    for (int index = threadInfo.messages.length - 1; index >= 0; index--) {
      TdApi.Message message = threadInfo.messages[index];
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

  public void updateMessageInteractionInfo (long messageId, @Nullable TdApi.MessageInteractionInfo interactionInfo) {
    TdApi.Message message = getMessage(messageId);
    if (message != null) {
      message.interactionInfo = interactionInfo;
      if (message.canGetMessageThread) {
        updateReplyInfo(TD.getReplyInfo(interactionInfo));
      }
    }
  }

  public void updateMessageContent (long messageId, TdApi.MessageContent newContent) {
    TdApi.Message message = getMessage(messageId);
    if (message != null) {
      message.content = newContent;
    }
  }

  public void updateMessageEdited (long messageId, int editDate, @Nullable TdApi.ReplyMarkup replyMarkup) {
    TdApi.Message message = getMessage(messageId);
    if (message != null) {
      message.editDate = editDate;
      message.replyMarkup = replyMarkup;
    }
  }

  public void updateMessageIsPinned (long messageId, boolean isPinned) {
    TdApi.Message message = getMessage(messageId);
    if (message != null) {
      message.isPinned = isPinned;
    }
  }

  public void updateMessageOpened (long messageId) {
    TdApi.Message message = getMessage(messageId);
    if (message != null) {
      TD.setMessageOpened(message);
    }
  }

  public void updateMessageMentionRead (long messageId) {
    TdApi.Message message = getMessage(messageId);
    if (message != null) {
      message.containsUnreadMention = false;
    }
  }

  public void updateMessageUnreadReactions (long messageId, @Nullable TdApi.UnreadReaction[] unreadReactions) {
    TdApi.Message message = getMessage(messageId);
    if (message != null) {
      message.unreadReactions = unreadReactions;
    }
  }

  public void updateMessagesDeleted (long[] messageIds, int removedCount, int removedUnreadCount) {
    int deletedMessageCount;
    int messageCount = threadInfo.messages.length;
    if (messageCount > 0) {
      if (messageCount > 32) {
        throw new UnsupportedOperationException();
      }
      int deleted = 0;
      long oldestMessageId = getOldestMessageId();
      long newestMessageId = getNewestMessageId();
      for (long messageId : messageIds) {
        if (messageId < oldestMessageId || messageId > newestMessageId) {
          continue;
        }
        for (int index = 0; index < messageCount; index++) {
          TdApi.Message message = threadInfo.messages[index];
          if (message.id == messageId) {
            deleted = BitwiseUtils.setFlag(deleted, 1 << index, true);
          }
        }
      }
      deletedMessageCount = Integer.bitCount(deleted);
      if (deletedMessageCount > 0) {
        boolean isMessageThreadDeleted = false;
        TdApi.Message[] newMessages = new TdApi.Message[messageCount - deletedMessageCount];
        if (newMessages.length > 0) {
          int newIndex = 0;
          for (int index = 0; index < messageCount; index++) {
            TdApi.Message message = threadInfo.messages[index];
            if (!BitwiseUtils.hasFlag(deleted, 1 << index)) {
              newMessages[newIndex++] = message;
            } else if (message.canGetMessageThread) {
              isMessageThreadDeleted = true;
            }
          }
        } else {
          isMessageThreadDeleted = true;
        }
        threadInfo.messages = newMessages;
        if (isMessageThreadDeleted) {
          notifyMessageThreadDeleted();
          updateReplyInfo(null);
          return;
        }
      }
    } else {
      deletedMessageCount = 0;
    }
    int removedReplyCount = removedCount - deletedMessageCount;
    if (removedReplyCount > 0 && getReplyCount() > 0) {
      int replyCount = Math.max(getReplyCount() - removedReplyCount, 0);
      updateReplyCount(replyCount);
    }
    if (removedUnreadCount > 0 && getUnreadMessageCount() != UNKNOWN_UNREAD_MESSAGE_COUNT && hasUnreadMessages()) {
      int unreadMessageCount = getUnreadMessageCount() > removedUnreadCount ? getUnreadMessageCount() - removedUnreadCount : UNKNOWN_UNREAD_MESSAGE_COUNT;
      updateUnreadMessageCount(unreadMessageCount);
    }
  }

  public void updateNewMessage (TGMessage message) {
    if (message.isScheduled() || message.getMessageThreadId() != getMessageThreadId())
      return;

    int replyCount = getReplyCount() + message.getMessageCount();
    updateReplyCount(replyCount);

    long messageId = message.getBiggestId();
    updateLastMessage(messageId);

    if (message.isOutgoing()) {
      updateReadInbox(messageId);
    } else if (getLastReadInboxMessageId() < messageId && getUnreadMessageCount() != UNKNOWN_UNREAD_MESSAGE_COUNT) {
      int unreadMessageCount = getUnreadMessageCount() + 1 + message.getMessageCountBetween(getLastReadInboxMessageId(), messageId);
      updateUnreadMessageCount(unreadMessageCount);
    }
  }

  public void markAsRead () {
    updateReadInbox(getLastMessageId(), /* unreadMessageCount */ 0);
  }

  public void updateUnreadMessageCount (int unreadMessageCount) {
    updateReadInbox(getLastReadInboxMessageId(), unreadMessageCount);
  }

  public void updateReadInbox (long lastReadInboxMessageId) {
    updateReadInbox(lastReadInboxMessageId, getUnreadMessageCount());
  }

  public void updateReadInbox (@Nullable TdApi.Message message) {
    if (message == null || message.messageThreadId != getMessageThreadId() || TD.isScheduled(message))
      return;
    updateReadInbox(message.id);
  }

  public void addListener (MessageThreadListener listener) {
    listeners.add(listener);
  }

  public void removeListener (MessageThreadListener listener) {
    listeners.remove(listener);
  }

  private void updateLastMessage (long lastMessageId) {
    TdApi.MessageReplyInfo replyInfo = threadInfo.replyInfo;
    if (replyInfo != null && replyInfo.lastMessageId < lastMessageId) {
      replyInfo.lastMessageId = lastMessageId;
      notifyMessageThreadLastMessageChanged();
    }
  }

  public void updateReplyCount (int replyCount) {
    TdApi.MessageReplyInfo replyInfo = threadInfo.replyInfo;
    if (replyInfo != null && replyInfo.replyCount != replyCount) {
      replyInfo.replyCount = replyCount;
      notifyMessageThreadReplyCountChanged();
    }
  }

  public void updateReadInbox (long lastReadInboxMessageId, int unreadMessageCount) {
    TdApi.MessageReplyInfo replyInfo = threadInfo.replyInfo;
    if (replyInfo == null || replyInfo.lastReadInboxMessageId > lastReadInboxMessageId)
      return;
    if (replyInfo.lastReadInboxMessageId == lastReadInboxMessageId && (unreadMessageCount == UNKNOWN_UNREAD_MESSAGE_COUNT || unreadMessageCount == threadInfo.unreadMessageCount))
      return;
    updateLastMessage(lastReadInboxMessageId);
    replyInfo.lastReadInboxMessageId = lastReadInboxMessageId;
    threadInfo.unreadMessageCount = Td.hasUnread(replyInfo, getGlobalLastReadInboxMessageId()) ? unreadMessageCount : 0;
    notifyMessageThreadReadInbox();
  }

  private void updateReadOutbox (long lastReadOutboxMessageId) {
    TdApi.MessageReplyInfo replyInfo = threadInfo.replyInfo;
    if (replyInfo == null || replyInfo.lastReadOutboxMessageId >= lastReadOutboxMessageId)
      return;
    updateLastMessage(lastReadOutboxMessageId);
    replyInfo.lastReadOutboxMessageId = lastReadOutboxMessageId;
    notifyMessageThreadReadOutbox();
  }

  private void updateReplyInfo (@Nullable TdApi.MessageReplyInfo replyInfo) {
    if (replyInfo == null) {
      // thread has been deleted.
      if (threadInfo.replyInfo != null) {
        threadInfo.replyInfo.recentReplierIds = new TdApi.MessageSender[0];
      }
      updateLastMessage(MessageId.MAX_VALID_ID);
      updateReplyCount(0);
      updateReadInbox(MessageId.MAX_VALID_ID, /* unreadMessageCount */ 0);
      updateReadOutbox(MessageId.MAX_VALID_ID);
    } else {
      // update ids only
      if (threadInfo.replyInfo != null) {
        threadInfo.replyInfo.recentReplierIds = replyInfo.recentReplierIds;
      }
      updateLastMessage(replyInfo.lastMessageId);
      updateReadInbox(replyInfo.lastReadInboxMessageId, UNKNOWN_UNREAD_MESSAGE_COUNT);
      updateReadOutbox(replyInfo.lastReadOutboxMessageId);
    }
  }

  private void notifyMessageThreadReadInbox () {
    for (MessageThreadListener listener : listeners) {
      listener.onMessageThreadReadInbox(getChatId(), getMessageThreadId(), getLastReadInboxMessageId(), getUnreadMessageCount());
    }
  }

  private void notifyMessageThreadReadOutbox () {
    for (MessageThreadListener listener : listeners) {
      listener.onMessageThreadReadOutbox(getChatId(), getMessageThreadId(), getLastReadOutboxMessageId());
    }
  }

  private void notifyMessageThreadReplyCountChanged () {
    for (MessageThreadListener listener : listeners) {
      listener.onMessageThreadReplyCountChanged(getChatId(), getMessageThreadId(), getReplyCount());
    }
  }

  private void notifyMessageThreadLastMessageChanged () {
    for (MessageThreadListener listener : listeners) {
      listener.onMessageThreadLastMessageChanged(getChatId(), getMessageThreadId(), getLastMessageId());
    }
  }

  private void notifyMessageThreadDeleted () {
    for (MessageThreadListener listener : listeners) {
      listener.onMessageThreadDeleted(getChatId(), getMessageThreadId());
    }
  }

  private long getGlobalLastReadInboxMessageId () {
    TdApi.Chat chat = tdlib != null ? tdlib.chat(threadInfo.chatId) : null;
    return chat != null ? chat.lastReadInboxMessageId : 0;
  }
}
