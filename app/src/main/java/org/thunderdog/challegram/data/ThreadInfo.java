package org.thunderdog.challegram.data;

import android.os.Bundle;

import org.drinkless.td.libcore.telegram.TdApi;

import java.util.Objects;

public class ThreadInfo {
  private final boolean areComments;
  private final TdApi.MessageThreadInfo threadInfo;

  public ThreadInfo (TdApi.Message[] openedFromMessages, TdApi.MessageThreadInfo threadInfo, boolean forceComments) {
    this.areComments = openedFromMessages[0].isChannelPost || (forceComments && (threadInfo.messages[0].isChannelPost || (threadInfo.messages[0].senderId.getConstructor() == TdApi.MessageSenderChat.CONSTRUCTOR && ((TdApi.MessageSenderChat) threadInfo.messages[0].senderId).chatId != threadInfo.messages[0].chatId)));
    this.threadInfo = threadInfo;
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

  public TdApi.Message[] getMessages () {
    return threadInfo.messages;
  }

  @Override
  public boolean equals (Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ThreadInfo that = (ThreadInfo) o;
    return areComments() == that.areComments() &&
      threadInfo.chatId == that.threadInfo.chatId &&
      threadInfo.messageThreadId == that.threadInfo.messageThreadId;
  }

  @Override
  public int hashCode () {
    return Objects.hash(areComments(), threadInfo.chatId, threadInfo.messageThreadId);
  }

  public boolean areComments () {
    return areComments;
  }

  public boolean hasUnreadMessages () {
    long messageId = Math.max(threadInfo.replyInfo.lastReadInboxMessageId, threadInfo.replyInfo.lastReadOutboxMessageId);
    return messageId != 0 && threadInfo.replyInfo.lastMessageId > messageId;
  }

  public TdApi.MessageReplyInfo getReplyInfo () {
    return threadInfo.replyInfo;
  }

  public long getChatId () {
    return threadInfo.chatId;
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

  public TdApi.DraftMessage getDraft () {
    return threadInfo.draftMessage;
  }

  public int getSize () {
    return threadInfo.replyInfo.replyCount;
  }

  public TdApi.InputMessageContent getDraftContent () {
    return threadInfo.draftMessage != null ? threadInfo.draftMessage.inputMessageText : null;
  }

  public void saveTo (Bundle outState, String prefix) {
    // TODO
  }

  public static ThreadInfo restoreFrom (Bundle outState, String prefix) {
    return null;
  }
}
