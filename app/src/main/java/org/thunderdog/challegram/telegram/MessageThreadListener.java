package org.thunderdog.challegram.telegram;

public interface MessageThreadListener {
  default void onMessageThreadLastMessageChanged (long chatId, long messageThreadId, long lastMessageId) { }
  default void onMessageThreadReplyCountChanged (long chatId, long messageThreadId, int replyCount) { }
  default void onMessageThreadReadInbox (long chatId, long messageThreadId, long lastReadInboxMessageId, int remainingUnreadCount) { }
  default void onMessageThreadReadOutbox (long chatId, long messageThreadId, long lastReadOutboxMessageId) { }
  default void onMessageThreadDeleted (long chatId, long messageThreadId) { }
}
