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
import org.thunderdog.challegram.telegram.ChatListener;
import org.thunderdog.challegram.telegram.MessageThreadListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;

import java.util.Objects;

import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.reference.ReferenceList;
import me.vkryl.td.MessageId;
import me.vkryl.td.Td;

public class ThreadInfo implements MessageThreadListener, ChatListener {
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
    return Objects.hash(areComments(), contextChatId, threadInfo.chatId, threadInfo.messageThreadId);
  }

  public boolean belongsTo (long chatId, long messageThreadId) {
    return threadInfo.chatId == chatId && threadInfo.messageThreadId == messageThreadId;
  }

  public boolean hasMessages () {
    return threadInfo.messages != null && threadInfo.messages.length > 0;
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
    return tdlib != null && hasUnreadMessages(tdlib.chat(threadInfo.chatId));
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

  public String chatHeaderTitle () {
    if (tdlib == null)
      return null;
    return tdlib.chatTitle(getContextChatId());
  }

  public CharSequence chatHeaderSubtitle () {
    if (tdlib == null)
      return null;
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
    }
    if (messageId == threadInfo.messageThreadId) {
      updateReplyInfo(TD.getReplyInfo(interactionInfo));
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
        TdApi.Message[] newMessages = new TdApi.Message[messageCount - deletedMessageCount];
        if (newMessages.length > 0) {
          int newIndex = 0;
          for (int index = 0; index < messageCount; index++) {
            TdApi.Message message = threadInfo.messages[index];
            if (!BitwiseUtils.getFlag(deleted, 1 << index)) {
              newMessages[newIndex++] = message;
            }
          }
        }
        threadInfo.messages = newMessages;
      }
    } else {
      deletedMessageCount = 0;
    }
    int removedReplyCount = removedCount - deletedMessageCount;
    if (removedReplyCount > 0 && threadInfo.replyInfo.replyCount > 0) {
      int replyCount = Math.max(threadInfo.replyInfo.replyCount - removedReplyCount, 0);
      updateReplyCount(replyCount, true);
    }
    int unreadMessageCount;
    if (threadInfo.unreadMessageCount != UNKNOWN_UNREAD_MESSAGE_COUNT) {
      if (Td.hasUnread(threadInfo.replyInfo, getGlobalLastReadInboxMessageId())) {
        if (threadInfo.unreadMessageCount > removedUnreadCount) {
          unreadMessageCount = threadInfo.unreadMessageCount - removedUnreadCount;
        } else {
          unreadMessageCount = UNKNOWN_UNREAD_MESSAGE_COUNT;
        }
      } else {
        unreadMessageCount = 0;
      }
      updateUnreadMessageCount(unreadMessageCount, true);
    }
  }

  public void updateNewMessage (TGMessage message) {
    if (message.isScheduled())
      return;

    int replyCount = threadInfo.replyInfo.replyCount + message.getMessageCount();
    updateReplyCount(replyCount, true);

    long messageId = message.getBiggestId();
    updateLastMessage(messageId, true);

    if (!message.isOutgoing() && threadInfo.replyInfo.lastReadInboxMessageId < messageId) {
      if (message.canMarkAsViewed()) {
        if (threadInfo.unreadMessageCount != UNKNOWN_UNREAD_MESSAGE_COUNT) {
          int unreadMessageCount = threadInfo.unreadMessageCount + 1 + message.getMessageCountBetween(threadInfo.replyInfo.lastReadInboxMessageId, messageId);
          updateUnreadMessageCount(unreadMessageCount, true);
        }
      } else {
        updateReadInbox(messageId, true);
      }
    }
  }

  public void updateMessageViewed (TGMessage message) {
    if (message.isScheduled())
      return;
    long messageId = message.getBiggestId();
    updateLastMessage(messageId, true);
    if (threadInfo.replyInfo.lastReadInboxMessageId >= messageId)
      return;
    if (threadInfo.unreadMessageCount == UNKNOWN_UNREAD_MESSAGE_COUNT || message.isOutgoing()) {
      updateReadInbox(messageId, true);
      return;
    }
    int unreadMessageCount;
    if (threadInfo.replyInfo.lastMessageId > Math.max(getGlobalLastReadInboxMessageId(), messageId)) {
      int readMessageCount = 1 + message.getMessageCountBetween(threadInfo.replyInfo.lastReadInboxMessageId, messageId);
      if (threadInfo.unreadMessageCount > readMessageCount) {
        unreadMessageCount = threadInfo.unreadMessageCount - readMessageCount;
      } else {
        unreadMessageCount = UNKNOWN_UNREAD_MESSAGE_COUNT;
      }
    } else {
      unreadMessageCount = 0;
    }
    updateReadInbox(messageId, unreadMessageCount, true);
  }

  public void updateUnreadMessageCount (int unreadMessageCount) {
    updateUnreadMessageCount(unreadMessageCount, true);
  }

  @Override
  public void onChatReadInbox (long chatId, long lastReadInboxMessageId, int unreadCount, boolean availabilityChanged) {
    if (chatId == threadInfo.chatId) {
      UI.post(() -> updateReadInbox(lastReadInboxMessageId, true));
    }
  }

  @Override
  public void onChatReadOutbox (long chatId, long lastReadOutboxMessageId) {
    if (chatId == threadInfo.chatId) {
      UI.post(() -> updateReadOutbox(lastReadOutboxMessageId, true));
    }
  }

  @Override
  public void onMessageThreadReadInbox (long chatId, long messageThreadId, long lastReadInboxMessageId, int unreadMessageCount) {
    if (belongsTo(chatId, messageThreadId)) {
      updateReadInbox(lastReadInboxMessageId, unreadMessageCount, false);
    }
  }

  @Override
  public void onMessageThreadReadOutbox (long chatId, long messageThreadId, long lastReadOutboxMessageId) {
    if (belongsTo(chatId, messageThreadId)) {
      updateReadOutbox(lastReadOutboxMessageId, false);
    }
  }

  @Override
  public void onMessageThreadLastMessageChanged (long chatId, long messageThreadId, long lastMessageId) {
    if (belongsTo(chatId, messageThreadId)) {
      updateLastMessage(lastMessageId, false);
    }
  }

  @Override
  public void onMessageThreadReplyCountChanged (long chatId, long messageThreadId, int replyCount) {
    if (belongsTo(chatId, messageThreadId)) {
      updateReplyCount(replyCount, false);
    }
  }

  public void addListener (MessageThreadListener listener) {
    listeners.add(listener);
  }

  public void removeListener (MessageThreadListener listener) {
    listeners.remove(listener);
  }

  public void subscribeForUpdates () {
    if (tdlib != null) {
      tdlib.listeners().subscribeToChatUpdates(threadInfo.chatId, this);
      tdlib.listeners().subscribeToMessageThreadUpdates(threadInfo.chatId, threadInfo.messageThreadId, this);
    }
  }

  public void unsubscribeFromUpdates () {
    if (tdlib != null) {
      tdlib.listeners().unsubscribeFromChatUpdates(threadInfo.chatId, this);
      tdlib.listeners().unsubscribeFromMessageThreadUpdates(threadInfo.chatId, threadInfo.messageThreadId, this);
    }
  }

  private void updateLastMessage (long lastMessageId, boolean broadcast) {
    if (threadInfo.replyInfo.lastMessageId < lastMessageId) {
      threadInfo.replyInfo.lastMessageId = lastMessageId;

      for (MessageThreadListener listener : listeners) {
        listener.onMessageThreadLastMessageChanged(threadInfo.chatId, threadInfo.messageThreadId, lastMessageId);
      }
      if (tdlib != null && broadcast) {
        tdlib.listeners().notifyMessageThreadLastMessageChanged(threadInfo.chatId, threadInfo.messageThreadId, lastMessageId);
      }
    }
  }

  private void updateReplyCount (int replyCount, boolean broadcast) {
    if (threadInfo.replyInfo.replyCount != replyCount) {
      threadInfo.replyInfo.replyCount = replyCount;

      for (MessageThreadListener listener : listeners) {
        listener.onMessageThreadReplyCountChanged(threadInfo.chatId, threadInfo.messageThreadId, threadInfo.replyInfo.replyCount);
      }
      if (tdlib != null && broadcast) {
        tdlib.listeners().notifyMessageThreadReplyCountChanged(threadInfo.chatId, threadInfo.messageThreadId, threadInfo.replyInfo.replyCount);
      }
    }
  }

  private void updateUnreadMessageCount (int unreadMessageCount, @SuppressWarnings("SameParameterValue") boolean broadcast) {
    updateReadInbox(threadInfo.replyInfo.lastReadInboxMessageId, unreadMessageCount, broadcast);
  }

  private void updateReadInbox (long lastReadInboxMessageId, @SuppressWarnings("SameParameterValue") boolean broadcast) {
    updateReadInbox(lastReadInboxMessageId, threadInfo.unreadMessageCount, broadcast);
  }

  private void updateReadInbox (long lastReadInboxMessageId, int unreadMessageCount, boolean broadcast) {
    if (threadInfo.replyInfo.lastReadInboxMessageId < lastReadInboxMessageId || threadInfo.unreadMessageCount != unreadMessageCount) {
      threadInfo.replyInfo.lastReadInboxMessageId = lastReadInboxMessageId;
      threadInfo.unreadMessageCount = unreadMessageCount;

      for (MessageThreadListener listener : listeners) {
        listener.onMessageThreadReadInbox(threadInfo.chatId, threadInfo.messageThreadId, lastReadInboxMessageId, unreadMessageCount);
      }
      if (tdlib != null && broadcast) {
        tdlib.listeners().notifyMessageThreadReadInbox(threadInfo.chatId, threadInfo.messageThreadId, lastReadInboxMessageId, unreadMessageCount);
      }
    }
  }

  private void updateReadOutbox (long lastReadOutboxMessageId, boolean broadcast) {
    if (threadInfo.replyInfo.lastReadOutboxMessageId < lastReadOutboxMessageId) {
      threadInfo.replyInfo.lastReadOutboxMessageId = lastReadOutboxMessageId;

      for (MessageThreadListener listener : listeners) {
        listener.onMessageThreadReadOutbox(threadInfo.chatId, threadInfo.messageThreadId, lastReadOutboxMessageId);
      }
      if (tdlib != null && broadcast) {
        tdlib.listeners().notifyMessageThreadReadOutbox(threadInfo.chatId, threadInfo.messageThreadId, lastReadOutboxMessageId);
      }
    }
  }

  private void updateReplyInfo (@Nullable TdApi.MessageReplyInfo replyInfo) {
    if (replyInfo == null) {
      // thread has been deleted.
      threadInfo.replyInfo.recentReplierIds = new TdApi.MessageSender[0];
      updateLastMessage(MessageId.MAX_VALID_ID, true);
      updateReplyCount(0, true);
      updateReadInbox(MessageId.MAX_VALID_ID, /* unreadMessageCount */ 0, true);
      updateReadOutbox(MessageId.MAX_VALID_ID, true);
    } else {
      // update ids only
      threadInfo.replyInfo.recentReplierIds = replyInfo.recentReplierIds;
      updateLastMessage(replyInfo.lastMessageId, true);
      updateReadInbox(replyInfo.lastReadInboxMessageId, true);
      updateReadOutbox(replyInfo.lastReadOutboxMessageId, true);
    }
  }

  private long getGlobalLastReadInboxMessageId () {
    TdApi.Chat chat = tdlib != null ? tdlib.chat(threadInfo.chatId) : null;
    return chat != null ? chat.lastReadInboxMessageId : 0;
  }
}
