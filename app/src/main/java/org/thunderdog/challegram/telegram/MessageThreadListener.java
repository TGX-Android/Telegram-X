package org.thunderdog.challegram.telegram;

import org.drinkless.tdlib.TdApi;

public interface MessageThreadListener {
  default void onMessageThreadLastMessageChanged (long chatId, TdApi.MessageTopic topicId, long lastMessageId) { }
  default void onMessageThreadReplyCountChanged (long chatId, TdApi.MessageTopic topicId, int replyCount) { }
  default void onMessageThreadReadInbox (long chatId, TdApi.MessageTopic topicId, long lastReadInboxMessageId, int remainingUnreadCount) { }
  default void onMessageThreadReadOutbox (long chatId, TdApi.MessageTopic topicId, long lastReadOutboxMessageId) { }
  default void onMessageThreadDeleted (long chatId, TdApi.MessageTopic topicId) { }
}
