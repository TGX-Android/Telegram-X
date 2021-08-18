package org.thunderdog.challegram.telegram;

import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;

/**
 * Date: 2/15/18
 * Author: default
 */
public interface MessageListener {
  default void onNewMessage (TdApi.Message message) { }
  default void onMessageSendAcknowledged (long chatId, long messageId) { }
  default void onMessageSendSucceeded (TdApi.Message message, long oldMessageId) { }
  default void onMessageSendFailed (TdApi.Message message, long oldMessageId, int errorCode, String errorMessage) { }
  default void onMessageContentChanged (long chatId, long messageId, TdApi.MessageContent newContent) { }
  default void onMessageEdited (long chatId, long messageId, int editDate, @Nullable TdApi.ReplyMarkup replyMarkup) { }
  default void onMessagePinned (long chatId, long messageId, boolean isPinned) { }
  default void onMessageOpened (long chatId, long messageId) { }
  default void onMessageMentionRead (long chatId, long messageId) { }
  default void onMessageInteractionInfoChanged (long chatId, long messageId, @Nullable TdApi.MessageInteractionInfo interactionInfo) { }
  default void onMessagesDeleted (long chatId, long[] messageIds) { }
  default void onMessageLiveLocationViewed (long chatId, long messageId) { }
}
