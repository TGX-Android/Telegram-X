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
 *
 * File created on 18/02/2018
 */
package org.thunderdog.challegram.telegram;

import org.drinkless.tdlib.TdApi;

public interface GlobalMessageListener {
  void onNewMessage (Tdlib tdlib, TdApi.Message message);

  void onNewMessages (Tdlib tdlib, TdApi.Message[] messages);

  void onMessageSendSucceeded (Tdlib tdlib, TdApi.Message message, long oldMessageId);

  void onMessageSendFailed (Tdlib tdlib, TdApi.Message message, long oldMessageId, TdApi.Error error);

  void onMessagesDeleted (Tdlib tdlib, long chatId, long[] messageIds);
}
