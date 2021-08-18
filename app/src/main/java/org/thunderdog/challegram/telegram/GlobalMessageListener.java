package org.thunderdog.challegram.telegram;

import org.drinkless.td.libcore.telegram.TdApi;

/**
 * Date: 2/18/18
 * Author: default
 */
public interface GlobalMessageListener {
  void onNewMessage (Tdlib tdlib, TdApi.Message message);

  void onNewMessages (Tdlib tdlib, TdApi.Message[] messages);

  void onMessageSendSucceeded (Tdlib tdlib, TdApi.Message message, long oldMessageId);

  void onMessageSendFailed (Tdlib tdlib, TdApi.Message message, long oldMessageId, int errorCode, String errorMessage);

  void onMessagesDeleted (Tdlib tdlib, long chatId, long[] messageIds);
}
